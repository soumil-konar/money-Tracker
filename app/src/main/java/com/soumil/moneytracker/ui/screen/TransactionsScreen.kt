package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.TransactionFilter
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.TransactionItem

@Composable
fun TransactionsScreen(
    filter: TransactionFilter,
    transactions: List<TransactionRecord>,
    onFilterSelected: (TransactionFilter) -> Unit,
    onAddTransactionClick: () -> Unit,
    onApproveReview: (Long) -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
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

        item {
            OutlinedButton(onClick = onAddTransactionClick) {
                Text("Add manual transaction")
            }
        }

        if (transactions.isEmpty()) {
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
            items(transactions, key = { it.id }) { transaction ->
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
