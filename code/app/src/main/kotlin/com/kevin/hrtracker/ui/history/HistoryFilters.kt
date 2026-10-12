package com.kevin.hrtracker.ui.history

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One History section: ISO week starting [weekStart] (Monday), [weeksAgo] = 0 for the current week. */
data class WeekGroup<T>(val weeksAgo: Int, val weekStart: LocalDate, val items: List<T>)

/** [range] is inclusive local days from the lib DateRangeFilterDialog; null = no filter. */
internal fun inDateRange(startedAtMillis: Long, range: Pair<LocalDate, LocalDate>?, zone: ZoneId): Boolean {
    if (range == null) return true
    val day = Instant.ofEpochMilli(startedAtMillis).atZone(zone).toLocalDate()
    return !day.isBefore(range.first) && !day.isAfter(range.second)
}

private fun LocalDate.isoMonday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Groups [items] by local ISO week, newest week first; order inside a week is the input order. */
internal fun <T> groupByWeek(items: List<T>, startedAt: (T) -> Long, today: LocalDate, zone: ZoneId): List<WeekGroup<T>> {
    val thisMonday = today.isoMonday()
    return items
        .groupBy { Instant.ofEpochMilli(startedAt(it)).atZone(zone).toLocalDate().isoMonday() }
        .entries
        .sortedByDescending { it.key }
        .map { (monday, list) -> WeekGroup(ChronoUnit.WEEKS.between(monday, thisMonday).toInt(), monday, list) }
}
