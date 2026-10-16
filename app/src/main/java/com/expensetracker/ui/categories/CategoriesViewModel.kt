package com.expensetracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.MerchantRuleEntity
import com.expensetracker.data.repo.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repository: ExpenseRepository) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<MerchantRuleEntity>> = repository.merchantRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: CategoryEntity) {
        viewModelScope.launch { repository.saveCategory(category) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    fun forget(rule: MerchantRuleEntity) {
        viewModelScope.launch { repository.deleteRule(rule.merchantKey) }
    }
}
