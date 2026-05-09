package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.model.CategorySlice
import com.soumil.moneytracker.data.model.DashboardState
import com.soumil.moneytracker.ui.asCurrency
import com.soumil.moneytracker.ui.components.BudgetGauge
import com.soumil.moneytracker.ui.components.CashflowTrendChart
import com.soumil.moneytracker.ui.components.CategoryLegend
import com.soumil.moneytracker.ui.components.InsightBadge
import com.soumil.moneytracker.ui.components.PermissionBanner
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.SpendingPieChart
import com.soumil.moneytracker.ui.components.TransactionItem

@Composable
fun HomeScreen(
    dashboard: DashboardState,
    smsPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onImportRecentSms: () -> Unit,
    onSetBudgetClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Money at a glance",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }

        item {
            PermissionBanner(
                smsPermissionGranted = smsPermissionGranted,
                onRequestPermissions = onRequestPermissions,
                onImportRecentSms = onImportRecentSms,
            )
        }

        item {
            SectionCard(
                title = "This month",
                subtitle = "Budget-led dashboard with auto-tracked SMS transactions",
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
            ) {
                Text(
                    text = dashboard.monthSpent.asCurrency(),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Spent this month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BudgetGauge(
                    spent = dashboard.monthSpent,
                    budget = dashboard.budgetLimit,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(
                        onClick = onSetBudgetClick,
                        label = { Text(if (dashboard.budgetLimit == null) "Set budget" else "Update budget") },
                    )
                    AssistChip(
                        onClick = onAddTransactionClick,
                        label = { Text("Add transaction") },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InsightBadge(
                    title = "Income",
                    value = dashboard.monthIncome.asCurrency(),
                    modifier = Modifier.weight(1f),
                )
                InsightBadge(
                    title = "Review",
                    value = dashboard.reviewCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InsightBadge(
                    title = "Tracked balance",
                    value = dashboard.trackedBalance.asCurrency(),
                    modifier = Modifier.weight(1f),
                )
                InsightBadge(
                    title = "Subscriptions",
                    value = dashboard.activeSubscriptionsCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(
                title = "Spend mix",
                subtitle = "Category share this month",
            ) {
                if (dashboard.categoryBreakdown.isEmpty()) {
                    EmptyContent(
                        message = "No spending distribution yet. Import SMS or add transactions manually.",
                        onAction = onAddTransactionClick,
                        actionLabel = "Add transaction",
                    )
                } else {
                    SpendingPieChart(
                        slices = dashboard.categoryBreakdown.take(5),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryLegend(
                        slices = legendColors(dashboard.categoryBreakdown.take(5)),
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Cashflow trajectory",
                subtitle = "Last 7 days income vs expense",
            ) {
                if (dashboard.trendPoints.all { it.expense == 0.0 && it.income == 0.0 }) {
                    EmptyContent(
                        message = "The chart will start filling once transactions arrive.",
                        onAction = if (smsPermissionGranted) onImportRecentSms else onRequestPermissions,
                        actionLabel = if (smsPermissionGranted) "Import recent SMS" else "Enable SMS access",
                    )
                } else {
                    CashflowTrendChart(points = dashboard.trendPoints)
                }
            }
        }

        item {
            SectionCard(
                title = "Recent transactions",
                subtitle = "Latest posted and review items",
            ) {
                if (dashboard.recentTransactions.isEmpty()) {
                    EmptyContent(
                        message = "Nothing has been tracked yet.",
                        onAction = onAddTransactionClick,
                        actionLabel = "Add first transaction",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        dashboard.recentTransactions.forEach { transaction ->
                            TransactionItem(transaction = transaction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(
    message: String,
    onAction: () -> Unit,
    actionLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun legendColors(slices: List<CategorySlice>): List<Pair<Color, String>> {
    val palette = listOf(
        Color(0xFFF16621),
        Color(0xFFF2C661),
        Color(0xFF2FA56A),
        Color(0xFFD68846),
        Color(0xFF8C5832),
    )
    return slices.mapIndexed { index, slice ->
        palette[index % palette.size] to "${slice.category.label} ${slice.amount.asCurrency()}"
    }
}
