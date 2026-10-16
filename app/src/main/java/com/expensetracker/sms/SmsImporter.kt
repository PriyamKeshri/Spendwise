package com.expensetracker.sms

import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.data.prefs.SettingsStore
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.util.historyStartMillis
import javax.inject.Inject

class SmsImporter @Inject constructor(
    private val reader: SmsInboxReader,
    private val repository: ExpenseRepository,
    private val settings: SettingsStore,
) {
    data class Progress(val scanned: Int, val total: Int, val found: Int)

    /**
     * Reads every message from the start of the month [months] months ago (all of them when
     * negative), so "1 month" covers the whole of last month. Returns the number of new transactions.
     */
    suspend fun importHistory(months: Int, onProgress: suspend (Progress) -> Unit = {}): Int {
        val start = historyStartMillis(months)
        val added = import(start, onProgress)
        settings.recordHistoryImported(start)
        return added
    }

    /** Picks up messages received since the last run. Re-reading a little overlap is harmless. */
    suspend fun importNew(onProgress: suspend (Progress) -> Unit = {}): Int {
        val since = (settings.lastSyncedSmsTime() - OVERLAP_MS).coerceAtLeast(0L)
        return import(since, onProgress)
    }

    private suspend fun import(sinceMillis: Long, onProgress: suspend (Progress) -> Unit): Int {
        val categorizer = repository.newCategorizer()
        val batch = ArrayList<TransactionEntity>(BATCH_SIZE)
        var added = 0
        var found = 0
        var newest = 0L

        reader.forEachSince(sinceMillis) { message, index, total ->
            newest = maxOf(newest, message.timestamp)
            SmsParser.parse(message.sender, message.body)?.let { parsed ->
                found++
                val categoryId = categorizer.categorize(CategorizerInput.of(parsed, message.body))
                batch += parsed.toEntity(message, categoryId)
            }
            if (batch.size >= BATCH_SIZE) {
                added += repository.insertAll(batch)
                batch.clear()
            }
            if (index % PROGRESS_EVERY == 0 || index == total - 1) onProgress(Progress(index + 1, total, found))
        }
        if (batch.isNotEmpty()) added += repository.insertAll(batch)

        repository.reconcileTransfers()
        if (newest > 0) settings.recordSynced(newest)
        return added
    }

    private fun ParsedSms.toEntity(message: InboxMessage, categoryId: Long) = TransactionEntity(
        smsId = message.id,
        type = type,
        amountPaise = amountPaise,
        merchant = merchant,
        vpa = vpa,
        mode = mode,
        bank = bank,
        accountTail = accountTail,
        refNo = refNo,
        timestamp = message.timestamp,
        categoryId = categoryId,
        excluded = internalTransfer,
        personTransfer = personTransfer,
        internalTransfer = internalTransfer,
        sender = message.sender,
        body = message.body,
    )

    private companion object {
        const val BATCH_SIZE = 200
        const val PROGRESS_EVERY = 100
        const val OVERLAP_MS = 60 * 60 * 1000L
    }
}
