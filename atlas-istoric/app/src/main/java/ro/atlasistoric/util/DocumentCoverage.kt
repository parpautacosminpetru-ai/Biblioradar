package ro.atlasistoric.util

import ro.atlasistoric.data.PageAnnotation

/** Windows partition the ENTIRE transcription, including whitespace and punctuation. */
data class SourceTextWindow(val start: Int, val end: Int, val text: String) {
    init { require(end > start) }
}

data class SourceCoverage(
    val segmentCount: Int,
    val reviewedSegments: Int,
    val pendingSegments: Int,
    val annotatedSegments: Int,
    val pendingCharacters: Int
)

/** Deterministic text segmentation: no dropping, no trimming, no inferred sentences.
 * Short paragraphs remain together; very long paragraphs are sliced at bounded offsets.
 * The page is reconstructible EXACTLY by concatenating every segment's text.
 */
fun segmentEntireTranscription(text: String, maxChars: Int = 950): List<SourceTextWindow> {
    require(maxChars in 100..12000)
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<SourceTextWindow>()
    var start = 0
    while (start < text.length) {
        val limit = (start.toLong() + maxChars).coerceAtMost(text.length.toLong()).toInt()
        var end = limit
        if (limit < text.length) {
            // Prefer a paragraph break if it occurs within the current bounded window.
            val p1 = text.lastIndexOf("\n\n", startIndex = limit - 1)
            val p2 = text.lastIndexOf("\r\n\r\n", startIndex = limit - 1)
            val paragraphEnd = maxOf(if (p1 > start) p1 + 2 else -1,
                if (p2 > start) p2 + 4 else -1)
            if (paragraphEnd > start + maxChars / 4 && paragraphEnd <= limit) {
                end = paragraphEnd
            } else {
                // Split near a whitespace only for readability; never discard the boundary.
                val white = (limit - 1 downTo start + maxChars / 2).firstOrNull {
                    text[it].isWhitespace()
                }
                if (white != null && white + 1 < limit) end = white + 1
            }
        }
        if (end <= start) end = limit
        result += SourceTextWindow(start, end, text.substring(start, end))
        start = end
    }
    check(result.last().end == text.length)
    return result
}

fun documentCoverage(
    verifiedText: String,
    windows: List<SourceTextWindow>,
    annotations: List<PageAnnotation>
): SourceCoverage {
    if (verifiedText.isEmpty()) return SourceCoverage(0, 0, 0, 0, 0)
    val resolved = annotations.mapNotNull { a ->
        val anchor = resolveTextAnchor(verifiedText, a)
        if (anchor.resolved) a.category to anchor else null
    }
    var reviewed = 0
    var annotated = 0
    var remaining = 0
    for (window in windows) {
        val checked = resolved.any { (category, a) ->
            category == "SEGMENT_VERIFICAT" && a.start <= window.start && a.end >= window.end
        }
        if (checked) reviewed++
        if (resolved.any { (kind, a) ->
                kind != "SEGMENT_VERIFICAT" && a.start < window.end && a.end > window.start
            }) annotated++
        if (!checked) remaining += window.text.count { !it.isWhitespace() }
    }
    return SourceCoverage(windows.size, reviewed, windows.size - reviewed, annotated, remaining)
}
