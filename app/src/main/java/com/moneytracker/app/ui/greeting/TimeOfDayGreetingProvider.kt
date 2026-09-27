package com.moneytracker.app.ui.greeting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.moneytracker.app.data.model.DashboardState
import com.moneytracker.app.ui.asCurrency
import java.time.LocalTime

enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

data class ContextualGreetingMessage(
    val message: String,
    val icon: ImageVector,
    val tag: String,
)

object TimeOfDayGreetingProvider {

    fun getTimeOfDay(time: LocalTime = LocalTime.now()): TimeOfDay {
        val hour = time.hour
        return when {
            hour in 5..11 -> TimeOfDay.MORNING
            hour in 12..16 -> TimeOfDay.AFTERNOON
            hour in 17..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    fun getTopGreeting(userName: String, time: LocalTime = LocalTime.now()): String {
        val trimmed = userName.trim()
        val timeOfDay = getTimeOfDay(time)
        val greetingPrefix = when (timeOfDay) {
            TimeOfDay.MORNING -> "Good morning"
            TimeOfDay.AFTERNOON -> "Good afternoon"
            TimeOfDay.EVENING -> "Good evening"
            TimeOfDay.NIGHT -> "Welcome back"
        }
        return if (trimmed.isNotBlank()) {
            "$greetingPrefix, $trimmed"
        } else {
            greetingPrefix
        }
    }

    fun getContextualMessage(
        dashboard: DashboardState,
        userName: String,
        time: LocalTime = LocalTime.now(),
    ): ContextualGreetingMessage {
        val timeOfDay = getTimeOfDay(time)
        val firstName = userName.trim().split(" ").firstOrNull().orEmpty()

        return when (timeOfDay) {
            TimeOfDay.MORNING -> {
                when {
                    dashboard.budgetLimit != null && dashboard.monthSpent > dashboard.budgetLimit -> {
                        val over = (dashboard.monthSpent - dashboard.budgetLimit).asCurrency()
                        ContextualGreetingMessage(
                            message = "Budget notice: You're $over over target. Plan a low-spend morning.",
                            icon = Icons.Outlined.Speed,
                            tag = "Morning Alert",
                        )
                    }
                    dashboard.safeDailySpend > 0 -> {
                        ContextualGreetingMessage(
                            message = "Safe daily burn is ${dashboard.safeDailySpend.asCurrency()} today. Start your day with mindful spending.",
                            icon = Icons.Outlined.WbSunny,
                            tag = "Daily Target",
                        )
                    }
                    dashboard.monthNetCashflow > 0 -> {
                        ContextualGreetingMessage(
                            message = "Morning snapshot: Net positive cashflow of +${dashboard.monthNetCashflow.asCurrency()} this month.",
                            icon = Icons.Outlined.Savings,
                            tag = "Morning Cashflow",
                        )
                    }
                    else -> {
                        val nameStr = if (firstName.isNotBlank()) ", $firstName" else ""
                        ContextualGreetingMessage(
                            message = "Start your morning$nameStr with a clear view of your financial goals.",
                            icon = Icons.Outlined.WbSunny,
                            tag = "Morning Focus",
                        )
                    }
                }
            }
            TimeOfDay.AFTERNOON -> {
                when {
                    dashboard.reviewCount > 0 -> {
                        val plural = if (dashboard.reviewCount > 1) "s" else ""
                        ContextualGreetingMessage(
                            message = "Midday check: You have ${dashboard.reviewCount} transaction$plural pending review.",
                            icon = Icons.Outlined.AssignmentTurnedIn,
                            tag = "Review Queue",
                        )
                    }
                    dashboard.safeDailySpend > 0 -> {
                        ContextualGreetingMessage(
                            message = "Midday pace: Aim to keep discretionary debits under ${dashboard.safeDailySpend.asCurrency()} today.",
                            icon = Icons.Outlined.LightMode,
                            tag = "Midday Pace",
                        )
                    }
                    dashboard.cardSpendThisMonth > 0 -> {
                        ContextualGreetingMessage(
                            message = "Card debits stand at ${dashboard.cardSpendThisMonth.asCurrency()} this month. Keep tabs on dining & delivery.",
                            icon = Icons.Outlined.LightMode,
                            tag = "Card Spend",
                        )
                    }
                    else -> {
                        ContextualGreetingMessage(
                            message = "Midday pulse check: Steady spending habits build long-term financial freedom.",
                            icon = Icons.Outlined.LightMode,
                            tag = "Midday Tip",
                        )
                    }
                }
            }
            TimeOfDay.EVENING -> {
                when {
                    dashboard.reviewCount > 0 -> {
                        val plural = if (dashboard.reviewCount > 1) "s" else ""
                        ContextualGreetingMessage(
                            message = "Evening wrap-up: ${dashboard.reviewCount} unverified item$plural ready for your review.",
                            icon = Icons.Outlined.AssignmentTurnedIn,
                            tag = "Evening Review",
                        )
                    }
                    dashboard.monthNetCashflow >= 0 -> {
                        ContextualGreetingMessage(
                            message = "Great pacing today! Monthly net cashflow is at +${dashboard.monthNetCashflow.asCurrency()}.",
                            icon = Icons.Outlined.CheckCircle,
                            tag = "Cashflow Healthy",
                        )
                    }
                    dashboard.safeDailySpend > 0 -> {
                        ContextualGreetingMessage(
                            message = "Evening review: Safe daily burn stands at ${dashboard.safeDailySpend.asCurrency()} for remaining days.",
                            icon = Icons.Outlined.WbTwilight,
                            tag = "Evening Check",
                        )
                    }
                    else -> {
                        ContextualGreetingMessage(
                            message = "Time to wind down: Don't forget to log any cash or UPI payments made today.",
                            icon = Icons.Outlined.WbTwilight,
                            tag = "Evening Ledger",
                        )
                    }
                }
            }
            TimeOfDay.NIGHT -> {
                when {
                    dashboard.reviewCount > 0 -> {
                        val plural = if (dashboard.reviewCount > 1) "s" else ""
                        ContextualGreetingMessage(
                            message = "Late night review: ${dashboard.reviewCount} transaction$plural waiting for verification.",
                            icon = Icons.Outlined.AssignmentTurnedIn,
                            tag = "Night Queue",
                        )
                    }
                    dashboard.budgetLimit != null && dashboard.monthSpent > dashboard.budgetLimit -> {
                        ContextualGreetingMessage(
                            message = "Night summary: Monthly outflow is at ${dashboard.monthSpent.asCurrency()}. Tomorrow brings a fresh reset.",
                            icon = Icons.Outlined.Bedtime,
                            tag = "Night Summary",
                        )
                    }
                    else -> {
                        val nameStr = if (firstName.isNotBlank()) " $firstName, finances" else "Finances"
                        ContextualGreetingMessage(
                            message = "Rest easy$nameStr are locked, tracked, and secured. Good night!",
                            icon = Icons.Outlined.NightsStay,
                            tag = "Peace of Mind",
                        )
                    }
                }
            }
        }
    }
}
