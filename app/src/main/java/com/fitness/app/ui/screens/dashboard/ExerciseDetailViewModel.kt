package com.fitness.app.ui.screens.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exerciseName: String = "",
    val muscle: String = "",
    val bestE1rm: Double = 0.0,
    val maxWeight: Double = 0.0,
    val sessionCount: Int = 0,
    val totalSets: Int = 0,
    val progression: ExerciseProgression? = null,
    /** Month label to set count, chronological, last 12 months. */
    val monthlySets: List<Pair<String, Int>> = emptyList(),
    val repRangeDistribution: Map<String, Int> = emptyMap(),
    /** Newest first. */
    val prHistory: List<PrMarker> = emptyList()
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appStateRepository: AppStateRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])

    private val _state = MutableStateFlow(ExerciseDetailUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = appStateRepository.observe().first()?.currentUserId ?: return@launch
            val allSets = sessionRepository.allSetsForDashboard(userId)
            val zone = ZoneId.systemDefault()
            _state.value = withContext(Dispatchers.Default) {
                build(allSets.filter { it.exerciseId == exerciseId }, zone)
            }
        }
    }

    private fun build(
        exSets: List<com.fitness.app.data.db.dao.DashboardSetRow>,
        zone: ZoneId
    ): ExerciseDetailUiState {
        if (exSets.isEmpty()) return ExerciseDetailUiState(isLoading = false)

        // Lower session threshold than the dashboard tab — even a short history
        // is worth charting on a dedicated screen.
        val progression = buildExerciseProgression(exerciseId, exSets, zone, minSessions = 2)
        val bestSet = exSets.maxByOrNull { e1rm(it.weightKg, it.reps) }

        val byMonth = exSets.groupBy {
            YearMonth.from(Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate())
        }
        val lastMonth = byMonth.keys.maxOrNull() ?: return ExerciseDetailUiState(isLoading = false)
        val monthFmt = DateTimeFormatter.ofPattern("MMM")
        val monthly = (11 downTo 0).map { back ->
            val ym = lastMonth.minusMonths(back.toLong())
            ym.format(monthFmt) to (byMonth[ym]?.size ?: 0)
        }

        return ExerciseDetailUiState(
            isLoading = false,
            exerciseName = exSets.first().exerciseName,
            muscle = exSets.first().primaryMuscle,
            bestE1rm = bestSet?.let { e1rm(it.weightKg, it.reps) } ?: 0.0,
            maxWeight = exSets.maxOf { it.weightKg },
            sessionCount = exSets.map { it.sessionStartedAt }.distinct().size,
            totalSets = exSets.size,
            progression = progression,
            monthlySets = monthly,
            repRangeDistribution = buildRepRangeDistribution(exSets),
            prHistory = (progression?.prs ?: emptyList()).sortedByDescending { it.date }
        )
    }
}
