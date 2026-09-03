package com.example.recon.recording

import java.io.IOException

class RetentionManager {
    fun cleanStream(
        completedSegments: MutableList<RecordingSegment>,
        maximumBufferDurationMillis: Long,
        activeSegmentReserveMillis: Long,
    ): List<RecordingSegment> {
        val completedBudget =
            (maximumBufferDurationMillis - activeSegmentReserveMillis).coerceAtLeast(0L)
        val removed = mutableListOf<RecordingSegment>()
        var totalDuration = completedSegments.sumOf { it.durationMillis }

        while (completedSegments.isNotEmpty() && totalDuration > completedBudget) {
            val oldest = completedSegments.removeAt(0)
            totalDuration -= oldest.durationMillis
            removed += oldest
        }
        val deletionFailures = removed.filter { segment ->
            if (segment.file.exists() && !segment.file.delete()) {
                completedSegments.add(0, segment)
                true
            } else {
                false
            }
        }
        if (deletionFailures.isNotEmpty()) {
            throw IOException("Impossibile eliminare i segmenti più vecchi del buffer")
        }
        return removed.filterNot { it in completedSegments }
    }
}
