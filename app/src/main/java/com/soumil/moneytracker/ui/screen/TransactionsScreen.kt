package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionFilter
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.ui.asMonthYear
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.TransactionItem

@Composable
fun TransactionsScreen(
    filter: TransactionFilter,
    transactions: List<TransactionRecord>,
    cardAccounts: List<AccountEntity>,
    onFilterSelected: (TransactionFilter) -> Unit,
    onAddTransactionClick: () -> Unit,
    onApproveReview: (Long) -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCardAccountId by rememberSaveable(filter) { mutableStateOf<Long?>(null) }
    val visibleTransactions = if (filter == TransactionFilter.CARD && selectedCardAccountId != null) {
        transactions.filter { it.accountId == selectedCardAccountId }
    } else {
        transactions
    }
    val groupedTransactions = visibleTransactions.groupBy { it.occurredAtMillis.asMonthYear() }
    val selectableCards = cardAccounts.filter { it.kind == AccountKind.CARD }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(text = "Transactions", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Search-ready ledger for posted and review items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TransactionFilter.entries) { candidate ->
                    FilterChip(
                        selected = filter == candidate,
                        onClick = { onFilterSelected(candidate) },
                        label = { Text(candidate.label) },
                    )
                }
            }
        }

        if (filter == TransactionFilter.CARD && selectableCards.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedCardAccountId == null,
                            onClick = { selectedCardAccountId = null },
                            label = { Text("All cards") },
                        )
                    }
                    items(selectableCards, key = { it.id }) { account ->
                        FilterChip(
                            selected = selectedCardAccountId == account.id,
                            onClick = { selectedCardAccountId = account.id },
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

        item {
            OutlinedButton(onClick = onAddTransactionClick) {
                Text("Add manual transaction")
            }
        }

        if (visibleTransactions.isEmpty()) {
            item {
                SectionCard(
                    title = "No transactions yet",
                    subtitle = "SMS imports and manual entries will appear here.",
                ) {
                    Button(onClick = onAddTransactionClick) {
                        Text("Add transaction")
                    }
                }
            }
        } else {
            groupedTransactions.forEach { (monthYear, monthTransactions) ->
                item(key = "month-$monthYear") {
                    MonthYearDivider(label = monthYear)
                }
                items(monthTransactions, key = { it.id }) { transaction ->
                    SectionCard(
                        title = transaction.merchant,
                        subtitle = listOfNotNull(
                            transaction.category.label,
                            transaction.accountName,
                            transaction.sourceSender,
                        ).joinToString(" | "),
                    ) {
                        TransactionItem(transaction = transaction)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (transaction.status == TransactionStatus.REVIEW) {
                                Button(
                                    onClick = { onApproveReview(transaction.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Approve")
                                }
                            }
                            OutlinedButton(
                                onClick = { onEditTransaction(transaction) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Edit")
                            }
                            OutlinedButton(
                                onClick = { onDeleteTransaction(transaction) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Delete")
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
    modifier: Modifier = Modifier,
) {
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
    }
}
