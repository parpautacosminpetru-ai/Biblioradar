package ro.atlasistoric.util

import org.junit.Assert.*
import org.junit.Test
import ro.atlasistoric.data.EntityRelation
import ro.atlasistoric.data.HistoricalTopic

class TimeMathTest {
    @Test fun ancientAndModernCenturies() {
        assertEquals(1, TimeMath.century(100))
        assertEquals(2, TimeMath.century(101))
        assertEquals(-1, TimeMath.century(-100))
        assertEquals(-2, TimeMath.century(-101))
        assertEquals(-100..-1, TimeMath.centuryBounds(-1))
        assertEquals(1201..1300, TimeMath.centuryBounds(13))
    }
    @Test fun intervalFilters() {
        assertTrue(TimeMath.overlaps(1201, 1300, 1250, 1270))
        assertFalse(TimeMath.overlaps(1201, 1300, 1301, 1400))
        assertFalse(TimeMath.overlaps(null, null, 1201, 1300))
    }
    @Test fun nestingIsTimeSensitive() {
        val r = listOf(
            EntityRelation(fromEntityId = "a", toEntityId = "b", relationType = "PART_OF", yearStart = 1300, yearEnd = 1400),
            EntityRelation(fromEntityId = "c", toEntityId = "a", relationType = "PART_OF")
        )
        assertEquals(setOf("b", "a", "c"), descendantsOfPlace("b", r, 1350, 1350))
        assertEquals(setOf("b"), descendantsOfPlace("b", r, 1500, 1500))
    }
    @Test fun topicDescendants() {
        val topics = listOf(HistoricalTopic(id = "1", name = "Politic"), HistoricalTopic(id = "2", name = "Diplomație", parentId = "1"))
        assertEquals(setOf("1", "2"), descendantsOfTopic("1", topics))
    }
}
