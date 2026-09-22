package com.soumil.moneytracker.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soumil.moneytracker.data.model.CategorySlice
import com.soumil.moneytracker.data.model.TrendPoint
import com.soumil.moneytracker.ui.asCurrency
import kotlin.math.max

@Composable
fun BudgetGauge(
    spent: Double,
    budget: Double?,
    modifier: Modifier = Modifier,
) {
    val progress = when {
        budget == null || budget <= 0.0 -> 0f
        else -> (spent / budget).coerceIn(0.0, 1.2).toFloat()
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "budget_progress",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val progressBrush = when {
        progress > 1.0f -> Brush.linearGradient(listOf(Color(0xFFFF5E2B), Color(0xFFEF4444)))
        progress > 0.8f -> Brush.linearGradient(listOf(Color(0xFFF3C77C), Color(0xFFFF5E2B)))
        else -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(175.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val padding = 28.dp.toPx()
            val arcRect = Rect(
                left = padding,
                top = padding,
                right = size.width - padding,
                bottom = size.height * 2f - padding * 1.5f,
            )

            // Background Track
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(arcRect.left, arcRect.top),
                size = Size(arcRect.width, arcRect.height),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Progress Arc
            if (animatedProgress > 0f) {
                drawArc(
                    brush = progressBrush,
                    startAngle = 180f,
                    sweepAngle = (180f * animatedProgress.coerceAtMost(1f)),
                    useCenter = false,
                    topLeft = Offset(arcRect.left, arcRect.top),
                    size = Size(arcRect.width, arcRect.height),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (budget == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "No Budget Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = "Tap to set monthly target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val remaining = budget - spent
                if (remaining >= 0.0) {
                    Text(
                        text = "${remaining.asCurrency()} left",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "${(-remaining).asCurrency()} over budget",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${spent.asCurrency()} of ${budget.asCurrency()} (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SpendingPieChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFF38BDF8),
        Color(0xFFA78BFA),
        Color(0xFFF472B6),
    )
    val total = slices.sumOf { it.amount }
    val revealProgress = rememberRevealProgress(
        targetValue = if (total > 0.0) 1f else 0f,
        delayMillis = 110,
        durationMillis = 900,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp),
    ) {
        if (total <= 0.0) return@Canvas
        var startAngle = -90f
        val diameter = size.minDimension * 0.75f
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        slices.forEachIndexed { index, slice ->
            val sweep = ((slice.amount / total) * 360f * revealProgress).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = Size(diameter, diameter),
            )
            startAngle += sweep
        }
        drawCircle(
            color = surfaceColor,
            radius = diameter * 0.28f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

@Composable
fun CashflowTrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val incomeColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = max(
        points.maxOfOrNull { it.income } ?: 0.0,
        points.maxOfOrNull { it.expense } ?: 0.0,
    ).coerceAtLeast(1.0)
    val revealProgress = rememberRevealProgress(
        targetValue = 1f,
        delayMillis = 90,
        durationMillis = 950,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        if (points.isEmpty()) return@Canvas

        val leftPadding = 24.dp.toPx()
        val topPadding = 16.dp.toPx()
        val bottomPadding = 32.dp.toPx()
        val chartWidth = size.width - leftPadding
        val chartHeight = size.height - topPadding - bottomPadding
        val stepX = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth

        fun valueToY(value: Double): Float {
            val ratio = (value / maxValue).toFloat() * revealProgress
            return topPadding + chartHeight - (chartHeight * ratio)
        }

        val incomeOffsets = points.mapIndexed { index, point ->
            Offset(leftPadding + stepX * index, valueToY(point.income))
        }
        val expenseOffsets = points.mapIndexed { index, point ->
            Offset(leftPadding + stepX * index, valueToY(point.expense))
        }

        repeat(4) { index ->
            val y = topPadding + chartHeight / 3f * index
            drawLine(
                color = outlineColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f,
            )
        }

        incomeOffsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = incomeColor,
                start = start,
                end = end,
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
        }
        expenseOffsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = expenseColor,
                start = start,
                end = end,
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
        }

        incomeOffsets.forEach {
            drawCircle(incomeColor, radius = 6f, center = it)
        }
        expenseOffsets.forEach {
            drawCircle(expenseColor, radius = 6f, center = it)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        points.forEach { point ->
            Text(
                text = point.date.dayOfWeek.name.take(3),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
