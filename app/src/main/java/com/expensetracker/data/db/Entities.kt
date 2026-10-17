package com.expensetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.expensetracker.model.CategoryKind
import com.expensetracker.model.PaymentMode
import com.expensetracker.model.TxnSource
import com.expensetracker.model.TxnType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sms_id"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["ref_no"]),
        Index(value = ["category_id"]),
        Index(value = ["import_key"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `_id` of the source message in the SMS inbox; the unique index makes re-scans idempotent. */
    @ColumnInfo(name = "sms_id") val smsId: Long?,
    val type: TxnType,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    /** Who was paid (debit) or who paid the user (credit), as shown in the UI. */
    val merchant: String,
    val vpa: String?,
    val mode: PaymentMode,
    val bank: String?,
    @ColumnInfo(name = "account_tail") val accountTail: String?,
    @ColumnInfo(name = "ref_no") val refNo: String?,
    val timestamp: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    /** Set once the user picks a category by hand, so rule changes never override it. */
    @ColumnInfo(name = "user_categorized") val userCategorized: Boolean = false,
    /** Excluded rows are kept and shown, but left out of totals (card bill payments, self transfers). */
    val excluded: Boolean = false,
    @ColumnInfo(name = "exclude_locked") val excludeLocked: Boolean = false,
    @ColumnInfo(name = "person_transfer") val personTransfer: Boolean = false,
    @ColumnInfo(name = "internal_transfer") val internalTransfer: Boolean = false,
    val sender: String,
    val body: String,
    @ColumnInfo(name = "source", defaultValue = "SMS") val source: TxnSource = TxnSource.SMS,
    /** Identifies a row from an imported statement, so importing the same file again adds nothing. */
    @ColumnInfo(name = "import_key") val importKey: String? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** ARGB colour used for charts and avatars. */
    val color: Long,
    val kind: CategoryKind,
    val keywords: List<String>,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Built-in categories can be edited but not deleted. */
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
)

/** A merchant the user has explicitly filed under a category; beats keyword matching. */
@Entity(tableName = "merchant_rules", indices = [Index(value = ["category_id"])])
data class MerchantRuleEntity(
    @PrimaryKey @ColumnInfo(name = "merchant_key") val merchantKey: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val label: String,
)
