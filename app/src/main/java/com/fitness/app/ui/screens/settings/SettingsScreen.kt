package com.fitness.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showReimportConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(importResult) {
        val r = importResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(r.message)
        viewModel.clearImportResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ToggleRow(
                title = "Chime when rest finishes",
                subtitle = "Plays a short tone on the alarm stream so it's audible " +
                    "in vibrate / silent mode.",
                checked = state.chimeEnabled,
                onCheckedChange = viewModel::setChimeEnabled
            )

            HorizontalDivider()

            Column {
                Text(
                    "Default rest interval",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Used for newly-added exercises and custom workouts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = state.defaultRestSec.toFloat(),
                        onValueChange = { viewModel.setDefaultRestSec(it.toInt()) },
                        valueRange = 15f..240f,
                        steps = 14,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${state.defaultRestSec}s",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            HorizontalDivider()

            Column {
                Text("Units", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display unit for weights. (Persisted now; consumed by formatting in a follow-up.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                val options = listOf("KG", "LB")
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = state.units == label,
                            onClick = { viewModel.setUnits(label) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) { Text(label) }
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text("Data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Re-import Ron's workout history from the bundled text logs. " +
                        "This wipes Ron's existing sessions first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                FilledTonalButton(
                    onClick = { showReimportConfirm = true },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (importing) {
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text("  Importing…")
                    } else {
                        Text("Re-import Ron's history from text logs")
                    }
                }
            }
        }
    }

    if (showReimportConfirm) {
        AlertDialog(
            onDismissRequest = { showReimportConfirm = false },
            title = { Text("Re-import Ron's history?") },
            text = {
                Text(
                    "This will permanently delete all of Ron's existing logged sessions " +
                        "and replace them with what's parsed from the bundled text logs. " +
                        "Other users (e.g. testUser) are not affected. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReimportConfirm = false
                    viewModel.reimportRonHistory()
                }) { Text("Re-import") }
            },
            dismissButton = {
                TextButton(onClick = { showReimportConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
