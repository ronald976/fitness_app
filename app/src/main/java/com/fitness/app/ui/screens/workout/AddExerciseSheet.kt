package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(
    sheet: AddExerciseSheetState,
    onDismiss: () -> Unit,
    onPick: (exerciseId: Long) -> Unit,
    onCreate: (name: String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                onPick = { onPick(it.id) },
                onCreate = { name -> onCreate(name) }
            )
        }
    }
}
