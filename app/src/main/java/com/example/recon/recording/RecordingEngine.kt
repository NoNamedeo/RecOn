package com.example.recon.recording

import com.example.recon.config.RecordingConfig
import com.example.recon.data.AudioMetadataReader
import com.example.recon.data.RecordEntity
import com.example.recon.data.RecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingEngine(
    private val scope: CoroutineScope,
    private val segmentDirectory: File,
    private val recordingsDirectory: File,
    private val streamFactory: (RecordingConfig) -> StreamRecording,
    private val retentionManager: RetentionManager,
    private val exporter: AudioExporter,
    private val recordRepository: RecordRepository,
    private val metadataReader: AudioMetadataReader,
    private val sessionStore: RecordingSessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val operationMutex = Mutex()
    private val completedSegments = mutableListOf<RecordingSegment>()
    private var stream: StreamRecording? = null
    private var eventJob: Job? = null
    private var sessionStartedAt: Long = 0L

    suspend fun start(config: RecordingConfig) = operationMutex.withLock {
        check(stream == null) { "Registrazione già attiva" }
        val recovered = segmentFiles()
        check(recovered.isEmpty()) {
            "Esiste già un buffer recuperabile: salvarlo o eliminarlo prima di iniziare"
        }
        sessionStore.update(RecordingStatus.STARTING, hasBufferedAudio = false)
        val newStream = streamFactory(config)
        try {
            newStream.start()
            stream = newStream
            sessionStartedAt = now()
            eventJob = scope.launch { collectEvents(newStream, config) }
            sessionStore.update(RecordingStatus.RECORDING, hasBufferedAudio = false)
        } catch (error: Exception) {
            sessionStore.update(RecordingStatus.ERROR, false, readableMessage(error))
            throw error
        }
    }

    suspend fun save(title: String): RecordEntity = operationMutex.withLock {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Inserisci un titolo" }
        sessionStore.update(RecordingStatus.FINALIZING, hasBufferedAudio = true)
        var finalFile: File? = null
        var partialFile: File? = null
        var databasePersisted = false
        try {
            stopActiveStreamLocked()
            val segments = recoverAllSegments()
            require(segments.isNotEmpty()) { "Il buffer non contiene audio" }
            finalFile = createFinalFile()
            partialFile = File(
                finalFile.parentFile,
                "${finalFile.nameWithoutExtension}.partial.m4a",
            )
            exporter.export(segments, partialFile)
            check(partialFile.isFile && partialFile.length() > 0L) { "Export audio non valido" }
            check(partialFile.renameTo(finalFile)) { "Impossibile finalizzare il file audio" }

            val endedAt = now()
            val duration = withContext(Dispatchers.IO) { metadataReader.durationMillis(finalFile) }
            val record = RecordEntity(
                title = normalizedTitle,
                filePath = finalFile.absolutePath,
                startedAt = sessionStartedAt.takeIf { it > 0L }
                    ?: (endedAt - duration).coerceAtLeast(0L),
                endedAt = endedAt,
                durationMillis = duration,
            )
            val id = recordRepository.insert(record)
            databasePersisted = true
            val cleanupFailures = deleteSegmentsAfterSuccessfulSave(segments)
            val saved = record.copy(id = id)
            resetSessionLocked()
            sessionStore.update(RecordingStatus.IDLE, hasBufferedAudio = false)
            sessionStore.message(
                if (cleanupFailures.isEmpty()) {
                    "Registrazione salvata"
                } else {
                    "Registrazione salvata; alcuni file temporanei saranno ripuliti in seguito"
                },
            )
            saved
        } catch (error: Exception) {
            partialFile?.takeIf { it.exists() }?.delete()
            if (!databasePersisted) finalFile?.takeIf { it.exists() }?.delete()
            val hasAudio = segmentFiles().isNotEmpty()
            sessionStore.update(RecordingStatus.ERROR, hasAudio, readableMessage(error))
            throw error
        }
    }

    suspend fun discard() = operationMutex.withLock {
        sessionStore.update(RecordingStatus.STOPPING, hasBufferedAudio = true)
        try {
            stopActiveStreamLocked()
            val failures = segmentFiles().filterNot { it.delete() }
            check(failures.isEmpty()) { "Alcuni segmenti non possono essere eliminati" }
            resetSessionLocked()
            sessionStore.update(RecordingStatus.IDLE, hasBufferedAudio = false)
            sessionStore.message("Registrazione scartata")
        } catch (error: Exception) {
            val hasAudio = segmentFiles().isNotEmpty()
            sessionStore.update(RecordingStatus.ERROR, hasAudio, readableMessage(error))
            throw error
        }
    }

    suspend fun cleanup() {
        operationMutex.withLock {
            runCatching { stopActiveStreamLocked() }
            val hasAudio = segmentFiles().isNotEmpty()
            resetSessionLocked()
            sessionStore.update(
                if (hasAudio) RecordingStatus.ERROR else RecordingStatus.IDLE,
                hasAudio,
                if (hasAudio) "Registrazione interrotta: il buffer può ancora essere salvato" else null,
            )
        }
    }

    private suspend fun collectEvents(recording: StreamRecording, config: RecordingConfig) {
        recording.events.collect { event ->
            when (event) {
                is RecordingEvent.SegmentCompleted -> operationMutex.withLock {
                    completedSegments += event.segment
                    try {
                        retentionManager.cleanStream(
                            completedSegments,
                            config.bufferDurationMillis,
                            config.segmentDurationMillis,
                        )
                        sessionStore.update(RecordingStatus.RECORDING, hasBufferedAudio = true)
                    } catch (error: Exception) {
                        runCatching { recording.stop() }.getOrNull()?.let { completedSegments += it }
                        stream = null
                        sessionStore.update(
                            RecordingStatus.ERROR,
                            hasBufferedAudio = segmentFiles().isNotEmpty(),
                            errorMessage = readableMessage(error),
                        )
                    }
                }
                is RecordingEvent.Error -> operationMutex.withLock {
                    event.recoverableSegment?.let { completedSegments += it }
                    stream = null
                    sessionStore.update(
                        RecordingStatus.ERROR,
                        segmentFiles().isNotEmpty(),
                        event.description,
                    )
                }
                is RecordingEvent.Stopped -> Unit
            }
        }
    }

    private suspend fun stopActiveStreamLocked() {
        val active = stream
        stream = null
        active?.stop()?.let { completedSegments += it }
        val collector = eventJob
        eventJob = null
        collector?.cancelAndJoin()
    }

    private suspend fun recoverAllSegments(): List<RecordingSegment> = withContext(Dispatchers.IO) {
        val known = completedSegments.associateBy { it.file.absolutePath }
        segmentFiles().map { file ->
            known[file.absolutePath] ?: run {
                val duration = runCatching { metadataReader.durationMillis(file) }.getOrDefault(0L)
                val endedAt = file.lastModified().takeIf { it > 0L } ?: now()
                RecordingSegment(file, (endedAt - duration).coerceAtLeast(0L), endedAt)
            }
        }.sortedBy { it.startedAt }
    }

    private fun segmentFiles(): List<File> {
        segmentDirectory.mkdirs()
        return segmentDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("m4a", ignoreCase = true) && it.length() > 0L }
            .sortedBy { it.lastModified() }
    }

    private fun createFinalFile(): File {
        check(recordingsDirectory.exists() || recordingsDirectory.mkdirs())
        val timestamp = FINAL_NAME_FORMAT.get()!!.format(Date(now()))
        return File(recordingsDirectory, "$timestamp.m4a")
    }

    private fun deleteSegmentsAfterSuccessfulSave(segments: List<RecordingSegment>): List<File> =
        segments.map { it.file }.filter { it.exists() && !it.delete() }

    private fun resetSessionLocked() {
        completedSegments.clear()
        sessionStartedAt = 0L
    }

    private fun readableMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "Errore di registrazione inatteso"

    private companion object {
        val FINAL_NAME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH-mm-ss_dd-MM-yyyy", Locale.ITALY)
        }
    }
}
