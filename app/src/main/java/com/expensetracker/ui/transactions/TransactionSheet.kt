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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.expensetracker.model.TxnType
import com.expensetracker.sms.Categorizer
import com.expensetracker.sms.SmsParser
import com.expensetracker.ui.components.CategoryAvatar
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
            CategoryAvatar(category, size = 52.dp)
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
        Spacer(Modifier.height(16.dp))
        SignedAmount(txn, style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(12.dp))
        val via = listOfNotNull(txn.mode.label, txn.bank, txn.accountTail?.let { "••$it" }).joinToString(" · ")
        DetailLine(if (txn.type == TxnType.DEBIT) "Paid via" else "Received via", via)
        txn.vpa?.let { DetailLine("UPI ID", it) }
        txn.refNo?.let { DetailLine("Reference", it) }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Category", style = MaterialTheme.typography.titleSmall)
        if (ruleKey != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { rememberMerchant = !rememberMerchant },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberMerchant, onCheckedChange = { rememberMerchant = it })
                Text(
                    if (txn.type == TxnType.DEBIT) {
                        "Use for all payments to $merchantLabel"
                    } else {
                        "Use for all money from $merchantLabel"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.filter { it.kind == kind }.forEach { option ->
                FilterChip(
                    selected = option.id == txn.categoryId,
                    onClick = { onCategory(option.id, rememberMerchant && ruleKey != null) },
                    label = { Text("${option.emoji} ${option.name}") },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count in totals", style = MaterialTheme.typography.bodyLarge)
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

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Original message from ${txn.sender}", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
            Text(txn.body, Modifier.padding(12.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
        }
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
