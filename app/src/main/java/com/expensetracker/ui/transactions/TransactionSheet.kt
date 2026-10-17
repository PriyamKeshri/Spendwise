package com.expensetracker.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.model.CategoryKind
import com.expensetracker.model.TxnSource
import com.expensetracker.model.TxnType
import com.expensetracker.sms.Categorizer
import com.expensetracker.sms.SmsParser
import com.expensetracker.ui.components.CategoryGlyph
import com.expensetracker.ui.components.CategoryTile
import com.expensetracker.ui.components.CategoryVisuals
import com.expensetracker.ui.components.FilterPill
import com.expensetracker.ui.components.PillLabel
import com.expensetracker.ui.components.SignedAmount
import com.expensetracker.ui.components.dateTimeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(private val repository: ExpenseRepository) : ViewModel() {

    private val transactionId = MutableStateFlow<Long?>(null)

    val transaction: StateFlow<TransactionEntity?> = transactionId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.transaction(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun show(id: Long) {
        transactionId.value = id
    }

    fun setCategory(txn: TransactionEntity, categoryId: Long, rememberMerchant: Boolean) {
        viewModelScope.launch { repository.setCategory(txn, categoryId, rememberMerchant) }
    }

    fun setCounted(txn: TransactionEntity, counted: Boolean) {
        viewModelScope.launch { repository.setExcluded(txn.id, excluded = !counted) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(
    transactionId: Long,
    onDismiss: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(transactionId) { viewModel.show(transactionId) }
    val transaction by viewModel.transaction.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val txn = transaction
        if (txn == null || txn.id != transactionId) {
            Box(Modifier.fillMaxWidth().height(240.dp))
        } else {
            TransactionDetails(
                txn = txn,
                categories = categories,
                onCategory = { categoryId, remember -> viewModel.setCategory(txn, categoryId, remember) },
                onCounted = { viewModel.setCounted(txn, it) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionDetails(
    txn: TransactionEntity,
    categories: List<CategoryEntity>,
    onCategory: (categoryId: Long, rememberMerchant: Boolean) -> Unit,
    onCounted: (Boolean) -> Unit,
) {
    val category = categories.firstOrNull { it.id == txn.categoryId }
    val kind = if (txn.type == TxnType.DEBIT) CategoryKind.EXPENSE else CategoryKind.INCOME
    val ruleKey = Categorizer.ruleKeyFor(txn.merchant, txn.vpa)
    val merchantLabel = if (SmsParser.isGenericLabel(txn.merchant)) txn.vpa ?: txn.merchant else txn.merchant
    var rememberMerchant by rememberSaveable(txn.id) { mutableStateOf(ruleKey != null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(category, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(txn.merchant, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    txn.timestamp.dateTimeLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignedAmount(txn, style = MaterialTheme.typography.displaySmall)
            if (txn.excluded) {
                Spacer(Modifier.width(12.dp))
                PillLabel(
                    "Not counted",
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Panel {
            val via = listOfNotNull(txn.mode.label, txn.bank, txn.accountTail?.let { "••$it" }).joinToString(" · ")
            DetailLine(if (txn.type == TxnType.DEBIT) "Paid via" else "Received via", via)
            txn.vpa?.let { DetailLine("UPI ID", it) }
            txn.refNo?.let { DetailLine("Reference", it) }
        }

        Spacer(Modifier.height(24.dp))
        Text("Category", style = MaterialTheme.typography.titleMedium)
        if (ruleKey != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { rememberMerchant = !rememberMerchant },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberMerchant, onCheckedChange = { rememberMerchant = it })
                Text(
                    if (txn.type == TxnType.DEBIT) "Use for all payments to $merchantLabel" else "Use for all money from $merchantLabel",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.filter { it.kind == kind }.forEach { option ->
                val selected = option.id == txn.categoryId
                FilterPill(
                    label = option.name,
                    selected = selected,
                    onClick = { onCategory(option.id, rememberMerchant && ruleKey != null) },
                    unselectedColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    leading = {
                        CategoryGlyph(option, tint = if (selected) LocalContentColor.current else CategoryVisuals.color(option))
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Count in totals", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (txn.internalTransfer) {
                            "Looks like a card bill payment or a transfer between your own accounts"
                        } else {
                            "Turn off for money that isn't really spending or income, like moving it to yourself"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = !txn.excluded, onCheckedChange = onCounted)
            }
        }

        Spacer(Modifier.height(24.dp))
        val imported = txn.source == TxnSource.PAYTM
        Text(if (imported) "Statement entry" else "Original message", style = MaterialTheme.typography.titleMedium)
        Text(
            if (imported) "Imported from your Paytm statement" else "From ${txn.sender}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Panel {
            Text(txn.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
