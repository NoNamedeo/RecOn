package com.example.recon

import com.example.recon.recording.RecordingSegment
import com.example.recon.recording.RetentionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RetentionManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reservesSpaceForActiveSegmentAndDeletesOldestFirst() {
        val files = (1..4).map { index ->
            temporaryFolder.newFile("segment_$index.m4a").apply { writeText("audio") }
        }
        val segments = files.mapIndexed { index, file ->
            val start = index * FIFTEEN_MINUTES
            RecordingSegment(file, start, start + FIFTEEN_MINUTES)
        }.toMutableList()

        val removed = RetentionManager().cleanStream(
            completedSegments = segments,
            maximumBufferDurationMillis = 60L * 60_000L,
            activeSegmentReserveMillis = FIFTEEN_MINUTES,
        )

        assertEquals(listOf(files.first()), removed.map { it.file })
        assertFalse(files.first().exists())
        assertEquals(files.drop(1), segments.map { it.file })
        assertTrue(files.drop(1).all { it.exists() })
    }

    @Test
    fun deletionFailureIsReportedAndSegmentRemainsTracked() {
        val nonEmptyDirectory = temporaryFolder.newFolder("undeletable.m4a")
        temporaryFolder.newFile("undeletable.m4a/content")
        val newerFile = temporaryFolder.newFile("newer.m4a")
        val segments = mutableListOf(
            RecordingSegment(nonEmptyDirectory, 0L, FIFTEEN_MINUTES),
            RecordingSegment(newerFile, FIFTEEN_MINUTES, FIFTEEN_MINUTES * 2),
        )

        assertThrows(java.io.IOException::class.java) {
            RetentionManager().cleanStream(
                completedSegments = segments,
                maximumBufferDurationMillis = FIFTEEN_MINUTES * 2,
                activeSegmentReserveMillis = FIFTEEN_MINUTES,
            )
        }
        assertTrue(segments.any { it.file == nonEmptyDirectory })
    }

    private companion object {
        const val FIFTEEN_MINUTES = 15L * 60_000L
    }
}
