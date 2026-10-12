package ro.atlasistoric.util

import ro.atlasistoric.data.PageAnnotation

enum class AnchorStatus { EXACT, MOVED, AMBIGUOUS, MISSING }

data class ResolvedAnchor(val status: AnchorStatus, val start: Int, val end: Int) {
    val resolved: Boolean get() = status == AnchorStatus.EXACT || status == AnchorStatus.MOVED
}

/**
 * Resolve a textual selection after correcting OCR. Never guess between identical
 * quotations. If context does not disambiguate, the researcher must re-anchor it.
 */
fun resolveTextAnchor(text: String, a: PageAnnotation): ResolvedAnchor {
    val savedStart = a.startOffset
    val savedEnd = a.endOffset
    if (savedStart >= 0 && savedEnd <= text.length && savedStart < savedEnd &&
        text.substring(savedStart, savedEnd) == a.exactQuote &&
        text.substring(0, savedStart).endsWith(a.contextBefore) &&
        text.substring(savedEnd).startsWith(a.contextAfter)
    ) return ResolvedAnchor(AnchorStatus.EXACT, savedStart, savedEnd)
    if (a.exactQuote.isEmpty()) return ResolvedAnchor(AnchorStatus.MISSING, -1, -1)
    val matches = mutableListOf<Int>()
    var from = 0
    while (true) {
        val i = text.indexOf(a.exactQuote, startIndex = from)
        if (i < 0) break
        matches.add(i)
        from = i + 1
        if (matches.size > 10000) return ResolvedAnchor(AnchorStatus.AMBIGUOUS, -1, -1)
    }
    if (matches.isEmpty()) return ResolvedAnchor(AnchorStatus.MISSING, -1, -1)
    val contextMatched = matches.filter { pos ->
        text.substring(0, pos).endsWith(a.contextBefore) &&
            text.substring(pos + a.exactQuote.length).startsWith(a.contextAfter)
    }
    val winners = if (contextMatched.isNotEmpty()) contextMatched else matches
    if (winners.size != 1) return ResolvedAnchor(AnchorStatus.AMBIGUOUS, -1, -1)
    val resultStart = winners.single()
    return ResolvedAnchor(AnchorStatus.MOVED, resultStart, resultStart + a.exactQuote.length)
}

fun selectedTextAnchor(
    pageId: String,
    text: String,
    start: Int,
    end: Int,
    category: String,
    note: String
): PageAnnotation {
    require(start >= 0 && end <= text.length && start < end) { "Selectează textul înainte de adnotare" }
    require(end - start <= 12000) { "Selectează un fragment mai scurt" }
    return PageAnnotation(
        pageId = pageId,
        startOffset = start,
        endOffset = end,
        exactQuote = text.substring(start, end),
        contextBefore = text.substring((start - 48).coerceAtLeast(0), start),
        contextAfter = text.substring(end, (end + 48).coerceAtMost(text.length)),
        category = category,
        note = note.trim()
    )
}
