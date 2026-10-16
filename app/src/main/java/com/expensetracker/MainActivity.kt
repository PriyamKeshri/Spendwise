package com.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.prefs.SettingsStore
import com.expensetracker.sms.SmsPermissions
import com.expensetracker.sms.SyncScheduler
import com.expensetracker.ui.navigation.MainScaffold
import com.expensetracker.ui.onboarding.OnboardingScreen
import com.expensetracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    settings: SettingsStore,
) : ViewModel() {

    private val importRequested = MutableStateFlow(false)

    /** Null while loading; true once the first history import has started or finished. */
    val setUp: StateFlow<Boolean?> = combine(settings.historyImported, importRequested) { imported, requested ->
        imported || requested
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun startImport(months: Int) {
        importRequested.value = true
        syncScheduler.importHistory(months)
    }

    /** Catch up on messages that arrived while the app was closed (receivers don't run after a force stop). */
    fun onResume(hasPermission: Boolean) {
        if (hasPermission && setUp.value == true) syncScheduler.syncNow()
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(SmsPermissions.canRead(context)) }
    val setUp by viewModel.setUp.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        hasPermission = SmsPermissions.canRead(context)
        viewModel.onResume(hasPermission)
        onPauseOrDispose { }
    }

    when {
        setUp == null -> Unit
        hasPermission && setUp == true -> MainScaffold()
        else -> OnboardingScreen(
            hasPermission = hasPermission,
            onStart = { months ->
                hasPermission = SmsPermissions.canRead(context)
                viewModel.startImport(months)
            },
        )
    }
}
