package com.fitness.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val TABS = listOf("Progression", "Volume", "Frequency", "Balance", "Exercises", "Forecast")

// Colors matching the Python dashboard
private val COL_BLUE = Color(0xFF636EFA)
private val COL_RED = Color(0xFFEF553B)
private val COL_GREEN = Color(0xFF00CC96)
private val COL_PURPLE = Color(0xFFAB63FA)
private val COL_ORANGE = Color(0xFFFFA15A)
private val COL_CYAN = Color(0xFF19D3F3)
private val COL_GOLD = Color(0xFFFFD700)

private val MUSCLE_COLORS = mapOf(
    "Chest" to COL_BLUE, "Back" to COL_RED, "Shoulders" to COL_PURPLE,
    "Arms" to COL_ORANGE, "Legs" to COL_GREEN, "Core" to COL_CYAN
)

private val EXERCISE_COLORS = listOf(
    COL_BLUE, COL_RED, COL_GREEN, COL_PURPLE, COL_ORANGE, COL_CYAN,
    Color(0xFFFF6692), Color(0xFFB6E880), Color(0xFFFF97FF), Color(0xFFFECB52)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp
            ) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> ProgressionTab(state)
                    1 -> VolumeTab(state)
                    2 -> FrequencyTab(state)
                    3 -> BalanceTab(state)
                    4 -> ExercisesTab(state)
                    5 -> ForecastTab(state)
                }
            }
        }
    }
}

// ── Tab 1: Progression ─────────────────────────────────────────────────

@Composable
private fun ProgressionTab(state: DashboardUiState) {
    val exercises = state.progression.take(10)
    if (exercises.isEmpty()) {
        EmptyMessage("Not enough data for progression charts")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(exercises) { ex ->
            ProgressionCard(ex, exercises.indexOf(ex))
        }
    }
}

@Composable
private fun ProgressionCard(ex: ExerciseProgression, index: Int) {
    val color = EXERCISE_COLORS[index % EXERCISE_COLORS.size]
    val points = ex.dataPoints
    val trend = ex.trendPoints
    if (points.isEmpty()) return

    val minDate = points.first().date
    val maxDate = points.last().date
    val daySpan = java.time.temporal.ChronoUnit.DAYS.between(minDate, maxDate).toFloat().coerceAtLeast(1f)
    val minScore = points.minOf { it.score }.toFloat()
    val maxScore = points.maxOf { it.score }.toFloat()
    val scoreRange = (maxScore - minScore).coerceAtLeast(1f)

    fun dateToX(d: java.time.LocalDate) =
        java.time.temporal.ChronoUnit.DAYS.between(minDate, d).toFloat() / daySpan

    fun scoreToY(s: Double) = ((s.toFloat() - minScore) / scoreRange)

    val rawLine = ChartLine(
        points.map { dateToX(it.date) to scoreToY(it.score) },
        color, strokeWidth = 1.5f, alpha = 0.3f
    )
    val trendLine = ChartLine(
        trend.map { dateToX(it.date) to scoreToY(it.score) },
        color, strokeWidth = 3f
    )

    val prDots = ex.prs.filter { it.date in minDate..maxDate }.map { pr ->
        ChartDot(dateToX(pr.date), scoreToY(pr.score),
            if (pr.isRepPr) color else COL_GOLD, 5f)
    }

    // Y labels
    val ySteps = 4
    val yLabels = (0..ySteps).map { i ->
        val frac = i.toFloat() / ySteps
        frac to "${(minScore + scoreRange * frac).toInt()}"
    }

    // X labels (monthly)
    val xLabels = mutableListOf<Pair<Float, String>>()
    var cursor = minDate.withDayOfMonth(1).plusMonths(1)
    while (!cursor.isAfter(maxDate)) {
        xLabels.add(dateToX(cursor) to cursor.format(DateTimeFormatter.ofPattern("MMM")))
        cursor = cursor.plusMonths(1)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(ex.exerciseName, style = MaterialTheme.typography.titleMedium)

            val latest = points.last()
            Text(
                "Latest: ${latest.score.toInt()} • ${ex.prs.size} PRs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            LineChart(
                lines = listOf(rawLine, trendLine),
                dots = prDots,
                xLabels = xLabels,
                yLabels = yLabels,
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}

// ── Tab 2: Volume ──────────────────────────────────────────────────────

@Composable
private fun VolumeTab(state: DashboardUiState) {
    val volumes = state.weeklyVolume
    if (volumes.isEmpty()) {
        EmptyMessage("No volume data")
        return
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Weekly Tonnage", style = MaterialTheme.typography.titleMedium)

        val maxTonnage = volumes.maxOf { it.tonnage }.toFloat()
        val minWeek = volumes.first().weekStart
        val maxWeek = volumes.last().weekStart
        val weekSpan = java.time.temporal.ChronoUnit.WEEKS.between(minWeek, maxWeek).toFloat().coerceAtLeast(1f)

        val bars = volumes.map { v ->
            val x = java.time.temporal.ChronoUnit.WEEKS.between(minWeek, v.weekStart).toFloat() / weekSpan
            BarEntry(x, (v.tonnage / maxTonnage).toFloat(), COL_BLUE, 1f / weekSpan * 0.8f)
        }

        val yLabels = (0..4).map { i ->
            val frac = i.toFloat() / 4
            frac to "${(maxTonnage * frac / 1000).toInt()}k"
        }

        BarChart(
            bars = bars,
            yLabels = yLabels,
            modifier = Modifier.fillMaxWidth().height(250.dp)
        )

        // Summary
        val avgTonnage = volumes.map { it.tonnage }.average()
        val avgSets = volumes.map { it.sets }.average()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard("Avg Tonnage", "${(avgTonnage / 1000).toInt()}k kg/wk", Modifier.weight(1f))
            SummaryCard("Avg Sets", "${avgSets.toInt()}/wk", Modifier.weight(1f))
        }

        Text("Sets per Week", style = MaterialTheme.typography.titleMedium)

        val maxSets = volumes.maxOf { it.sets }.toFloat()
        val setBars = volumes.map { v ->
            val x = java.time.temporal.ChronoUnit.WEEKS.between(minWeek, v.weekStart).toFloat() / weekSpan
            BarEntry(x, (v.sets / maxSets).toFloat(), COL_CYAN, 1f / weekSpan * 0.8f)
        }

        BarChart(
            bars = setBars,
            yLabels = (0..4).map { i -> i.toFloat() / 4 to "${(maxSets * i / 4).toInt()}" },
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}

// ── Tab 3: Frequency ───────────────────────────────────────────────────

@Composable
private fun FrequencyTab(state: DashboardUiState) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Training Calendar", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Sessions", "${state.totalSessions}", Modifier.weight(1f))
            SummaryCard("Per Week", String.format("%.1f", state.avgPerWeek), Modifier.weight(1f))
            SummaryCard("Streak", "${state.currentStreak}d", Modifier.weight(1f))
            SummaryCard("Longest", "${state.longestStreak}d", Modifier.weight(1f))
        }

        CalendarHeatmap(
            trainedDates = state.trainingDays.toSet(),
            modifier = Modifier.fillMaxWidth().height(130.dp)
        )
    }
}

// ── Tab 4: Balance ─────────────────────────────────────────────────────

@Composable
private fun BalanceTab(state: DashboardUiState) {
    val muscles = state.muscleBalance
    if (muscles.isEmpty()) {
        EmptyMessage("No balance data")
        return
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sets by Muscle Group", style = MaterialTheme.typography.titleMedium)

        val order = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")
        val bars = order.mapNotNull { m ->
            val count = muscles[m] ?: return@mapNotNull null
            HBarEntry(m, count.toFloat(), MUSCLE_COLORS[m] ?: COL_BLUE)
        }

        HorizontalBarChart(
            bars = bars,
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )

        Text("Push : Pull Ratio (monthly)", style = MaterialTheme.typography.titleMedium)

        if (state.monthlyPushPull.isNotEmpty()) {
            val maxRatio = state.monthlyPushPull.maxOf { it.ratio }.toFloat().coerceAtLeast(1f)
            val items = state.monthlyPushPull
            val mBars = items.mapIndexed { i, pp ->
                val x = i.toFloat() / items.size.coerceAtLeast(1)
                val color = if (pp.ratio <= 1.5) COL_BLUE else COL_RED
                BarEntry(x, (pp.ratio / maxRatio).toFloat(), color, 0.8f / items.size)
            }
            val xLabels = items.mapIndexedNotNull { i, pp ->
                if (i % 3 == 0) i.toFloat() / items.size to pp.month.takeLast(2)
                else null
            }

            BarChart(
                bars = mBars,
                xLabels = xLabels,
                yLabels = listOf(
                    0f to "0",
                    (1f / maxRatio) to "1.0",
                    1f to String.format("%.1f", maxRatio)
                ),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}

// ── Tab 5: Exercises ───────────────────────────────────────────────────

@Composable
private fun ExercisesTab(state: DashboardUiState) {
    val prs = state.exercisePrs
    if (prs.isEmpty()) {
        EmptyMessage("No exercise data")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Rep range distribution
        item {
            Text("Rep Range Distribution", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val dist = state.repRangeDistribution
            val total = dist.values.sum().toFloat().coerceAtLeast(1f)
            val repColors = mapOf(
                "1-5 Strength" to COL_RED,
                "5-8 Power" to COL_PURPLE,
                "8-12 Hypertrophy" to COL_BLUE,
                "12+ Endurance" to COL_GREEN
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((label, count) in dist) {
                    val pct = (count / total * 100).toInt()
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(
                                    repColors[label] ?: COL_BLUE,
                                    MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$pct%", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(label.replace(" ", "\n"), fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // PR table
        item {
            Spacer(Modifier.height(16.dp))
            Text("Personal Records", style = MaterialTheme.typography.titleMedium)
        }

        items(prs) { pr ->
            PrRow(pr)
        }
    }
}

@Composable
private fun PrRow(pr: ExercisePrSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pr.exerciseName, fontWeight = FontWeight.Bold)
                Text(pr.muscle, style = MaterialTheme.typography.labelSmall,
                    color = MUSCLE_COLORS[pr.muscle] ?: COL_BLUE)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Best: ${pr.bestWeight.toInt()}kg × ${pr.bestReps} = ${pr.bestScore.toInt()} " +
                        "(${pr.bestDate.format(DateTimeFormatter.ofPattern("dd MMM yy"))})",
                style = MaterialTheme.typography.bodySmall
            )
            if (pr.lastRepPr != null) {
                Text(
                    "⭐ Rep PR: ${pr.lastRepPr.weightKg.toInt()}kg × ${pr.lastRepPr.reps} " +
                            "(${pr.lastRepPr.date.format(DateTimeFormatter.ofPattern("dd MMM yy"))})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (pr.lastWeightPr != null) {
                Text(
                    "💪 Weight PR: ${pr.lastWeightPr.weightKg.toInt()}kg × ${pr.lastWeightPr.reps} " +
                            "(${pr.lastWeightPr.date.format(DateTimeFormatter.ofPattern("dd MMM yy"))})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ── Tab 6: Forecast ────────────────────────────────────────────────────

@Composable
private fun ForecastTab(state: DashboardUiState) {
    val forecasts = state.forecasts
    if (forecasts.isEmpty()) {
        EmptyMessage("Not enough data for forecasts")
        return
    }

    val progressing = forecasts.count { it.status == "Progressing" }
    val plateau = forecasts.count { it.status == "Plateau" }
    val declining = forecasts.count { it.status == "Declining" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("📈 Progressing", "$progressing", Modifier.weight(1f), COL_GREEN)
                SummaryCard("⏸ Plateau", "$plateau", Modifier.weight(1f), COL_ORANGE)
                SummaryCard("📉 Declining", "$declining", Modifier.weight(1f), COL_RED)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Recent 3 Months vs Prior", style = MaterialTheme.typography.titleMedium)
        }

        items(forecasts) { f ->
            ForecastRow(f)
        }
    }
}

@Composable
private fun ForecastRow(f: ExerciseForecast) {
    val statusColor = when (f.status) {
        "Progressing" -> COL_GREEN
        "Declining" -> COL_RED
        else -> COL_ORANGE
    }
    val icon = when (f.status) {
        "Progressing" -> "📈"
        "Declining" -> "📉"
        else -> "⏸"
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(f.exerciseName, fontWeight = FontWeight.Bold)
                Text(
                    "$icon ${f.earlierMed.toInt()} → ${f.recentMed.toInt()} (${String.format("%+.0f", f.changePct)}%)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "6mo projection: ${f.recentMed.toInt()} → ${f.projectedMed.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .background(statusColor.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(f.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ── Shared components ──────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(modifier) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accentColor)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyMessage(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
