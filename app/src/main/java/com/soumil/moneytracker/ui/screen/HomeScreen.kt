package com.soumil.moneytracker.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.model.CategorySlice
import com.soumil.moneytracker.data.model.DashboardState
import com.soumil.moneytracker.ui.asCurrency
import com.soumil.moneytracker.ui.asMonthYear
import com.soumil.moneytracker.ui.components.BentoMetricCard
import com.soumil.moneytracker.ui.components.BudgetGauge
import com.soumil.moneytracker.ui.components.CashflowTrendChart
import com.soumil.moneytracker.ui.components.CategoryLegend
import com.soumil.moneytracker.ui.components.ExpressiveHeroCard
import com.soumil.moneytracker.ui.components.InsightBadge
import com.soumil.moneytracker.ui.components.MotionReveal
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
    onBudgetClick: () -> Unit,
    onRefreshAiInsights: () -> Unit = {},
    onOpenAssistant: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Welcome back",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = System.currentTimeMillis().asMonthYear(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Money at a glance",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
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
                ExpressiveHeroCard(
                    monthSpent = dashboard.monthSpent,
                    budget = dashboard.budgetLimit,
                    safeDailySpend = dashboard.safeDailySpend,
                    netSavings = dashboard.monthNetCashflow,
                    onSetBudgetClick = onSetBudgetClick,
                    onBudgetClick = onBudgetClick,
                )
            }
        }

        item {
            MotionReveal(index = 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BentoMetricCard(
                        title = "Monthly Inflow",
                        value = dashboard.monthIncome.asCurrency(),
                        icon = Icons.Outlined.ArrowDownward,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        subtitle = "Income",
                        modifier = Modifier.weight(1f),
                    )
                    BentoMetricCard(
                        title = "Net Cashflow",
                        value = (if (dashboard.monthNetCashflow >= 0) "+" else "") + dashboard.monthNetCashflow.asCurrency(),
                        icon = Icons.Outlined.Savings,
                        iconTint = if (dashboard.monthNetCashflow >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        subtitle = if (dashboard.monthNetCashflow >= 0) "Surplus" else "Deficit",
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
                    BentoMetricCard(
                        title = "Credit Card Spend",
                        value = dashboard.cardSpendThisMonth.asCurrency(),
                        icon = Icons.Outlined.CreditCard,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        subtitle = "Bank: ${dashboard.bankSpendThisMonth.asCurrency()}",
                        modifier = Modifier.weight(1f),
                    )
                    BentoMetricCard(
                        title = "Pending Review",
                        value = "${dashboard.reviewCount} items",
                        icon = Icons.Outlined.AssignmentTurnedIn,
                        iconTint = if (dashboard.reviewCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        subtitle = "Subs: ${dashboard.activeSubscriptionsCount}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            MotionReveal(index = 5) {
                SectionCard(
                    title = "AI Financial Insights",
                    subtitle = "Personalized budget pacing, drivers, and money-saving advice",
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
                                    Text("Analyzing finances...")
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
                                    FormattedInsightText(
                                        text = tip,
                                        modifier = Modifier.weight(1f),
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

@Composable
private fun FormattedInsightText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val tokenRegex = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|₹[0-9,]+)""")
            var currentIndex = 0
            tokenRegex.findAll(text).forEach { match ->
                if (match.range.first > currentIndex) {
                    append(text.substring(currentIndex, match.range.first))
                }
                val token = match.value
                when {
                    token.startsWith("**") && token.endsWith("**") -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(token.removeSurrounding("**"))
                        pop()
                    }
                    token.startsWith("*") && token.endsWith("*") -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(token.removeSurrounding("*"))
                        pop()
                    }
                    token.startsWith("₹") -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(token)
                        pop()
                    }
                    else -> append(token)
                }
                currentIndex = match.range.last + 1
            }
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}
