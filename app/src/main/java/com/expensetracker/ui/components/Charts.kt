package com.expensetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ChartSlice(val value: Float, val color: Color)

/** Ring chart of category shares, drawn clockwise from 12 o'clock with a hairline gap between slices. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit = {},
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(durationMillis = 700)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))

            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 1.2f else 0f
            var start = -90f
            for (slice in slices) {
                val sweep = 360f * (slice.value / total) * reveal.value
                if (sweep > gap) {
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** One bar per day of the month, labelled on day 1 and every fifth day. */
@Composable
fun DailyBarChart(values: List<Long>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val max = values.max().coerceAtLeast(1L)
        val slot = size.width / values.size
        val barWidth = (slot * 0.62f).coerceAtMost(12.dp.toPx())
        val minBar = 2.dp.toPx()

        drawLine(baseline, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val day = index + 1
            if (value > 0) {
                val height = (chartHeight * value / max).coerceAtLeast(minBar)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2, chartHeight - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
                )
            }
            if (day == 1 || day % 5 == 0) {
                val label = textMeasurer.measure(day.toString(), labelStyle)
                drawText(
                    label,
                    topLeft = Offset(slot * index + (slot - label.size.width) / 2, chartHeight + 4.dp.toPx()),
                )
            }
        }
    }
}
