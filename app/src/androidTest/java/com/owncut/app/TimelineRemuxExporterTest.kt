package com.owncut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimelineRemuxExporterTest {
    @Test
    fun acceptsContiguousSameSourceClips() {
        val plan = TimelineRemuxExporter.makePlan(listOf(
            clip("left", start = 0, duration = 120, sourceIn = 0, sourceOut = 4_000),
            clip("right", start = 120, duration = 150, sourceIn = 5_000, sourceOut = 10_000),
        ))
        assertEquals(listOf("left", "right"), plan.clips.map { it.id })
        assertEquals("content://media/original", plan.mediaId)
    }

    @Test
    fun roundsSubframeDurationDifferenceToNearestVideoFrame() {
        val plan = TimelineRemuxExporter.makePlan(listOf(
            clip("a", start = 0, duration = 180, sourceIn = 0, sourceOut = 6_007),
        ))
        assertEquals(180L, plan.clips.single().durationFrames)
        assertEquals(0L, plan.omittedGapFrames)

        assertThrows(IllegalArgumentException::class.java) {
            TimelineRemuxExporter.makePlan(listOf(
                clip("a", start = 0, duration = 180, sourceIn = 0, sourceOut = 6_020),
            ))
        }
    }

    @Test
    fun compactsLeadingAndInteriorEmptyTimelineRanges() {
        val plan = TimelineRemuxExporter.makePlan(listOf(
            clip("a", start = 16, duration = 120, sourceIn = 0, sourceOut = 4_000),
            clip("b", start = 150, duration = 120, sourceIn = 4_000, sourceOut = 8_000),
        ))
        assertEquals(30L, plan.omittedGapFrames)
        assertEquals(listOf(0L, 120L), plan.clips.map { it.startFrame })
    }

    @Test
    fun rejectsTimelineOverlap() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineRemuxExporter.makePlan(listOf(
                clip("a", start = 0, duration = 120, sourceIn = 0, sourceOut = 4_000),
                clip("b", start = 119, duration = 120, sourceIn = 4_000, sourceOut = 8_000),
            ))
        }
    }

    @Test
    fun rejectsMixedMediaTracksAndInconsistentDuration() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineRemuxExporter.makePlan(listOf(
                clip("a", start = 0, duration = 120, sourceIn = 0, sourceOut = 4_000),
                clip("b", start = 120, duration = 120, sourceIn = 0, sourceOut = 4_000, media = "content://media/other"),
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimelineRemuxExporter.makePlan(listOf(
                clip("a", start = 0, duration = 120, sourceIn = 0, sourceOut = 4_000),
                clip("b", start = 120, duration = 120, sourceIn = 4_000, sourceOut = 8_000, track = "a1"),
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimelineRemuxExporter.makePlan(listOf(
                clip("a", start = 0, duration = 120, sourceIn = 0, sourceOut = 4_100),
            ))
        }
    }

    private fun clip(
        id: String,
        start: Long,
        duration: Long,
        sourceIn: Long,
        sourceOut: Long,
        media: String = "content://media/original",
        track: String = "v1",
    ) = ClipSnapshot(id, track, media, start, duration, sourceIn, sourceOut)
}
