package com.expensetracker.ui.components

import com.expensetracker.sms.SmsSyncWorker

/** How far back to read the inbox. Ranges cover whole months, so "Last month" includes all of it. */
enum class HistoryRange(val label: String, val months: Int) {
    LAST_MONTH("Last month", 1),
    THREE_MONTHS("3 months", 3),
    SIX_MONTHS("6 months", 6),
    ONE_YEAR("1 year", 12),
    EVERYTHING("Everything", SmsSyncWorker.ALL_HISTORY),
}
