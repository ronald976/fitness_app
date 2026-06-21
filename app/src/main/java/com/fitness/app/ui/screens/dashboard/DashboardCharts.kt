package com.fitness.app.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Generic reusable chart components drawn with Compose Canvas.
 * Axis/grid colors come from the Material theme so charts stay legible in dark mode.
 */

private val LABEL_SIZE = 10.sp

// Shared LineChart insets. Public because ProgressionTab maps tap positions back
// to chart coordinates and must use the exact same geometry.
val LineChartLeftPad = 44.dp
val LineChartRightPad = 8.dp
val LineChartTopPad = 8.dp
val LineChartBottomPad = 22.dp

// ── Line/scatter chart ─────────────────────────────────────────────────

data class ChartLine(
    val points: List<Pair<Float, Float>>,
    val color: Color,
    val strokeWidth: Float = 3f,
    val isDashed: Boolean = false,
    val alpha: Float = 1f
)

data class ChartDot(
    val x: Float,
    val y: Float,
    val color: Color,
    val radius: Float = 6f
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun LineChart(
    lines: List<ChartLine>,
    dots: List<ChartDot> = emptyList(),
    xLabels: List<Pair<Float, String>> = emptyList(),
    yLabels: List<Pair<Float, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = LABEL_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val haloColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val leftPad = LineChartLeftPad.toPx()
        val rightPad = LineChartRightPad.toPx()
        val topPad = LineChartTopPad.toPx()
        val bottomPad = LineChartBottomPad.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        // Grid lines
        for ((y, _) in yLabels) {
            val py = topPad + chartH * (1 - y)
            drawLine(gridColor, Offset(leftPad, py), Offset(leftPad + chartW, py))
        }

        // Y labels
        for ((y, label) in yLabels) {
            val py = topPad + chartH * (1 - y)
            val result = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(result, topLeft = Offset(leftPad - result.size.width - 4f, py - result.size.height / 2f))
        }

        // X labels
        for ((x, label) in xLabels) {
            val px = leftPad + chartW * x
            val result = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(result, topLeft = Offset(px - result.size.width / 2f, topPad + chartH + 4f))
        }

        // Lines
        for (line in lines) {
            if (line.points.size < 2) continue
            val path = Path()
            var first = true
            for ((x, y) in line.points) {
                val px = leftPad + chartW * x
                val py = topPad + chartH * (1 - y)
                if (first) { path.moveTo(px, py); first = false }
                else path.lineTo(px, py)
            }
            val effect = if (line.isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
            drawPath(path, line.color.copy(alpha = line.alpha), style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, pathEffect = effect))
        }

        // Dots (halo in surface color so they read on both themes)
        for (dot in dots) {
            val px = leftPad + chartW * dot.x
            val py = topPad + chartH * (1 - dot.y)
            drawCircle(haloColor, dot.radius + 2f, Offset(px, py))
            drawCircle(dot.color, dot.radius, Offset(px, py))
        }
    }
}

// ── Bar chart ──────────────────────────────────────────────────────────

data class BarEntry(
    val x: Float,
    val y: Float,
    val color: Color,
    val width: Float = 0.02f
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun BarChart(
    bars: List<BarEntry>,
    xLabels: List<Pair<Float, String>> = emptyList(),
    yLabels: List<Pair<Float, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = LABEL_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Canvas(modifier = modifier) {
        val leftPad = 44.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        for ((y, _) in yLabels) {
            val py = topPad + chartH * (1 - y)
            drawLine(gridColor, Offset(leftPad, py), Offset(leftPad + chartW, py))
        }
        for ((y, label) in yLabels) {
            val py = topPad + chartH * (1 - y)
            val result = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(result, topLeft = Offset(leftPad - result.size.width - 4f, py - result.size.height / 2f))
        }
        for ((x, label) in xLabels) {
            val px = leftPad + chartW * x
            val result = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(result, topLeft = Offset(px - result.size.width / 2f, topPad + chartH + 4f))
        }

        for (bar in bars) {
            val px = leftPad + chartW * bar.x
            val barW = chartW * bar.width
            val barH = chartH * bar.y
            val r = (barW * 0.3f).coerceAtMost(3.dp.toPx())
            drawRoundRect(
                bar.color,
                topLeft = Offset(px - barW / 2, topPad + chartH - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(r, r)
            )
        }
    }
}

// ── Stacked bar chart ──────────────────────────────────────────────────

data class StackedBarEntry(
    val x: Float,
    /** Segment heights as fractions of the chart height, drawn bottom-up. */
    val segments: List<Pair<Float, Color>>,
    val width: Float
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun StackedBarChart(
    bars: List<StackedBarEntry>,
    overlay: ChartLine? = null,
    xLabels: List<Pair<Float, String>> = emptyList(),
    yLabels: List<Pair<Float, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = LABEL_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Canvas(modifier = modifier) {
        val leftPad = 44.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        for ((y, _) in yLabels) {
            val py = topPad + chartH * (1 - y)
            drawLine(gridColor, Offset(leftPad, py), Offset(leftPad + chartW, py))
        }
        for ((y, label) in yLabels) {
            val py = topPad + chartH * (1 - y)
            val result = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(result, topLeft = Offset(leftPad - result.size.width - 4f, py - result.size.height / 2f))
        }
        for ((x, label) in xLabels) {
            val px = leftPad + chartW * x
            val result = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(result, topLeft = Offset(px - result.size.width / 2f, topPad + chartH + 4f))
        }

        for (bar in bars) {
            val px = leftPad + chartW * bar.x
            val barW = chartW * bar.width
            var cum = 0f
            for ((frac, color) in bar.segments) {
                val segH = chartH * frac
                drawRect(
                    color,
                    topLeft = Offset(px - barW / 2, topPad + chartH - cum - segH),
                    size = Size(barW, segH)
                )
                cum += segH
            }
        }

        if (overlay != null && overlay.points.size >= 2) {
            val path = Path()
            var first = true
            for ((x, y) in overlay.points) {
                val px = leftPad + chartW * x
                val py = topPad + chartH * (1 - y)
                if (first) { path.moveTo(px, py); first = false }
                else path.lineTo(px, py)
            }
            val effect = if (overlay.isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
            drawPath(path, overlay.color.copy(alpha = overlay.alpha), style = Stroke(width = overlay.strokeWidth, cap = StrokeCap.Round, pathEffect = effect))
        }
    }
}

// ── Horizontal bar chart ───────────────────────────────────────────────

data class HBarEntry(
    val label: String,
    val value: Float,
    val color: Color
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun HorizontalBarChart(
    bars: List<HBarEntry>,
    modifier: Modifier = Modifier,
    valueFmt: (Float) -> String = { "${it.toInt()}" }
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = LABEL_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = LABEL_SIZE, color = MaterialTheme.colorScheme.onSurface)
    val maxVal = bars.maxOfOrNull { it.value } ?: 1f
    Canvas(modifier = modifier) {
        // Size the gutters to the actual text so labels never clip at any density.
        val labelResults = bars.map { textMeasurer.measure(AnnotatedString(it.label), labelStyle) }
        val valueResults = bars.map { textMeasurer.measure(AnnotatedString(valueFmt(it.value)), valueStyle) }
        val leftPad = (labelResults.maxOfOrNull { it.size.width }?.toFloat() ?: 0f) + 10.dp.toPx()
        val rightPad = (valueResults.maxOfOrNull { it.size.width }?.toFloat() ?: 0f) + 8.dp.toPx()
        val topPad = 8.dp.toPx()
        val barHeight = (size.height - topPad) / bars.size.coerceAtLeast(1) * 0.7f
        val gap = (size.height - topPad) / bars.size.coerceAtLeast(1)
        val chartW = size.width - leftPad - rightPad

        for ((i, bar) in bars.withIndex()) {
            val cy = topPad + gap * i + gap / 2
            val barW = (bar.value / maxVal) * chartW
            val r = (barHeight * 0.25f).coerceAtMost(4.dp.toPx())
            drawRoundRect(
                bar.color,
                Offset(leftPad, cy - barHeight / 2),
                Size(barW, barHeight),
                CornerRadius(r, r)
            )
            val labelResult = labelResults[i]
            drawText(labelResult, topLeft = Offset(leftPad - labelResult.size.width - 6f, cy - labelResult.size.height / 2f))
            val valResult = valueResults[i]
            drawText(valResult, topLeft = Offset(leftPad + barW + 4.dp.toPx(), cy - valResult.size.height / 2f))
        }
    }
}

// ── Calendar heatmap (vertical, phone-friendly) ───────────────────────

@OptIn(ExperimentalTextApi::class)
@Composable
fun CalendarHeatmap(
    trainedDates: Set<LocalDate>,
    modifier: Modifier = Modifier
) {
    if (trainedDates.isEmpty()) return
    val sorted = trainedDates.sorted()
    val start = sorted.first().with(DayOfWeek.MONDAY)
    val end = sorted.last()
    val totalWeeks = (ChronoUnit.WEEKS.between(start, end) + 1).toInt()
    val gap = 3.dp
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val restColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val trainColor = MaterialTheme.colorScheme.primary

    // Vertical: weeks go top→bottom with the most recent week first (no scrolling
    // needed to see current training), days go left→right.
    // Left margin for month labels, top margin for day-of-week labels.
    val leftMargin = 40.dp
    val topMargin = 20.dp

    BoxWithConstraints(modifier = modifier.verticalScroll(rememberScrollState())) {
        // Size cells to fill the available width, capped so they don't get absurdly large.
        val available = maxWidth - leftMargin
        val cellSize = ((available - gap * 6) / 7).coerceIn(14.dp, 36.dp)
        Canvas(
            modifier = Modifier
                .width(leftMargin + cellSize * 7 + gap * 6)
                .height(topMargin + (cellSize + gap) * totalWeeks)
        ) {
            val cPx = cellSize.toPx()
            val gPx = gap.toPx()
            val leftPx = leftMargin.toPx()
            val topPx = topMargin.toPx()
            val cornerR = CornerRadius(2.dp.toPx(), 2.dp.toPx())

            // Day-of-week headers (Mon-Sun across top)
            for (d in 0..6) {
                val x = leftPx + d * (cPx + gPx) + cPx / 2
                val result = textMeasurer.measure(AnnotatedString(dayLabels[d]), axisLabelStyle)
                drawText(result, topLeft = Offset(x - result.size.width / 2f, 0f))
            }

            // Month labels on the left + cells
            var lastMonthLabel = ""
            for (row in 0 until totalWeeks) {
                val weekStart = start.plusWeeks((totalWeeks - 1 - row).toLong())
                val monthLabel = weekStart.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
                val yearMonth = "${weekStart.year}-${weekStart.monthValue}"

                // Show month label on the topmost row of each month
                if (yearMonth != lastMonthLabel) {
                    lastMonthLabel = yearMonth
                    val y = topPx + row * (cPx + gPx) + cPx / 2
                    val result = textMeasurer.measure(AnnotatedString(monthLabel), axisLabelStyle)
                    drawText(result, topLeft = Offset(0f, y - result.size.height / 2f))
                }

                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date.isAfter(end)) continue
                    if (date.isBefore(sorted.first())) continue
                    val x = leftPx + d * (cPx + gPx)
                    val y = topPx + row * (cPx + gPx)
                    val color = if (date in trainedDates) trainColor else restColor
                    drawRoundRect(color, Offset(x, y), Size(cPx, cPx), cornerR)
                }
            }
        }
    }
}
