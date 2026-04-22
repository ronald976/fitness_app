package com.fitness.app.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 */

private val AXIS_COLOR = Color(0xFF999999)
private val GRID_COLOR = Color(0xFFE0E0E0)
private val LABEL_SIZE = 10.sp

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
    Canvas(modifier = modifier) {
        val leftPad = 50f
        val bottomPad = 30f
        val topPad = 10f
        val rightPad = 10f
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        // Grid lines
        for ((y, _) in yLabels) {
            val py = topPad + chartH * (1 - y)
            drawLine(GRID_COLOR, Offset(leftPad, py), Offset(leftPad + chartW, py))
        }

        // Y labels
        for ((y, label) in yLabels) {
            val py = topPad + chartH * (1 - y)
            val result = textMeasurer.measure(AnnotatedString(label), TextStyle(fontSize = LABEL_SIZE, color = AXIS_COLOR))
            drawText(result, topLeft = Offset(leftPad - result.size.width - 4f, py - result.size.height / 2f))
        }

        // X labels
        for ((x, label) in xLabels) {
            val px = leftPad + chartW * x
            val result = textMeasurer.measure(AnnotatedString(label), TextStyle(fontSize = LABEL_SIZE, color = AXIS_COLOR))
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

        // Dots
        for (dot in dots) {
            val px = leftPad + chartW * dot.x
            val py = topPad + chartH * (1 - dot.y)
            drawCircle(Color.White, dot.radius + 2f, Offset(px, py))
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
    Canvas(modifier = modifier) {
        val leftPad = 50f
        val bottomPad = 40f
        val topPad = 10f
        val rightPad = 10f
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        for ((y, _) in yLabels) {
            val py = topPad + chartH * (1 - y)
            drawLine(GRID_COLOR, Offset(leftPad, py), Offset(leftPad + chartW, py))
        }
        for ((y, label) in yLabels) {
            val py = topPad + chartH * (1 - y)
            val result = textMeasurer.measure(AnnotatedString(label), TextStyle(fontSize = LABEL_SIZE, color = AXIS_COLOR))
            drawText(result, topLeft = Offset(leftPad - result.size.width - 4f, py - result.size.height / 2f))
        }
        for ((x, label) in xLabels) {
            val px = leftPad + chartW * x
            val result = textMeasurer.measure(AnnotatedString(label), TextStyle(fontSize = LABEL_SIZE, color = AXIS_COLOR))
            drawText(result, topLeft = Offset(px - result.size.width / 2f, topPad + chartH + 4f))
        }

        for (bar in bars) {
            val px = leftPad + chartW * bar.x
            val barW = chartW * bar.width
            val barH = chartH * bar.y
            drawRect(
                bar.color,
                topLeft = Offset(px - barW / 2, topPad + chartH - barH),
                size = androidx.compose.ui.geometry.Size(barW, barH)
            )
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
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val maxVal = bars.maxOfOrNull { it.value } ?: 1f
    Canvas(modifier = modifier) {
        val leftPad = 120f
        val rightPad = 20f
        val topPad = 10f
        val barHeight = (size.height - topPad) / bars.size.coerceAtLeast(1) * 0.7f
        val gap = (size.height - topPad) / bars.size.coerceAtLeast(1)
        val chartW = size.width - leftPad - rightPad

        for ((i, bar) in bars.withIndex()) {
            val cy = topPad + gap * i + gap / 2
            val barW = (bar.value / maxVal) * chartW
            drawRect(bar.color, Offset(leftPad, cy - barHeight / 2), androidx.compose.ui.geometry.Size(barW, barHeight))
            val labelResult = textMeasurer.measure(AnnotatedString(bar.label), TextStyle(fontSize = LABEL_SIZE, color = AXIS_COLOR))
            drawText(labelResult, topLeft = Offset(leftPad - labelResult.size.width - 6f, cy - labelResult.size.height / 2f))
            val valResult = textMeasurer.measure(AnnotatedString("${bar.value.toInt()}"), TextStyle(fontSize = LABEL_SIZE, color = AXIS_COLOR))
            drawText(valResult, topLeft = Offset(leftPad + barW + 4f, cy - valResult.size.height / 2f))
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
    val cellSize = 14.dp
    val gap = 2.dp
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val textMeasurer = rememberTextMeasurer()

    // Vertical: weeks go top→bottom, days go left→right
    // Left margin for month labels, top margin for day-of-week labels
    val leftMargin = 40.dp
    val topMargin = 20.dp

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Canvas(
            modifier = Modifier
                .width(leftMargin + (cellSize + gap) * 7)
                .height(topMargin + (cellSize + gap) * totalWeeks)
        ) {
            val cPx = cellSize.toPx()
            val gPx = gap.toPx()
            val leftPx = leftMargin.toPx()
            val topPx = topMargin.toPx()
            val restColor = Color(0xFFEBEDF0)
            val trainColor = Color(0xFF40C463)

            // Day-of-week headers (Mon-Sun across top)
            for (d in 0..6) {
                val x = leftPx + d * (cPx + gPx) + cPx / 2
                val result = textMeasurer.measure(
                    AnnotatedString(dayLabels[d]),
                    TextStyle(fontSize = 9.sp, color = AXIS_COLOR)
                )
                drawText(result, topLeft = Offset(x - result.size.width / 2f, 0f))
            }

            // Month labels on the left + cells
            var lastMonthLabel = ""
            for (w in 0 until totalWeeks) {
                val weekStart = start.plusWeeks(w.toLong())
                val monthLabel = weekStart.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
                val yearMonth = "${weekStart.year}-${weekStart.monthValue}"

                // Show month label on first week of each month
                if (yearMonth != lastMonthLabel) {
                    lastMonthLabel = yearMonth
                    val y = topPx + w * (cPx + gPx) + cPx / 2
                    val result = textMeasurer.measure(
                        AnnotatedString(monthLabel),
                        TextStyle(fontSize = 9.sp, color = AXIS_COLOR)
                    )
                    drawText(result, topLeft = Offset(0f, y - result.size.height / 2f))
                }

                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date.isAfter(end)) continue
                    if (date.isBefore(sorted.first())) continue
                    val x = leftPx + d * (cPx + gPx)
                    val y = topPx + w * (cPx + gPx)
                    val color = if (date in trainedDates) trainColor else restColor
                    drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(cPx, cPx))
                }
            }
        }
    }
}
