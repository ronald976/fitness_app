package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Single sheet for the two structural edits a user can do to an exercise mid-workout:
 *  - swap to a different exercise (existing flow), or
 *  - remove it from this session.
 * The "Also update plan" toggle applies to whichever action they take.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeExerciseSheet(
    sheet: SwapSheetState,
    onDismiss: () -> Unit,
    onPick: (exerciseId: Long, alsoUpdatePlan: Boolean) -> Unit,
    onCreate: (name: String, alsoUpdatePlan: Boolean) -> Unit,
    onRemove: (alsoUpdatePlan: Boolean) -> Unit
) {
    var alsoUpdatePlan by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Change exercise", style = MaterialTheme.typography.titleLarge)
            Text(
                "Swap to a different exercise, or remove it from this workout. Tick the box " +
                    "to mirror the change in the underlying plan.",
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    .clickable { confirmingRemove = true }
                    .padding(vertical = 14.dp, horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Remove from this workout",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove exercise?") },
            text = {
                val planNote = if (alsoUpdatePlan)
                    " It will also be removed from the plan day."
                else ""
                Text(
                    "This drops the exercise and any sets you've logged for it in this " +
                        "session.$planNote"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemove = false
                    onRemove(alsoUpdatePlan)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemove = false }) { Text("Cancel") }
            }
        )
    }
}
