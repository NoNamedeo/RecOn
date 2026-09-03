package com.example.recon.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.recon.data.RecordEntity
import java.io.File

object RecordingSharer {
    fun share(context: Context, record: RecordEntity) {
        val file = File(record.filePath)
        require(file.isFile) { "Il file della registrazione non è disponibile" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Condividi registrazione"))
    }
}
