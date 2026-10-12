package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.atlasistoric.data.*

class OverlapIndexTest {
    private val spain = HistoricalEntity(id = "sp", name = "Spania", entityType = "PLACE")
    private val europe = HistoricalEntity(id = "eu", name = "Europa", entityType = "PLACE")
    private val africa = HistoricalEntity(id = "af", name = "Africa", entityType = "PLACE")
    private val partInEurope = EntityRelation(id = "r1", fromEntityId = "sp", toEntityId = "eu",
        relationType = "PART_OF", yearStart = 1100, yearEnd = 1300)
    private val partInAfrica = EntityRelation(id = "r2", fromEntityId = "sp", toEntityId = "af",
        relationType = "PART_OF", yearStart = 1400, yearEnd = 1550)

    private val politics = HistoricalTopic(id = "p", name = "Politic")
    private val diplomacy = HistoricalTopic(id = "d", name = "Diplomație", parentId = "p")
    private val economy = HistoricalTopic(id = "e", name = "Economic")

    private fun claim(id: String, year: Int? = 1200) = HistoricalAssertion(
        id = id, sourceId = "s", passage = "Pasajul $id", page = "42",
        yearStart = year, yearEnd = year
    )

    private val slots = listOf(
        TimeSlice("Y:1200", "1200", 1200, 1200),
        TimeSlice("Y:1500", "1500", 1500, 1500),
        TimeSlice(UNKNOWN_TIME_KEY, "Fără dată")
    )
    private val rows = listOf(
        PlaceBranch("eu", "Europa", 0, listOf("eu"), true),
        PlaceBranch("af", "Africa", 0, listOf("af"), true),
        PlaceBranch("sp", "Spania", 1, listOf("eu", "sp"), false),
        PlaceBranch(UNKNOWN_PLACE_ID, "Fără loc", 0, listOf(UNKNOWN_PLACE_ID), false)
    )

    @Test fun nestingIsTimeSensitiveAndDoesNotDuplicateClaimInCell() {
        val a = claim("A")
        val index = buildOverlapIndex(slots, rows, listOf(a),
            listOf(AssertionEntityLink("A", "sp", "LOC")),
            listOf(AssertionTopicLink("A", "d"), AssertionTopicLink("A", "e")),
            listOf(partInEurope, partInAfrica),
            listOf(politics, diplomacy, economy))
        assertEquals(1, index.cell("eu", "Y:1200").count)
        assertEquals(1, index.cell("sp", "Y:1200").count)
        assertEquals(0, index.cell("af", "Y:1200").count)
        assertEquals(0, index.cell("eu", "Y:1500").count)
        assertEquals(2, index.cell("eu", "Y:1200").themes.size)
        assertEquals(1, index.visibleAssertionIds.size)
    }

    @Test fun themeAndRequiresBothNestedBranches() {
        val a = claim("A")
        val b = claim("B")
        val links = listOf(
            AssertionTopicLink("A", "d"), AssertionTopicLink("A", "e"),
            AssertionTopicLink("B", "d")
        )
        val locs = listOf(
            AssertionEntityLink("A", "sp", "LOC"),
            AssertionEntityLink("B", "sp", "LOC")
        )
        val index = buildOverlapIndex(slots, rows, listOf(a, b), locs,
            links, listOf(partInEurope), listOf(politics, diplomacy, economy),
            selectedTopicIds = setOf("p", "e"), matchAllTopics = true)
        assertEquals(listOf("A"), index.cell("sp", "Y:1200").assertionIds)
    }

    @Test fun unknownTimeAndPlaceRemainReachableWithoutInventingDates() {
        val undated = claim("U", year = null)
        val known = claim("K")
        val index = buildOverlapIndex(slots, rows, listOf(undated, known),
            listOf(AssertionEntityLink("U", "sp", "LOC")),
            emptyList(), listOf(partInEurope),
            listOf(politics, diplomacy, economy))
        assertEquals(1, index.cell("sp", UNKNOWN_TIME_KEY).count)
        assertEquals(1, index.cell(UNKNOWN_PLACE_ID, "Y:1200").count)
        assertEquals(0, index.cell("sp", "Y:1200").count)
        assertEquals(1, index.undatedCount)
        assertEquals(1, index.unlocatedCount)
        assertFalse(index.cell("eu", "Y:1200").assertionIds.contains("U"))
        assertTrue(index.cell("sp", UNKNOWN_TIME_KEY).assertionIds.contains("U"))
    }

    @Test fun placeBranchCanAppearUnderTwoParentsWhileReusingIdentity() {
        val links = listOf(
            EntityRelation(id = "a", fromEntityId = "sp", toEntityId = "eu",
                relationType = "PART_OF"),
            EntityRelation(id = "b", fromEntityId = "sp", toEntityId = "af",
                relationType = "PART_OF")
        )
        val result = buildPlaceBranches(listOf(spain, europe, africa), links, 1200, 1200)
        assertEquals(2, result.count { it.id == "sp" })
        assertEquals(2, result.filter { it.id == "sp" }.map { it.path.first() }.toSet().size)
    }
}
