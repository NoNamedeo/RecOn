package com.example.recon

import android.content.Context
import com.example.recon.config.RecordingSettingsRepository
import com.example.recon.data.AppDatabase
import com.example.recon.data.AudioMetadataReader
import com.example.recon.data.RecordRepository
import com.example.recon.recording.AudioExporter
import com.example.recon.recording.MediaRecorderStreamRecording
import com.example.recon.recording.RecordingEngine
import com.example.recon.recording.RecordingSessionStore
import com.example.recon.recording.RetentionManager
import com.example.recon.recording.SegmentFileFactory
import kotlinx.coroutines.CoroutineScope
import java.io.File

class AppContainer(private val context: Context) {
    val recordingSettingsRepository = RecordingSettingsRepository(context)
    val recordingSessionStore = RecordingSessionStore()

    private val audioMetadataReader = AudioMetadataReader()
    private val database by lazy { AppDatabase.create(context) }
    val recordRepository by lazy {
        RecordRepository(
            dao = database.recordDao(),
            recordingsDirectory = recordingsDirectory,
            metadataReader = audioMetadataReader,
        )
    }

    val segmentDirectory: File = File(context.filesDir, "recording_segments")
    val recordingsDirectory: File = File(context.filesDir, "recordings")

    fun createRecordingEngine(scope: CoroutineScope): RecordingEngine {
        val fileFactory = SegmentFileFactory(segmentDirectory)
        return RecordingEngine(
            scope = scope,
            segmentDirectory = segmentDirectory,
            recordingsDirectory = recordingsDirectory,
            streamFactory = { config ->
                MediaRecorderStreamRecording(
                    context = context,
                    config = config,
                    fileFactory = fileFactory,
                    scope = scope,
                )
            },
            retentionManager = RetentionManager(),
            exporter = AudioExporter(context),
            recordRepository = recordRepository,
            metadataReader = audioMetadataReader,
            sessionStore = recordingSessionStore,
        )
    }
}
