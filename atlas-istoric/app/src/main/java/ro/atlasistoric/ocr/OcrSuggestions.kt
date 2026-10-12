package ro.atlasistoric.ocr

import ro.atlasistoric.data.HistoricalEntity
import ro.atlasistoric.data.HistoricalTopic

/** Conservative on-device hints, never automatically persisted or asserted as facts. */
data class OcrSuggestions(
    val entityIds: Set<String>,
    val topicIds: Set<String>,
    val candidateYears: List<Int>
)

fun suggestOcrLinks(text: String, entities: List<HistoricalEntity>, topics: List<HistoricalTopic>): OcrSuggestions {
    fun mentioned(name: String): Boolean {
        if (name.trim().length < 4) return false
        // Word boundaries are Unicode-aware, including Romanian letters.
        val pattern = "(?<![\\p{L}\\p{N}])${Regex.escape(name.trim())}(?![\\p{L}\\p{N}])"
        return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
    val foundEntities = entities.filter { entity ->
        mentioned(entity.name) || entity.aliases.split(';', ',').any { mentioned(it) }
    }.map { it.id }.toSet()
    val foundTopics = topics.filter { mentioned(it.name) }.map { it.id }.toSet()
    // Hints can detect long years, signed BCE years and short dates with explicit era labels.
    // Bare 1–2 digit numbers remain ambiguous (chapter/page numbers), so are not guessed.
    val normal = Regex("(?<![\\p{L}\\p{N}])[1-9][0-9]{2,3}(?![\\p{L}\\p{N}])")
        .findAll(text).mapNotNull { m -> m.value.toIntOrNull()?.let { m.range.first to it } }
    val signed = Regex("(?<![\\p{L}\\p{N}])-[1-9][0-9]{0,3}(?![\\p{L}\\p{N}])")
        .findAll(text).mapNotNull { m -> m.value.toIntOrNull()?.let { m.range.first to it } }
    val eraPattern = Regex("(?<![\\p{L}\\p{N}])([1-9][0-9]{0,3})\\s*(î\\.?\\s*Hr\\.?|i\\.?\\s*Hr\\.?|d\\.?\\s*Hr\\.?)",
        RegexOption.IGNORE_CASE)
    val explicit = eraPattern.findAll(text).mapNotNull { m ->
        val year = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val era = m.groupValues[2].trim().lowercase()
        m.range.first to if (era.startsWith("d")) year else -year
    }
    val years = (normal + signed + explicit).filter { (_, year) -> year in -4000..2200 && year != 0 }
        .sortedBy { it.first }.map { it.second }.distinct().take(12).toList()
    return OcrSuggestions(foundEntities, foundTopics, years)
}
