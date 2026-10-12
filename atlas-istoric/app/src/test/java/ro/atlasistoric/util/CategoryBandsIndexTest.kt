package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.atlasistoric.data.*

class CategoryBandsIndexTest {
    private val political = HistoricalTopic(id = "p", name = "Politic")
    private val diplomacy = HistoricalTopic(id = "d", name = "Diplomatic", parentId = "p")
    private val treaties = HistoricalTopic(id = "t", name = "Tratate", parentId = "d")
    private val economy = HistoricalTopic(id = "e", name = "Economic")
    private val topics = listOf(political, diplomacy, treaties, economy)

    private fun claim(id: String, year: Int? = 1200) = HistoricalAssertion(
        id = id, sourceId = "source", passage = "Pasajul " + id,
        page = "24", yearStart = year, yearEnd = year
    )

    private val columns = listOf(
        TimeSlice("Y:1200", "1200", 1200, 1200),
        TimeSlice("Y:1500", "1500", 1500, 1500),
        TimeSlice(UNKNOWN_TIME_KEY, "Fără dată")
    )

    @Test fun categoryGrowthKeepsSameAssertionIdentityInNestedParallelLanes() {
        val a = claim("a")
        val index = indexCategoryBands(topics, listOf(a),
            listOf(AssertionTopicLink("a", "t"), AssertionTopicLink("a", "d")),
            emptyList(), emptyList(), columns)
        assertEquals(listOf("a"), index.ids("p", "Y:1200"))
        assertEquals(listOf("a"), index.ids("d", "Y:1200"))
        assertEquals(listOf("a"), index.ids("t", "Y:1200"))
        assertEquals(1, index.visibleAssertionIds.size)
        val added = HistoricalTopic(id = "new", name = "Categorie adăugată", parentId = "p")
        val after = indexCategoryBands(topics + added, listOf(a),
            listOf(AssertionTopicLink("a", "t"), AssertionTopicLink("a", "d")),
            emptyList(), emptyList(), columns)
        assertEquals(1, after.count("p", "Y:1200"))
        assertEquals(0, after.count("new", "Y:1200"))
    }

    @Test fun collapsingNestedIndexHidesGrandchildren() {
        val open = categoryBandRows(topics)
        assertEquals(listOf("e", "p", "d", "t"), open.map { it.id })
        val collapsed = categoryBandRows(topics, setOf("p"))
        assertEquals(listOf("e", "p"), collapsed.map { it.id })
        val middle = categoryBandRows(topics, setOf("d"))
        assertEquals(listOf("e", "p", "d"), middle.map { it.id })
    }

    @Test fun undatedAndUnclassifiedAreNeverPlacedInMadeUpTimes() {
        val unclassified = claim("u", null)
        val index = indexCategoryBands(topics, listOf(unclassified),
            emptyList(), emptyList(), emptyList(), columns)
        assertEquals(1, index.count(UNCLASSIFIED_TOPIC_ID, UNKNOWN_TIME_KEY))
        assertEquals(0, index.count(UNCLASSIFIED_TOPIC_ID, "Y:1200"))
        assertEquals(1, index.visibleAssertionIds.size)
    }

    @Test fun spatialFilterUsesTimeSpecificHistoricalContainment() {
        val placeRelations = listOf(EntityRelation(
            id = "belongs", fromEntityId = "region", toEntityId = "europe",
            relationType = "PART_OF", yearStart = 1100, yearEnd = 1300
        ))
        val claims = listOf(claim("old", 1200), claim("later", 1500))
        val links = claims.map { AssertionEntityLink(it.id, "region", "LOC") }
        val thematic = claims.map { AssertionTopicLink(it.id, "p") }
        val filtered = indexCategoryBands(topics, claims, thematic,
            links, placeRelations, columns, setOf("europe"))
        assertEquals(listOf("old"), filtered.ids("p", "Y:1200"))
        assertEquals(0, filtered.count("p", "Y:1500"))
        val noFilter = indexCategoryBands(topics, claims, thematic,
            links, placeRelations, columns)
        assertTrue("later" in noFilter.ids("p", "Y:1500"))
        assertFalse("later" in filtered.visibleAssertionIds)
    }

    @Test fun independentCategoriesHaveParallelBandsWithoutDuplicatingSource() {
        val a = claim("a")
        val index = indexCategoryBands(topics, listOf(a),
            listOf(AssertionTopicLink("a", "t"), AssertionTopicLink("a", "e")),
            emptyList(), emptyList(), columns)
        assertEquals(1, index.count("p", "Y:1200"))
        assertEquals(1, index.count("e", "Y:1200"))
        assertEquals(1, index.visibleAssertionIds.size)
    }
}
