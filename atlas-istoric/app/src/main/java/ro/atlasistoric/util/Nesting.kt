package ro.atlasistoric.util

import ro.atlasistoric.data.EntityRelation
import ro.atlasistoric.data.HistoricalTopic

/** Returns selected ID plus its nested descendants; never duplicates source records. */
fun descendantsOfTopic(id: String, topics: List<HistoricalTopic>): Set<String> {
    val result = mutableSetOf(id)
    var changed: Boolean
    do {
        val size = result.size
        topics.filter { it.parentId in result }.forEach { result.add(it.id) }
        changed = size != result.size
    } while (changed)
    return result
}

/** Geography is a time-sensitive graph, not a permanent country/region tree. */
fun descendantsOfPlace(
    id: String, relations: List<EntityRelation>,
    fromYear: Int? = null, throughYear: Int? = null
): Set<String> {
    val result = mutableSetOf(id)
    var changed: Boolean
    do {
        val size = result.size
        relations.filter {
            it.relationType == "PART_OF" && it.toEntityId in result &&
                TimeMath.relationActive(it.yearStart, it.yearEnd, fromYear, throughYear)
        }.forEach { result.add(it.fromEntityId) }
        changed = size != result.size
    } while (changed)
    return result
}
