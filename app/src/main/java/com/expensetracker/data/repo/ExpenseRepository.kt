package com.expensetracker.data.repo

import androidx.room.withTransaction
import com.expensetracker.data.db.AppDatabase
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.DefaultCategories
import com.expensetracker.data.db.MerchantRuleEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.model.CategoryKind
import com.expensetracker.sms.Categorizer
import com.expensetracker.sms.CategorizerInput
import com.expensetracker.sms.SmsParser
import com.expensetracker.util.endMillis
import com.expensetracker.util.startMillis
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(private val db: AppDatabase) {

    private val transactionDao = db.transactionDao()
    private val categoryDao = db.categoryDao()

    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val merchantRules: Flow<List<MerchantRuleEntity>> = categoryDao.observeRules()
    val transactionCount: Flow<Int> = transactionDao.observeCount()

    fun transactionsIn(month: YearMonth): Flow<List<TransactionEntity>> =
        transactionDao.observeBetween(month.startMillis(), month.endMillis())

    fun spentIn(month: YearMonth): Flow<Long> =
        transactionDao.observeSpentBetween(month.startMillis(), month.endMillis())

    fun transaction(id: Long): Flow<TransactionEntity?> = transactionDao.observeById(id)

    suspend fun newCategorizer(): Categorizer = Categorizer(categoryDao.all(), categoryDao.allRules())

    /** Stores newly parsed transactions, skipping messages (or duplicate alerts) already imported. */
    suspend fun insertAll(transactions: List<TransactionEntity>): Int = db.withTransaction {
        var added = 0
        for (txn in transactions) {
            val ref = txn.refNo
            if (ref != null && transactionDao.existsByRef(ref, txn.amountPaise, txn.type.name)) continue
            if (transactionDao.insert(txn) != -1L) added++
        }
        added
    }

    /** Pairs up debits and credits between the user's own accounts so they don't count as spending. */
    suspend fun reconcileTransfers() = db.withTransaction {
        transactionDao.markSelfTransfers()
        transactionDao.fileInternalTransfers(CategoryIds.TRANSFERS, CategoryIds.TRANSFERS_IN)
    }

    suspend fun setCategory(txn: TransactionEntity, categoryId: Long, rememberMerchant: Boolean) {
        transactionDao.setCategory(txn.id, categoryId, userCategorized = true)
        if (!rememberMerchant) return
        val key = Categorizer.ruleKeyFor(txn.merchant, txn.vpa) ?: return
        val label = if (SmsParser.isGenericLabel(txn.merchant)) txn.vpa ?: key else txn.merchant
        categoryDao.upsertRule(MerchantRuleEntity(key, categoryId, label))
        recategorize()
    }

    suspend fun setExcluded(id: Long, excluded: Boolean) = transactionDao.setExcludedByUser(id, excluded)

    suspend fun saveCategory(category: CategoryEntity) {
        if (category.id == 0L) {
            val sortOrder = categoryDao.maxSortOrder() + 1
            val color = DefaultCategories.customPalette[sortOrder % DefaultCategories.customPalette.size]
            categoryDao.insert(category.copy(sortOrder = sortOrder, color = color, isSystem = false))
        } else {
            categoryDao.update(category)
        }
        recategorize()
    }

    /** Built-in categories stay; a custom one's transactions fall back to Others / Other Income. */
    suspend fun deleteCategory(category: CategoryEntity) {
        if (category.isSystem) return
        val fallback = if (category.kind == CategoryKind.EXPENSE) CategoryIds.OTHERS else CategoryIds.OTHER_INCOME
        db.withTransaction {
            transactionDao.moveCategory(category.id, fallback)
            categoryDao.deleteRulesFor(category.id)
            categoryDao.deleteCustom(category.id)
        }
        recategorize()
    }

    suspend fun deleteRule(merchantKey: String) {
        categoryDao.deleteRule(merchantKey)
        recategorize()
    }

    /** Re-runs the rules over everything the user hasn't filed by hand, e.g. after a keyword edit. */
    suspend fun recategorize() {
        val categorizer = newCategorizer()
        db.withTransaction {
            for (txn in transactionDao.autoCategorized()) {
                val categoryId = categorizer.categorize(CategorizerInput.of(txn))
                if (categoryId != txn.categoryId) transactionDao.setCategory(txn.id, categoryId, userCategorized = false)
            }
        }
    }

    suspend fun allTransactions(): List<TransactionEntity> = transactionDao.all()

    suspend fun allCategories(): List<CategoryEntity> = categoryDao.all()
}
