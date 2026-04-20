package com.fitness.app.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    planId: Long,
    onStartDay: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(planId) { viewModel.load(planId) }
    val plan by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.plan?.name ?: "Plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(planId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit plan")
                    }
                }
            )
        }
    ) { padding ->
        val current = plan ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(current.days.sortedBy { it.day.dayIndex }, key = { it.day.id }) { day ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(day.day.name, style = MaterialTheme.typography.titleLarge)
                        day.exercises.sortedBy { it.planned.orderIdx }.forEach { pwe ->
                            Text(
                                text = "• ${pwe.exercise.name}  ·  ${pwe.planned.targetSets}×${pwe.planned.repLow}–${pwe.planned.repHigh}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Button(
                            onClick = { viewModel.startDay(day.day.id, onStartDay) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null)
                            Text(" Start ${day.day.name}")
                        }
                    }
                }
            }
        }
    }
}
