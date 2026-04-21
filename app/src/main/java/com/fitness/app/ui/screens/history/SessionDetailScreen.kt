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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.data.db.dao.SessionExerciseWithSets
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val sws by viewModel.session.collectAsState()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)

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
            // Session header
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

            // Each exercise
            items(data.exercises, key = { it.sessionExercise.id }) { ex ->
                ExerciseDetailCard(ex)
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(ex: SessionExerciseWithSets) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(ex.exercise.name, style = MaterialTheme.typography.titleMedium)
            if (ex.sets.isEmpty()) {
                Text(
                    "No individual sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
                ex.sets.sortedBy { it.setIndex }.forEachIndexed { _, set ->
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
            }
        }
    }
}

private fun formatKg(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
