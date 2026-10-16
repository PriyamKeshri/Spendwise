package com.expensetracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val historyImported = booleanPreferencesKey("history_imported")
        val historyStart = longPreferencesKey("history_start")
        val lastSyncedSmsTime = longPreferencesKey("last_synced_sms_time")
    }

    private val data = context.settingsDataStore.data

    val historyImported: Flow<Boolean> = data.map { it[Keys.historyImported] ?: false }

    /** Start of the oldest month that has been scanned, or null before the first import. */
    val historyStart: Flow<Long?> = data.map { it[Keys.historyStart] }

    suspend fun lastSyncedSmsTime(): Long = data.first()[Keys.lastSyncedSmsTime] ?: 0L

    suspend fun recordSynced(newestSmsTime: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastSyncedSmsTime] = maxOf(prefs[Keys.lastSyncedSmsTime] ?: 0L, newestSmsTime)
        }
    }

    suspend fun recordHistoryImported(scannedFrom: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.historyImported] = true
            prefs[Keys.historyStart] = minOf(prefs[Keys.historyStart] ?: Long.MAX_VALUE, scannedFrom)
        }
    }
}
