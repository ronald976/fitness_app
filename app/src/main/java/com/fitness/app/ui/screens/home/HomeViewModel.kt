package com.fitness.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanWithDays
import com.fitness.app.data.db.entities.UserEntity
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.UserPrefsRepository
import com.fitness.app.data.repository.UserRepository
import com.fitness.app.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val users: List<UserEntity> = emptyList(),
    val currentUserId: Long? = null,
    val activePlan: PlanWithDays? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val userPrefsRepository: UserPrefsRepository,
    private val userRepository: UserRepository,
    planRepository: PlanRepository,
    private val startSession: StartSessionUseCase
) : ViewModel() {

    val state = combine(
        userRepository.observeAll(),
        appStateRepository.observe()
    ) { users, appState -> users to appState?.currentUserId }
        .flatMapLatest { (users, currentUserId) ->
            if (currentUserId == null) {
                flowOf(HomeUiState(users = users, currentUserId = null, activePlan = null))
            } else {
                userPrefsRepository.observe(currentUserId)
                    .flatMapLatest { prefs ->
                        val id = prefs?.activePlanId
                        if (id == null) flowOf(null) else planRepository.observePlan(id)
                    }
                    .let { planFlow ->
                        combine(flowOf(users), flowOf(currentUserId), planFlow) { u, cu, plan ->
                            HomeUiState(users = u, currentUserId = cu, activePlan = plan)
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectUser(userId: Long) {
        viewModelScope.launch { appStateRepository.setCurrentUser(userId) }
    }

    fun startDay(planDayId: Long, onStarted: (Long) -> Unit) {
        val userId = state.value.currentUserId ?: return
        viewModelScope.launch {
            val sessionId = startSession(userId, planDayId)
            onStarted(sessionId)
        }
    }
}
