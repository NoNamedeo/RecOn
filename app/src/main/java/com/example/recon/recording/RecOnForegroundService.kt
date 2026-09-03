package com.example.recon.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.recon.MainActivity
import com.example.recon.R
import com.example.recon.RecOnApplication
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class RecOnForegroundService : Service() {
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        reportFailure(error)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private val application: RecOnApplication get() = getApplication() as RecOnApplication
    private lateinit var engine: RecordingEngine
    private var intentionalStop = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = application.container.createRecordingEngine(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            application.container.recordingSessionStore.update(
                RecordingStatus.ERROR,
                hasBufferedAudio = application.container.segmentDirectory.listFiles()
                    .orEmpty()
                    .any { it.length() > 0L },
                errorMessage = "Il sistema ha interrotto il servizio di registrazione",
            )
            stopServiceAfterCommand()
            return START_NOT_STICKY
        }

        try {
            startAsForeground(notificationFor(intent.action), intent.action)
        } catch (error: Exception) {
            reportFailure(error)
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> startRecording()
            ACTION_SAVE -> saveRecording(intent.getStringExtra(EXTRA_TITLE).orEmpty())
            ACTION_DISCARD -> discardRecording()
            else -> reportFailure(IllegalArgumentException("Comando servizio non riconosciuto"))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (!intentionalStop) {
            application.applicationScope.launch { engine.cleanup() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reportFailure(SecurityException("Permesso microfono non concesso"))
            return
        }
        serviceScope.launch {
            engine.start(application.container.recordingSettingsRepository.config.value)
            updateNotification(getString(R.string.recording_notification_text))
            monitorRecordingFailure()
        }
    }

    private fun saveRecording(title: String) {
        serviceScope.launch {
            updateNotification("Salvataggio della registrazione…")
            engine.save(title)
            stopServiceAfterCommand()
        }
    }

    private fun discardRecording() {
        serviceScope.launch {
            updateNotification("Interruzione della registrazione…")
            engine.discard()
            stopServiceAfterCommand()
        }
    }

    private fun startAsForeground(notification: Notification, action: String?) {
        val foregroundType = when {
            action == ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundType,
        )
    }

    private fun notificationFor(action: String?): Notification {
        val text = when (action) {
            ACTION_SAVE -> "Preparazione del salvataggio…"
            ACTION_DISCARD -> "Preparazione dell'interruzione…"
            else -> getString(R.string.recording_notification_text)
        }
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_channel_description)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        reportFailure(IllegalStateException("Tempo massimo del salvataggio in background superato"))
    }

    private suspend fun monitorRecordingFailure() {
        val failure = application.container.recordingSessionStore.state.first {
            it.status == RecordingStatus.ERROR
        }
        updateNotification(failure.errorMessage ?: "Registrazione interrotta")
        stopServiceAfterCommand()
    }

    private fun reportFailure(error: Throwable) {
        val message = error.message ?: "Errore del servizio di registrazione"
        val hasAudio = application.container.segmentDirectory.listFiles()
            .orEmpty()
            .any { it.isFile && it.length() > 0L }
        application.container.recordingSessionStore.update(
            RecordingStatus.ERROR,
            hasAudio,
            message,
        )
        application.container.recordingSessionStore.message(message)
        stopServiceAfterCommand()
    }

    private fun stopServiceAfterCommand() {
        intentionalStop = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.example.recon.action.START"
        const val ACTION_SAVE = "com.example.recon.action.SAVE"
        const val ACTION_DISCARD = "com.example.recon.action.DISCARD"
        const val EXTRA_TITLE = "recording_title"

        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
    }
}
