package com.soumil.moneytracker.ui.screen

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionFilter
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.ui.asCurrency
import com.soumil.moneytracker.ui.asMonthYear
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.TransactionItem
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@Composable
fun TransactionsScreen(
    filter: TransactionFilter,
    transactions: List<TransactionRecord>,
    cardAccounts: List<AccountEntity>,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onFilterSelected: (TransactionFilter) -> Unit,
    onAddTransactionClick: () -> Unit,
    onApproveReview: (Long) -> Unit,
    onAnalyzeWithAi: ((Long) -> Unit)? = null,
    isAiAnalyzing: Boolean = false,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    onToggleBudgetInclusion: (TransactionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    var selectedCardAccountId by rememberSaveable(filter) { mutableStateOf<Long?>(null) }
    val visibleTransactions = remember(transactions, filter, selectedCardAccountId) {
        if (filter == TransactionFilter.CARD && selectedCardAccountId != null) {
            transactions.filter { it.accountId == selectedCardAccountId }
        } else {
            transactions
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
            }
        }

        item {
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

        item {
            MotionReveal(index = 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TransactionFilter.entries) { candidate ->
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

        if (filter == TransactionFilter.CARD && selectableCards.isNotEmpty()) {
            item {
                MotionReveal(index = 2) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
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

        item {
            MotionReveal(index = 3) {
                OutlinedButton(onClick = {
                    haptics.click()
                    onAddTransactionClick()
                }) {
                    Text("Add manual transaction")
                }
            }
        }

        if (visibleTransactions.isEmpty()) {
            item {
                MotionReveal(index = 4) {
                    SectionCard(
                        title = "No transactions yet",
                        subtitle = "SMS imports and manual entries will appear here.",
                    ) {
                        Button(onClick = {
                            haptics.click()
                            onAddTransactionClick()
                        }) {
                            Text("Add transaction")
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
                itemsIndexed(monthTransactions, key = { _, transaction -> transaction.id }) { index, transaction ->
                    MotionReveal(index = (index + 5).coerceAtMost(8)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                TransactionItem(transaction = transaction)
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
                                        onEdit = { onEditTransaction(transaction) },
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
                                        onEdit = { onEditTransaction(transaction) },
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleBudgetInclusion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    val countsTowardBudget = transaction.countsTowardBudget
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
            onClick = {
                haptics.click()
                onEdit()
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
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
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
