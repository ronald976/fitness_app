package com.fitness.app.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanWithDays
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val appStateRepository: AppStateRepository,
    private val startSession: StartSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<PlanWithDays?>(null)
    val state = _state.asStateFlow()

    fun load(planId: Long) {
        viewModelScope.launch {
            planRepository.observePlan(planId).collect { plan ->
                _state.update { plan }
            }
        }
    }

    fun startDay(planDayId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val userId = appStateRepository.observe().first()?.currentUserId ?: return@launch
            val id = startSession(userId, planDayId)
            onStarted(id)
        }
    }
}
