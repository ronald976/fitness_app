package com.fitness.app.ui.screens.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitness.app.ui.components.UserSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartWorkout: (Long) -> Unit,
    onBrowsePlans: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fitness") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserSwitcher(
                users = state.users,
                currentUserId = state.currentUserId,
                onSelect = viewModel::selectUser
            )

            val plan = state.activePlan
            if (plan == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No active plan", style = MaterialTheme.typography.titleLarge)
                        Text("Pick a plan to get started.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onBrowsePlans) { Text("Browse plans") }
                    }
                }
            } else {
                Text(plan.plan.name, style = MaterialTheme.typography.headlineMedium)
                Text(plan.plan.description, style = MaterialTheme.typography.bodyLarge)

                plan.days.sortedBy { it.day.dayIndex }.forEach { day ->
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(day.day.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = day.exercises
                                    .sortedBy { it.planned.orderIdx }
                                    .joinToString(" · ") { it.exercise.name },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Button(onClick = { viewModel.startDay(day.day.id, onStartWorkout) }) {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null)
                                Text(" Start ${day.day.name}")
                            }
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Custom", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Start with no preset exercises. Add them as you go.",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Button(onClick = { viewModel.startCustomWorkout(onStartWorkout) }) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null)
                            Text(" Start custom workout")
                        }
                    }
                }
            }

            OutlinedButton(onClick = onBrowsePlans) { Text("All plans") }
            OutlinedButton(onClick = onOpenHistory) {
                Icon(Icons.Default.History, contentDescription = null)
                Text(" History")
            }
            OutlinedButton(onClick = onOpenDashboard) {
                Icon(Icons.Default.BarChart, contentDescription = null)
                Text(" Dashboard")
            }
        }
    }
}
