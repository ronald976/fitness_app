package com.fitness.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.data.db.dao.SessionExerciseWithSets
import com.fitness.app.data.db.entities.SetLogEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val sws by viewModel.session.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sws?.session?.sessionType ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editMode) viewModel.cancelEditing() else onBack()
                    }) {
                        Icon(
                            if (editMode) Icons.Default.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (editMode) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (editMode) {
                        val hasChanges = drafts.isNotEmpty() || deleted.isNotEmpty()
                        IconButton(
                            onClick = { viewModel.saveEdits() },
                            enabled = hasChanges
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save changes")
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit session")
                        }
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
                Text(
                    "${data.exercises.size} exercises · $totalSets sets",
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
                ExerciseDetailCard(
                    ex = ex,
                    editMode = editMode,
                    drafts = drafts,
                    deleted = deleted,
                    onEdit = { id, w, r, n -> viewModel.setDraft(id, w, r, n) },
                    onToggleDelete = { id -> viewModel.toggleDeleted(id) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    ex: SessionExerciseWithSets,
    editMode: Boolean,
    drafts: Map<Long, SetDraft>,
    deleted: Set<Long>,
    onEdit: (Long, String?, String?, String?) -> Unit,
    onToggleDelete: (Long) -> Unit
) {
    val displayName = ex.sessionExercise.customLabel ?: ex.exercise.name
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            if (ex.sets.isEmpty()) {
                Text(
                    "No individual sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
                ex.sets.sortedBy { it.setIndex }.forEach { set ->
                    if (editMode) {
                        EditableSetRow(
                            set = set,
                            draft = drafts[set.id],
                            isDeleted = set.id in deleted,
                            onEdit = onEdit,
                            onToggleDelete = onToggleDelete
                        )
                    } else {
                        ReadOnlySetRow(set = set)
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
        val weightStr = if (set.weightKg > 0) "${formatKg(set.weightKg)}kg" else "BW"
        Text(
            "$weightStr × ${set.reps}",
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
private fun EditableSetRow(
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

private fun formatKg(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
