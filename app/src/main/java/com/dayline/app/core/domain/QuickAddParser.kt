package com.dayline.app.core.domain

import com.dayline.app.core.model.Priority
import java.time.DayOfWeek
import java.time.LocalDate

data class ParsedQuickAdd(
    val title: String,
    val date: LocalDate? = null,
    val priority: Priority? = null,
    val tags: List<String> = emptyList(),
    /** Name after "@", matched against lists/projects by the caller. */
    val listName: String? = null,
)

/**
 * Lightweight natural-language shortcuts for Quick Add:
 * "today", "tomorrow", weekday names, "next week", "!high" / "!1".."!4", "#tag", "@List".
 * Words are only treated as shortcuts when they stand alone, so titles keep their meaning.
 */
object QuickAddParser {
    private val weekdayNames: Map<String, DayOfWeek> = DayOfWeek.entries.flatMap { d ->
        val full = d.name.lowercase()
        listOf(full to d, full.take(3) to d)
    }.toMap()

    private val priorityWords = mapOf(
        "!low" to Priority.LOW, "!1" to Priority.LOW,
        "!medium" to Priority.MEDIUM, "!med" to Priority.MEDIUM, "!2" to Priority.MEDIUM,
        "!high" to Priority.HIGH, "!3" to Priority.HIGH,
        "!urgent" to Priority.URGENT, "!4" to Priority.URGENT,
    )

    fun parse(input: String, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): ParsedQuickAdd {
        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kept = mutableListOf<String>()
        var date: LocalDate? = null
        var priority: Priority? = null
        val tags = mutableListOf<String>()
        var listName: String? = null
        var i = 0
        while (i < tokens.size) {
            val raw = tokens[i]
            val tok = raw.lowercase()
            val nextTok = tokens.getOrNull(i + 1)?.lowercase()
            when {
                tok in priorityWords -> priority = priorityWords.getValue(tok)
                raw.startsWith("#") && raw.length > 1 -> tags += raw.drop(1)
                raw.startsWith("@") && raw.length > 1 -> listName = raw.drop(1).replace('_', ' ')
                date == null && (tok == "today" || tok == "tod") -> date = today
                date == null && (tok == "tomorrow" || tok == "tmr" || tok == "tmrw") -> date = today.plusDays(1)
                date == null && tok == "next" && nextTok == "week" -> {
                    date = DateRules.startOfWeek(today, weekStart).plusWeeks(1); i++
                }
                date == null && (tok == "on" || tok == "next") && nextTok != null && nextTok in weekdayNames -> {
                    date = DateRules.nextWeekday(today, weekdayNames.getValue(nextTok)); i++
                }
                date == null && tok in weekdayNames && tok.length > 3 -> date = DateRules.nextWeekday(today, weekdayNames.getValue(tok))
                else -> kept += raw
            }
            i++
        }
        return ParsedQuickAdd(kept.joinToString(" "), date, priority, tags.distinct(), listName)
    }
}
