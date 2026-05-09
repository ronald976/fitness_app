package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(
    sheet: AddExerciseSheetState,
    canAddToPlan: Boolean,
    onDismiss: () -> Unit,
    onPick: (exerciseId: Long, alsoAddToPlan: Boolean) -> Unit,
    onCreate: (name: String, alsoAddToPlan: Boolean) -> Unit
) {
    // Optional toggle so a one-tap pick still works; only shown when there's a plan day
    // to add to (ad-hoc sessions have nowhere to write).
    var alsoAddToPlan by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Add exercise", style = MaterialTheme.typography.titleLarge)
            Text(
                "Search the catalog or type a new name to create a custom exercise.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (canAddToPlan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alsoAddToPlan,
                        onCheckedChange = { alsoAddToPlan = it }
                    )
                    Text("Also add to plan")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ExercisePicker(
                all = sheet.allExercises,
                onPick = { onPick(it.id, alsoAddToPlan) },
                onCreate = { name -> onCreate(name, alsoAddToPlan) }
            )
        }
    }
}
