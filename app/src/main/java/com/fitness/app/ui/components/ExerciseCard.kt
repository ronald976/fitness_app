package com.fitness.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseCard(
    title: String,
    subtitle: String,
    suggestionNote: String?,
    prText: String? = null,
    isCurrent: Boolean = false,
    onSwap: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onJumpToCurrent: (() -> Unit)? = null,
    onAddSet: (() -> Unit)? = null,
    onEditRest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (isCurrent) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "Now",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
                                )
                            }
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.labelLarge)
                    if (!prText.isNullOrBlank()) {
                        Text(
                            text = prText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onMoveUp != null) {
                        // Single tap = move up one slot. Double tap = jump straight to
                        // the "Now" exercise's slot, so users don't have to mash up 8x.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = onMoveUp,
                                    onDoubleClick = onJumpToCurrent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                "Move up (double-tap to jump to Now)",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, "Move down",
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    if (onEditRest != null) {
                        IconButton(onClick = onEditRest, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Timer, "Edit rest interval",
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    TextButton(onClick = onSwap) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Text(" Swap")
                    }
                }
            }
            if (!suggestionNote.isNullOrBlank()) {
                Text(
                    text = suggestionNote,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
            if (onAddSet != null) {
                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Add set")
                }
            }
        }
    }
}
