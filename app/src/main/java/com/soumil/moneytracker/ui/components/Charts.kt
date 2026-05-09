package com.soumil.moneytracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val arcTrackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    val arcProgressColor = MaterialTheme.colorScheme.primary
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val progress = when {
        budget == null || budget <= 0.0 -> 0f
        else -> (spent / budget).coerceIn(0.0, 1.0).toFloat()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 34.dp.toPx()
            val arcRect = Rect(
                left = 48.dp.toPx(),
                top = 48.dp.toPx(),
                right = size.width - 48.dp.toPx(),
                bottom = size.height + 90.dp.toPx(),
            )
            drawArc(
                color = arcTrackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(arcRect.left, arcRect.top),
                size = Size(arcRect.width, arcRect.height),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcProgressColor,
                startAngle = 180f,
                sweepAngle = 180f * progress,
                useCenter = false,
                topLeft = Offset(arcRect.left, arcRect.top),
                size = Size(arcRect.width, arcRect.height),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(
            modifier = Modifier.padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (budget == null) "No budget yet" else "${(budget - spent).coerceAtLeast(0.0).asCurrency()} left",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (budget == null) "Set your monthly cap" else "Spent ${spent.asCurrency()} of ${budget.asCurrency()}",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
            )
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
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
    )
    val total = slices.sumOf { it.amount }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        if (total <= 0.0) return@Canvas
        var startAngle = -90f
        val diameter = size.minDimension * 0.72f
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        slices.forEachIndexed { index, slice ->
            val sweep = ((slice.amount / total) * 360f).toFloat()
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
            radius = diameter * 0.24f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

@Composable
fun CashflowTrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val incomeColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = max(
        points.maxOfOrNull { it.income } ?: 0.0,
        points.maxOfOrNull { it.expense } ?: 0.0,
    ).coerceAtLeast(1.0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (points.isEmpty()) return@Canvas

        val leftPadding = 32.dp.toPx()
        val topPadding = 20.dp.toPx()
        val bottomPadding = 40.dp.toPx()
        val chartWidth = size.width - leftPadding
        val chartHeight = size.height - topPadding - bottomPadding
        val stepX = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth

        fun valueToY(value: Double): Float {
            val ratio = (value / maxValue).toFloat()
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
                strokeWidth = 2f,
            )
        }

        incomeOffsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = incomeColor,
                start = start,
                end = end,
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
        }
        expenseOffsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = expenseColor,
                start = start,
                end = end,
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
        }

        incomeOffsets.forEach {
            drawCircle(incomeColor, radius = 8f, center = it)
        }
        expenseOffsets.forEach {
            drawCircle(expenseColor, radius = 8f, center = it)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        points.forEach { point ->
            Text(
                text = point.date.dayOfWeek.name.take(3),
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
