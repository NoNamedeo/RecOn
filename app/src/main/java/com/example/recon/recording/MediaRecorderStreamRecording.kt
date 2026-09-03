package com.example.recon.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.example.recon.config.RecordingConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaRecorderStreamRecording(
    private val context: Context,
    private val config: RecordingConfig,
    private val fileFactory: SegmentFileFactory,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : StreamRecording {
    private val lock = Any()
    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 8)
    override val events: Flow<RecordingEvent> = _events.asSharedFlow()

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var nextFile: File? = null
    private var currentStartedAt: Long = 0L
    private var running = false
    private var stopping = false
    private var legacyRotationInProgress = false

    override suspend fun start() = withContext(ioDispatcher) {
        synchronized(lock) {
            check(!running) { "Una registrazione è già attiva" }
            stopping = false
            startRecorderLocked(fileFactory.create(now()))
        }
    }

    override suspend fun stop(): RecordingSegment? = withContext(ioDispatcher) {
        synchronized(lock) {
            if (!running && recorder == null) return@synchronized null
            stopping = true
            val finalFile = currentFile
            val startedAt = currentStartedAt
            val endedAt = now()
            val activeRecorder = recorder

            recorder = null
            running = false
            currentFile = null
            currentStartedAt = 0L
            val unusedNext = nextFile
            nextFile = null

            try {
                activeRecorder?.stop()
            } catch (error: RuntimeException) {
                finalFile?.delete()
                throw IllegalStateException("MediaRecorder non ha prodotto audio valido", error)
            } finally {
                activeRecorder?.reset()
                activeRecorder?.release()
                if (unusedNext?.exists() == true && unusedNext.length() == 0L) unusedNext.delete()
            }

            finalFile
                ?.takeIf { it.isFile && it.length() > 0L }
                ?.let { RecordingSegment(it, startedAt, endedAt) }
                .also { _events.tryEmit(RecordingEvent.Stopped(it)) }
        }
    }

    private fun startRecorderLocked(outputFile: File) {
        val newRecorder = createRecorder()
        try {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(config.outputFormat)
                setAudioEncoder(config.audioEncoder)
                setAudioEncodingBitRate(config.audioBitrateBitsPerSecond)
                setAudioSamplingRate(config.sampleRateHz)
                setAudioChannels(config.channelCount)
                setOutputFile(outputFile.absolutePath)
                setMaxFileSize(config.segmentSizeBytes)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    setMaxDuration(config.segmentDurationMillis.toInt())
                }
                setOnInfoListener(::onInfo)
                setOnErrorListener { _, what, extra ->
                    synchronized(lock) {
                        failLocked("Errore MediaRecorder ($what/$extra)")
                    }
                }
                prepare()
                start()
            }
        } catch (error: Exception) {
            runCatching { newRecorder.reset() }
            newRecorder.release()
            outputFile.delete()
            throw IllegalStateException("Impossibile avviare MediaRecorder", error)
        }

        recorder = newRecorder
        currentFile = outputFile
        currentStartedAt = now()
        running = true
    }

    private fun onInfo(ignored: MediaRecorder, what: Int, extra: Int) {
        synchronized(lock) {
            if (!running || stopping) return
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> prepareNextFileLocked()
                MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> onNextFileStartedLocked()
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED,
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
                -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) rotateLegacyAsync()
                    else failLocked("MediaRecorder non è riuscito a passare al segmento successivo")
                }
            }
        }
    }

    private fun prepareNextFileLocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || nextFile != null) return
        val candidate = fileFactory.create(now())
        try {
            recorder?.setNextOutputFile(candidate)
            nextFile = candidate
        } catch (error: Exception) {
            candidate.delete()
            failLocked("Impossibile preparare il segmento audio successivo", error)
        }
    }

    private fun onNextFileStartedLocked() {
        val completedFile = currentFile ?: return
        val replacement = nextFile ?: return
        val switchedAt = now()
        val completed = RecordingSegment(completedFile, currentStartedAt, switchedAt)
        currentFile = replacement
        currentStartedAt = switchedAt
        nextFile = null
        _events.tryEmit(RecordingEvent.SegmentCompleted(completed))
    }

    private fun rotateLegacyAsync() {
        if (legacyRotationInProgress) return
        legacyRotationInProgress = true
        scope.launch(ioDispatcher) {
            synchronized(lock) {
                try {
                    val oldRecorder = recorder
                    val completedFile = currentFile
                    val startedAt = currentStartedAt
                    val endedAt = now()
                    recorder = null
                    running = false
                    runCatching { oldRecorder?.stop() }
                    oldRecorder?.reset()
                    oldRecorder?.release()
                    if (completedFile?.isFile == true && completedFile.length() > 0L) {
                        _events.tryEmit(
                            RecordingEvent.SegmentCompleted(
                                RecordingSegment(completedFile, startedAt, endedAt),
                            ),
                        )
                    }
                    if (!stopping) startRecorderLocked(fileFactory.create(now()))
                } catch (error: Exception) {
                    failLocked("Impossibile ruotare il segmento audio", error)
                } finally {
                    legacyRotationInProgress = false
                }
            }
        }
    }

    private fun failLocked(description: String, cause: Throwable? = null) {
        val failedFile = currentFile
        val failedStartedAt = currentStartedAt
        val failedAt = now()
        val failedRecorder = recorder
        recorder = null
        running = false
        currentFile = null
        currentStartedAt = 0L
        runCatching { failedRecorder?.stop() }
        runCatching { failedRecorder?.reset() }
        failedRecorder?.release()
        nextFile?.takeIf { it.length() == 0L }?.delete()
        nextFile = null
        val recoverable = failedFile
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.let { RecordingSegment(it, failedStartedAt, failedAt) }
        _events.tryEmit(RecordingEvent.Error(description, cause, recoverable))
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
}
