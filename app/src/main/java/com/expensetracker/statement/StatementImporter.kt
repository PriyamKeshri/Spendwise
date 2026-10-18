package com.expensetracker.statement

import android.content.Context
import android.net.Uri
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
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
        val (categoryId, tagged) = categorize(this, merchant, categories, categorizer)
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
            userCategorized = tagged,
            personTransfer = personTransfer,
            sender = SOURCE_LABEL,
            body = summary,
            source = TxnSource.PAYTM,
            importKey = importKey(this),
        )
    }

    companion object {
        /**
         * What makes a statement row unique, so importing a file twice adds nothing. A refund carries the
         * UPI reference of the payment it reverses, so the direction is part of the key.
         */
        fun importKey(entry: StatementEntry): String = with(entry) {
            "paytm:" + (refNo?.let { "$it:$type" } ?: "$timestamp:$amountPaise:$type:${Categorizer.normalize(counterparty ?: vpa.orEmpty())}")
        }

        const val SOURCE_LABEL = "Paytm statement"
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /**
         * The category for an imported payment, and whether its Paytm tag chose it. A tag is kept like a
         * manual category, except that Paytm files subscriptions such as YouTube under Bill Payments, so a
         * payment the keyword rules know as a subscription stays one.
         */
        fun categorize(
            entry: StatementEntry,
            merchant: String,
            categories: List<CategoryEntity>,
            categorizer: Categorizer,
        ): Pair<Long, Boolean> {
            val kind = if (entry.type == TxnType.DEBIT) CategoryKind.EXPENSE else CategoryKind.INCOME
            val byRules = categorizer.categorize(
                CategorizerInput(entry.type, PaymentMode.UPI, merchant, entry.vpa, entry.summary, entry.personTransfer, internalTransfer = false),
            )
            val tagged = entry.tags.firstNotNullOfOrNull { categoryForTag(it, categories, kind) }
                ?.takeUnless { it == CategoryIds.BILLS && byRules == CategoryIds.SUBSCRIPTIONS }
            return (tagged ?: byRules) to (tagged != null)
        }

        /**
         * Matches a tag like "Food" or "Bills & Recharges" to a category by its first word, falling back to
         * Paytm's own tag names ("Medical" is Health). A tag only counts for the kind of payment it fits.
         */
        fun categoryForTag(tag: String, categories: List<CategoryEntity>, kind: CategoryKind): Long? {
            val words = Categorizer.normalize(tag).split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            categories.firstOrNull { category ->
                category.kind == kind && Categorizer.normalize(category.name).split(' ').firstOrNull() == words.first()
            }?.let { return it.id }
            val names = PAYTM_TAGS.getValue(kind)
            val known = names[words.joinToString(" ")] ?: names[words.first()] ?: return null
            return known.takeIf { id -> categories.any { it.id == id && it.kind == kind } }
        }

        /**
         * Paytm tags (and close variants) whose names don't start like a category's. Paytm's catch-alls,
         * such as Miscellaneous and Services, are left to the keyword rules.
         */
        private val PAYTM_TAGS: Map<CategoryKind, Map<String, Long>> = mapOf(
            CategoryKind.EXPENSE to mapOf(
                "bill" to CategoryIds.BILLS,
                "recharge" to CategoryIds.BILLS,
                "recharges" to CategoryIds.BILLS,
                "utilities" to CategoryIds.BILLS,
                "medical" to CategoryIds.HEALTH,
                "medicine" to CategoryIds.HEALTH,
                "medicines" to CategoryIds.HEALTH,
                "pharmacy" to CategoryIds.HEALTH,
                "taxi" to CategoryIds.TRANSPORT,
                "cab" to CategoryIds.TRANSPORT,
                "auto" to CategoryIds.TRANSPORT,
                "commute" to CategoryIds.TRANSPORT,
                "money transfer" to CategoryIds.TRANSFERS,
                "money sent" to CategoryIds.TRANSFERS,
                "transfer" to CategoryIds.TRANSFERS,
            ),
            CategoryKind.INCOME to mapOf(
                "money received" to CategoryIds.TRANSFERS_IN,
                "money transfer" to CategoryIds.TRANSFERS_IN,
                "refund" to CategoryIds.REFUNDS,
                "cashback" to CategoryIds.REFUNDS,
                "reversal" to CategoryIds.REFUNDS,
            ),
        )
    }
}
