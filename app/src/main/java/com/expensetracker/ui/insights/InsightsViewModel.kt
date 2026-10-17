package com.expensetracker.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.insights.Insights
import com.expensetracker.insights.SpendingAnalyzer
import com.expensetracker.ui.SharedFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class InsightsUiState(val month: YearMonth = YearMonth.now(), val insights: Insights? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    repository: ExpenseRepository,
    private val filters: SharedFilters,
) : ViewModel() {

    val state: StateFlow<InsightsUiState> = filters.month.flatMapLatest { month ->
        combine(
            repository.transactionsBetween(month.minusMonths(HISTORY_MONTHS), month),
            repository.categories,
        ) { transactions, categories ->
            InsightsUiState(month, SpendingAnalyzer.analyze(transactions, categories.associateBy { it.id }, month))
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setMonth(month: YearMonth) {
        filters.month.value = month
    }

    private companion object {
        /** Enough history for three-month baselines and recurring payments. */
        const val HISTORY_MONTHS = 5L
    }
}
