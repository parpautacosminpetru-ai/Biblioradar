package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Test
import ro.atlasistoric.data.*
import ro.atlasistoric.ui.atlasPlaceOutline
import ro.atlasistoric.ui.threeAxisCounts

class ThreeAxisAtlasTest {
    @Test fun oneClaimInTwoThemesIsNotDuplicated() {
        val topics = listOf(
            HistoricalTopic(id = "politic", name = "Politic"),
            HistoricalTopic(id = "diplomatie", name = "Diplomație", parentId = "politic"),
            HistoricalTopic(id = "religios", name = "Religios")
        )
        val claim = HistoricalAssertion(id = "claim", sourceId = "source", passage = "Text", page = "12",
            yearStart = 1212, yearEnd = 1212)
        val index = threeAxisCounts(
            listOf(claim),
            listOf(AssertionEntityLink("claim", "castilia", "LOC"), AssertionEntityLink("claim", "iberia", "LOC")),
            listOf(AssertionTopicLink("claim", "diplomatie"), AssertionTopicLink("claim", "religios")),
            listOf(EntityRelation(id = "edge", fromEntityId = "castilia", toEntityId = "iberia",
                relationType = "PART_OF", yearStart = 1100, yearEnd = 1300)),
            topics, listOf("iberia"), listOf(TemporalNavigation.fromKey("C:13")!!),
            listOf(topics[0], topics[2])
        )["iberia" to "C:13"]!!
        assertEquals(1, index.total)
        assertEquals(1, index.themes["politic"])
        assertEquals(1, index.themes["religios"])
    }

    @Test fun placeContainmentChangesWithTime() {
        val claim = HistoricalAssertion(id = "a", sourceId = "s", passage = "Text", page = "14",
            yearStart = 1350, yearEnd = 1350)
        val index = threeAxisCounts(
            listOf(claim), listOf(AssertionEntityLink("a", "castilia", "LOC")),
            emptyList(), listOf(EntityRelation(id = "part", fromEntityId = "castilia",
                toEntityId = "iberia", relationType = "PART_OF", yearStart = 1300, yearEnd = 1400)),
            emptyList(), listOf("iberia"), listOf(
                TemporalNavigation.fromKey("C:13")!!,
                TemporalNavigation.fromKey("C:14")!!
            ), emptyList()
        )
        assertEquals(0, index["iberia" to "C:13"]!!.total)
        assertEquals(1, index["iberia" to "C:14"]!!.total)
    }

    @Test fun collapsedNestedPlacesStayHidden() {
        val places = listOf(
            HistoricalEntity(id = "europa", name = "Europa", entityType = "PLACE"),
            HistoricalEntity(id = "iberia", name = "Iberia", entityType = "PLACE"),
            HistoricalEntity(id = "castilia", name = "Castilia", entityType = "PLACE")
        )
        val edges = listOf(
            EntityRelation(id = "e1", fromEntityId = "iberia", toEntityId = "europa", relationType = "PART_OF"),
            EntityRelation(id = "e2", fromEntityId = "castilia", toEntityId = "iberia", relationType = "PART_OF")
        )
        assertEquals(listOf("europa"), atlasPlaceOutline(places, edges, 1201, 1300, emptySet()).map { it.id })
        assertEquals(listOf("europa", "iberia"),
            atlasPlaceOutline(places, edges, 1201, 1300, setOf("europa")).map { it.id })
        assertEquals(listOf("europa", "iberia", "castilia"),
            atlasPlaceOutline(places, edges, 1201, 1300, setOf("europa", "iberia")).map { it.id })
    }
}
