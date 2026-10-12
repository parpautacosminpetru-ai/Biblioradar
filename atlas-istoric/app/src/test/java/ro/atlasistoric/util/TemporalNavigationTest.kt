package ro.atlasistoric.util

import org.junit.Assert.*
import org.junit.Test

class TemporalNavigationTest {
    @Test fun crossingBCToADDoesNotCreateYearZero() {
        assertEquals(1, TemporalNavigation.advanceYear(-1, 1))
        assertEquals(-1, TemporalNavigation.advanceYear(1, -1))
        assertEquals(-1, TemporalNavigation.advanceCentury(1, -1))
        assertTrue(TemporalNavigation.visible("AN", 1, 1).none { it.from == 0 })
    }
    @Test fun rulerExistsWithoutHistoricalSources() {
        assertEquals(15, TemporalNavigation.visible("SECOL", 1848, 1).size)
        assertEquals(19, TemporalNavigation.visible("AN", 1848, 1).size)
        assertEquals(12, TemporalNavigation.visible("LUNA", 1848, 1).size)
        assertEquals(31, TemporalNavigation.visible("ZI", 1848, 3).size)
    }
    @Test fun temporalPrecisionIsNotInvented() {
        val century = TemporalNavigation.fromKey("C:13")!!
        assertTrue(century.matches(1250, 1280, null, null, ""))
        assertFalse(century.matches(null, null, null, null, ""))
        val exact = TemporalNavigation.fromKey("D:1848:3:15")!!
        assertTrue(exact.matches(1848, 1848, 3, 15, ""))
        assertFalse(exact.matches(1848, 1848, null, null, ""))
    }
    @Test fun eraLabelsPreserveOriginalWording() {
        val e = TemporalNavigation.fromKey("E:Epocă necunoscută: etapa B")!!
        assertEquals("Epocă necunoscută: etapa B", e.era)
    }
}
