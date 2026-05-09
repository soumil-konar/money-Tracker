package com.soumil.moneytracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : AppDestination("home", "Home", Icons.Outlined.Home)
    data object Transactions : AppDestination("transactions", "Transactions", Icons.Outlined.ReceiptLong)
    data object More : AppDestination("more", "More", Icons.Outlined.MoreHoriz)
    data object Settings : AppDestination("settings", "Settings", Icons.Outlined.Settings)
}

val bottomDestinations = listOf(
    AppDestination.Home,
    AppDestination.Transactions,
    AppDestination.More,
    AppDestination.Settings,
)

