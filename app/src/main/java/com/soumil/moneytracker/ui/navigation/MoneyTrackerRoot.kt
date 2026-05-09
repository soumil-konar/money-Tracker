package com.soumil.moneytracker.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soumil.moneytracker.ui.MainViewModel
import com.soumil.moneytracker.ui.components.AddSubscriptionDialog
import com.soumil.moneytracker.ui.components.AddTransactionDialog
import com.soumil.moneytracker.ui.components.BudgetDialog
import com.soumil.moneytracker.ui.screen.HomeScreen
import com.soumil.moneytracker.ui.screen.MoreScreen
import com.soumil.moneytracker.ui.screen.SettingsScreen
import com.soumil.moneytracker.ui.screen.TransactionsScreen

@Composable
fun MoneyTrackerRoot(
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val budget by viewModel.currentBudget.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsStateWithLifecycle()
    val suggestedSubscriptions by viewModel.suggestedSubscriptions.collectAsStateWithLifecycle()

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showAddSubscriptionDialog by remember { mutableStateOf(false) }
    var smsPermissionGranted by remember { mutableStateOf(context.hasSmsPermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        smsPermissionGranted = context.hasSmsPermissions()
    }

    LifecycleResumeEffect(Unit) {
        smsPermissionGranted = context.hasSmsPermissions()
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.onBackground,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .offset(y = (-4).dp),
            ) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                unselectedTextColor = MaterialTheme.colorScheme.secondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    dashboard = dashboard,
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = {
                        permissionLauncher.launch(context.smsPermissionArray())
                    },
                    onImportRecentSms = { viewModel.importRecentSms(context.contentResolver) },
                    onSetBudgetClick = { showBudgetDialog = true },
                    onAddTransactionClick = { showAddTransactionDialog = true },
                )
            }
            composable(AppDestination.Transactions.route) {
                TransactionsScreen(
                    filter = filter,
                    transactions = transactions,
                    onFilterSelected = viewModel::setFilter,
                    onAddTransactionClick = { showAddTransactionDialog = true },
                    onApproveReview = viewModel::approveReview,
                    onDismissTransaction = viewModel::dismissTransaction,
                )
            }
            composable(AppDestination.More.route) {
                MoreScreen(
                    accounts = accounts,
                    activeSubscriptions = activeSubscriptions,
                    suggestedSubscriptions = suggestedSubscriptions,
                    onAddSubscriptionClick = { showAddSubscriptionDialog = true },
                    onAcceptSuggestion = viewModel::acceptSuggestedSubscription,
                    onDismissSuggestion = viewModel::dismissSuggestedSubscription,
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = { permissionLauncher.launch(context.smsPermissionArray()) },
                    onImportRecentSms = { viewModel.importRecentSms(context.contentResolver) },
                )
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            currentValue = budget?.amountLimit,
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                viewModel.setMonthlyBudget(it)
                showBudgetDialog = false
            },
        )
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            accounts = accounts,
            onDismiss = { showAddTransactionDialog = false },
            onConfirm = {
                viewModel.addTransaction(it)
                showAddTransactionDialog = false
            },
        )
    }

    if (showAddSubscriptionDialog) {
        AddSubscriptionDialog(
            accounts = accounts,
            onDismiss = { showAddSubscriptionDialog = false },
            onConfirm = {
                viewModel.addSubscription(it)
                showAddSubscriptionDialog = false
            },
        )
    }
}

private fun android.content.Context.hasSmsPermissions(): Boolean {
    val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val receiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    return readSms && receiveSms
}

private fun android.content.Context.smsPermissionArray(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}
