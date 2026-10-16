package com.expensetracker.sms

import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.MerchantRuleEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.model.CategoryKind
import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnType

data class CategorizerInput(
    val type: TxnType,
    val mode: PaymentMode,
    val merchant: String,
    val vpa: String?,
    val body: String,
    val personTransfer: Boolean,
    val internalTransfer: Boolean,
) {
    companion object {
        fun of(parsed: ParsedSms, body: String) = CategorizerInput(
            parsed.type, parsed.mode, parsed.merchant, parsed.vpa, body, parsed.personTransfer, parsed.internalTransfer,
        )

        fun of(txn: TransactionEntity) = CategorizerInput(
            txn.type, txn.mode, txn.merchant, txn.vpa, txn.body, txn.personTransfer, txn.internalTransfer,
        )
    }
}

/**
 * Picks a category for a transaction. Order of precedence:
 * 1. structural facts (own-account transfers, ATM withdrawals),
 * 2. merchants the user has filed by hand,
 * 3. category keywords, longest match first so "amazon prime" beats "amazon",
 * 4. person-to-person transfers,
 * 5. "Others" (or "Other Income" for credits).
 */
class Categorizer(categories: List<CategoryEntity>, rules: List<MerchantRuleEntity>) {

    private class Term(val padded: String, val categoryId: Long, val kind: CategoryKind)

    private val kindById = categories.associate { it.id to it.kind }

    private val keywordTerms = categories.flatMap { category ->
        category.keywords.map { normalize(it) }.filter { it.isNotEmpty() }.map { Term(" $it ", category.id, category.kind) }
    }

    private val ruleTerms = rules.mapNotNull { rule ->
        val kind = kindById[rule.categoryId] ?: return@mapNotNull null
        rule.merchantKey.takeIf { it.isNotEmpty() }?.let { Term(" $it ", rule.categoryId, kind) }
    }

    fun categorize(input: CategorizerInput): Long {
        val kind = if (input.type == TxnType.DEBIT) CategoryKind.EXPENSE else CategoryKind.INCOME
        val expense = kind == CategoryKind.EXPENSE

        if (input.internalTransfer) return if (expense) CategoryIds.TRANSFERS else CategoryIds.TRANSFERS_IN
        if (expense && input.mode == PaymentMode.ATM) return CategoryIds.CASH

        val merchantText = padded(listOfNotNull(input.merchant, input.vpa?.substringBefore('@')).joinToString(" "))
        bestMatch(ruleTerms, merchantText, kind)?.let { return it }
        bestMatch(keywordTerms, merchantText, kind)?.let { return it }
        // Credits rarely name a merchant, but the body often says "salary", "refund" or "interest".
        if (!expense) bestMatch(keywordTerms, padded(input.body), kind)?.let { return it }

        if (input.personTransfer) return if (expense) CategoryIds.TRANSFERS else CategoryIds.TRANSFERS_IN
        if (!expense && input.mode == PaymentMode.UPI) return CategoryIds.TRANSFERS_IN
        return if (expense) CategoryIds.OTHERS else CategoryIds.OTHER_INCOME
    }

    private fun bestMatch(terms: List<Term>, text: String, kind: CategoryKind): Long? =
        terms.filter { it.kind == kind && text.contains(it.padded) }.maxByOrNull { it.padded.length }?.categoryId

    companion object {
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

        /** Lowercase words separated by single spaces: "Domino's Pizza!" -> "dominos pizza". */
        fun normalize(text: String): String =
            text.lowercase().replace("'", "").replace(NON_ALPHANUMERIC, " ").trim()

        private fun padded(text: String) = " ${normalize(text)} "

        /**
         * The text a "remember this merchant" rule is keyed on, or null when there is nothing
         * specific to remember (e.g. a bare "UPI payment" with no UPI ID).
         */
        fun ruleKeyFor(merchant: String, vpa: String?): String? {
            val source = if (SmsParser.isGenericLabel(merchant)) vpa?.substringBefore('@') else merchant
            return source?.let { normalize(it) }?.takeIf { it.isNotEmpty() }
        }
    }
}
