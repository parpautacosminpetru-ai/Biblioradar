package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAnchorsTest {
    @Test fun unchangedTextKeepsPreciseAnchor() {
        val text = "În cronica lui X apare Y, iar Y este reluat apoi."
        val start = text.indexOf("Y,")
        val anno = selectedTextAnchor("p", text, start, start + 2, "AFIRMATIE", "Notă")
        val resolved = resolveTextAnchor(text, anno)
        assertEquals(AnchorStatus.EXACT, resolved.status)
        assertEquals(start, resolved.start)
        assertEquals("Y,", anno.exactQuote)
    }

    @Test fun insertedTextBeforeAnAnchorIsRecalibratedWithoutGuessing() {
        val text = "Autorul menționează cetatea."
        val start = text.indexOf("cetatea")
        val anno = selectedTextAnchor("p", text, start, start + 7, "LOC", "")
        val changed = "În introducere, " + text
        val resolved = resolveTextAnchor(changed, anno)
        assertEquals(AnchorStatus.MOVED, resolved.status)
        assertEquals(changed.indexOf("cetatea"), resolved.start)
    }

    @Test fun twoUnresolvedIdenticalQuotesRemainAmbiguous() {
        val text = "Roma; Roma."
        val anno = selectedTextAnchor("p", text, 0, 4, "LOC", "")
        // Both context boundaries are removed; never choose by offset alone.
        val changed = "Roma Roma"
        val resolved = resolveTextAnchor(changed, anno)
        assertEquals(AnchorStatus.AMBIGUOUS, resolved.status)
        assertFalse(resolved.resolved)
    }

    @Test fun deletedPassageIsFlaggedNotFabricated() {
        val old = "Documentul indică anul 1241."
        val a = selectedTextAnchor("page", old, old.indexOf("1241"),
            old.indexOf("1241") + 4, "DATA", "")
        val resolved = resolveTextAnchor("Documentul indică anul incert.", a)
        assertEquals(AnchorStatus.MISSING, resolved.status)
        assertEquals(-1, resolved.start)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesEmptySelection() {
        selectedTextAnchor("p", "cuvinte", 0, 0, "AFIRMATIE", "")
    }

    @Test fun annotationsDoNotDiscardContextOrWholeText() {
        val text = "Întregul document este păstrat, pasajul este aici, apoi continuă."
        val start = text.indexOf("pasajul")
        val anno = selectedTextAnchor("p", text, start, start + 7, "NOTA", "Observație")
        assertTrue(anno.contextBefore.contains("păstrat"))
        assertTrue(anno.contextAfter.contains("este aici"))
        assertEquals(text, "Întregul document este păstrat, pasajul este aici, apoi continuă.")
    }
}
