package com.fitness.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exerciseName.ifEmpty { "Exercise" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.totalSets == 0) {
            Box(Modifier.padding(padding)) {
                EmptyMessage("No logged sets for this exercise")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Best e1RM", "${state.bestE1rm.toInt()}kg", Modifier.weight(1f))
                    SummaryCard("Top Weight", "${state.maxWeight.toInt()}kg", Modifier.weight(1f))
                    SummaryCard("Sessions", "${state.sessionCount}", Modifier.weight(1f))
                    SummaryCard("Sets", "${state.totalSets}", Modifier.weight(1f))
                }
            }

            state.progression?.let { prog ->
                item { ProgressionCard(prog, index = 0) }
            }

            item {
                Text("Sets per Month", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                MonthlySetsChart(state.monthlySets)
            }

            item {
                Text("Rep Ranges", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                RepRangeDistribution(state.repRangeDistribution)
            }

            if (state.prHistory.isNotEmpty()) {
                item {
                    Text("PR History", style = MaterialTheme.typography.titleMedium)
                }
                items(state.prHistory) { pr ->
                    PrHistoryRow(pr)
                }
            }
        }
    }
}

@Composable
private fun MonthlySetsChart(monthly: List<Pair<String, Int>>) {
    if (monthly.isEmpty()) return
    val n = monthly.size
    val maxSets = monthly.maxOf { it.second }.coerceAtLeast(1)

    val bars = monthly.mapIndexed { i, (_, count) ->
        BarEntry((i + 0.5f) / n, count.toFloat() / maxSets, COL_CYAN, 0.8f / n)
    }
    val xLabels = monthly.mapIndexedNotNull { i, (label, _) ->
        if (i % 2 == 0) (i + 0.5f) / n to label else null
    }
    val yLabels = (0..4).map { i -> i / 4f to "${maxSets * i / 4}" }

    BarChart(
        bars = bars,
        xLabels = xLabels,
        yLabels = yLabels,
        modifier = Modifier.fillMaxWidth().height(180.dp)
    )
}

@Composable
private fun PrHistoryRow(pr: PrMarker) {
    // Colors match the dots on the progression chart above: gold = weight PR,
    // line color (blue at index 0) = rep PR.
    val label = if (pr.isRepPr) "Rep PR" else "Weight PR"
    val color = if (pr.isRepPr) COL_BLUE else COL_GOLD

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = color, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium)
                Text(
                    "${pr.weightKg.toInt()}kg × ${pr.reps} · e1RM ${pr.score.toInt()}kg",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                pr.date.format(DateTimeFormatter.ofPattern("dd MMM yy")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
