package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.ScheduledTransactionRecord
import com.soumil.moneytracker.data.db.SubscriptionRecord
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.ui.components.AccountItem
import com.soumil.moneytracker.ui.components.ScheduledTransactionItem
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.SubscriptionItem

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
    onAcceptSuggestion: (Long) -> Unit,
    onDismissSuggestion: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountEntries = accounts.filter { it.kind != AccountKind.CARD }
    val cardEntries = accounts.filter { it.kind == AccountKind.CARD }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(text = "More", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Subscriptions, scheduled debits, accounts, exports, and future modules",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(
                title = "Subscriptions",
                subtitle = "Manual entries plus recurring-payment suggestions",
            ) {
                Button(onClick = onAddSubscriptionClick) {
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
                        activeSubscriptions.forEach { subscription ->
                            SubscriptionItem(subscription = subscription)
                        }
                    }
                }
            }
        }

        item {
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
                        scheduledTransactions.forEach { scheduledTransaction ->
                            ScheduledTransactionItem(scheduledTransaction = scheduledTransaction)
                        }
                    }
                }
            }
        }

        if (suggestedSubscriptions.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Recurring suggestions",
                    subtitle = "Detected from repeated debit patterns",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        suggestedSubscriptions.forEach { subscription ->
                            SubscriptionItem(
                                subscription = subscription,
                                trailing = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onAcceptSuggestion(subscription.id) }) {
                                            Text("Keep")
                                        }
                                        OutlinedButton(onClick = { onDismissSuggestion(subscription.id) }) {
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

        item {
            SectionCard(
                title = "Accounts",
                subtitle = "Bank and balance sources used to route imported SMS",
            ) {
                OutlinedButton(onClick = onAddBankClick) {
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
                        accountEntries.forEach { account ->
                            AccountItem(
                                account = account,
                                onEdit = { onEditAccount(account) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Cards",
                subtitle = "Credit and debit cards matched by their last 4 digits",
            ) {
                OutlinedButton(onClick = onAddCardClick) {
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
                        cardEntries.forEach { account ->
                            AccountItem(
                                account = account,
                                onEdit = { onEditAccount(account) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Export",
                subtitle = "CSV export is reserved for the next iteration.",
            ) {
                Text(
                    text = "The data model is already local-first, so export can be added without changing the ledger core.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
