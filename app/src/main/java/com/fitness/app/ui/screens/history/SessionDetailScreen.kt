package com.fitness.app.ui.screens.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.data.db.dao.SessionExerciseWithSets
import com.fitness.app.data.db.entities.SetLogEntity
import com.fitness.app.ui.screens.workout.ExercisePicker
import com.fitness.app.ui.util.formatRestGap
import com.fitness.app.ui.util.formatSessionDuration
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val sws by viewModel.session.collectAsState()
    val editingExerciseId by viewModel.editingExerciseId.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val newSets by viewModel.newSets.collectAsState()
    val addSheet by viewModel.addExerciseSheet.collectAsState()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)

    BackHandler(enabled = editingExerciseId != null) {
        viewModel.cancelEditing()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sws?.session?.sessionType ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val data = sws
        if (data == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            ) {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    df.format(Date(data.session.startedAt)),
                    style = MaterialTheme.typography.titleLarge
                )
                val totalSets = data.exercises.sumOf { it.sets.size }
                val durationStr = data.session.completedAt
                    ?.let { formatSessionDuration(it - data.session.startedAt) }
                Text(
                    text = buildString {
                        append("${data.exercises.size} exercises · $totalSets sets")
                        if (durationStr != null) append(" · $durationStr")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (data.session.notes.isNotBlank()) {
                    Text(
                        data.session.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            items(data.exercises, key = { it.sessionExercise.id }) { ex ->
                val isEditing = editingExerciseId == ex.sessionExercise.id
                val anyOtherEditing = editingExerciseId != null && !isEditing
                ExerciseDetailCard(
                    ex = ex,
                    isEditing = isEditing,
                    editEnabled = !anyOtherEditing,
                    drafts = drafts,
                    deleted = deleted,
                    newSets = if (isEditing) newSets else emptyList(),
                    onStartEditing = { viewModel.startEditing(ex.sessionExercise.id) },
                    onCancel = viewModel::cancelEditing,
                    onSave = viewModel::saveExerciseEdits,
                    onEditDraft = { id, w, r, n -> viewModel.setDraft(id, w, r, n) },
                    onToggleDelete = { id -> viewModel.toggleDeleted(id) },
                    onAddSet = viewModel::addNewSet,
                    onEditNewSet = { tempId, w, r, n ->
                        viewModel.setNewSetDraft(tempId, w, r, n)
                    },
                    onRemoveNewSet = { tempId -> viewModel.removeNewSet(tempId) }
                )
            }

            item {
                FilledTonalButton(
                    onClick = viewModel::openAddExercise,
                    enabled = editingExerciseId == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add exercise")
                }
            }
        }

        addSheet?.let { sheet ->
            ModalBottomSheet(onDismissRequest = viewModel::closeAddExercise) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Add exercise", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Search the catalog or type a new name to create a custom exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ExercisePicker(
                        all = sheet.allExercises,
                        onPick = { viewModel.confirmAddExercise(it.id) },
                        onCreate = { name -> viewModel.confirmAddNewExercise(name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    ex: SessionExerciseWithSets,
    isEditing: Boolean,
    editEnabled: Boolean,
    drafts: Map<Long, SetDraft>,
    deleted: Set<Long>,
    newSets: List<NewSetDraft>,
    onStartEditing: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onEditDraft: (Long, String?, String?, String?) -> Unit,
    onToggleDelete: (Long) -> Unit,
    onAddSet: () -> Unit,
    onEditNewSet: (Long, String?, String?, String?) -> Unit,
    onRemoveNewSet: (Long) -> Unit
) {
    val displayName = ex.sessionExercise.customLabel ?: ex.exercise.name
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (!isEditing) {
                    IconButton(
                        onClick = onStartEditing,
                        enabled = editEnabled,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit exercise",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (ex.sets.isEmpty() && !isEditing) {
                Text(
                    "No individual sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
                val sortedSets = ex.sets.sortedBy { it.setIndex }
                sortedSets.forEachIndexed { i, set ->
                    if (isEditing) {
                        EditableExistingSetRow(
                            set = set,
                            draft = drafts[set.id],
                            isDeleted = set.id in deleted,
                            onEdit = onEditDraft,
                            onToggleDelete = onToggleDelete
                        )
                    } else {
                        if (i > 0) {
                            val gapMs = (set.completedAt - sortedSets[i - 1].completedAt)
                                .coerceAtLeast(0L)
                            // Imported sets share session.startedAt, so gaps are 0 for them.
                            if (gapMs >= 1000L) {
                                Text(
                                    text = "rested ${formatRestGap(gapMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 12.dp, top = 2.dp, bottom = 2.dp
                                    )
                                )
                            }
                        }
                        ReadOnlySetRow(set = set)
                    }
                }
                if (isEditing) {
                    newSets.forEach { ns ->
                        EditableNewSetRow(
                            newSet = ns,
                            onEdit = onEditNewSet,
                            onRemove = onRemoveNewSet
                        )
                    }
                }
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add set")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onSave) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text(" Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlySetRow(set: SetLogEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val display = when {
            // Quick-shorthand placeholder ("Abs x3" style): no measured weight or reps.
            set.weightKg <= 0 && set.reps == 0 -> "✓ Completed"
            set.weightKg > 0 -> "${formatKg(set.weightKg)}kg × ${set.reps}"
            else -> "BW × ${set.reps}"
        }
        Text(
            display,
            style = MaterialTheme.typography.bodyLarge
        )
        if (set.note.isNotBlank()) {
            Text(
                set.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditableExistingSetRow(
    set: SetLogEntity,
    draft: SetDraft?,
    isDeleted: Boolean,
    onEdit: (Long, String?, String?, String?) -> Unit,
    onToggleDelete: (Long) -> Unit
) {
    val weightText = draft?.weight ?: formatKg(set.weightKg)
    val repsText = draft?.reps ?: set.reps.toString()
    val noteText = draft?.note ?: set.note

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDeleted) {
                Text(
                    text = "${formatKg(set.weightKg)}kg × ${set.reps}",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = TextDecoration.LineThrough
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onToggleDelete(set.id) }) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo delete")
                }
            } else {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { onEdit(set.id, it, null, null) },
                    label = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { onEdit(set.id, null, it, null) },
                    label = { Text("reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onToggleDelete(set.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete set")
                }
            }
        }
        if (!isDeleted) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { onEdit(set.id, null, null, it) },
                label = { Text("note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EditableNewSetRow(
    newSet: NewSetDraft,
    onEdit: (Long, String?, String?, String?) -> Unit,
    onRemove: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "new",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = newSet.weight,
                onValueChange = { onEdit(newSet.tempId, it, null, null) },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = newSet.reps,
                onValueChange = { onEdit(newSet.tempId, null, it, null) },
                label = { Text("reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onRemove(newSet.tempId) }) {
                Icon(Icons.Default.Close, contentDescription = "Remove new set")
            }
        }
        OutlinedTextField(
            value = newSet.note,
            onValueChange = { onEdit(newSet.tempId, null, null, it) },
            label = { Text("note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

private fun formatKg(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
