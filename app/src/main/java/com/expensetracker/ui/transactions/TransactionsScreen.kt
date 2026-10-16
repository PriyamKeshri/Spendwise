package com.expensetracker.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.ui.components.MonthSwitcher
import com.expensetracker.ui.components.TransactionRow
import com.expensetracker.ui.components.dayLabel
import com.expensetracker.ui.components.label
import com.expensetracker.util.Money

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transactions") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            item { MonthSwitcher(state.month, viewModel::setMonth, Modifier.padding(horizontal = 8.dp)) }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search transactions") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item { Filters(state, viewModel) }
            item {
                val summary = buildList {
                    add("${state.shownCount} transactions")
                    if (state.shownSpentPaise > 0) add("${Money.format(state.shownSpentPaise, false)} spent")
                    if (state.shownReceivedPaise > 0) add("${Money.format(state.shownReceivedPaise, false)} received")
                }.joinToString(" · ")
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (!state.loading && state.groups.isEmpty()) {
                item { EmptyResults(state) }
            }
            state.groups.forEach { group ->
                stickyHeader(key = "day-${group.date}") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                group.date.dayLabel(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (group.spentPaise > 0) {
                                Text(
                                    "−${Money.format(group.spentPaise)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(group.items, key = { it.id }) { txn ->
                    TransactionRow(txn, state.categories[txn.categoryId], onClick = { selectedId = txn.id })
                }
            }
        }
    }

    selectedId?.let { id -> TransactionSheet(id, onDismiss = { selectedId = null }) }
}

@Composable
private fun Filters(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(TypeFilter.entries) { filter ->
            FilterChip(
                selected = state.type == filter,
                onClick = { viewModel.setType(filter) },
                label = { Text(filter.label) },
            )
        }
        items(state.chipCategories, key = { it.id }) { category ->
            FilterChip(
                selected = state.categoryId == category.id,
                onClick = { viewModel.toggleCategory(category.id) },
                label = { Text("${category.emoji} ${category.name}") },
            )
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
        Text(if (filtered) "🔍" else "🧾", fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (filtered) "Nothing matches these filters" else "No transactions in ${state.month.label()}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
