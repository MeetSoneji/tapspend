package com.meetsoneji.tapspend.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A budget "month" that may start on any day 1..28 (for salary cycles). Both ends inclusive. */
data class Period(val start: LocalDate, val end: LocalDate) {
    val startIso: String get() = start.toString()
    val endIso: String get() = end.toString()
    val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    operator fun contains(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
}

object BudgetPeriod {
    fun containing(date: LocalDate, startDay: Int): Period {
        val sd = startDay.coerceIn(1, 28)
        val start = if (date.dayOfMonth >= sd) date.withDayOfMonth(sd) else date.minusMonths(1).withDayOfMonth(sd)
        return Period(start, start.plusMonths(1).minusDays(1))
    }

    fun previous(p: Period, startDay: Int) = containing(p.start.minusDays(1), startDay)
    fun next(p: Period, startDay: Int) = containing(p.end.plusDays(1), startDay)

    fun label(p: Period, startDay: Int, locale: Locale = Locale.getDefault()): String =
        if (startDay == 1) {
            p.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        } else {
            val f = DateTimeFormatter.ofPattern("d MMM", locale)
            "${p.start.format(f)} – ${p.end.format(f)}"
        }
}
