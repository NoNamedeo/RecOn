package com.example.recon.recording

import android.content.Context
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ExportMode { TRANSMUXED, TRANSCODED }

data class AudioExportResult(val file: File, val mode: ExportMode)

@SuppressLint("UnsafeOptInUsageError")
class AudioExporter(private val context: Context) {
    suspend fun export(segments: List<RecordingSegment>, outputFile: File): AudioExportResult {
        require(segments.isNotEmpty()) { "Nessun segmento da esportare" }
        outputFile.parentFile?.let { check(it.exists() || it.mkdirs()) }

        return try {
            exportOnce(segments, outputFile, transmux = true)
            AudioExportResult(outputFile, ExportMode.TRANSMUXED)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (remuxError: Exception) {
            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Impossibile ripulire l'export remux fallito", remuxError)
            }
            try {
                exportOnce(segments, outputFile, transmux = false)
                AudioExportResult(outputFile, ExportMode.TRANSCODED)
            } catch (transcodeError: Exception) {
                transcodeError.addSuppressed(remuxError)
                throw transcodeError
            }
        }
    }

    private suspend fun exportOnce(
        segments: List<RecordingSegment>,
        outputFile: File,
        transmux: Boolean,
    ) = withContext(Dispatchers.Main.immediate) {
        if (outputFile.exists() && !outputFile.delete()) {
            error("Impossibile sostituire il file di esportazione temporaneo")
        }
        val editedItems = segments.map { segment ->
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(segment.file)))
                .setRemoveVideo(true)
                .build()
        }
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            .addItems(editedItems)
            .build()
        val composition = Composition.Builder(sequence)
            .setTransmuxAudio(transmux)
            .build()

        suspendCancellableCoroutine { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (!continuation.isActive) return
                    if (outputFile.isFile && outputFile.length() > 0L) continuation.resume(Unit)
                    else continuation.resumeWithException(
                        IllegalStateException("Media3 ha terminato senza creare un file valido"),
                    )
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            val transformer = Transformer.Builder(context)
                .apply { if (!transmux) setAudioMimeType(MimeTypes.AUDIO_AAC) }
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { transformer.cancel() }
            }
            transformer.start(composition, outputFile.absolutePath)
        }
    }
}
