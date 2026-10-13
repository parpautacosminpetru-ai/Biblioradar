package ro.atlasistoric.util

import ro.atlasistoric.data.HistoricalEntity

/** A shortlist, not a claim that modern and historical regions are interchangeable. */
data class PlaceLookup(
    val matches: List<HistoricalEntity>,
    val count: Int
)

/**
 * Search ALL locally indexed places, including their historical aliases.
 * Matching is diacritic/case independent, but actual IDs/names remain intact.
 * The limit constrains only visible rows; it never truncates the searchable catalogue.
 */
fun lookupGeographicPlaces(
    entities: List<HistoricalEntity>,
    query: String,
    maxVisible: Int = 45,
    selectedIds: Set<String> = emptySet()
): PlaceLookup {
    require(maxVisible in 1..1000)
    val key = TimeMath.normalize(query)
    val candidates = entities.asSequence().filter { it.entityType == "PLACE" }
        .filter { entity ->
            key.isBlank() || TimeMath.normalize(entity.name).contains(key) ||
                entity.aliases.split(';', ',').any { TimeMath.normalize(it).contains(key) }
        }.toList()
    val ranked = candidates.sortedWith(compareBy<HistoricalEntity>(
        { it.id !in selectedIds },
        {
            val name = TimeMath.normalize(it.name)
            when {
                key.isBlank() -> 2
                name == key -> 0
                name.startsWith(key) -> 1
                else -> 2
            }
        },
        { TimeMath.normalize(it.name) },
        { it.id }
    ))
    return PlaceLookup(ranked.take(maxVisible), ranked.size)
}
