package com.soumil.moneytracker.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.soumil.moneytracker.ui.asFullDate
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
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
import com.soumil.moneytracker.ui.components.BalanceProofDialog
import com.soumil.moneytracker.ui.components.BudgetDialog
import com.soumil.moneytracker.ui.components.DeleteAccountDialog
import com.soumil.moneytracker.ui.components.DeleteTransactionDialog
import com.soumil.moneytracker.ui.components.ExportBackupPassphraseDialog
import com.soumil.moneytracker.ui.components.RestoreBackupPassphraseDialog
import com.soumil.moneytracker.ui.components.TrueUpBalanceDialog
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
    initialOpenAddTransaction: Boolean = false,
    onConsumeOpenAddTransaction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val homeListState = rememberLazyListState()
    val transactionsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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
    val isHapticEnabled by viewModel.isHapticEnabled.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val biometricTimeout by viewModel.biometricTimeout.collectAsStateWithLifecycle()
    val untransferredAtmTransactions by viewModel.untransferredAtmTransactions.collectAsStateWithLifecycle()
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
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeAccent by viewModel.themeAccent.collectAsStateWithLifecycle()
    var notificationPermissionGranted by remember { mutableStateOf(viewModel.isNotificationPermissionGranted(context)) }

    val haptics = LocalAppHaptics.current
    val primaryBankAccount = accounts.firstOrNull { it.kind == AccountKind.BANK && it.institutionName != null }
        ?: accounts.firstOrNull { it.kind == AccountKind.BANK }

    var showAiChatSheet by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showAddSubscriptionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialOpenAddTransaction) {
        if (initialOpenAddTransaction) {
            showAddTransactionDialog = true
            onConsumeOpenAddTransaction?.invoke()
        }
    }
    var chartReloadKey by remember { mutableStateOf(0) }
    var transactionAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var lastOpenedWasEditing by remember { mutableStateOf(false) }
    var activeEditingTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var rootLayoutSize by remember { mutableStateOf(IntSize.Zero) }
    var editingTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var pendingDeleteTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var pendingDeleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var transactionDialogKey by remember { mutableStateOf(0) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var accountDialogDraft by remember { mutableStateOf<AccountDraft?>(null) }
    var accountDialogKey by remember { mutableStateOf(0) }
    var viewingBalanceProofAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var viewingTrueUpAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var smsPermissionGranted by remember { mutableStateOf(context.hasSmsPermissions()) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let { destination ->
            pendingExportPassphrase?.let { pass ->
                viewModel.exportBackup(context, destination, pass)
                pendingExportPassphrase = null
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

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

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { bottomDestinations.size })
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isBudgetHistory = navBackStackEntry?.destination?.route == AppDestination.BudgetHistory.route
    val currentRoute = if (isBudgetHistory) AppDestination.BudgetHistory.route else bottomDestinations.getOrNull(pagerState.currentPage)?.route ?: AppDestination.Home.route

    BackHandler(enabled = !isBudgetHistory && pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    val isAddTransactionOpen = showAddTransactionDialog || editingTransaction != null
    val backgroundBlur by animateDpAsState(
        targetValue = if (isAddTransactionOpen) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "mainBackgroundBlur",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isAddTransactionOpen) 0.54f else 0.0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "scrimAlpha",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    rootLayoutSize = coordinates.size
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(backgroundBlur),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "main_pager",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable("main_pager") {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            when (page) {
                                0 -> HomeScreen(
                                    dashboard = dashboard,
                                    smsPermissionGranted = smsPermissionGranted,
                                    listState = homeListState,
                                    chartReloadKey = chartReloadKey,
                                    onRequestPermissions = {
                                        permissionLauncher.launch(context.smsPermissionArray())
                                    },
                                    onImportRecentSms = { viewModel.importRecentSms(context.contentResolver) },
                                    onSetBudgetClick = { showBudgetDialog = true },
                                    onAddTransactionClick = {
                                        transactionAnchorBounds = null
                                        lastOpenedWasEditing = false
                                        activeEditingTransaction = null
                                        editingTransaction = null
                                        transactionDialogKey += 1
                                        showAddTransactionDialog = true
                                    },
                                    onBudgetClick = {
                                        navController.navigate(AppDestination.BudgetHistory.route)
                                    },
                                    onSelectMonth = viewModel::setSelectedYearMonth,
                                    onRefreshAiInsights = { viewModel.refreshAiSpendingInsights() },
                                    onOpenAssistant = { showAiChatSheet = true },
                                    onAccountsClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(2)
                                        }
                                    },
                                    onAccountClick = { viewingBalanceProofAccount = it },
                                )
                                1 -> TransactionsScreen(
                                    filter = filter,
                                    transactions = transactions,
                                    cardAccounts = accounts.filter { it.kind == AccountKind.CARD },
                                    searchQuery = searchQuery,
                                    listState = transactionsListState,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onFilterSelected = viewModel::setFilter,
                                    onAddTransactionClick = {
                                        transactionAnchorBounds = null
                                        lastOpenedWasEditing = false
                                        activeEditingTransaction = null
                                        editingTransaction = null
                                        transactionDialogKey += 1
                                        showAddTransactionDialog = true
                                    },
                                    onApproveReview = viewModel::approveReview,
                                    onAnalyzeWithAi = viewModel::enrichTransactionWithAi,
                                    isAiAnalyzing = isAiAnalyzing,
                                    onEditTransaction = { transaction, bounds ->
                                        transactionAnchorBounds = bounds
                                        lastOpenedWasEditing = true
                                        activeEditingTransaction = transaction
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
                                    onTransferToCashWallet = viewModel::transferToCashWallet,
                                    onDismissAtmPrompt = viewModel::dismissAtmPrompt,
                                    untransferredAtmTransactions = untransferredAtmTransactions,
                                )
                                2 -> MoreScreen(
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
                                    onAccountClick = { viewingBalanceProofAccount = it },
                                )
                                3 -> SettingsScreen(
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
                                    biometricTimeout = biometricTimeout,
                                    onToggleBiometricEnabled = viewModel::setBiometricEnabled,
                                    onSelectBiometricTimeout = viewModel::setBiometricTimeout,
                                    isNotificationListenerEnabled = isNotificationListenerEnabled,
                                    isNotificationPermissionGranted = notificationPermissionGranted,
                                    isGmailMonitoringEnabled = isGmailMonitoringEnabled,
                                    isPaymentAppsMonitoringEnabled = isPaymentAppsMonitoringEnabled,
                                    isBankAppsMonitoringEnabled = isBankAppsMonitoringEnabled,
                                    notificationLastCapturedTimestamp = notificationLastCapturedTimestamp,
                                    notificationLastCapturedPackage = notificationLastCapturedPackage,
                                    notificationCapturedCount = notificationCapturedCount,
                                    onOpenNotificationSettings = {
                                        try {
                                            val intent = viewModel.buildNotificationSettingsIntent(context)
                                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        } catch (e: Exception) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            }
                                        }
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
                                    themeMode = themeMode,
                                    themeAccent = themeAccent,
                                    onSelectThemeMode = viewModel::setThemeMode,
                                    onSelectThemeAccent = viewModel::setThemeAccent,
                                    onRequestExportBackup = { showExportPassphraseDialog = true },
                                    onRequestRestoreBackup = { openBackupLauncher.launch(arrayOf("*/*")) },
                                )
                            }
                        }
                    }
                    composable(AppDestination.BudgetHistory.route) {
                        BudgetHistoryScreen(
                            history = budgetHistory,
                            initiallyExpandedKey = budgetHistory.firstOrNull()?.yearMonthKey,
                            onBack = {
                                navController.popBackStack()
                            },
                            onEditTransaction = { transaction, bounds ->
                                transactionAnchorBounds = bounds
                                lastOpenedWasEditing = true
                                activeEditingTransaction = transaction
                                showAddTransactionDialog = false
                                editingTransaction = transaction
                                transactionDialogKey += 1
                            },
                        )
                    }
                }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )

        val isMainPager = !isBudgetHistory
        val showFab = isMainPager && (pagerState.currentPage == 0 || pagerState.currentPage == 1)

        AnimatedVisibility(
            visible = showFab,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 88.dp),
        ) {
            val isHome = pagerState.currentPage == 0
            FloatingActionButton(
                onClick = {
                    haptics.click()
                    if (isHome) {
                        showAiChatSheet = true
                    } else {
                        transactionAnchorBounds = null
                        lastOpenedWasEditing = false
                        activeEditingTransaction = null
                        editingTransaction = null
                        transactionDialogKey += 1
                        showAddTransactionDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isHome,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            scaleIn(initialScale = 0.82f, animationSpec = tween(220, delayMillis = 90)))
                            .togetherWith(fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.82f, animationSpec = tween(90)))
                    },
                    label = "fabIconAnim",
                ) { home ->
                    if (home) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Ask Spending Assistant",
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add Transaction",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isMainPager,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TrackerBottomBar(
                currentRoute = currentRoute,
                onNavigate = { destination ->
                    val targetIndex = bottomDestinations.indexOf(destination)
                    if (targetIndex >= 0) {
                        if (targetIndex == pagerState.currentPage) {
                            haptics.click()
                            if (destination == AppDestination.Home) {
                                chartReloadKey += 1
                                coroutineScope.launch {
                                    if (homeListState.firstVisibleItemIndex > 2) {
                                        homeListState.scrollToItem(2)
                                    }
                                    homeListState.animateScrollToItem(0)
                                }
                            } else if (destination == AppDestination.Transactions) {
                                coroutineScope.launch {
                                    if (transactionsListState.firstVisibleItemIndex > 3) {
                                        transactionsListState.scrollToItem(3)
                                    }
                                    transactionsListState.animateScrollToItem(0)
                                }
                            }
                        } else {
                            haptics.selection()
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        }
                    }
                },
            )
        }
        }

        if (scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.click()
                            showAddTransactionDialog = false
                            editingTransaction = null
                        },
                    ),
            )
        }

        val isSpatialEdit = lastOpenedWasEditing || editingTransaction != null
        val anchorBounds = transactionAnchorBounds
        val hasSpatialAnchor = isSpatialEdit && anchorBounds != null && rootLayoutSize.width > 0 && rootLayoutSize.height > 0

        val spatialTransformOrigin = if (hasSpatialAnchor) {
            TransformOrigin(
                pivotFractionX = (anchorBounds!!.center.x / rootLayoutSize.width).coerceIn(0.08f, 0.92f),
                pivotFractionY = (anchorBounds.center.y / rootLayoutSize.height).coerceIn(0.08f, 0.92f),
            )
        } else if (isSpatialEdit) {
            TransformOrigin(0.5f, 0.45f)
        } else {
            TransformOrigin(0.5f, 0.88f)
        }

        AnimatedVisibility(
            visible = isAddTransactionOpen,
            enter = if (isSpatialEdit) {
                fadeIn(animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.58f,
                    transformOrigin = spatialTransformOrigin,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                ) +
                slideInVertically(
                    initialOffsetY = { _ ->
                        if (hasSpatialAnchor) {
                            val screenCenterY = rootLayoutSize.height / 2f
                            ((anchorBounds!!.center.y - screenCenterY) * 0.28f).toInt()
                        } else {
                            0
                        }
                    },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                )
            } else {
                fadeIn(animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { fullHeight -> (fullHeight * 0.18f).toInt() },
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow),
                ) +
                scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(0.5f, 0.88f),
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow),
                )
            },
            exit = if (isSpatialEdit) {
                fadeOut(animationSpec = tween(durationMillis = 190, easing = FastOutLinearInEasing)) +
                scaleOut(
                    targetScale = 0.58f,
                    transformOrigin = spatialTransformOrigin,
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium),
                ) +
                slideOutVertically(
                    targetOffsetY = { _ ->
                        if (hasSpatialAnchor) {
                            val screenCenterY = rootLayoutSize.height / 2f
                            ((anchorBounds!!.center.y - screenCenterY) * 0.22f).toInt()
                        } else {
                            0
                        }
                    },
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium),
                )
            } else {
                fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)) +
                slideOutVertically(
                    targetOffsetY = { fullHeight -> (fullHeight * 0.18f).toInt() },
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium),
                ) +
                scaleOut(
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0.88f),
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.click()
                            showAddTransactionDialog = false
                            editingTransaction = null
                        },
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val transactionToEdit = editingTransaction ?: activeEditingTransaction.takeIf { isSpatialEdit }
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
        }
        }
    }

    BackHandler(enabled = isAddTransactionOpen) {
        showAddTransactionDialog = false
        editingTransaction = null
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
            currentValue = dashboard.budgetLimit,
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                viewModel.setMonthlyBudget(it, dashboard.selectedYearMonth)
                showBudgetDialog = false
            },
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



    viewingBalanceProofAccount?.let { account ->
        BalanceProofDialog(
            account = account,
            onDismiss = { viewingBalanceProofAccount = null },
            onEditBalance = {
                val targetAccount = viewingBalanceProofAccount
                viewingBalanceProofAccount = null
                if (targetAccount != null) {
                    editingAccount = targetAccount
                    accountDialogDraft = targetAccount.toDraft()
                    accountDialogKey += 1
                }
            },
            onTrueUpBalance = {
                val targetAccount = viewingBalanceProofAccount
                viewingBalanceProofAccount = null
                if (targetAccount != null) {
                    viewingTrueUpAccount = targetAccount
                }
            },
        )
    }

    viewingTrueUpAccount?.let { account ->
        TrueUpBalanceDialog(
            account = account,
            onDismiss = { viewingTrueUpAccount = null },
            onConfirm = { newBalance, reason ->
                viewModel.trueUpAccountBalance(account.id, newBalance, reason)
            },
        )
    }

    if (showExportPassphraseDialog) {
        ExportBackupPassphraseDialog(
            onDismiss = { showExportPassphraseDialog = false },
            onConfirm = { pass ->
                showExportPassphraseDialog = false
                pendingExportPassphrase = pass
                createBackupLauncher.launch("money_tracker_backup_${System.currentTimeMillis() / 1000L}.mtbackup")
            },
        )
    }

    pendingRestoreUri?.let { uri ->
        RestoreBackupPassphraseDialog(
            onDismiss = { pendingRestoreUri = null },
            onConfirm = { pass ->
                pendingRestoreUri = null
                viewModel.restoreBackup(context, uri, pass)
            },
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
