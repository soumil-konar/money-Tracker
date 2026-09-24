package com.soumil.moneytracker.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.soumil.moneytracker.ui.asFullDate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics
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
import com.soumil.moneytracker.ui.screen.BudgetHistoryScreen
import com.soumil.moneytracker.ui.screen.HomeScreen
import com.soumil.moneytracker.ui.screen.MoreScreen
import com.soumil.moneytracker.ui.screen.SettingsScreen
import com.soumil.moneytracker.ui.screen.SpendingAssistantSheet
import com.soumil.moneytracker.ui.screen.TransactionsScreen

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoneyTrackerRoot(
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val budgetHistory by viewModel.budgetHistory.collectAsStateWithLifecycle()
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val budget by viewModel.currentBudget.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAiAnalyzing by viewModel.isAiAnalyzing.collectAsStateWithLifecycle()
    val aiApiKey by viewModel.aiApiKey.collectAsStateWithLifecycle()
    val isAiEnabled by viewModel.isAiEnabled.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val engineMode by viewModel.engineMode.collectAsStateWithLifecycle()
    val aiTestStatus by viewModel.aiTestStatus.collectAsStateWithLifecycle()
    val assistantMessages by viewModel.assistantMessages.collectAsStateWithLifecycle()
    val isAssistantThinking by viewModel.isAssistantThinking.collectAsStateWithLifecycle()
    val scheduledTransactions by viewModel.scheduledTransactions.collectAsStateWithLifecycle()
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsStateWithLifecycle()
    val suggestedSubscriptions by viewModel.suggestedSubscriptions.collectAsStateWithLifecycle()
    val isInitialSetupComplete by viewModel.isInitialSetupComplete.collectAsStateWithLifecycle()
    val isHapticEnabled by viewModel.isHapticEnabled.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val isEmailSyncEnabled by viewModel.isEmailSyncEnabled.collectAsStateWithLifecycle()
    val emailAddress by viewModel.emailAddress.collectAsStateWithLifecycle()
    val emailAppPassword by viewModel.emailAppPassword.collectAsStateWithLifecycle()
    val emailLastSyncTimestamp by viewModel.emailLastSyncTimestamp.collectAsStateWithLifecycle()
    val emailLastSyncStatus by viewModel.emailLastSyncStatus.collectAsStateWithLifecycle()
    val isEmailSyncing by viewModel.isEmailSyncing.collectAsStateWithLifecycle()
    val emailTestStatus by viewModel.emailTestStatus.collectAsStateWithLifecycle()

    val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled.collectAsStateWithLifecycle()
    val isGmailMonitoringEnabled by viewModel.isGmailMonitoringEnabled.collectAsStateWithLifecycle()
    val isPaymentAppsMonitoringEnabled by viewModel.isPaymentAppsMonitoringEnabled.collectAsStateWithLifecycle()
    val isBankAppsMonitoringEnabled by viewModel.isBankAppsMonitoringEnabled.collectAsStateWithLifecycle()
    val notificationLastCapturedTimestamp by viewModel.notificationLastCapturedTimestamp.collectAsStateWithLifecycle()
    val notificationLastCapturedPackage by viewModel.notificationLastCapturedPackage.collectAsStateWithLifecycle()
    val notificationCapturedCount by viewModel.notificationCapturedCount.collectAsStateWithLifecycle()
    val isExclusionFilterEnabled by viewModel.isExclusionFilterEnabled.collectAsStateWithLifecycle()
    val excludedKeywords by viewModel.excludedKeywords.collectAsStateWithLifecycle()
    var notificationPermissionGranted by remember { mutableStateOf(viewModel.isNotificationPermissionGranted(context)) }

    val haptics = LocalAppHaptics.current
    val primaryBankAccount = accounts.firstOrNull { it.kind == AccountKind.BANK && it.institutionName != null }
        ?: accounts.firstOrNull { it.kind == AccountKind.BANK }

    var showAiChatSheet by remember { mutableStateOf(false) }
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
        notificationPermissionGranted = viewModel.isNotificationPermissionGranted(context)
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Home.route,
                modifier = Modifier.fillMaxSize(),
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
                    onBudgetClick = {
                        navController.navigate(AppDestination.BudgetHistory.route)
                    },
                    onRefreshAiInsights = { viewModel.refreshAiSpendingInsights() },
                    onOpenAssistant = { showAiChatSheet = true },
                    onAccountsClick = { navController.navigate(AppDestination.More.route) },
                )
            }
            composable(AppDestination.BudgetHistory.route) {
                BudgetHistoryScreen(
                    history = budgetHistory,
                    initiallyExpandedKey = budgetHistory.firstOrNull()?.yearMonthKey,
                    onBack = {
                        navController.navigate(AppDestination.Home.route) {
                            popUpTo(AppDestination.BudgetHistory.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onEditTransaction = { transaction ->
                        showAddTransactionDialog = false
                        editingTransaction = transaction
                        transactionDialogKey += 1
                    },
                )
            }
            composable(AppDestination.Transactions.route) {
                TransactionsScreen(
                    filter = filter,
                    transactions = transactions,
                    cardAccounts = accounts.filter { it.kind == AccountKind.CARD },
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onFilterSelected = viewModel::setFilter,
                    onAddTransactionClick = {
                        editingTransaction = null
                        transactionDialogKey += 1
                        showAddTransactionDialog = true
                    },
                    onApproveReview = viewModel::approveReview,
                    onAnalyzeWithAi = viewModel::enrichTransactionWithAi,
                    isAiAnalyzing = isAiAnalyzing,
                    onEditTransaction = { transaction ->
                        showAddTransactionDialog = false
                        editingTransaction = transaction
                        transactionDialogKey += 1
                    },
                    onDeleteTransaction = { transaction ->
                        pendingDeleteTransaction = transaction
                    },
                    onToggleBudgetInclusion = { transaction ->
                        viewModel.setTransactionBudgetInclusion(
                            transactionId = transaction.id,
                            countsTowardBudget = !transaction.countsTowardBudget,
                        )
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
                    onExportCsv = { exportTransactionsToCsv(context, transactions) },
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = { permissionLauncher.launch(context.smsPermissionArray()) },
                    onImportRecentSms = { viewModel.importRecentSms(context.contentResolver) },
                    aiApiKey = aiApiKey,
                    isAiEnabled = isAiEnabled,
                    selectedModel = selectedModel,
                    engineMode = engineMode,
                    deviceAiStatus = viewModel.deviceStatus,
                    isPixel9Ready = viewModel.isTensorG4Ready,
                    aiTestStatus = aiTestStatus,
                    onUpdateApiKey = viewModel::updateAiApiKey,
                    onToggleAiEnabled = viewModel::setAiEnabled,
                    onSelectModel = viewModel::setSelectedModel,
                    onSelectEngineMode = viewModel::setAiEngineMode,
                    onTestAiConnection = viewModel::testAiConnection,
                    isHapticEnabled = isHapticEnabled,
                    hapticIntensity = hapticIntensity,
                    onToggleHapticEnabled = viewModel::setHapticEnabled,
                    onSelectHapticIntensity = viewModel::setHapticIntensity,
                    isBiometricEnabled = isBiometricEnabled,
                    isBiometricAvailable = viewModel.isBiometricHardwareAvailable(context),
                    onToggleBiometricEnabled = viewModel::setBiometricEnabled,
                    isNotificationListenerEnabled = isNotificationListenerEnabled,
                    isNotificationPermissionGranted = notificationPermissionGranted,
                    isGmailMonitoringEnabled = isGmailMonitoringEnabled,
                    isPaymentAppsMonitoringEnabled = isPaymentAppsMonitoringEnabled,
                    isBankAppsMonitoringEnabled = isBankAppsMonitoringEnabled,
                    notificationLastCapturedTimestamp = notificationLastCapturedTimestamp,
                    notificationLastCapturedPackage = notificationLastCapturedPackage,
                    notificationCapturedCount = notificationCapturedCount,
                    onOpenNotificationSettings = {
                        val intent = viewModel.buildNotificationSettingsIntent(context)
                        context.startActivity(intent)
                    },
                    onToggleNotificationListener = viewModel::setNotificationListenerEnabled,
                    onToggleGmailMonitoring = viewModel::setGmailMonitoringEnabled,
                    onTogglePaymentAppsMonitoring = viewModel::setPaymentAppsMonitoringEnabled,
                    onToggleBankAppsMonitoring = viewModel::setBankAppsMonitoringEnabled,
                    isExclusionFilterEnabled = isExclusionFilterEnabled,
                    excludedKeywords = excludedKeywords,
                    onToggleExclusionFilter = viewModel::setExclusionFilterEnabled,
                    onAddExclusionKeyword = viewModel::addExclusionKeyword,
                    onRemoveExclusionKeyword = viewModel::removeExclusionKeyword,
                    onResetExclusionKeywords = viewModel::resetExclusionKeywords,
                    isEmailSyncEnabled = isEmailSyncEnabled,
                    emailAddress = emailAddress,
                    emailAppPassword = emailAppPassword,
                    emailLastSyncTimestamp = emailLastSyncTimestamp,
                    emailLastSyncStatus = emailLastSyncStatus,
                    isEmailSyncing = isEmailSyncing,
                    emailTestStatus = emailTestStatus,
                    onToggleEmailSync = viewModel::setEmailSyncEnabled,
                    onUpdateEmailCredentials = viewModel::updateEmailCredentials,
                    onTestEmailConnection = viewModel::testEmailConnection,
                    onClearEmailCredentials = viewModel::clearEmailCredentials,
                    onSyncRecentEmails = viewModel::syncRecentEmails,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp),
        )

        FloatingActionButton(
            onClick = {
                haptics.click()
                showAiChatSheet = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 96.dp)
                .size(54.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "Ask Spending Assistant",
                modifier = Modifier.size(24.dp),
            )
        }

        TrackerBottomBar(
            currentRoute = currentRoute,
            onNavigate = { destination ->
                haptics.selection()
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onAddTransaction = {
                haptics.click()
                editingTransaction = null
                transactionDialogKey += 1
                showAddTransactionDialog = true
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        }
    }

    if (showAiChatSheet) {
        SpendingAssistantSheet(
            messages = assistantMessages,
            isThinking = isAssistantThinking,
            onSendMessage = viewModel::askAssistant,
            onClearChat = viewModel::clearAssistantChat,
            onDismiss = { showAiChatSheet = false },
        )
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

    val hasConfiguredBank = accounts.any { it.kind == AccountKind.BANK }
    val shouldShowInitialSetup = !isInitialSetupComplete && !dismissSetupForSession && !hasConfiguredBank

    if (shouldShowInitialSetup) {
        InitialSetupDialog(
            configuredBank = primaryBankAccount,
            configuredCards = accounts.filter { it.kind == AccountKind.CARD },
            onDismiss = {
                dismissSetupForSession = true
                viewModel.markInitialSetupComplete()
            },
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
        countsTowardBudget = countsTowardBudget,
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
        currentBalance = currentBalance,
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
    val barShape = RoundedCornerShape(32.dp)
    val baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            Color.Transparent,
        ),
    )
    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = barShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                )
                .clip(barShape)
                .background(baseColor)
                .background(brush = highlightBrush)
                .border(BorderStroke(1.2.dp, rimBrush), barShape),
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

private fun exportTransactionsToCsv(context: Context, transactions: List<TransactionRecord>) {
    val header = "ID,Date,Merchant,Amount,Direction,Category,Account,Note,Status\n"
    val rows = transactions.joinToString("\n") { t ->
        listOf(
            t.id,
            t.occurredAtMillis.asFullDate(),
            "\"${t.merchant.replace("\"", "\"\"")}\"",
            t.amount,
            t.direction.name,
            t.category.label,
            "\"${(t.accountName ?: "").replace("\"", "\"\"")}\"",
            "\"${(t.note ?: "").replace("\"", "\"\"")}\"",
            t.status.name,
        ).joinToString(",")
    }
    val csv = header + rows
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Money Tracker Transactions Export")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Transactions CSV"))
}
