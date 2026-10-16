package com.expensetracker.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.R
import com.expensetracker.ui.categories.CategoriesScreen
import com.expensetracker.ui.dashboard.DashboardScreen
import com.expensetracker.ui.settings.SettingsScreen
import com.expensetracker.ui.transactions.TransactionsScreen

private enum class Tab(val route: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    TRANSACTIONS("transactions", "Transactions"),
    CATEGORIES("categories", "Categories"),
    SETTINGS("settings", "Settings"),
}

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navController.openTab(tab) },
                        icon = { TabIcon(tab) },
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
                DashboardScreen(onOpenTransactions = { navController.openTab(Tab.TRANSACTIONS) })
            }
            composable(Tab.TRANSACTIONS.route) { TransactionsScreen() }
            composable(Tab.CATEGORIES.route) { CategoriesScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
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

@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.OVERVIEW -> Icon(Icons.Filled.Home, contentDescription = null)
        Tab.TRANSACTIONS -> Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
        Tab.CATEGORIES -> Icon(painterResource(R.drawable.ic_category), contentDescription = null)
        Tab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
    }
}
