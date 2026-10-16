package com.expensetracker.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Imports bank messages in the background, either a history window or just what's new. */
@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importer: SmsImporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SmsPermissions.canRead(applicationContext)) return Result.failure()

        val onProgress: suspend (SmsImporter.Progress) -> Unit = { progress ->
            setProgress(
                workDataOf(KEY_SCANNED to progress.scanned, KEY_TOTAL to progress.total, KEY_FOUND to progress.found),
            )
        }
        val added = try {
            if (inputData.keyValueMap.containsKey(KEY_HISTORY_MONTHS)) {
                importer.importHistory(inputData.getInt(KEY_HISTORY_MONTHS, DEFAULT_HISTORY_MONTHS), onProgress)
            } else {
                importer.importNew(onProgress)
            }
        } catch (e: SecurityException) {
            // Permission revoked while the work was queued.
            return Result.failure()
        }
        return Result.success(workDataOf(KEY_ADDED to added))
    }

    companion object {
        const val KEY_HISTORY_MONTHS = "history_months"
        const val KEY_SCANNED = "scanned"
        const val KEY_TOTAL = "total"
        const val KEY_FOUND = "found"
        const val KEY_ADDED = "added"

        const val DEFAULT_HISTORY_MONTHS = 6
        const val ALL_HISTORY = -1
    }
}
