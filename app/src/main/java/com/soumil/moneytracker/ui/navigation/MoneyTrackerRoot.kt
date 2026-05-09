package com.soumil.moneytracker.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.AccountDraft
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionDraft
import com.soumil.moneytracker.ui.MainViewModel
import com.soumil.moneytracker.ui.components.AddSubscriptionDialog
import com.soumil.moneytracker.ui.components.AccountEditorDialog
import com.soumil.moneytracker.ui.components.AddTransactionDialog
import com.soumil.moneytracker.ui.components.BudgetDialog
import com.soumil.moneytracker.ui.components.DeleteAccountDialog
import com.soumil.moneytracker.ui.components.DeleteTransactionDialog
import com.soumil.moneytracker.ui.components.InitialSetupDialog
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
    val scheduledTransactions by viewModel.scheduledTransactions.collectAsStateWithLifecycle()
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsStateWithLifecycle()
    val suggestedSubscriptions by viewModel.suggestedSubscriptions.collectAsStateWithLifecycle()
    val isInitialSetupComplete by viewModel.isInitialSetupComplete.collectAsStateWithLifecycle()
    val primaryBankAccount = accounts.firstOrNull { it.kind == AccountKind.BANK && it.institutionName != null }
        ?: accounts.firstOrNull { it.kind == AccountKind.BANK }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showAddSubscriptionDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var pendingDeleteTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var pendingDeleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var transactionDialogKey by remember { mutableStateOf(0) }
    var dismissSetupForSession by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var accountDialogDraft by remember { mutableStateOf<AccountDraft?>(null) }
    var accountDialogKey by remember { mutableStateOf(0) }
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
            TrackerBottomBar(
                currentRoute = currentRoute,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAddTransaction = {
                    editingTransaction = null
                    transactionDialogKey += 1
                    showAddTransactionDialog = true
                },
            )
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
                    onAddTransactionClick = {
                        editingTransaction = null
                        transactionDialogKey += 1
                        showAddTransactionDialog = true
                    },
                )
            }
            composable(AppDestination.Transactions.route) {
                TransactionsScreen(
                    filter = filter,
                    transactions = transactions,
                    cardAccounts = accounts.filter { it.kind == AccountKind.CARD },
                    onFilterSelected = viewModel::setFilter,
                    onAddTransactionClick = {
                        editingTransaction = null
                        transactionDialogKey += 1
                        showAddTransactionDialog = true
                    },
                    onApproveReview = viewModel::approveReview,
                    onEditTransaction = { transaction ->
                        showAddTransactionDialog = false
                        editingTransaction = transaction
                        transactionDialogKey += 1
                    },
                    onDeleteTransaction = { transaction ->
                        pendingDeleteTransaction = transaction
                    },
                )
            }
            composable(AppDestination.More.route) {
                MoreScreen(
                    accounts = accounts,
                    activeSubscriptions = activeSubscriptions,
                    scheduledTransactions = scheduledTransactions,
                    suggestedSubscriptions = suggestedSubscriptions,
                    onAddSubscriptionClick = { showAddSubscriptionDialog = true },
                    onAddBankClick = {
                        editingAccount = null
                        accountDialogDraft = AccountDraft(
                            name = "",
                            kind = AccountKind.BANK,
                            institutionName = primaryBankAccount?.institutionName,
                        )
                        accountDialogKey += 1
                    },
                    onAddCardClick = {
                        editingAccount = null
                        accountDialogDraft = AccountDraft(
                            name = "",
                            kind = AccountKind.CARD,
                            institutionName = primaryBankAccount?.institutionName,
                        )
                        accountDialogKey += 1
                    },
                    onEditAccount = { account ->
                        editingAccount = account
                        accountDialogDraft = account.toDraft()
                        accountDialogKey += 1
                    },
                    onDeleteAccount = { account ->
                        pendingDeleteAccount = account
                    },
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

    if (showAddTransactionDialog || editingTransaction != null) {
        val transactionToEdit = editingTransaction
        AddTransactionDialog(
            accounts = accounts,
            onDismiss = {
                showAddTransactionDialog = false
                editingTransaction = null
            },
            onConfirm = {
                if (transactionToEdit == null) {
                    viewModel.addTransaction(it)
                } else {
                    viewModel.updateTransaction(transactionToEdit.id, it)
                }
                showAddTransactionDialog = false
                editingTransaction = null
            },
            title = if (transactionToEdit == null) "Add transaction" else "Edit transaction",
            confirmLabel = if (transactionToEdit == null) "Save" else "Save changes",
            initialDraft = transactionToEdit?.toDraft(),
            dialogKey = transactionDialogKey,
        )
    }

    pendingDeleteTransaction?.let { transaction ->
        DeleteTransactionDialog(
            merchant = transaction.merchant,
            amount = transaction.amount,
            onDismiss = { pendingDeleteTransaction = null },
            onConfirm = {
                viewModel.deleteTransaction(transaction.id)
                pendingDeleteTransaction = null
            },
        )
    }

    pendingDeleteAccount?.let { account ->
        DeleteAccountDialog(
            account = account,
            onDismiss = { pendingDeleteAccount = null },
            onConfirm = {
                viewModel.deleteAccount(account.id)
                pendingDeleteAccount = null
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

    accountDialogDraft?.let { initialDraft ->
        AccountEditorDialog(
            initialDraft = initialDraft,
            onDismiss = {
                accountDialogDraft = null
                editingAccount = null
            },
            onConfirm = { draft ->
                val accountToEdit = editingAccount
                if (accountToEdit == null) {
                    viewModel.addAccount(draft)
                } else {
                    viewModel.updateAccount(accountToEdit.id, draft)
                }
                accountDialogDraft = null
                editingAccount = null
            },
            title = if (editingAccount == null) {
                if (initialDraft.kind == AccountKind.CARD) "Add card" else "Add bank"
            } else {
                "Edit account"
            },
            confirmLabel = if (editingAccount == null) "Save" else "Save changes",
            dialogKey = accountDialogKey,
        )
    }

    if (!isInitialSetupComplete && !dismissSetupForSession) {
        InitialSetupDialog(
            configuredBank = primaryBankAccount,
            configuredCards = accounts.filter { it.kind == AccountKind.CARD },
            onDismiss = { dismissSetupForSession = true },
            onSaveBank = viewModel::configurePrimaryBank,
            onAddCard = viewModel::addAccount,
            onFinish = viewModel::markInitialSetupComplete,
        )
    }
}

private fun TransactionRecord.toDraft(): TransactionDraft {
    return TransactionDraft(
        amount = amount,
        direction = direction,
        merchant = merchant,
        category = category,
        accountId = accountId,
        note = note,
        occurredAtMillis = occurredAtMillis,
    )
}

private fun AccountEntity.toDraft(): AccountDraft {
    return AccountDraft(
        name = name,
        kind = kind,
        institutionName = institutionName,
        cardType = cardType,
        lastFourDigits = lastFourDigits,
        isRupayCreditCard = isRupayCreditCard,
    )
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

@Composable
private fun TrackerBottomBar(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BottomDockItem(
                    destination = AppDestination.Home,
                    selected = currentRoute == AppDestination.Home.route,
                    onClick = { onNavigate(AppDestination.Home) },
                )
                BottomDockItem(
                    destination = AppDestination.Transactions,
                    selected = currentRoute == AppDestination.Transactions.route,
                    onClick = { onNavigate(AppDestination.Transactions) },
                )
                AddDockItem(onClick = onAddTransaction)
                BottomDockItem(
                    destination = AppDestination.More,
                    selected = currentRoute == AppDestination.More.route,
                    onClick = { onNavigate(AppDestination.More) },
                )
                BottomDockItem(
                    destination = AppDestination.Settings,
                    selected = currentRoute == AppDestination.Settings.route,
                    onClick = { onNavigate(AppDestination.Settings) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomDockItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navIconTint",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navContainerColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navIconScale",
    )
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            border = if (selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            } else {
                null
            },
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
    }
}

@Composable
private fun RowScope.AddDockItem(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add transaction",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
