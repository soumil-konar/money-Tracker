package com.moneytracker.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.moneytracker.app.MainActivity
import com.moneytracker.app.R
import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.ui.asCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SafeSpendState(
    val monthName: String,
    val monthSpent: Double,
    val budgetLimit: Double,
    val safeDailySpend: Double,
    val percentUsed: Int,
    val progressFloat: Float,
    val daysRemaining: Int,
)

class SafeSpendGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = querySafeSpendState(context)
        provideContent {
            GlanceTheme {
                SafeSpendWidgetContent(context, state)
            }
        }
    }

    companion object {
        const val ACTION_ADD_TRANSACTION = "com.moneytracker.app.action.ADD_TRANSACTION"
        const val EXTRA_OPEN_ADD_TRANSACTION = "extra_open_add_transaction"

        fun updateAllWidgets(context: Context) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching {
                    SafeSpendGlanceWidget().updateAll(context)
                }
            }
        }

        suspend fun querySafeSpendState(context: Context): SafeSpendState = withContext(Dispatchers.IO) {
            val database = FinanceDatabase.create(context)
            val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
            val budget = database.budgetDao().getOverallBudget(currentMonthKey)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfMonthMillis = calendar.timeInMillis

            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.MILLISECOND, -1)
            val endOfMonthMillis = calendar.timeInMillis

            val monthTransactions = database.transactionDao().getTransactionsBetween(startOfMonthMillis, endOfMonthMillis)

            val monthSpent = monthTransactions.asSequence()
                .filter {
                    (it.status == TransactionStatus.POSTED) &&
                        (it.direction == TransactionDirection.DEBIT) &&
                        (it.category != TransactionCategory.TRANSFER) &&
                        it.countsTowardBudget
                }
                .sumOf { it.amount }

            val budgetLimit = budget?.amountLimit ?: 0.0
            val today = Calendar.getInstance()
            val maxDays = today.getActualMaximum(Calendar.DAY_OF_MONTH)
            val currentDay = today.get(Calendar.DAY_OF_MONTH)
            val daysRemaining = (maxDays - currentDay + 1).coerceAtLeast(1)

            val remainingBudget = if (budgetLimit > 0) (budgetLimit - monthSpent).coerceAtLeast(0.0) else 0.0
            val safeDailySpend = if (budgetLimit > 0) remainingBudget / daysRemaining else 0.0
            val progress = if (budgetLimit > 0) (monthSpent / budgetLimit).toFloat().coerceIn(0f, 1f) else 0f
            val percentUsed = if (budgetLimit > 0) ((monthSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0

            SafeSpendState(
                monthName = monthName,
                monthSpent = monthSpent,
                budgetLimit = budgetLimit,
                safeDailySpend = safeDailySpend,
                percentUsed = percentUsed,
                progressFloat = progress,
                daysRemaining = daysRemaining,
            )
        }
    }
}

@Composable
fun SafeSpendWidgetContent(context: Context, state: SafeSpendState) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val addTxIntent = Intent(context, MainActivity::class.java).apply {
        action = SafeSpendGlanceWidget.ACTION_ADD_TRANSACTION
        putExtra(SafeSpendGlanceWidget.EXTRA_OPEN_ADD_TRANSACTION, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(openAppIntent)),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            // 1. Top Row: App brand mark + current month name
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_chart),
                    contentDescription = "App Brand Mark",
                    modifier = GlanceModifier.size(18.dp),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Money Tracker",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = state.monthName,
                    style = TextStyle(
                        color = GlanceTheme.colors.outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 2. Center Visual: Progress indicator showing % budget consumed
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = state.progressFloat,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .cornerRadius(3.dp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = if (state.budgetLimit > 0) {
                            "${state.percentUsed}% consumed (${state.daysRemaining}d left)"
                        } else {
                            "No budget set"
                        },
                        style = TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 10.sp,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (state.budgetLimit > 0) {
                        Text(
                            text = "Limit: ${state.budgetLimit.asCurrency()}",
                            style = TextStyle(
                                color = GlanceTheme.colors.outline,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 3. Metrics & Quick Actions
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // Metric 1: Daily Safe-to-Spend
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Daily Safe-to-Spend",
                        style = TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 10.sp,
                        ),
                    )
                    Text(
                        text = state.safeDailySpend.asCurrency(),
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                // Metric 2: Total Spent
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Total Spent",
                        style = TextStyle(
                            color = GlanceTheme.colors.outline,
                            fontSize = 10.sp,
                        ),
                    )
                    Text(
                        text = state.monthSpent.asCurrency(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                // Quick Action: 1-tap Glance action button launching MainActivity with deep link
                Button(
                    text = "+ Add",
                    onClick = actionStartActivity(addTxIntent),
                )
            }
        }
    }
}

class SafeSpendGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeSpendGlanceWidget()
}
