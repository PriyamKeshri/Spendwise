package com.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** Returns the new row id, or -1 when the message was already imported. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TransactionEntity): Long

    /** Some banks send two alerts for one payment; the shared reference number gives them away. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM transactions " +
            "WHERE ref_no = :refNo AND amount_paise = :amountPaise AND type = :type)",
    )
    suspend fun existsByRef(refNo: String, amountPaise: Long, type: String): Boolean

    /**
     * Whether an SMS already recorded a payment of this amount and direction within the window. With
     * [onlyWithoutRef], SMS rows that carry their own reference are ignored, since a different
     * reference means a different payment.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM transactions WHERE source = 'SMS' AND amount_paise = :amountPaise " +
            "AND type = :type AND timestamp BETWEEN :from AND :to AND (:onlyWithoutRef = 0 OR ref_no IS NULL))",
    )
    suspend fun existsSmsNear(amountPaise: Long, type: String, from: Long, to: Long, onlyWithoutRef: Boolean): Boolean

    /** Once the individual UPI Lite payments are imported, the bank's Lite top-up debits would count them twice. */
    @Query(
        "UPDATE transactions SET excluded = 1, internal_transfer = 1 WHERE exclude_locked = 0 AND type = 'DEBIT' " +
            "AND source = 'SMS' AND body LIKE '%upi lite%' AND timestamp BETWEEN :from AND :to",
    )
    suspend fun excludeUpiLiteTopUps(from: Long, to: Long): Int

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE user_categorized = 0")
    suspend fun autoCategorized(): List<TransactionEntity>

    @Query("UPDATE transactions SET category_id = :categoryId, user_categorized = :userCategorized WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long, userCategorized: Boolean)

    @Query("UPDATE transactions SET excluded = :excluded, exclude_locked = 1 WHERE id = :id")
    suspend fun setExcludedByUser(id: Long, excluded: Boolean)

    @Query("UPDATE transactions SET category_id = :toId, user_categorized = 0 WHERE category_id = :fromId")
    suspend fun moveCategory(fromId: Long, toId: Long)

    /**
     * A debit and a credit with the same reference and amount is money moved between the user's
     * own accounts, so neither side is real spending or income.
     */
    @Query(
        """
        UPDATE transactions SET excluded = 1, internal_transfer = 1
        WHERE exclude_locked = 0 AND internal_transfer = 0 AND ref_no IS NOT NULL AND EXISTS (
            SELECT 1 FROM transactions AS other
            WHERE other.ref_no = transactions.ref_no
              AND other.amount_paise = transactions.amount_paise
              AND other.type != transactions.type
        )
        """,
    )
    suspend fun markSelfTransfers(): Int

    @Query(
        "UPDATE transactions SET category_id = CASE type WHEN 'DEBIT' THEN :debitCategory ELSE :creditCategory END " +
            "WHERE internal_transfer = 1 AND user_categorized = 0",
    )
    suspend fun fileInternalTransfers(debitCategory: Long, creditCategory: Long)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>
}
