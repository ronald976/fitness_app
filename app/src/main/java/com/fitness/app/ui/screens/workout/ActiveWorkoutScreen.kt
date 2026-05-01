package com.fitness.app.ui.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.timer.RestTimerService
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
    val context = LocalContext.current

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* result ignored — fall back to in-app timer if denied */ }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.restKey, state.restSeconds) {
        val secs = state.restSeconds
        if (secs != null && state.restKey > 0) {
            RestTimerService.start(context, secs)
        } else {
            RestTimerService.stop(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose { RestTimerService.stop(context) }
    }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = !state.finished) { showLeaveConfirm = true }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Cancel workout?") },
            text = {
                Text("This permanently deletes the in-progress session and " +
                    "any sets you've logged. Tap \"Finish workout\" instead " +
                    "to keep it in your history.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.cancelSession()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Stay") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Workout") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            state.restSeconds?.let { seconds ->
                RestTimer(
                    totalSeconds = seconds,
                    restKey = state.restKey,
                    onDismiss = viewModel::dismissRest,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    state.exercises,
                    key = { it.sessionExerciseId }
                ) { ex ->
                    val exIndex = state.exercises.indexOf(ex)
                    ExerciseCard(
                        title = ex.exerciseName,
                        subtitle = "${ex.targetSets} × ${ex.repLow}–${ex.repHigh}  ·  rest ${ex.restSec}s",
                        suggestionNote = ex.suggestionNote,
                        prText = ex.prText,
                        onSwap = { viewModel.openSwap(ex.sessionExerciseId) },
                        onMoveUp = if (exIndex > 0) {
                            { viewModel.moveExercise(ex.sessionExerciseId, -1) }
                        } else null,
                        onMoveDown = if (exIndex < state.exercises.lastIndex) {
                            { viewModel.moveExercise(ex.sessionExerciseId, 1) }
                        } else null,
                        onAddSet = { viewModel.addSet(ex.sessionExerciseId) },
                        onEditRest = { viewModel.openEditRest(ex.sessionExerciseId) }
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            // Quick-text entry
                            var quickText by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = quickText,
                                onValueChange = { quickText = it },
                                label = { Text("Quick: 80x8 80x8f 80x7") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    if (quickText.isNotBlank()) {
                                        viewModel.quickParse(ex.sessionExerciseId, quickText)
                                        quickText = ""
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )

                            ex.sets.forEach { row ->
                                SetRow(
                                    index = row.index,
                                    weight = row.input.weightKg,
                                    reps = row.input.reps,
                                    note = row.input.note,
                                    onWeightChange = { w ->
                                        viewModel.updateInput(ex.sessionExerciseId, row.index, weight = w)
                                    },
                                    onRepsChange = { r ->
                                        viewModel.updateInput(ex.sessionExerciseId, row.index, reps = r)
                                    },
                                    onNoteChange = { n ->
                                        viewModel.updateInput(ex.sessionExerciseId, row.index, note = n)
                                    },
                                    onLog = { viewModel.logSet(ex.sessionExerciseId, row.index) },
                                    logged = row.logged
                                )
                            }

                            // Quick log all button
                            val hasUnlogged = ex.sets.any {
                                !it.logged &&
                                it.input.weightKg.toDoubleOrNull() != null &&
                                it.input.reps.toIntOrNull() != null
                            }
                            if (hasUnlogged) {
                                FilledTonalButton(
                                    onClick = { viewModel.quickLogAll(ex.sessionExerciseId) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null)
                                    Text(" Log all sets")
                                }
                            }
                        }
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = viewModel::openAddExercise,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Add exercise")
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
                onPick = { id, alsoUpdatePlan -> viewModel.confirmSwap(id, alsoUpdatePlan) },
                onCreate = { name, alsoUpdatePlan -> viewModel.confirmSwapNew(name, alsoUpdatePlan) }
            )
        }

        state.addSheet?.let { sheet ->
            AddExerciseSheet(
                sheet = sheet,
                onDismiss = viewModel::closeAddExercise,
                onPick = { id -> viewModel.confirmAddExercise(id) },
                onCreate = { name -> viewModel.confirmAddNewExercise(name) }
            )
        }

        state.editRestSheet?.let { sheet ->
            EditRestDialog(
                sheet = sheet,
                onDismiss = viewModel::closeEditRest,
                onConfirm = { secs, applyToPlan ->
                    viewModel.confirmEditRest(secs, applyToPlan)
                }
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
