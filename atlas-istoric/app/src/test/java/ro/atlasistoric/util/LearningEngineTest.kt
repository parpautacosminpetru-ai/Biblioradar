package ro.atlasistoric.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.atlasistoric.data.*

class LearningEngineTest {
    private val claim = HistoricalAssertion(
        id = "claim1", sourceId = "source1", passage = "Afirmație documentară exactă.",
        page = "83", yearStart = 1241, yearEnd = 1241, precision = "AN"
    )
    private val source = HistoricalSource(
        id = "source1", author = "Autor A", title = "Cronica A"
    )
    private val loc = HistoricalEntity(id = "trans", name = "Transilvania", entityType = "PLACE")
    private val politics = HistoricalTopic(id = "polit", name = "Politic")
    private val diplomacy = HistoricalTopic(id = "diplom", name = "Diplomație", parentId = "polit")
    private val economic = HistoricalTopic(id = "econ", name = "Economic")
    private val card = StudyCard(id = "c1", assertionId = claim.id,
        question = "Ce documentează această sursă?")

    @Test fun initiallyDueThenGoodIsOneDayLater() {
        val now = 1_700_000_000_000L
        val review = RecallScheduler.schedule(card, RecallScheduler.GOOD, now)
        assertEquals(now + RecallScheduler.DAY_MS, review.card.dueAt)
        assertEquals(1, review.card.intervalDays)
        assertEquals(1, review.card.repetitions)
        assertEquals(0, review.review.previousIntervalDays)
        assertEquals("c1", review.review.cardId)
    }

    @Test fun repeatedGoodAndEasyExtendInterval() {
        val start = 1_700_000_000_000L
        val first = RecallScheduler.schedule(card, RecallScheduler.GOOD, start).card
        val second = RecallScheduler.schedule(first, RecallScheduler.GOOD, first.dueAt).card
        val third = RecallScheduler.schedule(second, RecallScheduler.EASY, second.dueAt).card
        assertEquals(3, second.intervalDays)
        assertTrue(third.intervalDays > second.intervalDays)
        assertTrue(third.easePermille > second.easePermille)
    }

    @Test fun againSchedulesSoonAndRetainsHistoryMetadata() {
        val start = 1_700_000_000_000L
        val studied = RecallScheduler.schedule(card, RecallScheduler.EASY, start).card
        val failed = RecallScheduler.schedule(studied, RecallScheduler.AGAIN, studied.dueAt)
        assertEquals(studied.dueAt + 900_000L, failed.card.dueAt)
        assertEquals(0, failed.card.repetitions)
        assertEquals(1, failed.card.lapses)
        assertEquals(studied.intervalDays, failed.review.previousIntervalDays)
        assertEquals(0, failed.review.nextIntervalDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownGradesNeverWriteSchedules() {
        RecallScheduler.schedule(card, 9, 1_000L)
    }

    @Test fun sourceAnswersAreNotFabricatedOrCopied() {
        val nameLinks = listOf(AssertionEntityLink(claim.id, loc.id, "LOC"))
        assertEquals(claim.passage, learningAnswer("PASAJ", claim, listOf(loc), nameLinks))
        assertEquals("1241 d.Hr.", learningAnswer("DATA", claim, listOf(loc), nameLinks))
        assertEquals("Transilvania", learningAnswer("LOC", claim, listOf(loc), nameLinks))
        val prompt = learningPrompt(claim, source, "DATA", listOf(loc), nameLinks)
        assertTrue(prompt.contains("Cronica A"))
        assertTrue(prompt.contains("Transilvania"))
        assertFalse(prompt.contains("1241"))
    }

    @Test fun yearPrecisionIsVisibleAtDayZoomWithoutInventingDay() {
        assertTrue(matchesLearningTime(claim, "D:1241:5:12"))
        assertTrue(matchesLearningTime(claim, "Y:1241"))
        assertFalse(matchesLearningTime(claim, "Y:1242"))
        assertFalse(matchesLearningTime(claim, UNKNOWN_TIME_KEY))
        val precise = claim.copy(month = 4, day = 10, precision = "ZI")
        assertFalse(matchesLearningTime(precise, "D:1241:5:12"))
        assertTrue(matchesLearningTime(precise, "D:1241:4:10"))
    }

    @Test fun learningRespectsThematicAndTimeVaryingTerritories() {
        val two = listOf(claim, claim.copy(id = "new", yearStart = 1530, yearEnd = 1530))
        val cards = listOf(card, card.copy(id = "c2", assertionId = "new"))
        val entityLinks = listOf(
            AssertionEntityLink("claim1", "trans", "LOC"),
            AssertionEntityLink("new", "trans", "LOC")
        )
        val topics = listOf(politics, diplomacy, economic)
        val topicLinks = listOf(
            AssertionTopicLink("claim1", "diplom"),
            AssertionTopicLink("new", "diplom")
        )
        val relation = EntityRelation(id = "r", fromEntityId = "trans", toEntityId = "eu",
            relationType = "PART_OF", yearStart = 1200, yearEnd = 1300)
        val early = learningFilteredCardIds(cards, two, entityLinks, topicLinks,
            listOf(relation), topics, "Y:1241", setOf("eu"), setOf("polit"))
        assertEquals(setOf("c1"), early)
        val late = learningFilteredCardIds(cards, two, entityLinks, topicLinks,
            listOf(relation), topics, "Y:1530", setOf("eu"), setOf("polit"))
        assertEquals(emptySet<String>(), late)
    }

    @Test fun alternativesCanRemainConcurrentAndSourceBacked() {
        val second = claim.copy(id = "other", sourceId = "second",
            passage = "O altă interpretare documentată.")
        val cards = listOf(card, card.copy(id = "c2", assertionId = second.id))
        val ids = learningFilteredCardIds(cards, listOf(claim, second),
            emptyList(), emptyList(), emptyList(), emptyList(),
            null, emptySet(), emptySet())
        assertEquals(2, ids.size)
        assertEquals(claim.passage, learningAnswer("PASAJ", claim, emptyList(), emptyList()))
        assertEquals(second.passage, learningAnswer("PASAJ", second, emptyList(), emptyList()))
    }
}
