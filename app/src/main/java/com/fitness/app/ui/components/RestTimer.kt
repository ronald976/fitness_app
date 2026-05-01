package com.fitness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RestTimer(
    totalSeconds: Int,
    restKey: Int = 0,
    onDismiss: () -> Unit,
    onSetRemaining: (newTotalSec: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingMs by remember(totalSeconds, restKey) { mutableLongStateOf(totalSeconds * 1000L) }

    LaunchedEffect(totalSeconds, restKey) {
        val endAt = System.currentTimeMillis() + totalSeconds * 1000L
        while (true) {
            val left = endAt - System.currentTimeMillis()
            remainingMs = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(200)
        }
    }

    val secondsLeft = (remainingMs / 1000).toInt()
    val progress = (remainingMs.toFloat() / (totalSeconds * 1000f)).coerceIn(0f, 1f)

    val adjust = { delta: Int ->
        val newRemaining = (secondsLeft + delta).coerceIn(5, 600)
        onSetRemaining(newRemaining)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rest: ${secondsLeft}s",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { adjust(-10) },
                        label = { Text("−10s") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    AssistChip(
                        onClick = { adjust(10) },
                        label = { Text("+10s") },
                        modifier = Modifier.padding(start = 6.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp).padding(start = 4.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss timer")
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
