package com.fitness.app.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.entities.PlanEntity
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlansUiState(
    val plans: List<PlanEntity> = emptyList(),
    val activePlanId: Long? = null,
    val currentUserId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlansViewModel @Inject constructor(
    planRepository: PlanRepository,
    appStateRepository: AppStateRepository,
    private val userPrefsRepository: UserPrefsRepository
) : ViewModel() {

    private val userPrefsFlow = appStateRepository.observe()
        .flatMapLatest { appState ->
            val uid = appState?.currentUserId
            if (uid == null) flowOf<Pair<Long?, Long?>>(null to null)
            else userPrefsRepository.observe(uid).map { prefs -> uid to prefs?.activePlanId }
        }

    val state = combine(
        planRepository.observeAll(),
        userPrefsFlow
    ) { plans, pair ->
        PlansUiState(plans = plans, activePlanId = pair.second, currentUserId = pair.first)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun setActive(planId: Long) {
        val uid = state.value.currentUserId ?: return
        viewModelScope.launch { userPrefsRepository.setActivePlan(uid, planId) }
    }
}
