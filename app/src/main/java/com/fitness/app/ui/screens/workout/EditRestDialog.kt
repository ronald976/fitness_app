package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun EditRestDialog(
    sheet: EditRestSheetState,
    onDismiss: () -> Unit,
    onConfirm: (seconds: Int, alsoUpdatePlan: Boolean) -> Unit
) {
    var text by remember(sheet.currentRestSec) {
        mutableStateOf(sheet.currentRestSec.toString())
    }
    var alsoUpdatePlan by remember { mutableStateOf(false) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest interval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Seconds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = sheet.hasPlannedExercise) {
                            alsoUpdatePlan = !alsoUpdatePlan
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alsoUpdatePlan,
                        onCheckedChange = { alsoUpdatePlan = it },
                        enabled = sheet.hasPlannedExercise
                    )
                    Column {
                        Text(
                            "Apply to plan",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sheet.hasPlannedExercise)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!sheet.hasPlannedExercise) {
                            Text(
                                "Exercise has no plan entry to update",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it, alsoUpdatePlan) } },
                enabled = parsed != null && parsed in 5..600
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
