package com.fitness.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.app.ui.theme.LocalFitnessColors

/**
 * Row for one set.
 *
 * Logged sets render as a static "Set N · 89kg · 8rp" line with a green
 * checkmark — the data is the spectacle. Unlogged sets keep editable
 * weight/reps fields and a tappable circle to log them. The note button is
 * always available; tapping it opens a quick-note dialog.
 */
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
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    logged: Boolean,
    canRemove: Boolean = true,
    modifier: Modifier = Modifier
) {
    val c = LocalFitnessColors.current
    var editingNote by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val repsFocus = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status circle / log button
        if (logged) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c.success),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Logged",
                    tint = c.onAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            // Always tappable — bodyweight/cable/abs entries don't always need weight or
            // reps, and forcing a value typed before the circle accepts a tap is a friction
            // that the user explicitly wants gone. Empty inputs are stored as 0.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, c.fgFaint, CircleShape)
                    .clickable(onClick = onLog)
            )
        }

        Text(
            "Set ${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = c.fgDim,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp)
        )

        if (logged) {
            Spacer(Modifier.weight(1f))
            val w = weight.toDoubleOrNull() ?: 0.0
            val r = reps.toIntOrNull() ?: 0
            if (w == 0.0 && r == 0) {
                // Sets-only / bodyweight log — no numbers to show.
                Text(
                    "Done",
                    color = c.fg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        weight,
                        color = c.fg,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        " kg",
                        color = c.fgDim,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        reps,
                        color = c.fg,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        "rp",
                        color = c.fgDim,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // Editable inputs
            InlineNumericField(
                value = weight,
                placeholder = "kg",
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                onChange = onWeightChange,
                onImeAction = { repsFocus.requestFocus() },
                modifier = Modifier.width(78.dp)
            )
            InlineNumericField(
                value = reps,
                placeholder = "reps",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                onChange = onRepsChange,
                onImeAction = {
                    onLog()
                    focusManager.clearFocus()
                },
                modifier = Modifier
                    .width(64.dp)
                    .focusRequester(repsFocus)
            )
            Spacer(Modifier.weight(1f))
        }

        // Note button — always available
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { editingNote = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = "Set note",
                tint = if (note.isNotEmpty()) c.accent else c.fgDim,
                modifier = Modifier.size(18.dp)
            )
        }
        if (canRemove) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove set",
                    tint = c.fgDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (editingNote) {
        NoteDialog(
            index = index,
            currentNote = note,
            onSave = { onNoteChange(it); editingNote = false },
            onDismiss = { editingNote = false }
        )
    }
}

@Composable
private fun InlineNumericField(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onChange: (String) -> Unit,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFitnessColors.current
    var textValue by remember(value) {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.bg)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .onFocusChanged { focusState ->
                if (focusState.isFocused && textValue.text.isNotEmpty()) {
                    textValue = textValue.copy(selection = TextRange(0, textValue.text.length))
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onChange(it.text)
            },
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            textStyle = TextStyle(
                color = c.fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum"
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() }
            ),
            decorationBox = { inner ->
                if (textValue.text.isEmpty()) {
                    Text(placeholder, color = c.fgDim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                inner()
            }
        )
    }
}

private val QUICK_NOTES = listOf("to failure", "plus blowout", "paused", "tempo")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteDialog(
    index: Int,
    currentNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentNote) { mutableStateOf(currentNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set ${index + 1} note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QUICK_NOTES.forEach { qn ->
                        val selected = draft.contains(qn, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = if (selected) {
                                    draft.replace(qn, "", ignoreCase = true).trim()
                                        .replace(Regex("\\s*,\\s*,"), ",").trim(',', ' ')
                                } else {
                                    if (draft.isBlank()) qn else "$draft, $qn"
                                }
                            },
                            label = { Text(qn) }
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Custom note") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
