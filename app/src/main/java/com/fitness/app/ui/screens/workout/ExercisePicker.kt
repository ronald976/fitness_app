package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitness.app.data.db.entities.ExerciseEntity

/**
 * Type-ahead exercise selector.
 *  - Empty query: shows [recommended] above [all].
 *  - Non-empty query: filters [all] (case-insensitive substring), recommendations are hidden.
 *  - When a non-empty query has zero matches, surfaces a "Create '<query>'" row that calls
 *    [onCreate] so the user can free-input a brand new exercise.
 */
@Composable
fun ExercisePicker(
    all: List<ExerciseEntity>,
    recommended: List<ExerciseEntity> = emptyList(),
    onPick: (ExerciseEntity) -> Unit,
    onCreate: ((String) -> Unit)? = null,
    placeholder: String = "Search or type a new name"
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()

    val matches = if (q.isEmpty()) all
    else all.filter { it.name.contains(q, ignoreCase = true) }
        .sortedWith(
            compareByDescending<ExerciseEntity> { it.name.startsWith(q, ignoreCase = true) }
                .thenBy { it.name }
        )

    val recsToShow = if (q.isEmpty()) recommended else emptyList()
    val showCreate = onCreate != null && q.isNotEmpty() && matches.isEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (recsToShow.isNotEmpty()) {
                item {
                    Text(
                        "Recommended alternatives",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(recsToShow, key = { "rec-${it.id}" }) { ex ->
                    ExerciseRow(ex) { onPick(ex) }
                    HorizontalDivider()
                }
                item {
                    Text(
                        "All exercises",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
            }

            items(matches, key = { "all-${it.id}" }) { ex ->
                ExerciseRow(ex) { onPick(ex) }
                HorizontalDivider()
            }

            if (showCreate) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCreate?.invoke(q) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            "+ Create \"$q\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Adds a custom exercise",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(ex: ExerciseEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(ex.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${ex.primaryMuscle} · ${ex.equipment}",
            style = MaterialTheme.typography.labelMedium
        )
    }
}
