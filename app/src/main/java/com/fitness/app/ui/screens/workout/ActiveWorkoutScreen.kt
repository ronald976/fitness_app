package com.fitness.app.ui.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.timer.RestTimerService
import com.fitness.app.ui.components.ExerciseCard
import com.fitness.app.ui.components.RestTimer
import com.fitness.app.ui.components.SetRow
import com.fitness.app.ui.theme.LocalFitnessColors
import kotlinx.coroutines.delay

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
    val c = LocalFitnessColors.current

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
    var pendingRemove by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var pendingRemoveExercise by remember { mutableStateOf<Long?>(null) }
    var quickLogTarget by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = !state.finished) { showLeaveConfirm = true }

    val currentExerciseId = state.exercises
        .firstOrNull { ex -> ex.sets.any { !it.logged } }
        ?.sessionExerciseId
        ?: state.exercises.lastOrNull()?.sessionExerciseId

    val totalSets = state.exercises.sumOf { it.targetSets }
    val loggedSets = state.exercises.sumOf { it.sets.count { row -> row.logged } }
    val currentExIndex = state.exercises.indexOfFirst { it.sessionExerciseId == currentExerciseId }
        .let { if (it < 0) 0 else it + 1 }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        ProgressStrip(
            loggedSets = loggedSets,
            totalSets = totalSets,
            currentExIndex = currentExIndex,
            totalExercises = state.exercises.size
        )
        state.restSeconds?.let { seconds ->
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                RestTimer(
                    totalSeconds = seconds,
                    restKey = state.restKey,
                    onDismiss = viewModel::dismissRest,
                    onSetRemaining = viewModel::setRestSeconds
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp
            )
        ) {
            items(state.exercises, key = { it.sessionExerciseId }) { ex ->
                val exIndex = state.exercises.indexOf(ex)
                val prev = state.exercises.getOrNull(exIndex - 1)
                val next = state.exercises.getOrNull(exIndex + 1)
                val pairedWithPrev = ex.supersetGroupId != null &&
                    prev?.supersetGroupId == ex.supersetGroupId
                val pairedWithNext = ex.supersetGroupId != null &&
                    next?.supersetGroupId == ex.supersetGroupId
                if (exIndex > 0 && !pairedWithPrev) Spacer(Modifier.height(12.dp))
                ExerciseCard(
                    title = ex.exerciseName,
                    subtitle = "${ex.targetSets} × ${ex.repLow}–${ex.repHigh}  ·  rest ${ex.restSec}s",
                    suggestionNote = ex.suggestionNote,
                    prText = ex.prText,
                    lastSummary = ex.lastSummary,
                    isCurrent = ex.sessionExerciseId == currentExerciseId,
                    isPaired = ex.supersetGroupId != null,
                    pairedWithPrevious = pairedWithPrev,
                    pairedWithNext = pairedWithNext,
                    onSwap = { viewModel.openSwap(ex.sessionExerciseId) },
                    onMoveUp = if (exIndex > 0) {
                        { viewModel.moveExercise(ex.sessionExerciseId, -1) }
                    } else null,
                    onMoveDown = if (exIndex < state.exercises.lastIndex) {
                        { viewModel.moveExercise(ex.sessionExerciseId, 1) }
                    } else null,
                    onJumpToCurrent = { viewModel.jumpExerciseToCurrent(ex.sessionExerciseId) },
                    onPair = { viewModel.openPair(ex.sessionExerciseId) },
                    onUnpair = { viewModel.unpair(ex.sessionExerciseId) },
                    onAddSet = { viewModel.addSet(ex.sessionExerciseId) },
                    onEditRest = { viewModel.openEditRest(ex.sessionExerciseId) },
                    onQuickLog = { quickLogTarget = ex.sessionExerciseId },
                    onAdjustPr = { viewModel.openAdjustPr(ex.sessionExerciseId) },
                    onRemove = { pendingRemoveExercise = ex.sessionExerciseId }
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        ex.sets.forEachIndexed { displayIdx, row ->
                            SetRow(
                                index = displayIdx,
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
                                onRemove = {
                                    if (row.setLogId != null) {
                                        pendingRemove = ex.sessionExerciseId to row.index
                                    } else {
                                        viewModel.removeSet(ex.sessionExerciseId, row.index)
                                    }
                                },
                                onEdit = { viewModel.editSet(ex.sessionExerciseId, row.index) },
                                logged = row.logged,
                                canRemove = ex.sets.size > 1
                            )
                        }

                        val hasUnlogged = ex.sets.any {
                            !it.logged &&
                            it.input.weightKg.toDoubleOrNull() != null &&
                            it.input.reps.toIntOrNull() != null
                        }
                        if (hasUnlogged) {
                            QuickLogAllButton(
                                onClick = { viewModel.quickLogAll(ex.sessionExerciseId) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    var quickAdd by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = quickAdd,
                        onValueChange = { quickAdd = it },
                        label = { Text("Quick-add: abs x3, leg press 200x10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (quickAdd.isNotBlank()) {
                                viewModel.quickAddExercise(quickAdd)
                                quickAdd = ""
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface2)
                            .clickable(onClick = viewModel::openAddExercise)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = c.fg, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add exercise", color = c.fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        WorkoutHeader(
            startedAt = state.sessionStartedAt,
            onClose = { showLeaveConfirm = true },
            onFinish = viewModel::finishWorkout
        )
    }

    state.swapSheet?.let { sheet ->
        ChangeExerciseSheet(
            sheet = sheet,
            onDismiss = viewModel::closeSwap,
            onPick = { id, alsoUpdatePlan -> viewModel.confirmSwap(id, alsoUpdatePlan) },
            onCreate = { name, alsoUpdatePlan -> viewModel.confirmSwapNew(name, alsoUpdatePlan) },
            onRemove = { alsoUpdatePlan -> viewModel.confirmRemoveExercise(alsoUpdatePlan) }
        )
    }

    state.addSheet?.let { sheet ->
        AddExerciseSheet(
            sheet = sheet,
            canAddToPlan = sheet.canAddToPlan,
            onDismiss = viewModel::closeAddExercise,
            onPick = { id, alsoAddToPlan -> viewModel.confirmAddExercise(id, alsoAddToPlan) },
            onCreate = { name, alsoAddToPlan -> viewModel.confirmAddNewExercise(name, alsoAddToPlan) }
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

    state.pairSheet?.let { sheet ->
        PairExerciseSheet(
            sheet = sheet,
            onDismiss = viewModel::closePair,
            onPick = { partnerId -> viewModel.confirmPair(partnerId) }
        )
    }

    quickLogTarget?.let { sessionExId ->
        QuickLogDialog(
            onSubmit = { text -> viewModel.quickParse(sessionExId, text) },
            onDismiss = { quickLogTarget = null }
        )
    }

    pendingRemove?.let { (sessionExId, setIdx) ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove set?") },
            text = {
                Text("This deletes the logged data for this set and removes the row. " +
                    "The other sets aren't affected.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSet(sessionExId, setIdx)
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            }
        )
    }

    pendingRemoveExercise?.let { sessionExId ->
        val name = state.exercises
            .firstOrNull { it.sessionExerciseId == sessionExId }?.exerciseName ?: "exercise"
        AlertDialog(
            onDismissRequest = { pendingRemoveExercise = null },
            title = { Text("Remove $name?") },
            text = {
                Text("This drops the exercise and any sets you've logged for it in this " +
                    "session. The plan is not changed — it will be back next workout.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeExerciseFromSession(sessionExId)
                    pendingRemoveExercise = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveExercise = null }) { Text("Cancel") }
            }
        )
    }

    state.adjustPrSheet?.let { sheet ->
        AdjustPrDialog(
            sheet = sheet,
            onSetExcluded = { setId, exclude -> viewModel.setPrExcluded(setId, exclude) },
            onDismiss = viewModel::closeAdjustPr
        )
    }

    state.pr?.let { pr ->
        val (title, body) = when (pr.kind) {
            PrCelebration.Kind.REP ->
                "New rep PR!" to "${pr.exerciseName} · ${formatKgDisplay(pr.weightKg)} kg × ${pr.reps} reps (previous best ${pr.previousBestText})"
            PrCelebration.Kind.WEIGHT ->
                "New weight PR!" to "${pr.exerciseName} · ${formatKgDisplay(pr.weightKg)} kg × ${pr.reps} reps (previous best ${pr.previousBestText})"
            PrCelebration.Kind.VOLUME ->
                "New session PR!" to "${pr.exerciseName} · ${formatKgDisplay(pr.weightKg)} kg total over ${pr.reps} sets (previous best ${pr.previousBestText})"
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissPr,
            icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = c.accent) },
            title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPr) { Text("Nice!") }
            }
        )
    }
}

@Composable
private fun WorkoutHeader(
    startedAt: Long,
    onClose: () -> Unit,
    onFinish: () -> Unit
) {
    val c = LocalFitnessColors.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val durationLabel = if (startedAt > 0) {
        val elapsed = ((nowMs - startedAt) / 1000).toInt().coerceAtLeast(0)
        val h = elapsed / 3600
        val m = (elapsed % 3600) / 60
        val s = elapsed % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    } else "--:--"

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.line)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.dp, c.line, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = c.fg, modifier = Modifier.size(20.dp))
            }
            Text(
                durationLabel,
                modifier = Modifier.weight(1f),
                color = c.fg,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.fg)
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    "Finish",
                    color = c.bg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ProgressStrip(
    loggedSets: Int,
    totalSets: Int,
    currentExIndex: Int,
    totalExercises: Int
) {
    val c = LocalFitnessColors.current
    val progress = if (totalSets > 0) loggedSets.toFloat() / totalSets else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c.fgFaint)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.accent)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
            Text(
                "Exercise $currentExIndex of $totalExercises",
                style = MaterialTheme.typography.labelMedium,
                color = c.fgDim,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$loggedSets / $totalSets sets",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = c.fgDim,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuickLogAllButton(onClick: () -> Unit) {
    val c = LocalFitnessColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FlashOn, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Log all sets", color = c.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

private fun formatKgDisplay(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
