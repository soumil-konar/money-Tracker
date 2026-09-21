package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
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
import com.soumil.moneytracker.ui.components.MotionReveal
import com.soumil.moneytracker.ui.components.PermissionBanner
import com.soumil.moneytracker.ui.components.SectionCard
import com.soumil.moneytracker.ui.components.SpendingPieChart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import com.soumil.moneytracker.ui.components.TransactionItem

@Composable
fun HomeScreen(
    dashboard: DashboardState,
    smsPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onImportRecentSms: () -> Unit,
    onSetBudgetClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onBudgetClick: () -> Unit,
    onRefreshAiInsights: () -> Unit = {},
    onOpenAssistant: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MotionReveal(index = 0) {
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
        }

        item {
            MotionReveal(index = 1) {
                PermissionBanner(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = onRequestPermissions,
                    onImportRecentSms = onImportRecentSms,
                )
            }
        }

        item {
            MotionReveal(index = 2) {
                SectionCard(
                    title = "This month",
                    subtitle = "Budget-led dashboard with auto-tracked SMS transactions",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dashboard.monthSpent.asCurrency(),
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(
                                text = "Spent this month",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onSetBudgetClick) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = if (dashboard.budgetLimit == null) {
                                    "Set budget"
                                } else {
                                    "Update budget"
                                },
                            )
                        }
                    }
                    Box(modifier = Modifier.clickable(onClick = onBudgetClick)) {
                        BudgetGauge(
                            spent = dashboard.monthSpent,
                            budget = dashboard.budgetLimit,
                        )
                    }
                }
            }
        }

        item {
            MotionReveal(index = 3) {
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
        }

        item {
            MotionReveal(index = 4) {
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
        }

        item {
            MotionReveal(index = 5) {
                SectionCard(
                    title = "AI Financial Insights",
                    subtitle = "Powered by Google AI Studio Gemini models",
                ) {
                    if (dashboard.spendingInsights.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Get personalized observations on your budget pacing, top spend drivers, and tailored money-saving advice.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onRefreshAiInsights,
                                enabled = !dashboard.isAiLoading,
                            ) {
                                if (dashboard.isAiLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Analyzing with Gemini...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Analyze with AI")
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            dashboard.spendingInsights.forEach { tip ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(top = 2.dp),
                                    )
                                    Text(
                                        text = tip,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onRefreshAiInsights,
                                    enabled = !dashboard.isAiLoading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (dashboard.isAiLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text("Refreshing...")
                                    } else {
                                        Text("Refresh")
                                    }
                                }
                                if (onOpenAssistant != null) {
                                    Button(
                                        onClick = onOpenAssistant,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("Ask AI")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MotionReveal(index = 6) {
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
        }

        item {
            MotionReveal(index = 6) {
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
        }

        item {
            MotionReveal(index = 7) {
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
                            dashboard.recentTransactions.forEachIndexed { index, transaction ->
                                MotionReveal(index = index.coerceAtMost(4)) {
                                    TransactionItem(transaction = transaction)
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
