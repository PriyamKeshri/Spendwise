package com.expensetracker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Month and category selection shared by the Overview and Transactions tabs. */
@Singleton
class SharedFilters @Inject constructor() {
    val month = MutableStateFlow(YearMonth.now())
    val categoryId = MutableStateFlow<Long?>(null)
}
