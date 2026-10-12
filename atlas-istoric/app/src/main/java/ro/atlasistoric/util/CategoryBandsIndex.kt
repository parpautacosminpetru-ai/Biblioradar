package ro.atlasistoric.util

import ro.atlasistoric.data.AssertionEntityLink
import ro.atlasistoric.data.AssertionTopicLink
import ro.atlasistoric.data.EntityRelation
import ro.atlasistoric.data.HistoricalAssertion
import ro.atlasistoric.data.HistoricalTopic

/** A root topic is a user-defined index; child topics are nested categories. */
data class CategoryBandRow(
    val id: String,
    val label: String,
    val rootId: String,
    val depth: Int,
    val hasChildren: Boolean
)

/** All bands are projections of ONE set of source assertions, not duplicates. */
data class CategoryBandIndex(
    val cells: Map<Pair<String, String>, List<String>>,
    val visibleAssertionIds: Set<String>
) {
    fun ids(topicId: String, timeKey: String): List<String> =
        cells[topicId to timeKey].orEmpty()
    fun count(topicId: String, timeKey: String): Int = ids(topicId, timeKey).size
}

fun categoryBandRows(
    topics: List<HistoricalTopic>,
    collapsedIds: Set<String> = emptySet()
): List<CategoryBandRow> {
    val byId = topics.associateBy { it.id }
    val children = topics.groupBy { it.parentId }.mapValues {
        it.value.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { t -> t.name })
    }
    val rows = mutableListOf<CategoryBandRow>()
    val visited = mutableSetOf<String>()
    fun visit(id: String, depth: Int, rootId: String) {
        if (!visited.add(id) || depth > 50) return
        val topic = byId[id] ?: return
        rows.add(CategoryBandRow(topic.id, topic.name, rootId, depth,
            children[topic.id].orEmpty().isNotEmpty()))
        if (id !in collapsedIds) children[id].orEmpty().forEach { visit(it.id, depth + 1, rootId) }
    }
    val roots = topics.filter { it.parentId == null || it.parentId !in byId }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    roots.forEach { visit(it.id, 0, it.id) }
    // Keep data with corrupt/detached ancestry discoverable, never silently discard it.
    topics.filter { it.id !in visited }.forEach { topic ->
        if (topic.parentId !in collapsedIds) visit(topic.id, 0, topic.id)
    }
    return rows
}

/** A historically undefined period or location must not be silently assigned a date/place.
 * Each claim contributes once per lane/column even when tagged in multiple descendants.
 * Applying a geographic filter uses the territory hierarchy valid FOR EACH time column.
 */
fun indexCategoryBands(
    topics: List<HistoricalTopic>,
    assertions: List<HistoricalAssertion>,
    topicLinks: List<AssertionTopicLink>,
    entityLinks: List<AssertionEntityLink>,
    relations: List<EntityRelation>,
    slots: List<TimeSlice>,
    selectedPlaceIds: Set<String> = emptySet()
): CategoryBandIndex {
    val byId = topics.associateBy { it.id }
    val topicsByAssertion = topicLinks.groupBy { it.assertionId }
        .mapValues { (_, links) -> links.map { it.topicId }.toSet() }
    val placesByAssertion = entityLinks.asSequence().filter { it.role == "LOC" }
        .groupBy { it.assertionId }.mapValues { (_, links) -> links.map { it.entityId }.toSet() }
    val buckets = mutableMapOf<Pair<String, String>, LinkedHashSet<String>>()
    val visibleIds = mutableSetOf<String>()

    for (slot in slots) {
        val placeClosure = selectedPlaceIds.flatMap {
            descendantsOfPlace(it, relations, slot.from, slot.through)
        }.toSet()
        for (claim in assertions) {
            val inTime = if (slot.key == UNKNOWN_TIME_KEY) {
                claim.yearStart == null && claim.yearEnd == null && claim.periodLabel.isBlank()
            } else slot.matches(claim.yearStart, claim.yearEnd, claim.month, claim.day, claim.periodLabel)
            if (!inTime) continue
            if (selectedPlaceIds.isNotEmpty() &&
                placesByAssertion[claim.id].orEmpty().none { it in placeClosure }) continue
            for (linkedTopicId in topicsByAssertion[claim.id].orEmpty()) {
                var current: String? = linkedTopicId
                val seen = mutableSetOf<String>()
                while (current != null && current in byId && seen.add(current)) {
                    buckets.getOrPut(current to slot.key) { linkedSetOf() }.add(claim.id)
                    visibleIds.add(claim.id)
                    current = byId[current]?.parentId
                }
            }
        }
    }
    return CategoryBandIndex(buckets.mapValues { (_, ids) -> ids.toList() }, visibleIds)
}
