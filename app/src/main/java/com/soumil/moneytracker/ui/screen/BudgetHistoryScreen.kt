package com.soumil.moneytracker.ui.screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.MonthBudgetSummary
import com.soumil.moneytracker.ui.asCurrency
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.TransactionItem
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics

@Composable
fun BudgetHistoryScreen(
    history: List<MonthBudgetSummary>,
    initiallyExpandedKey: String?,
    onBack: () -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current
    var expandedKey by rememberSaveable { mutableStateOf(initiallyExpandedKey) }

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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MotionReveal(index = 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        haptics.click()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Budgets",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "How each month tracked against its cap",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                MotionReveal(index = 1) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "No history yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Set a monthly budget and add or import transactions to start building history.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            history.forEachIndexed { index, summary ->
                item(key = "month-${summary.yearMonthKey}") {
                    MotionReveal(index = (index + 1).coerceAtMost(8)) {
                        MonthBudgetCard(
                            summary = summary,
                            expanded = expandedKey == summary.yearMonthKey,
                            onToggle = {
                                haptics.tick()
                                expandedKey = if (expandedKey == summary.yearMonthKey) {
                                    null
                                } else {
                                    summary.yearMonthKey
                                }
                            },
                            onEditTransaction = onEditTransaction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthBudgetCard(
    summary: MonthBudgetSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
) {
    val budget = summary.budgetLimit
    val spent = summary.spent
    val ratio = when {
        budget == null || budget <= 0.0 -> 0f
        else -> (spent / budget).coerceIn(0.0, 1.5).toFloat()
    }
    val overBudget = budget != null && spent > budget
    val accent = when {
        budget == null -> MaterialTheme.colorScheme.onSurfaceVariant
        overBudget -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = summary.monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (budget == null) {
                            "No budget set"
                        } else if (overBudget) {
                            "Over by ${(spent - budget).asCurrency()}"
                        } else {
                            "${(budget - spent).coerceAtLeast(0.0).asCurrency()} left"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = spent.asCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = budget?.asCurrency()?.let { "of $it" } ?: "${summary.transactions.size} txns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (budget != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { ratio.coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (summary.transactions.isEmpty()) {
                        Text(
                            text = "No transactions recorded for this month.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        BudgetTransactionSection(
                            label = "Counted toward budget",
                            transactions = summary.transactions.filter { it.countsTowardBudget },
                            onEditTransaction = onEditTransaction,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        BudgetTransactionSection(
                            label = "Excluded from budget",
                            transactions = summary.transactions.filter { !it.countsTowardBudget },
                            onEditTransaction = onEditTransaction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetTransactionSection(
    label: String,
    transactions: List<TransactionRecord>,
    onEditTransaction: (TransactionRecord) -> Unit,
) {
    if (transactions.isEmpty()) return
    val haptics = LocalAppHaptics.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            transactions.forEach { transaction ->
                TransactionItem(
                    transaction = transaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            onEditTransaction(transaction)
                        },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.Transparent),
                )
            }
        }
    }
}
