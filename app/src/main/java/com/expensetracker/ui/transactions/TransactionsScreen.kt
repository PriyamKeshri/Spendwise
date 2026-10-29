package com.expensetracker.ui.transactions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.ui.components.AppCard
import com.expensetracker.ui.components.CategoryGlyph
import com.expensetracker.ui.components.CategoryVisuals
import com.expensetracker.ui.components.FilterPill
import com.expensetracker.ui.components.MonthSelector
import com.expensetracker.ui.components.ScreenTitle
import com.expensetracker.ui.components.TransactionRow
import com.expensetracker.ui.components.dayLabel
import com.expensetracker.ui.components.label
import com.expensetracker.ui.theme.incomeColor
import com.expensetracker.ui.theme.money
import com.expensetracker.util.Money

@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                ScreenTitle("Transactions")
                MonthSelector(state.month, viewModel::setMonth, Modifier.offset(x = (-10).dp))
            }
        }
        item { SearchField(state.query, viewModel::setQuery) }
        item { Filters(state, viewModel) }
        item { Summary(state) }
        if (!state.loading && state.groups.isEmpty()) {
            item { EmptyResults(state) }
        }
        state.groups.forEach { group ->
            item(key = "day-${group.date}") {
                DayGroupCard(group, state.categories) { selectedId = it }
            }
        }
    }

    selectedId?.let { id -> TransactionSheet(id, onDismiss = { selectedId = null }) }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search transactions") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Filters(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(TypeFilter.entries) { filter ->
            FilterPill(filter.label, selected = state.type == filter, onClick = { viewModel.setType(filter) })
        }
        items(state.chipCategories, key = { it.id }) { category ->
            val selected = state.categoryId == category.id
            FilterPill(
                label = category.name,
                selected = selected,
                onClick = { viewModel.toggleCategory(category.id) },
                leading = {
                    CategoryGlyph(category, tint = if (selected) LocalContentColor.current else CategoryVisuals.color(category))
                },
            )
        }
    }
}

@Composable
private fun Summary(state: TransactionsUiState) {
    AppCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            SummaryValue("Spent", Money.format(state.shownSpentPaise, false), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryValue("Received", Money.format(state.shownReceivedPaise, false), incomeColor, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryValue("Count", state.shownCount.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.money(), color = color, maxLines = 1)
    }
}

@Composable
private fun DayGroupCard(group: DayGroup, categories: Map<Long, CategoryEntity>, onClick: (Long) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.date.dayLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (group.spentPaise > 0) {
                Text(
                    "−${Money.format(group.spentPaise)}",
                    style = MaterialTheme.typography.labelLarge.money(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            group.items.forEachIndexed { index, txn ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
                TransactionRow(txn, categories[txn.categoryId], onClick = { onClick(txn.id) })
            }
        }
    }
}

@Composable
private fun EmptyResults(state: TransactionsUiState) {
    val filtered = state.query.isNotBlank() || state.categoryId != null || state.type != TypeFilter.ALL
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (filtered) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (filtered) "No matching transactions" else "No transactions in ${state.month.label()}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
