package com.expensetracker.statement

import android.content.Context
import android.net.Uri
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.model.CategoryKind
import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnSource
import com.expensetracker.model.TxnType
import com.expensetracker.sms.Categorizer
import com.expensetracker.sms.CategorizerInput
import com.expensetracker.sms.SmsParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportReport(
    val added: Int,
    /** Rows Spendwise already had, from an SMS or an earlier import of the same statement. */
    val alreadyTracked: Int,
    val skippedNotCompleted: Int,
    val skippedLiteTopUps: Int,
    val unreadableRows: Int,
)

/** Imports a Paytm UPI statement file picked by the user. Everything happens on the phone. */
class StatementImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository,
) {

    suspend fun import(uri: Uri): ImportReport = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(MAX_FILE_BYTES) }
            ?: throw StatementFormatException("Couldn't open that file.")
        val parsed = StatementParser.parse(SheetReader.read(bytes))
        if (parsed.entries.isEmpty() && parsed.skippedNotCompleted == 0 && parsed.skippedLiteTopUps == 0) {
            throw StatementFormatException("No transactions were found in this file.")
        }

        val categorizer = repository.newCategorizer()
        val categories = repository.allCategories()
        val added = repository.insertImported(parsed.entries.map { it.toEntity(categorizer, categories) })

        // With the individual Lite payments in, the bank's top-up debits for the same period would count twice.
        val litePayments = parsed.entries.filter { it.viaUpiLite }
        if (litePayments.isNotEmpty()) {
            repository.excludeUpiLiteTopUps(
                from = litePayments.minOf { it.timestamp } - DAY_MS,
                to = litePayments.maxOf { it.timestamp } + DAY_MS,
            )
        }
        repository.reconcileTransfers()

        ImportReport(
            added = added,
            alreadyTracked = parsed.entries.size - added,
            skippedNotCompleted = parsed.skippedNotCompleted,
            skippedLiteTopUps = parsed.skippedLiteTopUps,
            unreadableRows = parsed.unreadableRows,
        )
    }

    private fun StatementEntry.toEntity(categorizer: Categorizer, categories: List<CategoryEntity>): TransactionEntity {
        val merchant = counterparty ?: SmsParser.genericLabel(type, PaymentMode.UPI)
        val kind = if (type == TxnType.DEBIT) CategoryKind.EXPENSE else CategoryKind.INCOME
        // A tag the user set in Paytm is an explicit choice, so it's kept like a manual category.
        val tagged = tags.firstNotNullOfOrNull { categoryForTag(it, categories, kind) }
        val categoryId = tagged ?: categorizer.categorize(
            CategorizerInput(type, PaymentMode.UPI, merchant, vpa, summary, personTransfer, internalTransfer = false),
        )
        return TransactionEntity(
            smsId = null,
            type = type,
            amountPaise = amountPaise,
            merchant = merchant,
            vpa = vpa,
            mode = PaymentMode.UPI,
            bank = bank,
            accountTail = accountTail,
            refNo = refNo,
            timestamp = timestamp,
            categoryId = categoryId,
            userCategorized = tagged != null,
            personTransfer = personTransfer,
            sender = SOURCE_LABEL,
            body = summary,
            source = TxnSource.PAYTM,
            importKey = importKey(),
        )
    }

    private fun StatementEntry.importKey(): String =
        "paytm:" + (refNo ?: "$timestamp:$amountPaise:$type:${Categorizer.normalize(counterparty ?: vpa.orEmpty())}")

    companion object {
        const val SOURCE_LABEL = "Paytm statement"
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Matches a tag like "Food" or "Bills & Recharges" to a category by its first word. */
        fun categoryForTag(tag: String, categories: List<CategoryEntity>, kind: CategoryKind): Long? {
            val words = Categorizer.normalize(tag).split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            return categories.firstOrNull { category ->
                category.kind == kind && Categorizer.normalize(category.name).split(' ').firstOrNull() == words.first()
            }?.id
        }
    }
}
