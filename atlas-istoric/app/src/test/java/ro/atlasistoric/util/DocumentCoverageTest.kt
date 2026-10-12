package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentCoverageTest {
    @Test fun everyCharacterIsPreservedWhenPartitioning() {
        val text = "Titlu\n\nConținut  cu  spații!\r\n\r\n" +
            "A".repeat(1250) + "\n\nUltimul rând  "
        val segments = segmentEntireTranscription(text, 240)
        assertTrue(segments.size > 3)
        assertEquals(text, segments.joinToString("") { it.text })
        assertEquals(0, segments.first().start)
        assertEquals(text.length, segments.last().end)
        assertTrue(segments.all { it.end - it.start in 1..240 })
        assertTrue(segments.zipWithNext().all { (a,b) -> a.end == b.start })
    }

    @Test fun emptyTranscriptionCreatesNoRecords() {
        assertTrue(segmentEntireTranscription("").isEmpty())
        assertEquals(0, documentCoverage("", emptyList(), emptyList()).segmentCount)
    }

    @Test fun onlyExplicitReviewMarkersCompleteCoverage() {
        val text = "Prima informație.\n\nA doua afirmație."
        val windows = segmentEntireTranscription(text, 100)
        val unreviewed = documentCoverage(text, windows, emptyList())
        assertEquals(0, unreviewed.reviewedSegments)
        assertEquals(windows.size, unreviewed.pendingSegments)
        val notes = listOf(selectedTextAnchor("p", text, 0, 5, "AFIRMATIE", ""))
        val withNotes = documentCoverage(text, windows, notes)
        assertEquals(0, withNotes.reviewedSegments)
        assertEquals(1, withNotes.annotatedSegments)
        val checked = windows.map {
            selectedTextAnchor("p", text, it.start, it.end, "SEGMENT_VERIFICAT", "")
        }
        val after = documentCoverage(text, windows, notes + checked)
        assertEquals(windows.size, after.reviewedSegments)
        assertEquals(0, after.pendingSegments)
        assertEquals(0, after.pendingCharacters)
    }

    @Test fun removedQuotationNeverCountsAsReviewed() {
        val text = "A".repeat(200)
        val windows = segmentEntireTranscription(text, 100)
        val a = selectedTextAnchor("p", text, windows[0].start, windows[0].end,
            "SEGMENT_VERIFICAT", "")
        val changed = "B".repeat(200)
        val otherWindows = segmentEntireTranscription(changed, 100)
        assertEquals(0, documentCoverage(changed, otherWindows, listOf(a)).reviewedSegments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotSetZeroLengthWindows() {
        segmentEntireTranscription("abcdefg", 0)
    }
}
