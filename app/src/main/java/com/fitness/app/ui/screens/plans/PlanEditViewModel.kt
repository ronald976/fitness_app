package com.fitness.app.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanWithDays
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.PlannedExerciseEntity
import com.fitness.app.data.repository.ExerciseRepository
import com.fitness.app.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerState(val planDayId: Long)

data class PlanEditUiState(
    val plan: PlanWithDays? = null,
    val picker: PickerState? = null
)

@HiltViewModel
class PlanEditViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlanEditUiState())
    val state = _state.asStateFlow()

    val allExercises = exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ExerciseEntity>())

    fun load(planId: Long) {
        viewModelScope.launch {
            planRepository.observePlan(planId).collect { plan ->
                _state.update { it.copy(plan = plan) }
            }
        }
    }

    fun moveUp(planDayId: Long, plannedId: Long) = reorder(planDayId, plannedId, -1)
    fun moveDown(planDayId: Long, plannedId: Long) = reorder(planDayId, plannedId, 1)

    private fun reorder(planDayId: Long, plannedId: Long, delta: Int) {
        val day = _state.value.plan?.days?.firstOrNull { it.day.id == planDayId } ?: return
        val sorted = day.exercises.sortedBy { it.planned.orderIdx }.map { it.planned.id }.toMutableList()
        val idx = sorted.indexOf(plannedId)
        val target = idx + delta
        if (idx < 0 || target !in sorted.indices) return
        sorted[idx] = sorted[target].also { sorted[target] = sorted[idx] }
        viewModelScope.launch { planRepository.reorderDay(planDayId, sorted) }
    }

    fun removeExercise(plannedId: Long) {
        viewModelScope.launch { planRepository.deletePlannedExercise(plannedId) }
    }

    fun updatePlanned(
        plannedId: Long,
        targetSets: Int? = null,
        repLow: Int? = null,
        repHigh: Int? = null,
        restSec: Int? = null
    ) {
        val day = _state.value.plan?.days?.firstOrNull { d ->
            d.exercises.any { it.planned.id == plannedId }
        } ?: return
        val pe = day.exercises.firstOrNull { it.planned.id == plannedId }?.planned ?: return
        val updated = pe.copy(
            targetSets = targetSets ?: pe.targetSets,
            repLow = repLow ?: pe.repLow,
            repHigh = repHigh ?: pe.repHigh,
            restSec = restSec ?: pe.restSec
        )
        viewModelScope.launch { planRepository.updatePlannedExercise(updated) }
    }

    fun openPicker(planDayId: Long) {
        _state.update { it.copy(picker = PickerState(planDayId)) }
    }

    fun closePicker() {
        _state.update { it.copy(picker = null) }
    }

    fun addExercise(exerciseId: Long) {
        val picker = _state.value.picker ?: return
        val templatePe = _state.value.plan?.days
            ?.firstOrNull { it.day.id == picker.planDayId }
            ?.exercises?.firstOrNull()?.planned
        viewModelScope.launch {
            planRepository.addPlannedExerciseToDay(
                planDayId = picker.planDayId,
                exerciseId = exerciseId,
                targetSets = templatePe?.targetSets ?: 3,
                repLow = templatePe?.repLow ?: 6,
                repHigh = templatePe?.repHigh ?: 10,
                restSec = templatePe?.restSec ?: 75,
                weightIncrementKg = templatePe?.weightIncrementKg ?: 2.5
            )
            _state.update { it.copy(picker = null) }
        }
    }
}
