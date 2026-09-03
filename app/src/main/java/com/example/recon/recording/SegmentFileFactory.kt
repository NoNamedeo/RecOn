package com.example.recon.recording

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class SegmentFileFactory(private val directory: File) {
    private val sequence = AtomicInteger()

    fun create(nowMillis: Long = System.currentTimeMillis()): File {
        check(directory.exists() || directory.mkdirs()) {
            "Impossibile creare la directory dei segmenti"
        }
        val timestamp = FORMATTER.get()!!.format(Date(nowMillis))
        return File(directory, "segment_${timestamp}_${sequence.getAndIncrement()}.m4a")
    }

    private companion object {
        val FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH-mm-ss_dd-MM-yyyy", Locale.ITALY)
        }
    }
}
