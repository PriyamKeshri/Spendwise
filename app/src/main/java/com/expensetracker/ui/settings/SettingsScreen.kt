package com.expensetracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.sms.SyncStatus
import com.expensetracker.statement.ImportReport
import com.expensetracker.ui.components.AppCard
import com.expensetracker.ui.components.HistoryRange
import com.expensetracker.ui.components.ScreenTitle
import com.expensetracker.ui.components.dateLabel
import java.time.LocalDate

@Composable
fun SettingsScreen(onOpenCategories: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val historyStart by viewModel.historyStart.collectAsStateWithLifecycle()
    val count by viewModel.transactionCount.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showScanDialog by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importStatement)
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            ScreenTitle("Settings", Modifier.padding(start = 4.dp))

            val start = historyStart
            val scanned = when {
                sync is SyncStatus.Running -> "Reading messages…"
                start == 0L -> "Every message has been read · $count transactions"
                start != null -> "Read since ${start.dateLabel()} · $count transactions"
                else -> "Not scanned yet"
            }
            SectionLabel("Messages")
            AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                SettingRow(Icons.Rounded.CalendarMonth, "Scan older messages", scanned) { showScanDialog = true }
                RowDivider()
                SettingRow(
                    Icons.Rounded.Refresh,
                    "Check for new messages",
                    "New bank messages are picked up automatically as they arrive",
                    onClick = viewModel::checkNow,
                )
            }

            SectionLabel("Sorting")
            AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                SettingRow(
                    Icons.Rounded.Category,
                    "Categories and keywords",
                    "Change how payments are sorted, and the merchants you've sorted yourself",
                    onClick = onOpenCategories,
                )
            }

            SectionLabel("Data")
            AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                SettingRow(
                    Icons.Rounded.UploadFile,
                    "Import Paytm statement",
                    if (importState == SettingsViewModel.ImportState.Running) {
                        "Importing…"
                    } else {
                        "Adds payments that never came by SMS, like UPI Lite. In Paytm: Balance & History → " +
                            "Download UPI Statement → Excel."
                    },
                ) { importLauncher.launch(STATEMENT_TYPES) }
                RowDivider()
                SettingRow(Icons.Rounded.IosShare, "Export to CSV", "Save every transaction as a spreadsheet") {
                    exportLauncher.launch("expenses-${LocalDate.now()}.csv")
                }
            }

            SectionLabel("Privacy")
            AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                SettingRow(
                    Icons.Rounded.Lock,
                    "Stays on your phone",
                    "Messages are read and stored only on this device. The app has no internet permission, " +
                        "so nothing can be uploaded.",
                )
            }

            SectionLabel("About")
            AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                SettingRow(
                    Icons.Rounded.Info,
                    "Version ${appVersion()}",
                    "Set in Plus Jakarta Sans, used under the SIL Open Font License",
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showScanDialog) {
        ScanHistoryDialog(
            onDismiss = { showScanDialog = false },
            onScan = { range ->
                viewModel.scanHistory(range.months)
                showScanDialog = false
            },
        )
    }

    when (val state = importState) {
        is SettingsViewModel.ImportState.Done -> ImportResultDialog(
            title = "Paytm statement imported",
            message = importSummary(state.report),
            onDismiss = viewModel::dismissImportResult,
        )
        is SettingsViewModel.ImportState.Failed -> ImportResultDialog(
            title = "Couldn't import this file",
            message = state.message + "\n\nIn Paytm, go to Balance & History → Download UPI Statement and choose Excel.",
            onDismiss = viewModel::dismissImportResult,
        )
        else -> Unit
    }
}

/** Spreadsheet types a file picker may report for a Paytm statement (some report .xlsx as a plain binary). */
private val STATEMENT_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values",
    "application/octet-stream",
)

private fun importSummary(report: ImportReport): String = buildList {
    add(
        if (report.added == 0) {
            "Nothing new: everything in this file is already in Spendwise."
        } else {
            "Added ${plural(report.added, "payment")}."
        },
    )
    if (report.added > 0 && report.alreadyTracked > 0) {
        add("${plural(report.alreadyTracked, "payment")} ${if (report.alreadyTracked == 1) "was" else "were"} already tracked.")
    }
    if (report.skippedNotCompleted > 0) {
        add("Skipped ${plural(report.skippedNotCompleted, "failed or pending payment")}.")
    }
    if (report.skippedLiteTopUps > 0) {
        add("Skipped ${plural(report.skippedLiteTopUps, "UPI Lite top-up")}, since the Lite payments themselves are counted.")
    }
    if (report.unreadableRows > 0) {
        add("${plural(report.unreadableRows, "row")} couldn't be read.")
    }
}.joinToString("\n")

private fun plural(count: Int, noun: String) = "$count $noun" + if (count == 1) "" else "s"

@Composable
private fun ImportResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScanHistoryDialog(onDismiss: () -> Unit, onScan: (HistoryRange) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(HistoryRange.ONE_YEAR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan older messages") },
        text = {
            Column {
                Text(
                    "Messages already imported are skipped, and your category changes are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                HistoryRange.entries.forEach { range ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = selected == range, onClick = { selected = range }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == range, onClick = null)
                        Text(range.label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onScan(selected) }) { Text("Scan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
}
