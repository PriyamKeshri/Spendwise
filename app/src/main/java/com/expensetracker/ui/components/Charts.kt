package com.expensetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.YearMonth

data class ChartSlice(val value: Float, val color: Color)

/** Ring chart with rounded segment ends, drawn clockwise from 12 o'clock. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 18.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    center: @Composable () -> Unit = {},
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(durationMillis = 800)) }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))

            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            // Round caps poke past each end of an arc; leave room for both plus a small visible gap.
            val capDegrees = Math.toDegrees((stroke / diameter).toDouble()).toFloat()
            val gap = if (slices.size > 1) capDegrees * 2 + 2.5f else 0f
            var start = -90f
            for (slice in slices) {
                val sweep = 360f * (slice.value / total) * reveal.value
                drawArc(
                    color = slice.color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
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
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(durationMillis = 700)) }

    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = 20.dp.toPx()
        val chartHeight = size.height - labelHeight
        val max = values.max().coerceAtLeast(1L)
        val slot = size.width / values.size
        val barWidth = (slot * 0.56f).coerceAtMost(10.dp.toPx())
        val minBar = 3.dp.toPx()

        drawLine(baseline, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { index, value ->
            val day = index + 1
            if (value > 0) {
                val height = (chartHeight * value / max * grow.value).coerceAtLeast(minBar)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2, chartHeight - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
            if (day == 1 || day % 5 == 0) {
                val label = textMeasurer.measure(day.toString(), labelStyle)
                drawText(label, topLeft = Offset(slot * index + (slot - label.size.width) / 2, chartHeight + 6.dp.toPx()))
            }
        }
    }
}

data class TrendPoint(val month: YearMonth, val spentPaise: Long)

/** Month-by-month spending bars; tapping a bar selects that month. Sized for the summary card. */
@Composable
fun TrendBars(
    points: List<TrendPoint>,
    selected: YearMonth,
    onSelect: (YearMonth) -> Unit,
    barColor: Color,
    dimBarColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val max = points.maxOfOrNull { it.spentPaise }?.coerceAtLeast(1L) ?: 1L
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            val isSelected = point.month == selected
            val target = if (point.spentPaise == 0L) 0.04f else (point.spentPaise.toFloat() / max).coerceAtLeast(0.08f)
            val fraction by animateFloatAsState(target, tween(600), label = "trend")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = isSelected, onClick = { onSelect(point.month) }, role = Role.Tab),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) barColor else dimBarColor),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    point.month.shortLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) barColor else labelColor,
                )
            }
        }
    }
}
