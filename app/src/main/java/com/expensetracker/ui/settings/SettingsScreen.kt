package com.expensetracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.sms.SyncStatus
import com.expensetracker.ui.components.HistoryRange
import com.expensetracker.ui.components.dateLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val historyStart by viewModel.historyStart.collectAsStateWithLifecycle()
    val count by viewModel.transactionCount.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showScanDialog by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Messages")
            val start = historyStart
            val scanned = when {
                sync is SyncStatus.Running -> "Reading messages…"
                start == 0L -> "Every message has been read · $count transactions"
                start != null -> "Read since ${start.dateLabel()} · $count transactions"
                else -> "Not scanned yet"
            }
            ListItem(
                modifier = Modifier.clickable { showScanDialog = true },
                leadingContent = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                headlineContent = { Text("Scan older messages") },
                supportingContent = { Text(scanned) },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = viewModel::checkNow),
                leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                headlineContent = { Text("Check for new messages") },
                supportingContent = { Text("New bank messages are picked up automatically as they arrive") },
            )

            SectionTitle("Data")
            ListItem(
                modifier = Modifier.clickable { exportLauncher.launch("expenses-${LocalDate.now()}.csv") },
                leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                headlineContent = { Text("Export to CSV") },
                supportingContent = { Text("Save every transaction as a spreadsheet") },
            )

            SectionTitle("Privacy")
            ListItem(
                leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                headlineContent = { Text("Stays on your phone") },
                supportingContent = {
                    Text(
                        "Messages are read and stored only on this device. The app has no internet " +
                            "permission, so nothing can be uploaded.",
                    )
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("Version") },
                supportingContent = { Text(appVersion()) },
            )
        }
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
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
                HistoryRange.entries.forEach { range ->
                    Row(
                        Modifier
                            .fillMaxWidth()
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
