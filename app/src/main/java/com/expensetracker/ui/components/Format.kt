package com.expensetracker.ui.components

import com.expensetracker.util.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM")
private val monthNameFormat = DateTimeFormatter.ofPattern("MMMM")
private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private val sameYearDateTimeFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val sameYearDateFormat = DateTimeFormatter.ofPattern("d MMM")

fun YearMonth.label(): String = format(monthFormat)

fun YearMonth.shortLabel(): String = format(shortMonthFormat)

fun YearMonth.monthName(): String = format(monthNameFormat)

fun LocalDate.dayLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(dayFormat)
}

fun Long.timeLabel(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFormat)

/** The year is left out for dates in the current year. */
fun Long.dateTimeLabel(): String {
    val time = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    return time.format(if (time.year == LocalDate.now().year) sameYearDateTimeFormat else dateTimeFormat)
}

/** The year is left out for dates in the current year. */
fun Long.dateLabel(): String {
    val date = toLocalDate()
    return date.format(if (date.year == LocalDate.now().year) sameYearDateFormat else dateFormat)
}
