package com.fitness.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fitness.app.domain.usecase.OutlierPrCandidate
import java.time.format.DateTimeFormatter

@Composable
fun OutlierReviewDialog(
    candidates: List<OutlierPrCandidate>,
    onResolve: (setId: Long, exclude: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Review possible logging mistakes") },
        text = {
            if (candidates.isEmpty()) {
                Text("Nothing to review — your PRs all look plausible.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(candidates, key = { it.setId }) { c ->
                        OutlierRow(c, onResolve)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun OutlierRow(c: OutlierPrCandidate, onResolve: (Long, Boolean) -> Unit) {
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM yy")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(c.exerciseName, fontWeight = FontWeight.SemiBold)
            Text(
                "${c.weightKg.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }} kg × ${c.reps} · ${c.date.format(dateFmt)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                c.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onResolve(c.setId, false) }) { Text("Keep") }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = { onResolve(c.setId, true) }) { Text("Exclude") }
            }
        }
    }
}
