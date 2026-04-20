package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.ui.components.ExerciseCard
import com.fitness.app.ui.components.RestTimer
import com.fitness.app.ui.components.SetRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    sessionId: Long,
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Workout") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            state.restSeconds?.let { seconds ->
                RestTimer(
                    totalSeconds = seconds,
                    onDismiss = viewModel::dismissRest,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.exercises, key = { it.sessionExerciseId }) { ex ->
                    ExerciseCard(
                        title = ex.exerciseName,
                        subtitle = "${ex.targetSets} × ${ex.repLow}–${ex.repHigh}  ·  rest ${ex.restSec}s",
                        suggestionNote = ex.suggestionNote,
                        onSwap = { viewModel.openSwap(ex.sessionExerciseId) }
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            ex.sets.forEach { row ->
                                SetRow(
                                    index = row.index,
                                    weight = row.input.weightKg,
                                    reps = row.input.reps,
                                    onWeightChange = { w ->
                                        viewModel.updateInput(ex.sessionExerciseId, row.index, weight = w, reps = null)
                                    },
                                    onRepsChange = { r ->
                                        viewModel.updateInput(ex.sessionExerciseId, row.index, weight = null, reps = r)
                                    },
                                    onLog = { viewModel.logSet(ex.sessionExerciseId, row.index) },
                                    logged = row.logged
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::finishWorkout,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Text(" Finish workout")
            }
        }

        state.swapSheet?.let { sheet ->
            SwapExerciseSheet(
                sheet = sheet,
                onDismiss = viewModel::closeSwap,
                onPick = { id, alsoUpdatePlan -> viewModel.confirmSwap(id, alsoUpdatePlan) }
            )
        }

        state.pr?.let { pr ->
            val (title, body) = when (pr.kind) {
                PrCelebration.Kind.REP ->
                    "New rep PR!" to "${pr.exerciseName} · ${formatKgDisplay(pr.weightKg)} kg × ${pr.reps} reps (previous best ${pr.previousBestText})"
                PrCelebration.Kind.WEIGHT ->
                    "New weight PR!" to "${pr.exerciseName} · ${formatKgDisplay(pr.weightKg)} kg × ${pr.reps} reps (previous best ${pr.previousBestText})"
            }
            AlertDialog(
                onDismissRequest = viewModel::dismissPr,
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                text = { Text(body) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissPr) { Text("Nice!") }
                }
            )
        }
    }
}

private fun formatKgDisplay(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
