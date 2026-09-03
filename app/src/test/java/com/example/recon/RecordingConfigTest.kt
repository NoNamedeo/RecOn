package com.example.recon

import com.example.recon.config.RecordingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordingConfigTest {
    @Test
    fun defaultsMatchProductRequirements() {
        val config = RecordingConfig()

        assertEquals(15, config.segmentDurationMinutes)
        assertEquals(60, config.bufferDurationMinutes)
        assertEquals(14_400_000L, config.segmentSizeBytes)
    }

    @Test
    fun unsupportedDurationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingConfig(segmentDurationMinutes = 7)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecordingConfig(bufferDurationMinutes = 90)
        }
    }
}
