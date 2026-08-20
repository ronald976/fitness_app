package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

/**
 * Power-user shortcut moved out of the always-visible card to keep the workout
 * screen uncluttered. `<weight>x<reps>[f]` tokens, space-separated, fed into
 * [ActiveWorkoutViewModel.quickParse]. Within a multi-token line the weight may be omitted
 * ("x8") to reuse the previous token's weight, else this exercise's last-used weight.
 * A single "x4" is the fast path: 4 sets ticked off with no numbers at all.
 */
@Composable
fun QuickLogDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onSubmit(text)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick log") },
        text = {
            Column {
                Text(
                    "80x8 x8f 80x7  ·  f = to failure  ·  x8 mid-line = reps only (reuses last weight)\n" +
                        "Just \"x4\" on its own = 4 sets done, no weight or reps recorded."
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Sets") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit) { Text("Log all") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
