package ro.atlasistoric.util

/**
 * A navigable temporal window, independent of whether a source has been indexed in that period.
 * Intervals and identifiers use historical year numbering (-1 = 1 BCE, +1 = 1 CE).
 * The display must never infer the calendar or precision of a historical assertion.
 */
data class TimeSlice(
    val key: String,
    val label: String,
    val from: Int? = null,
    val through: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val era: String? = null
) {
    fun matches(start: Int?, end: Int?, monthOfClaim: Int?, dayOfClaim: Int?, periodLabel: String): Boolean {
        if (era != null) return era == periodLabel.trim()
        if (!TimeMath.overlaps(start, end, from, through)) return false
        if (month != null && month != monthOfClaim) return false
        if (day != null && day != dayOfClaim) return false
        return true
    }
}

object TemporalNavigation {
    fun advanceYear(year: Int, steps: Int): Int {
        require(year != 0)
        // Translate to astronomical numbering ONLY while stepping across 1 BCE ↔ 1 CE.
        val astronomical = if (year < 0) year.toLong() + 1 else year.toLong()
        val next = (astronomical + steps).coerceIn(Int.MIN_VALUE.toLong() + 1, Int.MAX_VALUE.toLong())
        return (if (next <= 0) next - 1 else next).toInt()
    }

    fun advanceCentury(century: Int, steps: Int): Int {
        require(century != 0)
        val astro = if (century < 0) century + 1L else century.toLong()
        val next = (astro + steps).coerceIn(-21_474_835L, 21_474_835L)
        return (if (next <= 0) next - 1 else next).toInt()
    }

    fun stepAnchor(level: String, year: Int, steps: Int): Int = when (level) {
        "SECOL" -> TimeMath.centuryBounds(advanceCentury(TimeMath.century(year), steps)).first
        "AN" -> advanceYear(year, steps)
        "LUNA", "ZI" -> advanceYear(year, steps)
        else -> advanceYear(year, steps * 100)
    }

    fun visible(
        level: String, anchorYear: Int, anchorMonth: Int, periodNames: List<String> = emptyList()
    ): List<TimeSlice> = when (level) {
        "EPOCA" -> periodNames.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().sorted()
            .map { TimeSlice("E:$it", it, era = it) }.toList()
        "SECOL" -> (-7..7).map { advanceCentury(TimeMath.century(anchorYear), it) }.distinct().map { century ->
            val bounds = TimeMath.centuryBounds(century)
            TimeSlice("C:$century", TimeMath.centuryLabel(century), bounds.first, bounds.last)
        }
        "AN" -> (-9..9).map { advanceYear(anchorYear, it) }.distinct().map { year ->
            TimeSlice("Y:$year", TimeMath.yearLabel(year), year, year)
        }
        "LUNA" -> (1..12).map { month ->
            TimeSlice("M:$anchorYear:$month", "$month / ${TimeMath.yearLabel(anchorYear)}", anchorYear, anchorYear, month)
        }
        // 31 slots are UI coordinates, not a claim that every historical month had 31 days.
        "ZI" -> (1..31).map { day ->
            TimeSlice("D:$anchorYear:$anchorMonth:$day", "$day.$anchorMonth.${TimeMath.yearLabel(anchorYear)}",
                anchorYear, anchorYear, anchorMonth, day)
        }
        else -> emptyList()
    }

    fun fromKey(key: String?): TimeSlice? {
        if (key == null) return null
        return when {
            key.startsWith("E:") -> key.substring(2).takeIf(String::isNotBlank)?.let {
                TimeSlice(key, it, era = it)
            }
            key.startsWith("C:") -> key.substring(2).toIntOrNull()?.takeIf { it != 0 }?.let { c ->
                val bounds = TimeMath.centuryBounds(c)
                TimeSlice(key, TimeMath.centuryLabel(c), bounds.first, bounds.last)
            }
            key.startsWith("Y:") -> key.substring(2).toIntOrNull()?.takeIf { it != 0 }?.let {
                TimeSlice(key, TimeMath.yearLabel(it), it, it)
            }
            key.startsWith("M:") -> key.split(":").takeIf { it.size == 3 }?.let { parts ->
                val y = parts[1].toIntOrNull() ?: return null
                val m = parts[2].toIntOrNull() ?: return null
                if (y == 0 || m !in 1..12) null else TimeSlice(key, "$m / ${TimeMath.yearLabel(y)}", y, y, m)
            }
            key.startsWith("D:") -> key.split(":").takeIf { it.size == 4 }?.let { parts ->
                val y = parts[1].toIntOrNull() ?: return null
                val m = parts[2].toIntOrNull() ?: return null
                val d = parts[3].toIntOrNull() ?: return null
                if (y == 0 || m !in 1..12 || d !in 1..31) null else
                    TimeSlice(key, "$d.$m.${TimeMath.yearLabel(y)}", y, y, m, d)
            }
            else -> null
        }
    }
}
