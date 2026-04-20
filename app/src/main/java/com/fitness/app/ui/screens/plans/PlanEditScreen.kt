package com.fitness.app.ui.screens.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.data.db.dao.PlanDayWithExercises
import com.fitness.app.data.db.dao.PlannedExerciseWithExercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(
    planId: Long,
    onBack: () -> Unit,
    viewModel: PlanEditViewModel = hiltViewModel()
) {
    LaunchedEffect(planId) { viewModel.load(planId) }
    val state by viewModel.state.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit ${state.plan?.plan?.name ?: "Plan"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val plan = state.plan ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(plan.days.sortedBy { it.day.dayIndex }, key = { it.day.id }) { day ->
                DayEditCard(day = day, viewModel = viewModel)
            }
        }
    }

    state.picker?.let { picker ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = viewModel::closePicker
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Add exercise", style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(allExercises, key = { it.id }) { ex ->
                        ListItem(
                            headlineContent = { Text(ex.name) },
                            supportingContent = { Text("${ex.primaryMuscle} · ${ex.equipment}") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable { viewModel.addExercise(ex.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEditCard(
    day: PlanDayWithExercises,
    viewModel: PlanEditViewModel
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.day.name, style = MaterialTheme.typography.titleLarge)
            val sorted = day.exercises.sortedBy { it.planned.orderIdx }
            sorted.forEachIndexed { idx, pwe ->
                PlannedExerciseRow(
                    pwe = pwe,
                    isFirst = idx == 0,
                    isLast = idx == sorted.lastIndex,
                    onUp = { viewModel.moveUp(day.day.id, pwe.planned.id) },
                    onDown = { viewModel.moveDown(day.day.id, pwe.planned.id) },
                    onRemove = { viewModel.removeExercise(pwe.planned.id) },
                    onSetsChange = { v -> viewModel.updatePlanned(pwe.planned.id, targetSets = v) },
                    onRepLowChange = { v -> viewModel.updatePlanned(pwe.planned.id, repLow = v) },
                    onRepHighChange = { v -> viewModel.updatePlanned(pwe.planned.id, repHigh = v) },
                    onRestChange = { v -> viewModel.updatePlanned(pwe.planned.id, restSec = v) }
                )
            }
            OutlinedButton(onClick = { viewModel.openPicker(day.day.id) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Add exercise")
            }
        }
    }
}

@Composable
private fun PlannedExerciseRow(
    pwe: PlannedExerciseWithExercise,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepLowChange: (Int) -> Unit,
    onRepHighChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(pwe.exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onUp, enabled = !isFirst) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(onClick = onDown, enabled = !isLast) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("Sets", pwe.planned.targetSets, onSetsChange, Modifier.weight(1f))
            NumberField("Rep↓", pwe.planned.repLow, onRepLowChange, Modifier.weight(1f))
            NumberField("Rep↑", pwe.planned.repHigh, onRepHighChange, Modifier.weight(1f))
            NumberField("Rest", pwe.planned.restSec, onRestChange, Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { v -> v.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
