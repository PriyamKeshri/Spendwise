package com.expensetracker.ui.insights

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.expensetracker.insights.SavingTip
import com.expensetracker.ui.components.CategoryVisuals
import com.expensetracker.ui.components.monthName
import com.expensetracker.util.Money
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

/** What a tip looks like on screen: an icon, its colour, a short title and the numbers behind it. */
data class TipCopy(val icon: ImageVector, val color: Color, val title: String, val body: String)

fun SavingTip.copy(month: YearMonth): TipCopy = when (this) {
    is SavingTip.SpendingAboveIncome -> TipCopy(
        Icons.Rounded.WarningAmber,
        Color(0xFFEF4444),
        "Spending is more than income",
        "You spent ${rupees(overPaise)} more than you received in ${month.monthName()}. Start with your biggest category.",
    )
    is SavingTip.FoodDelivery -> TipCopy(
        Icons.Rounded.DeliveryDining,
        Color(0xFFFF7A45),
        "Order food less often",
        "$orders delivery orders cost ${rupees(totalPaise)}. Try cooking 1 in 4 of those meals.",
    )
    is SavingTip.CategoryAboveUsual -> TipCopy(
        CategoryVisuals.icon(category) ?: Icons.AutoMirrored.Rounded.TrendingUp,
        CategoryVisuals.color(category),
        if (comparablePaise >= usualPaise * 2) {
            "${category.name} is ${ratio(comparablePaise, usualPaise)} your usual"
        } else {
            "${category.name} is up ${percent(comparablePaise - usualPaise, usualPaise)}%"
        },
        if (onPace) {
            "On track for ${rupees(comparablePaise)} this month. You usually spend ${rupees(usualPaise)}."
        } else {
            "You spent ${rupees(comparablePaise)}. You usually spend ${rupees(usualPaise)}."
        },
    )
    is SavingTip.Subscriptions -> TipCopy(
        Icons.Rounded.Autorenew,
        Color(0xFFD946EF),
        "Check your subscriptions",
        if (names.size == 1) {
            "${names.first()} costs ${rupees(monthlyPaise)} a month. Cancel it if you rarely use it."
        } else {
            "${names.size} subscriptions cost ${rupees(monthlyPaise)} a month: ${names.joinToString(", ")}. " +
                "Cancelling just $cheapestName saves ${rupees(cheapestPaise)}."
        },
    )
    is SavingTip.SmallSpends -> TipCopy(
        Icons.Rounded.LocalCafe,
        Color(0xFF8D6E63),
        "Small spends add up",
        "$count payments under ₹200 added up to ${rupees(totalPaise)}. Try skipping 1 in 4.",
    )
    is SavingTip.CashHeavy -> TipCopy(
        Icons.Rounded.LocalAtm,
        Color(0xFF84CC16),
        "Cash is hard to track",
        "${rupees(totalPaise)} (${(share * 100).roundToInt()}% of spending) was cash. Pay by UPI so every rupee is tracked.",
    )
    is SavingTip.WeekendHeavy -> TipCopy(
        Icons.Rounded.Weekend,
        Color(0xFFA855F7),
        "Weekends cost the most",
        "${(share * 100).roundToInt()}% of your spending was on weekends. Try a weekend budget.",
    )
}

private fun rupees(paise: Long) = Money.format(paise, showPaise = false)

/** Estimates are rounded to the nearest ₹10 so they don't look more precise than they are. */
fun about(paise: Long): String = rupees(((paise + 500) / 1_000) * 1_000)

private fun percent(part: Long, whole: Long): Int = if (whole <= 0) 0 else (part * 100f / whole).roundToInt()

/** "2.4×" or "12×": big jumps read better as multiples than as four-digit percentages. */
fun ratio(amount: Long, usual: Long): String {
    val times = amount.toDouble() / usual
    val text = if (times < 10) String.format(Locale.ENGLISH, "%.1f", times).removeSuffix(".0") else times.roundToInt().toString()
    return "$text×"
}
