package com.moneytracker.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReceiptLong
import com.moneytracker.app.data.db.canTransferToCash
import com.moneytracker.app.data.db.ScheduledTransactionRecord
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionFilter
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.ui.asCurrency
import com.moneytracker.app.ui.asMonthYear
import com.moneytracker.app.ui.asShortDate
import com.moneytracker.app.ui.components.MotionReveal
import com.moneytracker.app.ui.components.SectionCard
import com.moneytracker.app.ui.components.TransactionItem
import com.moneytracker.app.ui.haptics.LocalAppHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    filter: TransactionFilter,
    transactions: List<TransactionRecord>,
    cardAccounts: List<AccountEntity>,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onFilterSelected: (TransactionFilter) -> Unit,
    onAddTransactionClick: (Rect?) -> Unit,
    onApproveReview: (Long) -> Unit,
    onAnalyzeWithAi: ((Long) -> Unit)? = null,
    isAiAnalyzing: Boolean = false,
    onEditTransaction: (TransactionRecord, Rect?) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    onToggleBudgetInclusion: (TransactionRecord) -> Unit,
    onTransferToCashWallet: ((Long) -> Unit)? = null,
    onDismissAtmPrompt: ((Long) -> Unit)? = null,
    untransferredAtmTransactions: List<TransactionRecord> = emptyList(),
    pendingReminders: List<ScheduledTransactionRecord> = emptyList(),
    onMarkBillPaid: (Long) -> Unit = {},
    onConfirmBillPayment: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteReminder: (Long) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    var selectedCardAccountId by rememberSaveable(filter) { mutableStateOf<Long?>(null) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNotificationSection by rememberSaveable { mutableStateOf(false) }
    var selectedReminderForDetails by remember { mutableStateOf<ScheduledTransactionRecord?>(null) }

    if (selectedReminderForDetails != null) {
        BillReminderDetailsDialog(
            reminder = selectedReminderForDetails!!,
            onDismiss = { selectedReminderForDetails = null },
            onBillPaid = {
                onMarkBillPaid(it)
                selectedReminderForDetails = null
            },
            onConfirmPayment = { id, confirmed ->
                onConfirmBillPayment(id, confirmed)
                selectedReminderForDetails = null
            },
            onDelete = {
                onDeleteReminder(it)
                selectedReminderForDetails = null
            },
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    haptics.click()
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    showDatePicker = false
                }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val visibleTransactions = remember(transactions, filter, selectedCardAccountId, selectedDateMillis) {
        val base = if (filter == TransactionFilter.CARD && selectedCardAccountId != null) {
            transactions.filter { it.accountId == selectedCardAccountId }
        } else {
            transactions
        }
        if (selectedDateMillis != null) {
            val filterDate = java.time.Instant.ofEpochMilli(selectedDateMillis!!)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            base.filter {
                val txDate = java.time.Instant.ofEpochMilli(it.occurredAtMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                txDate == filterDate
            }
        } else {
            base
        }
    }
    val groupedTransactions = remember(visibleTransactions) {
        visibleTransactions.groupBy { it.occurredAtMillis.asMonthYear() }
    }
    val selectableCards = remember(cardAccounts) {
        cardAccounts.filter { it.kind == AccountKind.CARD }
    }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = statusBarInset + 16.dp,
            bottom = navBarInset + 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "transactions_header") {
            MotionReveal(index = 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search-ready ledger for posted and review items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Box {
                        IconButton(
                            onClick = {
                                haptics.click()
                                showNotificationSection = !showNotificationSection
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (showNotificationSection) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                        ) {
                            Icon(
                                imageVector = if (showNotificationSection) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = "Reminders & Notifications",
                                tint = if (showNotificationSection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (pendingReminders.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${pendingReminders.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNotificationSection) {
            item(key = "transactions_notification_section") {
                Card(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Pending Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = { showNotificationSection = false },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        if (pendingReminders.isEmpty()) {
                            Text(
                                text = "No pending bill reminders or notifications right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            pendingReminders.forEach { reminder ->
                                BillReminderCard(
                                    reminder = reminder,
                                    onClick = { selectedReminderForDetails = reminder },
                                    onBillPaid = { onMarkBillPaid(reminder.id) },
                                    onConfirmPayment = { confirmed -> onConfirmBillPayment(reminder.id, confirmed) },
                                    onDelete = { onDeleteReminder(reminder.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "transactions_search_field") {
            Box(modifier = Modifier.animateItem().fillMaxWidth()) {
                MotionReveal(index = 1) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search merchant, note, amount...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    haptics.tick()
                                    onSearchQueryChange("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }

        item(key = "transactions_filter_chips") {
            Box(modifier = Modifier.animateItem().fillMaxWidth()) {
                MotionReveal(index = 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "filter_calendar") {
                            FilterChip(
                                selected = selectedDateMillis != null,
                                onClick = {
                                    haptics.click()
                                    if (selectedDateMillis != null) {
                                        selectedDateMillis = null
                                    } else {
                                        showDatePicker = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = "Date filter",
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                trailingIcon = if (selectedDateMillis != null) {
                                    {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear date filter",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    haptics.click()
                                                    selectedDateMillis = null
                                                },
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = selectedDateMillis?.asShortDate()?.let { "Date: $it" } ?: "Calendar",
                                    )
                                },
                            )
                        }
                        items(TransactionFilter.entries, key = { it.name }) { candidate ->
                            FilterChip(
                                selected = filter == candidate,
                                onClick = {
                                    haptics.tick()
                                    onFilterSelected(candidate)
                                },
                                label = { Text(candidate.label) },
                            )
                        }
                    }
                }
            }
        }

        if (filter == TransactionFilter.CARD && selectableCards.isNotEmpty()) {
            item(key = "transactions_card_filter_chips") {
                Box(modifier = Modifier.animateItem().fillMaxWidth()) {
                    MotionReveal(index = 2) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "filter_card_all") {
                                FilterChip(
                                    selected = selectedCardAccountId == null,
                                    onClick = {
                                        haptics.tick()
                                        selectedCardAccountId = null
                                    },
                                    label = { Text("All cards") },
                                )
                            }
                            items(selectableCards, key = { it.id }) { account ->
                                FilterChip(
                                    selected = selectedCardAccountId == account.id,
                                    onClick = {
                                        haptics.tick()
                                        selectedCardAccountId = account.id
                                    },
                                    label = {
                                        Text(
                                            account.lastFourDigits?.let { "${account.name} ending $it" } ?: account.name,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (untransferredAtmTransactions.isNotEmpty()) {
            val topAtm = untransferredAtmTransactions.first()
            item(key = "atm-prompt-${topAtm.id}") {
                MotionReveal(index = 2) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ATM Cash Withdrawal • ${topAtm.amount.asCurrency()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Text(
                                        text = "Transfer to Cash in Hand wallet to keep budget & net worth accurate.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        haptics.click()
                                        onTransferToCashWallet?.invoke(topAtm.id)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text("Transfer to Cash Wallet", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = {
                                        haptics.click()
                                        onDismissAtmPrompt?.invoke(topAtm.id)
                                    },
                                ) {
                                    Text("Keep as Expense", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "transactions_add_manual_button") {
            Box(modifier = Modifier.animateItem()) {
                MotionReveal(index = 3) {
                    var addManualCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    OutlinedButton(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            addManualCoordinates = coords
                        },
                        onClick = {
                            haptics.click()
                            val bounds = addManualCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
                            onAddTransactionClick(bounds)
                        },
                    ) {
                        Text("Add manual transaction")
                    }
                }
            }
        }

        if (visibleTransactions.isEmpty()) {
            item(key = "transactions_empty_state") {
                Box(modifier = Modifier.animateItem()) {
                    MotionReveal(index = 4) {
                        SectionCard(
                            title = "No transactions yet",
                            subtitle = "SMS imports and manual entries will appear here.",
                        ) {
                            var addEmptyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                            Button(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    addEmptyCoordinates = coords
                                },
                                onClick = {
                                    haptics.click()
                                    val bounds = addEmptyCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
                                    onAddTransactionClick(bounds)
                                },
                            ) {
                                Text("Add transaction")
                            }
                        }
                    }
                }
            }
        } else {
            groupedTransactions.forEach { (monthYear, monthTransactions) ->
                item(key = "month-$monthYear") {
                    MotionReveal(index = 4) {
                        MonthYearDivider(
                            label = monthYear,
                            total = monthTransactions.signedTotal(),
                        )
                    }
                }
                items(monthTransactions, key = { it.id }) { transaction ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            TransactionItem(
                                transaction = transaction,
                                onTransferToCash = if (transaction.canTransferToCash && onTransferToCashWallet != null) {
                                    {
                                        haptics.click()
                                        onTransferToCashWallet(transaction.id)
                                    }
                                } else null,
                            )
                                Spacer(modifier = Modifier.height(10.dp))
                            if (transaction.status == TransactionStatus.REVIEW) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (onAnalyzeWithAi != null && !transaction.smsBody.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                haptics.click()
                                                onAnalyzeWithAi(transaction.id)
                                            },
                                            enabled = !isAiAnalyzing,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("AI Fix")
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            haptics.success()
                                            onApproveReview(transaction.id)
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Approve")
                                    }
                                    TransactionCardActions(
                                        transaction = transaction,
                                        onEdit = { bounds -> onEditTransaction(transaction, bounds) },
                                        onDelete = { onDeleteTransaction(transaction) },
                                        onToggleBudgetInclusion = { onToggleBudgetInclusion(transaction) },
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TransactionCardActions(
                                        transaction = transaction,
                                        onEdit = { bounds -> onEditTransaction(transaction, bounds) },
                                        onDelete = { onDeleteTransaction(transaction) },
                                        onToggleBudgetInclusion = { onToggleBudgetInclusion(transaction) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthYearDivider(
    label: String,
    total: Double,
    modifier: Modifier = Modifier,
) {
    val totalColor = when {
        total > 0 -> MaterialTheme.colorScheme.tertiary
        total < 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val totalLabel = when {
        total > 0 -> "+${total.asCurrency()}"
        total < 0 -> "-${(-total).asCurrency()}"
        else -> 0.0.asCurrency()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
        Text(
            text = totalLabel,
            style = MaterialTheme.typography.titleMedium,
            color = totalColor,
        )
    }
}

private fun List<TransactionRecord>.signedTotal(): Double = sumOf { record ->
    when (record.direction) {
        TransactionDirection.CREDIT -> record.amount
        TransactionDirection.DEBIT -> -record.amount
    }
}

@Composable
private fun TransactionCardActions(
    transaction: TransactionRecord,
    onEdit: (Rect?) -> Unit,
    onDelete: () -> Unit,
    onToggleBudgetInclusion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    val countsTowardBudget = transaction.countsTowardBudget
    var editButtonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransactionActionButton(
            icon = if (countsTowardBudget) Icons.Outlined.Savings else Icons.Outlined.MoneyOff,
            contentDescription = if (countsTowardBudget) {
                "Exclude from budget"
            } else {
                "Add to budget"
            },
            tint = if (countsTowardBudget) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            containerColor = if (countsTowardBudget) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            onClick = {
                haptics.toggle()
                onToggleBudgetInclusion()
            },
        )
        TransactionActionButton(
            icon = Icons.Outlined.Edit,
            contentDescription = "Edit transaction",
            tint = MaterialTheme.colorScheme.onBackground,
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
            modifier = Modifier.onGloballyPositioned { coords ->
                editButtonCoordinates = coords
            },
            onClick = {
                haptics.click()
                val bounds = editButtonCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
                onEdit(bounds)
            },
        )
        TransactionActionButton(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete transaction",
            tint = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            onClick = {
                haptics.warning()
                onDelete()
            },
        )
    }
}

@Composable
private fun TransactionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

@Composable
private fun BillReminderCard(
    reminder: ScheduledTransactionRecord,
    onClick: () -> Unit,
    onBillPaid: () -> Unit,
    onConfirmPayment: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                haptics.click()
                onClick()
            }),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = reminder.merchant,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Due ${reminder.scheduledForMillis.asShortDate()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = "₹${reminder.amount.asCurrency()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (reminder.requiresConfirmation) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Detected matching payment of ₹${reminder.amount.toInt()}. Did you pay this bill?",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    haptics.click()
                                    onConfirmPayment(true)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text("Yes, Bill Paid", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    haptics.click()
                                    onConfirmPayment(false)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text("No, Keep Unpaid", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tap for full note details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            haptics.click()
                            onBillPaid()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Bill Paid", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = {
                            haptics.warning()
                            onDelete()
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillReminderDetailsDialog(
    reminder: ScheduledTransactionRecord,
    onDismiss: () -> Unit,
    onBillPaid: (Long) -> Unit,
    onConfirmPayment: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = reminder.merchant,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Due Date",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = reminder.scheduledForMillis.asShortDate(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Amount Due",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "₹${reminder.amount.asCurrency()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (reminder.requiresConfirmation) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "A matching payment of ₹${reminder.amount.toInt()} was found. Mark as paid?",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        haptics.click()
                                        onConfirmPayment(reminder.id, true)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text("Yes, Paid", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.click()
                                        onConfirmPayment(reminder.id, false)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text("No", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Column {
                    Text(
                        text = "Full Note / Message Content",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                    ) {
                        Text(
                            text = reminder.smsBody ?: "No full message text available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.click()
                    onBillPaid(reminder.id)
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Bill Paid")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        haptics.warning()
                        onDelete(reminder.id)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

