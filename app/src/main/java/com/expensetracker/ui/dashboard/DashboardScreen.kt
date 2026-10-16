package com.expensetracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.sms.SyncStatus
import com.expensetracker.ui.components.CategoryAvatar
import com.expensetracker.ui.components.ChartSlice
import com.expensetracker.ui.components.DailyBarChart
import com.expensetracker.ui.components.DonutChart
import com.expensetracker.ui.components.MonthSwitcher
import com.expensetracker.ui.components.TransactionRow
import com.expensetracker.ui.components.dateLabel
import com.expensetracker.ui.components.label
import com.expensetracker.ui.components.monthName
import com.expensetracker.ui.transactions.TransactionSheet
import com.expensetracker.util.Money
import com.expensetracker.util.startMillis
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenTransactions: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val running = state.sync as? SyncStatus.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = running == null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check for new messages")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSwitcher(state.month, viewModel::setMonth) }
            if (running != null && running.history) {
                item { ImportProgressCard(running) }
            }
            item { SummaryCard(state) }
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
    }

    selectedId?.let { id -> TransactionSheet(id, onDismiss = { selectedId = null }) }
}

@Composable
private fun ImportProgressCard(running: SyncStatus.Running) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Reading your messages…", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            if (running.total > 0) {
                LinearProgressIndicator(
                    progress = { running.scanned.toFloat() / running.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (running.total > 0) {
                    "${running.scanned} of ${running.total} messages checked · ${running.found} transactions found"
                } else {
                    "Starting…"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryCard(state: DashboardUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            Text(
                "Spent in ${state.month.label()}",
                style = MaterialTheme.typography.labelLarge,
                color = onContainer.copy(alpha = 0.8f),
            )
            Text(
                Money.format(state.spentPaise, showPaise = false),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
            )
            Comparison(state)
            Spacer(Modifier.height(16.dp))
            Row {
                Stat(
                    label = "Received",
                    value = Money.format(state.receivedPaise, showPaise = false),
                    modifier = Modifier.weight(1f),
                )
                Stat(label = "Expenses", value = state.expenseCount.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Comparison(state: DashboardUiState) {
    val previous = state.previousSpentPaise
    val previousMonth = state.month.minusMonths(1).monthName()
    if (previous <= 0) {
        if (state.spentPaise > 0) {
            Text(
                "Nothing recorded for $previousMonth to compare with",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        return
    }
    val change = (state.spentPaise - previous).toDouble() / previous * 100
    val reference = "${Money.format(previous, false)} in $previousMonth"
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            abs(change) < 0.5 -> Unit
            // The pill carries its own background so it stays readable on any wallpaper colour.
            change > 0 -> ChangePill("▲ ${change.roundToInt()}%", Color(0xFFFFDAD6), Color(0xFF93000A))
            else -> ChangePill("▼ ${abs(change).roundToInt()}%", Color(0xFFC8E6C9), Color(0xFF1B5E20))
        }
        Text(
            if (abs(change) < 0.5) "Same as $reference" else "  vs $reference",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ChangePill(text: String, background: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownCard(state: DashboardUiState, onCategoryClick: (Long) -> Unit) {
    var showIncome by rememberSaveable { mutableStateOf(false) }
    val shares = if (showIncome) state.income else state.spending
    val total = if (showIncome) state.receivedPaise else state.spentPaise

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (showIncome) "Where it came from" else "Where it went",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showIncome,
                    onClick = { showIncome = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Spent") }
                SegmentedButton(
                    selected = showIncome,
                    onClick = { showIncome = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Received") }
            }
            Spacer(Modifier.height(16.dp))
            if (shares.isEmpty()) {
                Text(
                    if (showIncome) "Nothing received this month" else "Nothing spent this month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally),
                )
            } else {
                DonutChart(
                    slices = shares.map { ChartSlice(it.amountPaise.toFloat(), Color(it.category.color)) },
                    modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            Money.format(total, showPaise = false),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (showIncome) "received" else "spent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                shares.forEach { share -> CategoryShareRow(share) { onCategoryClick(share.category.id) } }
            }
        }
    }
}

@Composable
private fun CategoryShareRow(share: CategoryShare, onClick: () -> Unit) {
    val color = Color(share.category.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(share.category, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    share.category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(share.amountPaise, showPaise = false), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(share.share.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(color),
                    )
                }
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
    val thisMonth = state.month == YearMonth.now()
    val days = if (thisMonth) LocalDate.now().dayOfMonth else state.month.lengthOfMonth()
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Daily spending", style = MaterialTheme.typography.titleMedium)
            Text(
                "About ${Money.format(state.spentPaise / days.coerceAtLeast(1), showPaise = false)} a day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            DailyBarChart(values = state.daily, modifier = Modifier.fillMaxWidth().height(140.dp))
        }
    }
}

@Composable
private fun RecentCard(state: DashboardUiState, onSeeAll: () -> Unit, onClick: (Long) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
            state.recent.forEach { txn ->
                TransactionRow(txn, state.categories[txn.categoryId], onClick = { onClick(txn.id) }, showTime = false)
            }
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
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🧾", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
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
