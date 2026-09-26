package com.moneytracker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
) {
    data object Home : AppDestination("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Transactions : AppDestination("transactions", "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong)
    data object More : AppDestination("more", "More", Icons.Outlined.Widgets, Icons.Filled.Widgets)
    data object Settings : AppDestination("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
    data object BudgetHistory : AppDestination("budget-history", "Budgets", Icons.Outlined.Savings, Icons.Filled.Savings)
}

val bottomDestinations = listOf(
    AppDestination.Home,
    AppDestination.Transactions,
    AppDestination.More,
    AppDestination.Settings,
)

