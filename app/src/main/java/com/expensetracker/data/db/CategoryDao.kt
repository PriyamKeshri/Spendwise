package com.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY kind, sort_order, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND is_system = 0")
    suspend fun deleteCustom(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM merchant_rules")
    suspend fun allRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules ORDER BY label")
    fun observeRules(): Flow<List<MerchantRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE merchant_key = :merchantKey")
    suspend fun deleteRule(merchantKey: String)

    @Query("DELETE FROM merchant_rules WHERE category_id = :categoryId")
    suspend fun deleteRulesFor(categoryId: Long)
}
