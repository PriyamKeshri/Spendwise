package com.expensetracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.model.TxnType
import com.expensetracker.ui.SharedFilters
import com.expensetracker.util.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class TypeFilter(val label: String) { ALL("All"), SPENT("Spent"), RECEIVED("Received") }

data class DayGroup(val date: LocalDate, val spentPaise: Long, val items: List<TransactionEntity>)

data class TransactionsUiState(
    val month: YearMonth = YearMonth.now(),
    val loading: Boolean = true,
    val groups: List<DayGroup> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    /** Categories that appear this month, offered as filter chips. */
    val chipCategories: List<CategoryEntity> = emptyList(),
    val query: String = "",
    val type: TypeFilter = TypeFilter.ALL,
    val categoryId: Long? = null,
    val shownCount: Int = 0,
    val shownSpentPaise: Long = 0,
    val shownReceivedPaise: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    repository: ExpenseRepository,
    private val filters: SharedFilters,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val type = MutableStateFlow(TypeFilter.ALL)

    private val monthTransactions = filters.month.flatMapLatest { month ->
        repository.transactionsIn(month).map { month to it }
    }

    val state: StateFlow<TransactionsUiState> = combine(
        monthTransactions,
        repository.categories,
        query,
        type,
        filters.categoryId,
    ) { (month, transactions), categories, query, type, categoryId ->
        val byId = categories.associateBy { it.id }
        val needle = query.trim().lowercase()
        val shown = transactions.filter { txn ->
            val typeMatches = when (type) {
                TypeFilter.ALL -> true
                TypeFilter.SPENT -> txn.type == TxnType.DEBIT
                TypeFilter.RECEIVED -> txn.type == TxnType.CREDIT
            }
            typeMatches &&
                (categoryId == null || txn.categoryId == categoryId) &&
                (needle.isEmpty() || txn.matches(needle, byId[txn.categoryId]))
        }
        val counted = shown.filterNot { it.excluded }
        val presentIds = transactions.mapTo(HashSet()) { it.categoryId }

        TransactionsUiState(
            month = month,
            loading = false,
            groups = shown.groupBy { it.timestamp.toLocalDate() }.map { (date, items) ->
                DayGroup(date, items.filter { it.type == TxnType.DEBIT && !it.excluded }.sumOf { it.amountPaise }, items)
            },
            categories = byId,
            chipCategories = categories.filter { it.id in presentIds || it.id == categoryId },
            query = query,
            type = type,
            categoryId = categoryId,
            shownCount = shown.size,
            shownSpentPaise = counted.filter { it.type == TxnType.DEBIT }.sumOf { it.amountPaise },
            shownReceivedPaise = counted.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setMonth(month: YearMonth) {
        filters.month.value = month
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setType(value: TypeFilter) {
        type.value = value
    }

    fun toggleCategory(categoryId: Long) {
        filters.categoryId.value = if (filters.categoryId.value == categoryId) null else categoryId
    }

    private fun TransactionEntity.matches(needle: String, category: CategoryEntity?): Boolean =
        merchant.lowercase().contains(needle) ||
            vpa?.contains(needle) == true ||
            category?.name?.lowercase()?.contains(needle) == true ||
            body.lowercase().contains(needle)
}
