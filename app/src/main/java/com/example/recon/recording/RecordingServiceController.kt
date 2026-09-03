package com.example.recon.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object RecordingServiceController {
    fun start(context: Context) = send(context, RecOnForegroundService.ACTION_START)

    fun save(context: Context, title: String) = send(
        context,
        RecOnForegroundService.ACTION_SAVE,
        title,
    )

    fun discard(context: Context) = send(context, RecOnForegroundService.ACTION_DISCARD)

    private fun send(context: Context, action: String, title: String? = null) {
        val intent = Intent(context, RecOnForegroundService::class.java).setAction(action)
        if (title != null) intent.putExtra(RecOnForegroundService.EXTRA_TITLE, title)
        ContextCompat.startForegroundService(context, intent)
    }
}
