package ro.atlasistoric.util

import java.util.Locale
import java.text.Normalizer

/** Historical integer years: -44 = 44 î.Hr., +44 = 44 d.Hr.; year zero is invalid.
 *  Arithmetic is performed through astronomical years internally. */
object TimeMath {
    private fun astronomical(y: Int): Int = if (y < 0) y + 1 else y
    private fun historical(y: Int): Int = if (y <= 0) y - 1 else y

    fun century(year: Int): Int {
        require(year != 0)
        return if (year > 0) (year - 1) / 100 + 1 else -(((-year - 1) / 100) + 1)
    }
    fun centuryBounds(century: Int): IntRange {
        require(century != 0)
        return if (century > 0) ((century - 1) * 100 + 1)..(century * 100)
        else (century * 100)..((century + 1) * 100 - 1)
    }
    fun overlaps(start: Int?, end: Int?, filterStart: Int?, filterEnd: Int?): Boolean {
        if (filterStart == null || filterEnd == null) return true
        if (start == null && end == null) return false // uncertainty stays visible when no filter
        return (start ?: end!!).let { it <= filterEnd } && (end ?: start!!).let { it >= filterStart }
    }
    fun relationActive(start: Int?, end: Int?, filterStart: Int?, filterEnd: Int?): Boolean {
        if (filterStart == null || filterEnd == null) return true
        // Unbounded relation dates mean no known chronological limits, not proof of permanence.
        return (start == null || start <= filterEnd) && (end == null || end >= filterStart)
    }
    fun yearLabel(y: Int): String = if (y < 0) "${-y} î.Hr." else "$y d.Hr."
    fun centuryLabel(c: Int): String = if (c < 0) "Sec. ${-c} î.Hr." else "Sec. $c"
    fun centuryOfInterval(start: Int?, end: Int?): Int? = start?.let(::century) ?: end?.let(::century)
    fun normalize(text: String): String = Normalizer.normalize(
        text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")
}
