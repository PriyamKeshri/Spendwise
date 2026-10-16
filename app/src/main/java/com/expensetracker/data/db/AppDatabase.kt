package com.expensetracker.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, MerchantRuleEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "expenses.db")
                .addCallback(SeedCallback)
                .build()
    }

    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DefaultCategories.all.forEach { category ->
                val values = ContentValues().apply {
                    put("id", category.id)
                    put("name", category.name)
                    put("emoji", category.emoji)
                    put("color", category.color)
                    put("kind", category.kind.name)
                    put("keywords", Converters.encodeKeywords(category.keywords))
                    put("sort_order", category.sortOrder)
                    put("is_system", if (category.isSystem) 1 else 0)
                }
                db.insert("categories", SQLiteDatabase.CONFLICT_IGNORE, values)
            }
        }
    }
}
