package com.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.data.prefs.SettingsStore
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.model.TxnType
import com.expensetracker.sms.SyncScheduler
import com.expensetracker.sms.SyncStatus
import com.expensetracker.ui.SharedFilters
import com.expensetracker.util.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class CategoryShare(val category: CategoryEntity, val amountPaise: Long, val share: Float)

data class DashboardUiState(
    val month: YearMonth = YearMonth.now(),
    val loading: Boolean = true,
    val spentPaise: Long = 0,
    val receivedPaise: Long = 0,
    val previousSpentPaise: Long = 0,
    val expenseCount: Int = 0,
    val spending: List<CategoryShare> = emptyList(),
    val income: List<CategoryShare> = emptyList(),
    val daily: List<Long> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val hasTransactions: Boolean = false,
    val sync: SyncStatus = SyncStatus.Idle,
    val historyStart: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val filters: SharedFilters,
    private val syncScheduler: SyncScheduler,
    settings: SettingsStore,
) : ViewModel() {

    private val monthSummary = filters.month.flatMapLatest { month ->
        combine(
            repository.transactionsIn(month),
            repository.spentIn(month.minusMonths(1)),
            repository.categories,
        ) { transactions, previousSpent, categories -> summarize(month, transactions, previousSpent, categories) }
    }

    val state: StateFlow<DashboardUiState> =
        combine(monthSummary, syncScheduler.status, settings.historyStart) { summary, sync, historyStart ->
            summary.copy(sync = sync, historyStart = historyStart)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setMonth(month: YearMonth) {
        filters.month.value = month
    }

    fun showCategory(categoryId: Long) {
        filters.categoryId.value = categoryId
    }

    fun showAllTransactions() {
        filters.categoryId.value = null
    }

    fun refresh() = syncScheduler.syncNow()

    private fun summarize(
        month: YearMonth,
        transactions: List<TransactionEntity>,
        previousSpent: Long,
        categories: List<CategoryEntity>,
    ): DashboardUiState {
        val byId = categories.associateBy { it.id }
        val counted = transactions.filterNot { it.excluded }
        val debits = counted.filter { it.type == TxnType.DEBIT }
        val credits = counted.filter { it.type == TxnType.CREDIT }

        val daily = LongArray(month.lengthOfMonth())
        for (txn in debits) {
            val day = txn.timestamp.toLocalDate().dayOfMonth - 1
            if (day in daily.indices) daily[day] += txn.amountPaise
        }

        return DashboardUiState(
            month = month,
            loading = false,
            spentPaise = debits.sumOf { it.amountPaise },
            receivedPaise = credits.sumOf { it.amountPaise },
            previousSpentPaise = previousSpent,
            expenseCount = debits.size,
            spending = shares(debits, byId, CategoryIds.OTHERS),
            income = shares(credits, byId, CategoryIds.OTHER_INCOME),
            daily = daily.toList(),
            recent = transactions.take(5),
            categories = byId,
            hasTransactions = transactions.isNotEmpty(),
        )
    }

    private fun shares(
        transactions: List<TransactionEntity>,
        byId: Map<Long, CategoryEntity>,
        fallbackId: Long,
    ): List<CategoryShare> {
        val total = transactions.sumOf { it.amountPaise }
        if (total <= 0) return emptyList()
        return transactions
            .groupBy { if (it.categoryId in byId) it.categoryId else fallbackId }
            .mapNotNull { (categoryId, items) ->
                val category = byId[categoryId] ?: return@mapNotNull null
                val amount = items.sumOf { it.amountPaise }
                CategoryShare(category, amount, amount.toFloat() / total)
            }
            .sortedByDescending { it.amountPaise }
    }
}
