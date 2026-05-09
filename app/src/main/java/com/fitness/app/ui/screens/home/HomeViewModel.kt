package com.fitness.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanDayWithExercises
import com.fitness.app.data.db.dao.PlanWithDays
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.UserEntity
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.data.repository.UserPrefsRepository
import com.fitness.app.data.repository.UserRepository
import com.fitness.app.data.xlsx.XlsxImporter
import com.fitness.app.domain.usecase.PickTodayDayUseCase
import com.fitness.app.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI-friendly stats for one plan day, derived once at flow time. */
data class DayMeta(
    val planDayId: Long,
    val name: String,
    val exerciseCount: Int,
    val totalSets: Int,
    val estMinutes: Int,
    val lastDurationLabel: String? = null, // "1:08"
    val lastVolumeLabel: String? = null     // "2.4t"
)

data class HomeUiState(
    val users: List<UserEntity> = emptyList(),
    val currentUserId: Long? = null,
    val activePlan: PlanWithDays? = null,
    val todayDayId: Long? = null,
    /** All days excluding today, ordered by dayIndex. */
    val otherDayIds: List<Long> = emptyList(),
    val dayMeta: Map<Long, DayMeta> = emptyMap(),
    /** Cycle position 1..N where N = days.size. */
    val cycleIndex: Int = 0,
    val cycleSize: Int = 0
) {
    val currentUser: UserEntity? get() = users.firstOrNull { it.id == currentUserId }
    val todayDay: PlanDayWithExercises? get() {
        val id = todayDayId ?: return null
        return activePlan?.days?.firstOrNull { it.day.id == id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val userPrefsRepository: UserPrefsRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    planRepository: PlanRepository,
    private val startSession: StartSessionUseCase,
    private val pickTodayDay: PickTodayDayUseCase,
    private val xlsxImporter: XlsxImporter
) : ViewModel() {

    val state = combine(
        userRepository.observeAll(),
        appStateRepository.observe()
    ) { users, appState -> users to appState?.currentUserId }
        .flatMapLatest { (users, currentUserId) ->
            if (currentUserId == null) {
                flowOf(HomeUiState(users = users, currentUserId = null))
            } else {
                val planFlow = userPrefsRepository.observe(currentUserId)
                    .flatMapLatest { prefs ->
                        val id = prefs?.activePlanId
                        if (id == null) flowOf(null) else planRepository.observePlan(id)
                    }
                val recentFlow = sessionRepository.observeRecent(currentUserId, limit = 30)
                combine(planFlow, recentFlow) { plan, recent ->
                    buildState(users, currentUserId, plan, recent)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(
        users: List<UserEntity>,
        currentUserId: Long,
        plan: PlanWithDays?,
        recent: List<SessionWithExercises>
    ): HomeUiState {
        val todayId = pickTodayDay(plan, recent)
        val orderedDays = plan?.days?.sortedBy { it.day.dayIndex }.orEmpty()
        val others = orderedDays.map { it.day.id }.filter { it != todayId }
        val meta = orderedDays.associate { day ->
            day.day.id to dayMeta(day, recent)
        }
        val cycleIndex = orderedDays.indexOfFirst { it.day.id == todayId } + 1
        return HomeUiState(
            users = users,
            currentUserId = currentUserId,
            activePlan = plan,
            todayDayId = todayId,
            otherDayIds = others,
            dayMeta = meta,
            cycleIndex = cycleIndex,
            cycleSize = orderedDays.size
        )
    }

    private fun dayMeta(
        day: PlanDayWithExercises,
        recent: List<SessionWithExercises>
    ): DayMeta {
        val exerciseCount = day.exercises.size
        val totalSets = day.exercises.sumOf { it.planned.targetSets }
        // Estimate: each set ≈ rest + 30s of work. Sum across exercises.
        val estSec = day.exercises.sumOf { ex ->
            ex.planned.targetSets * (ex.planned.restSec + 30)
        }
        val estMin = (estSec / 60).coerceAtLeast(1)
        val lastSession = recent.firstOrNull { it.session.planDayId == day.day.id }
        val durationLabel = lastSession?.session?.completedAt?.let { c ->
            val durMs = c - lastSession.session.startedAt
            val totalMin = (durMs / 60_000).toInt().coerceAtLeast(0)
            val h = totalMin / 60
            val m = totalMin % 60
            "%d:%02d".format(h, m)
        }
        val volumeLabel = lastSession?.let { sws ->
            val kg = sws.exercises.sumOf { ex -> ex.sets.sumOf { it.weightKg * it.reps } }
            if (kg > 0) {
                val tonnes = kg / 1000.0
                if (tonnes >= 10) "%.0ft".format(tonnes) else "%.1ft".format(tonnes)
            } else null
        }
        return DayMeta(
            planDayId = day.day.id,
            name = day.day.name,
            exerciseCount = exerciseCount,
            totalSets = totalSets,
            estMinutes = estMin,
            lastDurationLabel = durationLabel,
            lastVolumeLabel = volumeLabel
        )
    }

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

    /** Start a custom workout: an empty session with no plan day. The user picks
     *  exercises via the existing Add Exercise flow on the active workout screen. */
    fun startCustomWorkout(onStarted: (Long) -> Unit) {
        val userId = state.value.currentUserId ?: return
        viewModelScope.launch {
            val sessionId = sessionRepository.insertSession(
                SessionEntity(
                    userId = userId,
                    planDayId = null,
                    startedAt = System.currentTimeMillis(),
                    completedAt = null,
                    sessionType = "Custom"
                )
            )
            onStarted(sessionId)
        }
    }

    fun importXlsx(input: InputStream, onResult: (Int, Int) -> Unit) {
        val userId = state.value.currentUserId ?: return
        viewModelScope.launch {
            val result = xlsxImporter.import(input, userId)
            onResult(result.sessionsImported, result.rowsSkipped)
        }
    }
}
