package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fitness.app.data.db.dao.PrCandidateSetRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lets the user fix a bogus PR: shows the top-scoring historical sets for the exercise
 * (best first), with the current record holder marked. Excluding a set hides it from the
 * PR badge, PR detection, and dashboard stats — the set itself stays in history and can
 * be restored here at any time.
 */
@Composable
fun AdjustPrDialog(
    sheet: AdjustPrSheetState,
    onSetExcluded: (setId: Long, exclude: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM yy")
    val zone = ZoneId.systemDefault()
    val currentPrId = sheet.candidates.firstOrNull { !it.excludeFromPr }?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust PR — ${sheet.exerciseName}") },
        text = {
            Column {
                Text(
                    "Excluded sets stop counting toward the PR badge, PR detection and " +
                        "charts, but stay in your history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (sheet.candidates.isEmpty()) {
                    Text("No logged history for this exercise yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sheet.candidates, key = { it.id }) { row ->
                            PrCandidateRow(
                                row = row,
                                isCurrentPr = row.id == currentPrId,
                                dateLabel = Instant.ofEpochMilli(row.completedAt)
                                    .atZone(zone).toLocalDate().format(dateFmt),
                                onSetExcluded = onSetExcluded
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun PrCandidateRow(
    row: PrCandidateSetRow,
    isCurrentPr: Boolean,
    dateLabel: String,
    onSetExcluded: (setId: Long, exclude: Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatKgValue(row.weightKg)} kg × ${row.reps}" +
                    if (isCurrentPr) "  🏆" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentPr) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (row.excludeFromPr) TextDecoration.LineThrough else null,
                color = if (row.excludeFromPr) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { onSetExcluded(row.id, !row.excludeFromPr) }) {
            Text(
                if (row.excludeFromPr) "Restore"
                else "Exclude",
                color = if (row.excludeFromPr) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatKgValue(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
