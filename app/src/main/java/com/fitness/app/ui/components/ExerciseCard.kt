package com.fitness.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.app.ui.theme.LocalFitnessColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseCard(
    title: String,
    subtitle: String,
    suggestionNote: String?,
    prText: String? = null,
    lastSummary: String? = null,
    isCurrent: Boolean = false,
    isPaired: Boolean = false,
    pairedWithPrevious: Boolean = false,
    pairedWithNext: Boolean = false,
    onSwap: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onJumpToCurrent: (() -> Unit)? = null,
    onPair: (() -> Unit)? = null,
    onUnpair: (() -> Unit)? = null,
    onAddSet: (() -> Unit)? = null,
    onEditRest: (() -> Unit)? = null,
    onQuickLog: (() -> Unit)? = null,
    onAdjustPr: (() -> Unit)? = null,
    onPushNext: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = LocalFitnessColors.current
    val topRadius = if (pairedWithPrevious) 0.dp else 18.dp
    val bottomRadius = if (pairedWithNext) 0.dp else 18.dp
    val shape = RoundedCornerShape(
        topStart = topRadius, topEnd = topRadius,
        bottomStart = bottomRadius, bottomEnd = bottomRadius
    )
    val borderColor = when {
        isPaired -> c.accent
        isCurrent -> c.accent.copy(alpha = 0.4f)
        else -> c.line
    }
    val borderWidth = if (isPaired || isCurrent) 1.5.dp else 1.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(borderWidth, borderColor, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 19.sp),
                        color = c.fg,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.accent)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "NOW",
                                color = c.onAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.fgDim
                )
                if (!prText.isNullOrBlank()) {
                    Text(
                        text = prText,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = if (onAdjustPr != null) {
                            Modifier.clickable(onClick = onAdjustPr)
                        } else Modifier
                    )
                }
                if (!lastSummary.isNullOrBlank()) {
                    Text(
                        text = lastSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.fgDim
                    )
                }
            }
            ActionMenu(
                onSwap = onSwap,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onJumpToCurrent = onJumpToCurrent,
                onPair = onPair,
                onUnpair = onUnpair,
                onEditRest = onEditRest,
                onQuickLog = onQuickLog,
                onAdjustPr = onAdjustPr,
                onPushNext = onPushNext,
                onRemove = onRemove,
                isPaired = isPaired
            )
        }
        if (!suggestionNote.isNullOrBlank()) {
            Text(
                text = suggestionNote,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
                color = c.accent,
                fontWeight = FontWeight.SemiBold
            )
        }
        content()
        if (onAddSet != null) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.surface2)
                    .clickable(onClick = onAddSet)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = c.fg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add set", color = c.fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionMenu(
    onSwap: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onJumpToCurrent: (() -> Unit)?,
    onPair: (() -> Unit)?,
    onUnpair: (() -> Unit)?,
    onEditRest: (() -> Unit)?,
    onQuickLog: (() -> Unit)?,
    onAdjustPr: (() -> Unit)?,
    onPushNext: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    isPaired: Boolean
) {
    val c = LocalFitnessColors.current
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onMoveUp != null) {
            // Single tap = move up; double tap = jump to "Now" — preserves the
            // existing power-user shortcut so users don't have to mash up 8x.
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
                    contentDescription = "Move up",
                    tint = c.fgDim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (onMoveDown != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoveDown),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = c.fgDim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c.surface2)
                    .clickable { open = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "More",
                    tint = c.fg,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Change exercise…") },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onClick = { open = false; onSwap() }
                )
                if (onEditRest != null) {
                    DropdownMenuItem(
                        text = { Text("Edit rest") },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        onClick = { open = false; onEditRest() }
                    )
                }
                if (onQuickLog != null) {
                    DropdownMenuItem(
                        text = { Text("Quick log…") },
                        leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null) },
                        onClick = { open = false; onQuickLog() }
                    )
                }
                if (isPaired && onUnpair != null) {
                    DropdownMenuItem(
                        text = { Text("Unpair superset") },
                        leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null) },
                        onClick = { open = false; onUnpair() }
                    )
                } else if (!isPaired && onPair != null) {
                    DropdownMenuItem(
                        text = { Text("Pair as superset") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        onClick = { open = false; onPair() }
                    )
                }
                if (onAdjustPr != null) {
                    DropdownMenuItem(
                        text = { Text("Adjust PR…") },
                        leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                        onClick = { open = false; onAdjustPr() }
                    )
                }
                if (onPushNext != null) {
                    DropdownMenuItem(
                        text = { Text("Push to next session") },
                        leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                        onClick = { open = false; onPushNext() }
                    )
                }
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { open = false; onRemove() }
                    )
                }
            }
        }
    }
}
