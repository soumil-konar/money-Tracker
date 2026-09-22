package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.ScheduledTransactionRecord
import com.soumil.moneytracker.data.db.SubscriptionRecord
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.ui.components.AccountItem
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.ScheduledTransactionItem
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.SubscriptionItem
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@Composable
fun MoreScreen(
    accounts: List<AccountEntity>,
    activeSubscriptions: List<SubscriptionRecord>,
    scheduledTransactions: List<ScheduledTransactionRecord>,
    suggestedSubscriptions: List<SubscriptionRecord>,
    onAddSubscriptionClick: () -> Unit,
    onAddBankClick: () -> Unit,
    onAddCardClick: () -> Unit,
    onEditAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit,
    onAcceptSuggestion: (Long) -> Unit,
    onDismissSuggestion: (Long) -> Unit,
    onExportCsv: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    val accountEntries = accounts.filter { it.kind != AccountKind.CARD }
    val cardEntries = accounts.filter { it.kind == AccountKind.CARD }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = statusBarInset + 16.dp,
            bottom = navBarInset + 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MotionReveal(index = 0) {
                Column {
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Subscriptions, scheduled debits, accounts, exports, and future modules",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MotionReveal(index = 1) {
                SectionCard(
                    title = "Subscriptions",
                    subtitle = "Manual entries plus recurring-payment suggestions",
                ) {
                    Button(onClick = {
                        haptics.click()
                        onAddSubscriptionClick()
                    }) {
                        Text("Add subscription")
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (activeSubscriptions.isEmpty()) {
                        Text(
                            text = "No active subscriptions yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            activeSubscriptions.forEachIndexed { index, subscription ->
                                MotionReveal(index = index.coerceAtMost(4)) {
                                    SubscriptionItem(subscription = subscription)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = 2) {
                SectionCard(
                    title = "Scheduled transactions",
                    subtitle = "Mandates and auto-debit notices detected from SMS",
                ) {
                    if (scheduledTransactions.isEmpty()) {
                        Text(
                            text = "Future mandate debits will appear here with the scheduled date and amount.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            scheduledTransactions.forEachIndexed { index, scheduledTransaction ->
                                MotionReveal(index = index.coerceAtMost(4)) {
                                    ScheduledTransactionItem(scheduledTransaction = scheduledTransaction)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (suggestedSubscriptions.isNotEmpty()) {
            item {
                MotionReveal(index = 3) {
                    SectionCard(
                        title = "Recurring suggestions",
                        subtitle = "Detected from repeated debit patterns",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            suggestedSubscriptions.forEachIndexed { index, subscription ->
                                MotionReveal(index = index.coerceAtMost(4)) {
                                    SubscriptionItem(
                                        subscription = subscription,
                                        trailing = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = {
                                                    haptics.success()
                                                    onAcceptSuggestion(subscription.id)
                                                }) {
                                                    Text("Keep")
                                                }
                                                OutlinedButton(onClick = {
                                                    haptics.warning()
                                                    onDismissSuggestion(subscription.id)
                                                }) {
                                                    Text("Dismiss")
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = if (suggestedSubscriptions.isNotEmpty()) 4 else 3) {
                SectionCard(
                    title = "Accounts",
                    subtitle = "Bank and balance sources used to route imported SMS",
                ) {
                    OutlinedButton(onClick = {
                        haptics.click()
                        onAddBankClick()
                    }) {
                        Text("Add bank")
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (accountEntries.isEmpty()) {
                        Text(
                            text = "Add your bank so incoming SMS can map to a named account instead of a placeholder.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            accountEntries.forEachIndexed { index, account ->
                                MotionReveal(index = index.coerceAtMost(4)) {
                                    AccountItem(
                                        account = account,
                                        onEdit = {
                                            haptics.click()
                                            onEditAccount(account)
                                        },
                                        onDelete = {
                                            haptics.warning()
                                            onDeleteAccount(account)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = if (suggestedSubscriptions.isNotEmpty()) 5 else 4) {
                SectionCard(
                    title = "Cards",
                    subtitle = "Credit and debit cards matched by their last 4 digits",
                ) {
                    OutlinedButton(onClick = {
                        haptics.click()
                        onAddCardClick()
                    }) {
                        Text("Add card")
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (cardEntries.isEmpty()) {
                        Text(
                            text = "Add your cards here so SMS imports can attach spends to the exact card.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            cardEntries.forEachIndexed { index, account ->
                                MotionReveal(index = index.coerceAtMost(4)) {
                                    AccountItem(
                                        account = account,
                                        onEdit = {
                                            haptics.click()
                                            onEditAccount(account)
                                        },
                                        onDelete = {
                                            haptics.warning()
                                            onDeleteAccount(account)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = if (suggestedSubscriptions.isNotEmpty()) 6 else 5) {
                SectionCard(
                    title = "Export",
                    subtitle = "Export transactions for backup, Excel, or spreadsheets",
                ) {
                    Text(
                        text = "Export your full local transaction ledger into CSV format. All records stay local on your device until shared.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        haptics.success()
                        onExportCsv()
                    }) {
                        Text("Export ledger to CSV")
                    }
                }
            }
        }
    }
}
