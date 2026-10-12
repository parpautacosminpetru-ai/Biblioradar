package ro.atlasistoric.util

import ro.atlasistoric.data.AssertionEntityLink
import ro.atlasistoric.data.AssertionTopicLink
import ro.atlasistoric.data.EntityRelation
import ro.atlasistoric.data.HistoricalAssertion
import ro.atlasistoric.data.HistoricalEntity
import ro.atlasistoric.data.HistoricalSource
import ro.atlasistoric.data.HistoricalTopic
import ro.atlasistoric.data.StudyCard
import ro.atlasistoric.data.StudyReview
import kotlin.math.max
import kotlin.math.roundToInt

/** Predictable, entirely local scheduling. No ML or unsupported accuracy claims. */
object RecallScheduler {
    const val AGAIN = 0
    const val HARD = 1
    const val GOOD = 2
    const val EASY = 3
    const val DAY_MS = 86_400_000L
    const val AGAIN_DELAY_MS = 15L * 60L * 1000L

    data class Scheduled(val card: StudyCard, val review: StudyReview)

    fun schedule(card: StudyCard, grade: Int, at: Long): Scheduled {
        require(grade in AGAIN..EASY) { "Evaluare necunoscută" }
        require(at >= 0L) { "Timp invalid" }
        val ease = when (grade) {
            AGAIN -> (card.easePermille - 200).coerceIn(1300, 3000)
            HARD -> (card.easePermille - 150).coerceIn(1300, 3000)
            GOOD -> card.easePermille.coerceIn(1300, 3000)
            else -> (card.easePermille + 100).coerceIn(1300, 3000)
        }
        val interval = when (grade) {
            AGAIN -> 0
            HARD -> if (card.intervalDays <= 0) 1
                else max(1, (card.intervalDays * 1.20).roundToInt())
            GOOD -> when (card.repetitions) {
                0 -> 1
                1 -> 3
                else -> max(1, (card.intervalDays * ease / 1000.0).roundToInt())
            }
            else -> if (card.repetitions == 0) 4
                else max(3, (card.intervalDays * ease / 1000.0 * 1.30).roundToInt())
        }.coerceIn(0, 3650)
        val next = if (grade == AGAIN) at + AGAIN_DELAY_MS else at + interval * DAY_MS
        val updated = card.copy(
            dueAt = next,
            intervalDays = interval,
            easePermille = ease,
            repetitions = if (grade == AGAIN) 0 else card.repetitions + 1,
            lapses = card.lapses + if (grade == AGAIN) 1 else 0,
            lastReviewedAt = at,
            updatedAt = at
        )
        return Scheduled(updated, StudyReview(
            cardId = card.id,
            grade = grade,
            reviewedAt = at,
            previousIntervalDays = card.intervalDays,
            nextIntervalDays = interval,
            nextDueAt = next
        ))
    }
}

fun learningPrompt(
    claim: HistoricalAssertion,
    source: HistoricalSource?,
    mode: String,
    entities: List<HistoricalEntity>,
    entityLinks: List<AssertionEntityLink>
): String {
    val nameById = entities.associateBy { it.id }
    val subjects = entityLinks.asSequence().filter { it.assertionId == claim.id }
        .mapNotNull { nameById[it.entityId]?.name }.distinct().take(2).toList()
    val referent = if (subjects.isEmpty()) "afirmația documentată" else subjects.joinToString(" și ")
    val provenance = source?.title?.let { "potrivit sursei «$it»" } ?: "potrivit sursei"
    return when (mode) {
        "DATA" -> "În ce an sau interval este datată $referent, $provenance?"
        "LOC" -> "Ce locuri sunt asociate cu $referent, $provenance?"
        else -> "Ce afirmă $provenance despre $referent?"
    }
}

fun learningAnswer(
    mode: String,
    claim: HistoricalAssertion,
    entities: List<HistoricalEntity>,
    entityLinks: List<AssertionEntityLink>
): String {
    return when (mode) {
        "DATA" -> {
            val start = claim.yearStart
            val end = claim.yearEnd
            when {
                start != null && end != null && start == end -> TimeMath.yearLabel(start)
                start != null && end != null -> "${TimeMath.yearLabel(start)} – ${TimeMath.yearLabel(end)}"
                start != null -> "De la ${TimeMath.yearLabel(start)}"
                end != null -> "Până la ${TimeMath.yearLabel(end)}"
                else -> claim.periodLabel.ifBlank { "Data nu este precizată de sursă." }
            }
        }
        "LOC" -> {
            val byId = entities.associateBy { it.id }
            entityLinks.asSequence().filter { it.assertionId == claim.id && it.role == "LOC" }
                .mapNotNull { byId[it.entityId]?.name }.distinct().joinToString(", ")
                .ifBlank { "Nu există un loc explicit asociat acestei afirmații." }
        }
        else -> claim.passage
    }
}

/**
 * Learning filters are intentionally inclusive about UNKNOWN precision:
 * an annual claim remains viewable at monthly/day zoom, marked as less precise,
 * not silently assigned a month/day or lost.
 */
fun matchesLearningTime(claim: HistoricalAssertion, key: String?): Boolean {
    if (key == null) return true
    if (key == UNKNOWN_TIME_KEY) return claim.yearStart == null &&
        claim.yearEnd == null && claim.periodLabel.isBlank()
    val slot = TemporalNavigation.fromKey(key) ?: return true
    if (slot.era != null) return slot.matches(claim.yearStart, claim.yearEnd,
        claim.month, claim.day, claim.periodLabel)
    if (!TimeMath.overlaps(claim.yearStart, claim.yearEnd, slot.from, slot.through)) return false
    if (slot.month != null && claim.month != null && slot.month != claim.month) return false
    if (slot.day != null && claim.day != null && slot.day != claim.day) return false
    return true
}

fun learningFilteredCardIds(
    cards: List<StudyCard>,
    assertions: List<HistoricalAssertion>,
    entityLinks: List<AssertionEntityLink>,
    topicLinks: List<AssertionTopicLink>,
    relations: List<EntityRelation>,
    topics: List<HistoricalTopic>,
    timeKey: String?,
    placeIds: Set<String>,
    topicIds: Set<String>,
    allTopicsRequired: Boolean = false
): Set<String> {
    val byId = assertions.associateBy { it.id }
    val locs = entityLinks.asSequence().filter { it.role == "LOC" }.groupBy { it.assertionId }
    val themes = topicLinks.groupBy { it.assertionId }
    val thematic = topicIds.map { descendantsOfTopic(it, topics) }
    val slot = TemporalNavigation.fromKey(timeKey)
    val regions = placeIds.flatMap {
        descendantsOfPlace(it, relations, slot?.from, slot?.through)
    }.toSet()
    return cards.filter { card ->
        val claim = byId[card.assertionId] ?: return@filter false
        if (!matchesLearningTime(claim, timeKey)) return@filter false
        if (placeIds.isNotEmpty() &&
            locs[claim.id].orEmpty().none { it.entityId in regions }) return@filter false
        val attached = themes[claim.id].orEmpty().map { it.topicId }.toSet()
        thematic.isEmpty() ||
            (if (allTopicsRequired) thematic.all { subtree -> attached.any { it in subtree } }
            else thematic.any { subtree -> attached.any { it in subtree } })
    }.map { it.id }.toSet()
}
