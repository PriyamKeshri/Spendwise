package com.expensetracker.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.ui.categories.CategoriesScreen
import com.expensetracker.ui.dashboard.DashboardScreen
import com.expensetracker.ui.insights.InsightsScreen
import com.expensetracker.ui.settings.SettingsScreen
import com.expensetracker.ui.transactions.TransactionsScreen

private enum class Tab(val route: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    OVERVIEW("overview", "Overview", Icons.Rounded.Home, Icons.Outlined.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    INSIGHTS("insights", "Insights", Icons.Rounded.Insights, Icons.Outlined.Insights),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/** Opened from Settings rather than the tab bar: editing categories is occasional. */
private const val CATEGORIES_ROUTE = "categories"

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                Tab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route || (tab == Tab.SETTINGS && currentRoute == CATEGORIES_ROUTE)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.openTab(tab) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.OVERVIEW.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.OVERVIEW.route) {
                DashboardScreen(
                    onOpenTransactions = { navController.openTab(Tab.TRANSACTIONS) },
                    onOpenInsights = { navController.openTab(Tab.INSIGHTS) },
                )
            }
            composable(Tab.TRANSACTIONS.route) { TransactionsScreen() }
            composable(Tab.INSIGHTS.route) { InsightsScreen() }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) })
            }
            composable(CATEGORIES_ROUTE) { CategoriesScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
