package com.expensetracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

fun YearMonth.startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun YearMonth.endMillis(zone: ZoneId = ZoneId.systemDefault()): Long = plusMonths(1).startMillis(zone)

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Start of the month [months] months ago, so "1 month" covers the whole of last month. */
fun historyStartMillis(months: Int, now: YearMonth = YearMonth.now()): Long =
    if (months < 0) 0L else now.minusMonths(months.toLong()).startMillis()
