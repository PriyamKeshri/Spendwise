package com.expensetracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.prefs.SettingsStore
import com.expensetracker.data.repo.ExpenseRepository
import com.expensetracker.model.TxnSource
import com.expensetracker.sms.SyncScheduler
import com.expensetracker.sms.SyncStatus
import com.expensetracker.statement.ImportReport
import com.expensetracker.statement.StatementFormatException
import com.expensetracker.statement.StatementImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository,
    private val syncScheduler: SyncScheduler,
    private val statementImporter: StatementImporter,
    settings: SettingsStore,
) : ViewModel() {

    val historyStart: StateFlow<Long?> = settings.historyStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactionCount: StateFlow<Int> = repository.transactionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sync: StateFlow<SyncStatus> = syncScheduler.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.Idle)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        data class Done(val report: ImportReport) : ImportState
        data class Failed(val message: String) : ImportState
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    fun importStatement(uri: Uri) {
        if (_importState.value == ImportState.Running) return
        _importState.value = ImportState.Running
        viewModelScope.launch {
            _importState.value = try {
                ImportState.Done(statementImporter.import(uri))
            } catch (e: StatementFormatException) {
                ImportState.Failed(e.message ?: UNREADABLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A corrupt or unexpected file: the zip, XML or text decoding gave up.
                ImportState.Failed(UNREADABLE)
            }
        }
    }

    fun dismissImportResult() {
        _importState.value = ImportState.Idle
    }

    fun scanHistory(months: Int) {
        syncScheduler.importHistory(months)
        messageChannel.trySend("Reading older messages in the background")
    }

    fun checkNow() {
        syncScheduler.syncNow()
        messageChannel.trySend("Checking for new messages")
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val count = withContext(Dispatchers.IO) { writeCsv(uri) }
                "Exported $count transactions"
            } catch (e: IOException) {
                "Couldn't export: ${e.message}"
            }
            messageChannel.send(message)
        }
    }

    private suspend fun writeCsv(uri: Uri): Int {
        val transactions = repository.allTransactions()
        val categories = repository.allCategories().associateBy { it.id }
        val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file")
        stream.bufferedWriter().use { writer ->
            writer.appendLine("Date,Type,Amount,Merchant,Category,Mode,Bank,Account,Reference,Counted,Source")
            for (txn in transactions) {
                val row = listOf(
                    Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(CSV_DATE),
                    if (txn.type.name == "DEBIT") "Debit" else "Credit",
                    BigDecimal.valueOf(txn.amountPaise, 2).toPlainString(),
                    txn.merchant,
                    categories[txn.categoryId]?.name.orEmpty(),
                    txn.mode.label,
                    txn.bank.orEmpty(),
                    txn.accountTail.orEmpty(),
                    txn.refNo.orEmpty(),
                    if (txn.excluded) "No" else "Yes",
                    if (txn.source == TxnSource.PAYTM) StatementImporter.SOURCE_LABEL else "SMS",
                )
                writer.appendLine(row.joinToString(",") { csvField(it) })
            }
        }
        return transactions.size
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

    private companion object {
        val CSV_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        const val UNREADABLE = "Couldn't read this file as a statement."
    }
}
