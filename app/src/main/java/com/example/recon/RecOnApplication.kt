package com.example.recon

import android.app.Application
import com.example.recon.recording.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecOnApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        val hasRecoverableBuffer = container.segmentDirectory.listFiles()
            .orEmpty()
            .any { it.isFile && it.length() > 0L }
        container.recordingSessionStore.update(
            status = if (hasRecoverableBuffer) RecordingStatus.ERROR else RecordingStatus.IDLE,
            hasBufferedAudio = hasRecoverableBuffer,
            errorMessage = if (hasRecoverableBuffer) {
                "È stato recuperato un buffer da una sessione interrotta"
            } else {
                null
            },
        )

        applicationScope.launch {
            runCatching { container.recordRepository.reconcile() }
                .onFailure { error ->
                    container.recordingSessionStore.message(
                        error.message ?: "Impossibile verificare le registrazioni salvate",
                    )
                }
        }
    }
}
