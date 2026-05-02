package com.fitness.app.ui.screens.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet shown when the user taps the link icon on an exercise card. Lists the
 * other unpaired exercises in the current session — picking one immediately pairs them
 * as a superset (rest after a set in either drops to 10s, and reorders move the pair
 * together).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairExerciseSheet(
    sheet: PairExerciseSheetState,
    onDismiss: () -> Unit,
    onPick: (partnerSessionExerciseId: Long) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Pair as superset", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick another exercise to pair with. Sets in a paired exercise rest 10s " +
                    "instead of the full interval, and reorders move both cards together.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (sheet.candidates.isEmpty()) {
                Text(
                    "No unpaired exercises available. Add another exercise first, or " +
                        "unpair an existing pair.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                sheet.candidates.forEach { c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(c.sessionExerciseId) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
