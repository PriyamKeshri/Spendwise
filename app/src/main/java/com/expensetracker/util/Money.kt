package com.expensetracker.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    private val india: Locale = Locale.forLanguageTag("en-IN")

    /** Indian grouping: 123456.5 -> "₹1,23,456.50". Paise are shown only when non-zero. */
    fun format(paise: Long, showPaise: Boolean = paise % 100 != 0L): String {
        val format = NumberFormat.getCurrencyInstance(india).apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits = if (showPaise) 2 else 0
            maximumFractionDigits = if (showPaise) 2 else 0
        }
        return format.format(BigDecimal.valueOf(paise, 2))
    }

    /** Short form for chart labels: "₹950", "₹12.4K", "₹1.2L". */
    fun compact(paise: Long): String {
        val rupees = paise / 100.0
        return when {
            rupees >= 1_00_00_000 -> "₹%.1fCr".format(rupees / 1_00_00_000)
            rupees >= 1_00_000 -> "₹%.1fL".format(rupees / 1_00_000)
            rupees >= 1_000 -> "₹%.1fK".format(rupees / 1_000)
            else -> "₹%.0f".format(rupees)
        }.replace(".0", "")
    }
}
