package com.fitness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun SetRow(
    index: Int,
    weight: String,
    reps: String,
    note: String,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onLog: () -> Unit,
    logged: Boolean,
    modifier: Modifier = Modifier
) {
    var editingNote by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(24.dp)
        )
        OutlinedTextField(
            value = weight,
            onValueChange = onWeightChange,
            label = { Text("kg") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(110.dp)
        )
        OutlinedTextField(
            value = reps,
            onValueChange = onRepsChange,
            label = { Text("reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(90.dp)
        )
        IconButton(onClick = { editingNote = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = "Set note",
                tint = if (note.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        }
        if (logged) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Logged",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            Button(onClick = onLog) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(" Log")
            }
        }
    }

    if (editingNote) {
        var draft by remember(note) { mutableStateOf(note) }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text("Set ${index + 1} note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("e.g. to failure, + blowout") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onNoteChange(draft)
                    editingNote = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = false }) { Text("Cancel") }
            }
        )
    }
}
