package com.expensetracker.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.insights.CategoryTrend
import com.expensetracker.insights.Insights
import com.expensetracker.insights.SavingTip
import com.expensetracker.ui.components.AppCard
import com.expensetracker.ui.components.CategoryTile
import com.expensetracker.ui.components.MonthSelector
import com.expensetracker.ui.components.PillLabel
import com.expensetracker.ui.components.ScreenTitle
import com.expensetracker.ui.components.label
import com.expensetracker.ui.components.monthName
import com.expensetracker.ui.theme.appColors
import com.expensetracker.ui.theme.money
import com.expensetracker.util.Money
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insights = state.insights

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp)) {
                ScreenTitle("Insights")
                MonthSelector(state.month, viewModel::setMonth, Modifier.offset(x = (-10).dp))
            }
        }
        when {
            insights == null -> Unit
            insights.spentPaise == 0L && insights.receivedPaise == 0L -> item { EmptyMonth(state.month) }
            else -> {
                item { MonthSummary(insights) }
                item { SectionHeader("Ways to save", "Estimates from your own spending") }
                if (insights.tips.isEmpty()) {
                    item { NoTips() }
                } else {
                    insights.tips.forEach { tip -> item { TipCard(tip, insights.month) } }
                }
                if (insights.categoryTrends.isNotEmpty()) {
                    item {
                        SectionHeader(
                            "Compared with your usual",
                            if (insights.usualSpendPaise == null) "No earlier months to compare with yet" else "Your average of recent months",
                        )
                    }
                    item { TrendsCard(insights) }
                }
                if (insights.recurring.isNotEmpty()) {
                    item { SectionHeader("Recurring payments", "Paid in more than one month for about the same amount") }
                    item { RecurringCard(insights) }
                }
                if (insights.topMerchants.isNotEmpty()) {
                    item { SectionHeader("Where most of it went", null) }
                    item { MerchantsCard(insights) }
                }
                item { SectionHeader("Patterns", null) }
                item { PatternsGrid(insights) }
            }
        }
    }
}

@Composable
private fun MonthSummary(insights: Insights) {
    val rate = insights.savingsRate
    val headline = when {
        rate == null -> "${Money.format(insights.spentPaise, false)} spent"
        rate >= 0 -> "You kept ${(rate * 100).roundToInt()}% of what came in"
        else -> "You spent ${(abs(rate) * 100).roundToInt()}% more than came in"
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(headline, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                listOf(
                    "In ${Money.format(insights.receivedPaise, false)}",
                    "out ${Money.format(insights.spentPaise, false)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.money(),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            val facts = buildList {
                insights.projectedSpendPaise?.let { add("On pace for" to Money.format(it, false)) }
                insights.usualSpendPaise?.let { add("Usual month" to Money.format(it, false)) }
            }
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    facts.forEach { (label, value) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
                                .padding(12.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.titleSmall.money())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TipCard(tip: SavingTip, month: YearMonth, modifier: Modifier = Modifier) {
    val copy = tip.copy(month)
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(copy.icon, copy.color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(copy.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(copy.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                tip.monthlySavingPaise?.takeIf { it > 0 }?.let { saving ->
                    Spacer(Modifier.height(10.dp))
                    PillLabel("Save ~${about(saving)}/month", appColors.downContainer, appColors.onDownContainer)
                }
            }
        }
    }
}

@Composable
private fun NoTips() {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.CheckCircle, appColors.income)
            Spacer(Modifier.width(14.dp))
            Text(
                "Nothing stands out this month. Your spending looks steady.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrendsCard(insights: Insights) {
    AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        insights.categoryTrends.forEachIndexed { index, trend ->
            if (index > 0) RowDivider()
            TrendRow(trend)
        }
    }
}

@Composable
private fun TrendRow(trend: CategoryTrend) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(trend.category, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(trend.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                trend.usualPaise?.let { "Usually ${Money.format(it, false)}" } ?: "New this month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(trend.amountPaise, false), style = MaterialTheme.typography.titleSmall.money())
            trend.change?.takeIf { abs(it) >= 0.05f }?.let { change ->
                Spacer(Modifier.height(4.dp))
                val up = change > 0
                val usual = trend.usualPaise ?: 1
                PillLabel(
                    if (change >= 1f) "▲ " + ratio(usual + (change * usual).toLong(), usual)
                    else (if (up) "▲ " else "▼ ") + "${(abs(change) * 100).roundToInt()}%",
                    if (up) appColors.upContainer else appColors.downContainer,
                    if (up) appColors.onUpContainer else appColors.onDownContainer,
                )
            }
        }
    }
}

@Composable
private fun RecurringCard(insights: Insights) {
    AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        insights.recurring.forEachIndexed { index, payment ->
            if (index > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTile(payment.category, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(payment.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Paid in ${payment.months} months",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${Money.format(payment.monthlyPaise, false)}/mo", style = MaterialTheme.typography.titleSmall.money())
            }
        }
        RowDivider()
        Text(
            "About ${Money.format(insights.recurring.sumOf { it.monthlyPaise }, false)} a month in all",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MerchantsCard(insights: Insights) {
    AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        insights.topMerchants.forEachIndexed { index, merchant ->
            if (index > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTile(merchant.category, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(merchant.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (merchant.count == 1) "1 payment" else "${merchant.count} payments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(merchant.amountPaise, false), style = MaterialTheme.typography.titleSmall.money())
            }
        }
    }
}

@Composable
private fun PatternsGrid(insights: Insights) {
    val p = insights.patterns
    val cells = listOf(
        "Weekends" to (p.weekendShare?.let { "${(it * 100).roundToInt()}%" } ?: "—") to "of spending",
        "Under ₹200" to "${p.smallCount}" to "${Money.format(p.smallTotalPaise, false)} in all",
        "Busiest day" to (p.busiestDay?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "—") to
            Money.format(p.busiestDayPaise, false),
        "Cash" to Money.format(p.cashPaise, false) to "ATM withdrawals",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (top, detail) ->
                    val (label, value) = top
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.titleMedium.money(), maxLines = 1)
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMonth(month: YearMonth) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(14.dp))
        Text("Nothing to analyse in ${month.label()}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Insights appear once ${month.monthName()} has some transactions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IconTile(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}
