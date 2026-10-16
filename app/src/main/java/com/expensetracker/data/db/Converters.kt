package com.expensetracker.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun keywordsToString(keywords: List<String>): String = encodeKeywords(keywords)

    @TypeConverter
    fun stringToKeywords(value: String): List<String> =
        value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        fun encodeKeywords(keywords: List<String>): String =
            keywords.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
