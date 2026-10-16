package com.expensetracker.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.SmsPermissions
import com.expensetracker.ui.components.AppCard
import com.expensetracker.ui.components.FilterPill
import com.expensetracker.ui.components.HistoryRange
import com.expensetracker.ui.components.label
import com.expensetracker.ui.theme.appColors
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(hasPermission: Boolean, onStart: (months: Int) -> Unit) {
    val context = LocalContext.current
    var range by rememberSaveable { mutableStateOf(HistoryRange.SIX_MONTHS) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[android.Manifest.permission.READ_SMS] == true) onStart(range.months) else denied = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(appColors.hero),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = appColors.onHero,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Your spending, sorted automatically", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            "Spendwise reads the alerts your bank sends by SMS and turns them into a monthly picture " +
                "of where your money goes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        AppCard(contentPadding = PaddingValues(vertical = 8.dp)) {
            Feature(
                Icons.Rounded.MarkEmailRead,
                "Only bank alerts",
                "Debits and credits from SBI and other banks. OTPs, offers and personal chats are skipped.",
            )
            Feature(Icons.Rounded.Lock, "Stays on your phone", "No account and no cloud. The app doesn't even have internet access.")
            Feature(
                Icons.Rounded.AutoAwesome,
                "Sorted for you",
                "Food, shopping, bills and more. Unknown merchants go to Others, and it learns when you correct it.",
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Read past messages from", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRange.entries.forEach { option ->
                FilterPill(option.label, selected = range == option, onClick = { range = option })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Whole months are read, so “Last month” includes all of ${YearMonth.now().minusMonths(1).label()}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (hasPermission) onStart(range.months) else permissionLauncher.launch(SmsPermissions.required)
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (hasPermission) "Read my messages" else "Allow SMS access", style = MaterialTheme.typography.titleMedium)
        }
        if (denied && !hasPermission) {
            Spacer(Modifier.height(16.dp))
            Text(
                "SMS access is needed to find your transactions. If Android doesn't ask again, turn on " +
                    "SMS under Permissions in the app's settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Open app settings") }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
