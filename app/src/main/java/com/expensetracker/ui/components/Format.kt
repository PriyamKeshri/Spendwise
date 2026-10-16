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

fun YearMonth.label(): String = format(monthFormat)

fun YearMonth.shortLabel(): String = format(shortMonthFormat)

fun YearMonth.monthName(): String = format(monthNameFormat)

fun LocalDate.dayLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(dayFormat)
}

fun Long.timeLabel(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFormat)

fun Long.dateTimeLabel(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

fun Long.dateLabel(): String = toLocalDate().format(dateFormat)
