package com.fitness.app.ui.screens.history

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.LocalBottomBarPadding
import com.fitness.app.ui.screens.workout.ExercisePicker
import com.fitness.app.ui.theme.LocalFitnessColors
import com.fitness.app.ui.theme.TilePalette
import com.fitness.app.ui.util.formatSessionDuration
import java.io.File
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val filterExerciseId by viewModel.filterExerciseId.collectAsState()
    val historyExercises by viewModel.historyExercises.collectAsState()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val context = LocalContext.current
    val c = LocalFitnessColors.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val inSelectionMode = selected.isNotEmpty()
    val filterExercise = remember(filterExerciseId, historyExercises) {
        filterExerciseId?.let { id -> historyExercises.firstOrNull { it.id == id } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (inSelectionMode) "${selected.size} selected" else "History",
                style = MaterialTheme.typography.displayLarge,
                color = c.fg,
                modifier = Modifier.weight(1f)
            )
            if (inSelectionMode) {
                CircleIconButton(onClick = viewModel::clearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = c.fg)
                }
                Spacer(Modifier.size(8.dp))
                CircleIconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = c.accent)
                }
            } else {
                CircleIconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter by exercise",
                        tint = if (filterExerciseId != null) c.accent else c.fg
                    )
                }
                Spacer(Modifier.size(8.dp))
                Box {
                    CircleIconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = c.fg)
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Excel (.xlsx)") },
                            onClick = {
                                showExportMenu = false
                                exportXlsxAndShare(context, viewModel)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export text log (.txt)") },
                            onClick = {
                                showExportMenu = false
                                exportTxtAndShare(context, viewModel)
                            }
                        )
                    }
                }
            }
        }

        if (filterExercise != null) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.accent.copy(alpha = 0.12f))
                    .border(1.dp, c.accent.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                    .combinedClickable(onClick = { viewModel.setFilter(null) })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    filterExercise.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent
                )
                Spacer(Modifier.size(6.dp))
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = c.accent, modifier = Modifier.size(14.dp))
            }
        }

        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (filterExercise != null)
                        "No sessions for ${filterExercise.name}."
                    else
                        "No completed sessions yet.",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.fg
                )
                Text(
                    if (filterExercise != null)
                        "Clear the filter to see all sessions."
                    else
                        "Finish a workout and it'll show up here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.fgDim
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 4.dp,
                    bottom = 16.dp + LocalBottomBarPadding.current
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions, key = { it.session.id }) { sws ->
                    val sessionId = sws.session.id
                    val isSelected = sessionId in selected
                    val totalSets = sws.exercises.sumOf { it.sets.size }
                    val exerciseCount = sws.exercises.size
                    val durationStr = sws.session.completedAt
                        ?.let { formatSessionDuration(it - sws.session.startedAt) }
                    val volumeKg = sws.exercises.sumOf { ex ->
                        ex.sets.sumOf { it.weightKg * it.reps }
                    }
                    // Prefer the session's snapshotted day-name (set by StartSessionUseCase
                    // for plan-driven sessions and by the importer for historical logs).
                    // Fall back to the first exercise so custom workouts still get a useful
                    // label, and "Workout" as a last resort.
                    val sessionType = sws.session.sessionType?.takeIf { it.isNotBlank() }
                    val title = sessionType
                        ?: sws.exercises.firstOrNull()?.exercise?.name?.let { name ->
                            if (sws.exercises.size > 1) "$name + ${sws.exercises.size - 1}"
                            else name
                        }
                        ?: "Workout"
                    val tile = TilePalette[abs(title.hashCode()) % TilePalette.size]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.surface)
                            .border(
                                1.dp,
                                if (isSelected) c.accent else c.line,
                                RoundedCornerShape(18.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (inSelectionMode) viewModel.toggleSelected(sessionId)
                                    else onOpenSession(sessionId)
                                },
                                onLongClick = { viewModel.toggleSelected(sessionId) }
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tile.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(c.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = c.onAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = tile,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                color = c.fg
                            )
                            Text(
                                buildString {
                                    append(df.format(Date(sws.session.startedAt)))
                                    append(" · ")
                                    append("$exerciseCount ex · $totalSets sets")
                                    if (durationStr != null) {
                                        append(" · ")
                                        append(durationStr)
                                    }
                                    if (volumeKg > 0) {
                                        append(" · ")
                                        append("${formatVol(volumeKg)}t")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = c.fgDim
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = c.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Filter by exercise", style = MaterialTheme.typography.titleLarge, color = c.fg)
                Text(
                    "Show only sessions that include the selected exercise.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.fgDim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (filterExerciseId != null) {
                    TextButton(onClick = {
                        viewModel.setFilter(null)
                        showFilterSheet = false
                    }) { Text("Clear filter") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = c.line)
                ExercisePicker(
                    all = historyExercises,
                    onPick = {
                        viewModel.setFilter(it.id)
                        showFilterSheet = false
                    }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $count log${if (count == 1) "" else "s"}?") },
            text = {
                Text(
                    "This permanently removes the selected session${if (count == 1) "" else "s"} " +
                        "and all logged sets. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = LocalFitnessColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.line, CircleShape)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private fun formatVol(kg: Double): String {
    val tonnes = kg / 1000.0
    return if (tonnes >= 10) "%.0f".format(tonnes)
    else "%.1f".format(tonnes)
}

private fun exportXlsxAndShare(context: Context, viewModel: HistoryViewModel) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val file = File(dir, "fitness_log_$stamp.xlsx")
    viewModel.exportXlsx(file) { sessions, sets ->
        Toast.makeText(context, "Exported $sessions sessions, $sets sets", Toast.LENGTH_SHORT).show()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "fitness_log.xlsx")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share fitness log"))
    }
}

private fun exportTxtAndShare(context: Context, viewModel: HistoryViewModel) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val file = File(dir, "fitness_log_$stamp.txt")
    viewModel.exportTxt(file) { sessions, sets ->
        Toast.makeText(context, "Exported $sessions sessions, $sets sets", Toast.LENGTH_SHORT).show()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "fitness_log.txt")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share fitness log"))
    }
}
