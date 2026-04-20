package com.fitness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun NumberStepper(
    value: String,
    onValueChange: (String) -> Unit,
    step: Double = 2.5,
    keyboard: KeyboardType = KeyboardType.Decimal,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(onClick = { onValueChange(bump(value, -step, keyboard)) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = label?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.width(112.dp)
        )
        FilledTonalIconButton(onClick = { onValueChange(bump(value, step, keyboard)) }) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

private fun bump(value: String, delta: Double, keyboard: KeyboardType): String {
    return if (keyboard == KeyboardType.Number) {
        val current = value.toIntOrNull() ?: 0
        (current + delta.toInt()).coerceAtLeast(0).toString()
    } else {
        val current = value.toDoubleOrNull() ?: 0.0
        val next = (current + delta).coerceAtLeast(0.0)
        if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
    }
}
