package ro.atlasistoric.util

import ro.atlasistoric.data.AssertionEntityLink
import ro.atlasistoric.data.AssertionTopicLink
import ro.atlasistoric.data.EntityRelation
import ro.atlasistoric.data.HistoricalAssertion
import ro.atlasistoric.data.HistoricalEntity
import ro.atlasistoric.data.HistoricalTopic

/**
 * Immutable, source-backed intersection projection. No statement is copied or generated here.
 * A statement may be visible at multiple geographic nesting levels or in multiple topic bands,
 * but its ID is counted only once per cell.
 */
data class PlaceBranch(
    val id: String,
    val name: String,
    val depth: Int,
    val path: List<String>,
    val expandable: Boolean
) {
    val rowKey: String get() = path.joinToString("/")
}

/**
 * Multiple parents are represented as multiple visual paths to the SAME stable entity ID.
 * The displayed nesting belongs to the selected focus interval; membership is calculated
 * independently for each column's actual time window.
 */
fun buildPlaceBranches(
    places: List<HistoricalEntity>, relations: List<EntityRelation>,
    focusStart: Int?, focusEnd: Int?
): List<PlaceBranch> {
    val ids = places.map { it.id }.toSet()
    val entityById = places.associateBy { it.id }
    val edges = relations.filter {
        it.relationType == "PART_OF" && it.fromEntityId in ids && it.toEntityId in ids &&
            it.fromEntityId != it.toEntityId &&
            TimeMath.relationActive(it.yearStart, it.yearEnd, focusStart, focusEnd)
    }
    val children = edges.groupBy { it.toEntityId }.mapValues { (_, group) ->
        group.mapNotNull { entityById[it.fromEntityId] }.distinctBy { it.id }.sortedBy { it.name }
    }
    val childIds = edges.map { it.fromEntityId }.toSet()
    val roots = places.filter { it.id !in childIds }.sortedBy { it.name }
    val rows = mutableListOf<PlaceBranch>()
    val renderedIds = mutableSetOf<String>()
    fun walk(node: HistoricalEntity, path: List<String>) {
        if (node.id in path || path.size >= 12 || rows.size >= 300) return
        val nextPath = path + node.id
        rows += PlaceBranch(node.id, node.name, path.size, nextPath,
            children[node.id].orEmpty().any { it.id !in nextPath })
        renderedIds += node.id
        children[node.id].orEmpty().forEach { walk(it, nextPath) }
    }
    roots.forEach { walk(it, emptyList()) }
    // Also show disconnected nodes and cycles so imperfect source data is not silently lost.
    places.filter { it.id !in renderedIds }.sortedBy { it.name }
        .forEach { walk(it, emptyList()) }
    return rows
}

data class ThemeBand(val rootTopicId: String, val assertionCount: Int)
data class OverlapCell(val assertionIds: List<String>, val themes: List<ThemeBand>, val withoutTopic: Int) {
    val count: Int get() = assertionIds.size
}
data class OverlapIndex(
    val cells: Map<Pair<String, String>, OverlapCell>,
    val visibleAssertionIds: Set<String>,
    val undatedCount: Int,
    val unlocatedCount: Int
) {
    fun cell(placeId: String, timeKey: String) = cells[placeId to timeKey] ?: EMPTY_CELL
}
private val EMPTY_CELL = OverlapCell(emptyList(), emptyList(), 0)
const val UNKNOWN_TIME_KEY = "ATLAS:UNKNOWN_TIME"
const val UNKNOWN_PLACE_ID = "ATLAS:UNKNOWN_PLACE"

/** The palette uses top-level themes; nested child themes keep their actual classification. */
fun rootTopic(id: String, topics: Map<String, HistoricalTopic>): String {
    var current = id
    val seen = mutableSetOf<String>()
    while (seen.add(current)) {
        val parent = topics[current]?.parentId ?: return current
        if (parent !in topics) return current
        current = parent
    }
    return current
}

fun buildOverlapIndex(
    slots: List<TimeSlice>,
    rows: List<PlaceBranch>,
    assertions: List<HistoricalAssertion>,
    entityLinks: List<AssertionEntityLink>,
    topicLinks: List<AssertionTopicLink>,
    relations: List<EntityRelation>,
    topics: List<HistoricalTopic>,
    selectedTopicIds: Set<String> = emptySet(),
    matchAllTopics: Boolean = false
): OverlapIndex {
    val entitiesByClaim = entityLinks.filter { it.role == "LOC" }
        .groupBy { it.assertionId }.mapValues { it.value.map { edge -> edge.entityId }.toSet() }
    val topicsByClaim = topicLinks.groupBy { it.assertionId }
        .mapValues { it.value.map { edge -> edge.topicId }.toSet() }
    val topicById = topics.associateBy { it.id }
    val branches = selectedTopicIds.map { descendantsOfTopic(it, topics) }
    fun matchesThemes(claim: HistoricalAssertion): Boolean {
        if (branches.isEmpty()) return true
        val related = topicsByClaim[claim.id].orEmpty()
        return if (matchAllTopics) branches.all { branch -> related.any { it in branch } }
            else branches.any { branch -> related.any { it in branch } }
    }
    val eligible = assertions.filter(::matchesThemes)
    val uniquePlaces = rows.map { it.id }.distinct()
    val used = mutableSetOf<String>()
    val output = mutableMapOf<Pair<String, String>, OverlapCell>()
    for (slot in slots) {
        val inTime = eligible.filter {
            (if (slot.key == UNKNOWN_TIME_KEY)
                it.yearStart == null && it.yearEnd == null && it.periodLabel.isBlank()
            else slot.matches(it.yearStart, it.yearEnd, it.month, it.day, it.periodLabel))
        }
        for (placeId in uniquePlaces) {
            val nested = if (placeId == UNKNOWN_PLACE_ID) emptySet()
                else descendantsOfPlace(placeId, relations, slot.from, slot.through)
            val inPlace = inTime.filter { claim ->
                if (placeId == UNKNOWN_PLACE_ID) entitiesByClaim[claim.id].isNullOrEmpty()
                else entitiesByClaim[claim.id].orEmpty().any { it in nested }
            }
            if (inPlace.isEmpty()) continue
            val ids = inPlace.map { it.id }.distinct()
            used.addAll(ids)
            val grouped = mutableMapOf<String, Int>()
            var without = 0
            inPlace.forEach { claim ->
                val roots = topicsByClaim[claim.id].orEmpty().map { rootTopic(it, topicById) }.toSet()
                if (roots.isEmpty()) without++
                else roots.forEach { root -> grouped[root] = (grouped[root] ?: 0) + 1 }
            }
            output[placeId to slot.key] = OverlapCell(ids,
                grouped.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key }).map { ThemeBand(it.key, it.value) }, without)
        }
    }
    return OverlapIndex(
        output, used,
        eligible.count { it.yearStart == null && it.yearEnd == null && it.periodLabel.isBlank() },
        eligible.count { entitiesByClaim[it.id].isNullOrEmpty() }
    )
}
