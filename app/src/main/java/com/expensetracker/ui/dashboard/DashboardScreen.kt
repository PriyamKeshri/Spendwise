package com.expensetracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.sms.SyncStatus
import com.expensetracker.ui.components.AppCard
import com.expensetracker.ui.components.CategoryTile
import com.expensetracker.ui.components.CategoryVisuals
import com.expensetracker.ui.components.ChartSlice
import com.expensetracker.ui.components.DailyBarChart
import com.expensetracker.ui.components.DonutChart
import com.expensetracker.ui.components.MonthSelector
import com.expensetracker.ui.components.PillLabel
import com.expensetracker.ui.components.PillToggle
import com.expensetracker.ui.components.ScreenTitle
import com.expensetracker.ui.components.ShareBar
import com.expensetracker.ui.components.TransactionRow
import com.expensetracker.ui.components.TrendBars
import com.expensetracker.ui.components.dateLabel
import com.expensetracker.ui.components.label
import com.expensetracker.ui.components.monthName
import com.expensetracker.ui.theme.appColors
import com.expensetracker.ui.theme.money
import com.expensetracker.ui.transactions.TransactionSheet
import com.expensetracker.util.Money
import com.expensetracker.util.startMillis
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(onOpenTransactions: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val running = state.sync as? SyncStatus.Running

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenTitle("Overview", Modifier.padding(start = 4.dp)) {
                FilledTonalIconButton(onClick = viewModel::refresh, enabled = running == null) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Check for new messages")
                }
            }
        }
        if (running != null && running.history) {
            item { ImportProgressCard(running) }
        }
        item { SummaryCard(state, onMonthChange = viewModel::setMonth) }
        if (state.spentPaise > 0) {
            item { InsightsRow(state) }
        }
        if (state.spending.isNotEmpty() || state.income.isNotEmpty()) {
            item {
                BreakdownCard(state) { categoryId ->
                    viewModel.showCategory(categoryId)
                    onOpenTransactions()
                }
            }
        }
        if (state.spentPaise > 0) {
            item { DailyCard(state) }
        }
        if (state.recent.isNotEmpty()) {
            item {
                RecentCard(
                    state = state,
                    onSeeAll = {
                        viewModel.showAllTransactions()
                        onOpenTransactions()
                    },
                    onClick = { selectedId = it },
                )
            }
        }
        if (!state.loading && !state.hasTransactions) {
            item { EmptyMonth(state) }
        }
    }

    selectedId?.let { id -> TransactionSheet(id, onDismiss = { selectedId = null }) }
}

@Composable
private fun ImportProgressCard(running: SyncStatus.Running) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                "Reading your messages…",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            if (running.total > 0) {
                LinearProgressIndicator(
                    progress = { running.scanned.toFloat() / running.total },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    trackColor = trackColor,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), trackColor = trackColor)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (running.total > 0) {
                    "${running.scanned} of ${running.total} messages checked · ${running.found} transactions found"
                } else {
                    "Starting…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** The violet card: month, total, change versus last month, six-month trend, money in and left over. */
@Composable
private fun SummaryCard(state: DashboardUiState, onMonthChange: (YearMonth) -> Unit) {
    val colors = appColors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.hero)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.45f, Offset(size.width * 0.98f, 0f))
                drawCircle(Color.White.copy(alpha = 0.05f), size.width * 0.25f, Offset(size.width * 0.78f, size.height * 0.2f))
            }
            .padding(20.dp),
    ) {
        MonthSelector(
            month = state.month,
            onMonthChange = onMonthChange,
            contentColor = colors.onHero,
            modifier = Modifier.offset(x = (-10).dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("Spent", style = MaterialTheme.typography.labelLarge, color = colors.onHeroMuted)
        Text(
            Money.format(state.spentPaise, showPaise = false),
            style = MaterialTheme.typography.displayMedium.money(),
            color = colors.onHero,
            maxLines = 1,
        )
        Comparison(state)
        Spacer(Modifier.height(20.dp))
        TrendBars(
            points = state.trend,
            selected = state.month,
            onSelect = onMonthChange,
            barColor = colors.onHero,
            dimBarColor = colors.onHeroMuted.copy(alpha = 0.35f),
            labelColor = colors.onHeroMuted,
            modifier = Modifier.fillMaxWidth().height(92.dp),
        )
        Spacer(Modifier.height(18.dp))
        val net = state.receivedPaise - state.spentPaise
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(Icons.Rounded.SouthWest, "Money in", Money.format(state.receivedPaise, false), Modifier.weight(1f))
            HeroStat(
                Icons.Rounded.Savings,
                if (net >= 0) "Left over" else "Overspent",
                Money.format(abs(net), false),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Comparison(state: DashboardUiState) {
    val colors = appColors
    val previous = state.previousSpentPaise
    val previousMonth = state.month.minusMonths(1).monthName()
    if (previous <= 0) {
        Text(
            if (state.spentPaise > 0) "Nothing recorded for $previousMonth to compare with" else "Nothing spent yet",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onHeroMuted,
        )
        return
    }
    val change = (state.spentPaise - previous).toDouble() / previous * 100
    val reference = "${Money.format(previous, false)} in $previousMonth"
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            abs(change) < 0.5 -> Unit
            change > 0 -> PillLabel("▲ ${change.roundToInt()}%", colors.upContainer, colors.onUpContainer)
            else -> PillLabel("▼ ${abs(change).roundToInt()}%", colors.downContainer, colors.onDownContainer)
        }
        Text(
            if (abs(change) < 0.5) "Same as $reference" else "  vs $reference",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onHeroMuted,
        )
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val colors = appColors
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onHero, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onHeroMuted)
            Text(value, style = MaterialTheme.typography.titleSmall.money(), color = colors.onHero, maxLines = 1)
        }
    }
}

@Composable
private fun InsightsRow(state: DashboardUiState) {
    val top = state.spending.firstOrNull()
    val biggest = state.biggestExpense
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Insight(
            modifier = Modifier.weight(1f),
            icon = { CategoryTile(top?.category, size = 32.dp) },
            label = "Top category",
            value = top?.category?.name ?: "—",
            detail = top?.let { "${(it.share * 100).roundToInt()}% share" } ?: "",
        )
        Insight(
            modifier = Modifier.weight(1f),
            icon = { IconTile(Icons.Rounded.LocalFireDepartment, Color(0xFFFF7A45)) },
            label = "Biggest",
            value = biggest?.let { Money.format(it.amountPaise, false) } ?: "—",
            detail = biggest?.merchant ?: "",
        )
        Insight(
            modifier = Modifier.weight(1f),
            icon = { IconTile(Icons.Rounded.Today, MaterialTheme.colorScheme.primary) },
            label = "Per day",
            value = Money.format(state.dailyAveragePaise, false),
            detail = "on average",
        )
    }
}

@Composable
private fun Insight(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            icon()
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.money(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

@Composable
private fun IconTile(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BreakdownCard(state: DashboardUiState, onCategoryClick: (Long) -> Unit) {
    var showIncome by rememberSaveable { mutableStateOf(false) }
    val shares = if (showIncome) state.income else state.spending
    val total = if (showIncome) state.receivedPaise else state.spentPaise

    AppCard {
        Text(if (showIncome) "Where it came from" else "Where it went", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        PillToggle(
            options = listOf("Spent", "Received"),
            selected = if (showIncome) 1 else 0,
            onSelect = { showIncome = it == 1 },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        if (shares.isEmpty()) {
            Text(
                if (showIncome) "Nothing received this month" else "Nothing spent this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally),
            )
        } else {
            DonutChart(
                slices = shares.map { ChartSlice(it.amountPaise.toFloat(), CategoryVisuals.color(it.category)) },
                modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                thickness = 20.dp,
                track = appColors.track,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Money.format(total, showPaise = false), style = MaterialTheme.typography.titleLarge.money())
                    Text(
                        "${shares.sumOf { it.count }} ${if (showIncome) "credits" else "expenses"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            shares.forEach { share -> CategoryShareRow(share) { onCategoryClick(share.category.id) } }
        }
    }
}

@Composable
private fun CategoryShareRow(share: CategoryShare, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(share.category, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    share.category.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(share.amountPaise, showPaise = false), style = MaterialTheme.typography.titleSmall.money())
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareBar(
                    fraction = share.share,
                    color = CategoryVisuals.color(share.category),
                    track = appColors.track,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (share.share < 0.005f) "<1%" else "${(share.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

@Composable
private fun DailyCard(state: DashboardUiState) {
    val busiest = state.daily.indices.maxByOrNull { state.daily[it] }
    AppCard {
        Text("Daily spending", style = MaterialTheme.typography.titleMedium)
        if (busiest != null && state.daily[busiest] > 0) {
            Text(
                "Busiest day: ${busiest + 1} ${state.month.monthName().take(3)} · ${Money.format(state.daily[busiest], false)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        DailyBarChart(values = state.daily, modifier = Modifier.fillMaxWidth().height(150.dp))
    }
}

@Composable
private fun RecentCard(state: DashboardUiState, onSeeAll: () -> Unit, onClick: (Long) -> Unit) {
    AppCard(contentPadding = PaddingValues(vertical = 10.dp)) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        state.recent.forEach { txn ->
            TransactionRow(txn, state.categories[txn.categoryId], onClick = { onClick(txn.id) }, showDate = true)
        }
    }
}

@Composable
private fun EmptyMonth(state: DashboardUiState) {
    val historyStart = state.historyStart
    val note = when {
        state.sync is SyncStatus.Running -> "Still reading your messages…"
        historyStart != null && state.month.startMillis() < historyStart ->
            "Messages before ${historyStart.dateLabel()} haven't been read yet. Scan older messages from Settings."
        else -> "Bank messages for this month will show up here."
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("No transactions in ${state.month.label()}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
