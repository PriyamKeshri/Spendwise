package com.expensetracker.sms

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    /** [history] is true for the full inbox scan, false for quick catch-up syncs. */
    data class Running(val scanned: Int, val total: Int, val found: Int, val history: Boolean) : SyncStatus
    data class Finished(val added: Int) : SyncStatus
}

/** All imports share one unique work chain, so a history scan and live updates never run at once. */
@Singleton
class SyncScheduler @Inject constructor(private val workManager: WorkManager) {

    val status: Flow<SyncStatus> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        val active = infos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || (it.state == WorkInfo.State.ENQUEUED && TAG_HISTORY in it.tags)
        }
        val finished = infos.lastOrNull { it.state == WorkInfo.State.SUCCEEDED }
        when {
            active != null -> SyncStatus.Running(
                scanned = active.progress.getInt(SmsSyncWorker.KEY_SCANNED, 0),
                total = active.progress.getInt(SmsSyncWorker.KEY_TOTAL, 0),
                found = active.progress.getInt(SmsSyncWorker.KEY_FOUND, 0),
                history = TAG_HISTORY in active.tags,
            )
            finished != null -> SyncStatus.Finished(finished.outputData.getInt(SmsSyncWorker.KEY_ADDED, 0))
            else -> SyncStatus.Idle
        }
    }

    /** Scans the inbox back to the start of the month [months] months ago ([SmsSyncWorker.ALL_HISTORY] for all). */
    fun importHistory(months: Int) {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInputData(workDataOf(SmsSyncWorker.KEY_HISTORY_MONTHS to months))
            .addTag(TAG_HISTORY)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Catches up on anything that arrived while the app wasn't listening. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "sms-sync"
        private const val TAG_HISTORY = "history"

        /**
         * Called by [SmsReceiver] when a bank-looking message arrives. The short delay gives the
         * default SMS app time to write the message into the inbox we read from.
         */
        fun onNewMessage(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
