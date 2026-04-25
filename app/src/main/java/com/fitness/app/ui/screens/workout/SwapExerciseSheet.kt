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
fun SwapExerciseSheet(
    sheet: SwapSheetState,
    onDismiss: () -> Unit,
    onPick: (exerciseId: Long, alsoUpdatePlan: Boolean) -> Unit,
    onCreate: (name: String, alsoUpdatePlan: Boolean) -> Unit
) {
    var alsoUpdatePlan by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Swap exercise", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick an alternative or search the catalog. Tick the box to also update the plan.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = alsoUpdatePlan,
                    onCheckedChange = { alsoUpdatePlan = it }
                )
                Text("Also update plan")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ExercisePicker(
                all = sheet.allExercises,
                recommended = sheet.alternatives,
                onPick = { onPick(it.id, alsoUpdatePlan) },
                onCreate = { name -> onCreate(name, alsoUpdatePlan) }
            )
        }
    }
}
