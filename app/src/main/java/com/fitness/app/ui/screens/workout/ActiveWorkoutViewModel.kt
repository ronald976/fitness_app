package com.fitness.app.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.ExerciseRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.domain.suggestion.Suggestion
import com.fitness.app.domain.usecase.DetectPrUseCase
import com.fitness.app.domain.usecase.FinishSessionUseCase
import com.fitness.app.domain.usecase.GetSuggestionUseCase
import com.fitness.app.domain.usecase.LogSetUseCase
import com.fitness.app.domain.usecase.PrResult
import com.fitness.app.domain.usecase.SwapExerciseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetInput(val weightKg: String, val reps: String, val note: String = "")

data class SetRowState(
    val index: Int,
    val input: SetInput,
    val logged: Boolean
)

data class WorkoutExerciseUi(
    val sessionExerciseId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val plannedExerciseId: Long?,
    val targetSets: Int,
    val repLow: Int,
    val repHigh: Int,
    val restSec: Int,
    val suggestionNote: String?,
    val sets: List<SetRowState>
)

data class SwapSheetState(
    val sessionExerciseId: Long,
    val alternatives: List<ExerciseEntity>
)

data class PrCelebration(
    val exerciseName: String,
    val kind: Kind,
    val weightKg: Double,
    val reps: Int,
    val previousBestText: String
) {
    enum class Kind { REP, WEIGHT }
}

data class WorkoutUiState(
    val sessionId: Long = 0L,
    val userId: Long? = null,
    val exercises: List<WorkoutExerciseUi> = emptyList(),
    val restSeconds: Int? = null,
    val restKey: Int = 0,
    val swapSheet: SwapSheetState? = null,
    val pr: PrCelebration? = null,
    val finished: Boolean = false
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appStateRepository: AppStateRepository,
    private val planDao: PlanDao,
    private val logSet: LogSetUseCase,
    private val swap: SwapExerciseUseCase,
    private val finish: FinishSessionUseCase,
    private val getSuggestion: GetSuggestionUseCase,
    private val detectPr: DetectPrUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutUiState())
    val state = _state.asStateFlow()

    fun load(sessionId: Long) {
        viewModelScope.launch {
            val session = sessionRepository.getSessionWithExercises(sessionId) ?: return@launch
            val userId = session.session.userId
            val uiExercises = session.exercises
                .sortedBy { it.sessionExercise.orderIdx }
                .map { sxs ->
                    val planned = sxs.sessionExercise.plannedExerciseId
                        ?.let { planDao.getPlannedExercise(it) }
                    val suggestion: Suggestion? = sxs.sessionExercise.plannedExerciseId
                        ?.let { getSuggestion(userId, it) }

                    val targetSets = planned?.targetSets ?: (suggestion?.sets?.size ?: 3)
                    val existing = sxs.sets.sortedBy { it.setIndex }

                    val rows = (0 until targetSets).map { idx ->
                        val logged = existing.firstOrNull { it.setIndex == idx }
                        val input = when {
                            logged != null -> SetInput(
                                weightKg = formatKg(logged.weightKg),
                                reps = logged.reps.toString(),
                                note = logged.note
                            )
                            suggestion != null && idx < suggestion.sets.size -> SetInput(
                                weightKg = formatKg(suggestion.sets[idx].weightKg),
                                reps = suggestion.sets[idx].reps.toString()
                            )
                            else -> SetInput(weightKg = "", reps = "")
                        }
                        SetRowState(index = idx, input = input, logged = logged != null)
                    }

                    WorkoutExerciseUi(
                        sessionExerciseId = sxs.sessionExercise.id,
                        exerciseId = sxs.exercise.id,
                        exerciseName = sxs.exercise.name,
                        plannedExerciseId = sxs.sessionExercise.plannedExerciseId,
                        targetSets = targetSets,
                        repLow = planned?.repLow ?: 0,
                        repHigh = planned?.repHigh ?: 0,
                        restSec = planned?.restSec ?: 120,
                        suggestionNote = suggestion?.note,
                        sets = rows
                    )
                }

            _state.update { it.copy(sessionId = sessionId, userId = userId, exercises = uiExercises) }
        }
    }

    fun updateInput(
        sessionExerciseId: Long,
        setIndex: Int,
        weight: String? = null,
        reps: String? = null,
        note: String? = null
    ) {
        _state.update { st ->
            st.copy(exercises = st.exercises.map { ex ->
                if (ex.sessionExerciseId != sessionExerciseId) ex
                else ex.copy(sets = ex.sets.map { row ->
                    if (row.index != setIndex) row
                    else row.copy(
                        input = row.input.copy(
                            weightKg = weight ?: row.input.weightKg,
                            reps = reps ?: row.input.reps,
                            note = note ?: row.input.note
                        )
                    )
                })
            })
        }
    }

    fun logSet(sessionExerciseId: Long, setIndex: Int) {
        val ex = _state.value.exercises.firstOrNull { it.sessionExerciseId == sessionExerciseId } ?: return
        val row = ex.sets.getOrNull(setIndex) ?: return
        val weight = row.input.weightKg.toDoubleOrNull() ?: return
        val reps = row.input.reps.toIntOrNull() ?: return

        viewModelScope.launch {
            val setId = logSet.invoke(
                sessionExerciseId = sessionExerciseId,
                setIndex = setIndex,
                weightKg = weight,
                reps = reps,
                note = row.input.note
            )
            _state.update { st ->
                st.copy(
                    restSeconds = ex.restSec,
                    restKey = st.restKey + 1,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sessionExerciseId) e
                        else e.copy(sets = e.sets.map { r ->
                            if (r.index != setIndex) r else r.copy(logged = true)
                        })
                    }
                )
            }

            val userId = _state.value.userId
                ?: appStateRepository.observe().first()?.currentUserId
                ?: return@launch
            val prResult = detectPr(
                userId = userId,
                exerciseId = ex.exerciseId,
                plannedExerciseId = ex.plannedExerciseId,
                loggedSetId = setId
            )
            val celebration = when (prResult) {
                is PrResult.RepPr -> PrCelebration(
                    exerciseName = ex.exerciseName,
                    kind = PrCelebration.Kind.REP,
                    weightKg = prResult.weightKg,
                    reps = prResult.reps,
                    previousBestText = "${prResult.previousReps} reps"
                )
                is PrResult.WeightPr -> PrCelebration(
                    exerciseName = ex.exerciseName,
                    kind = PrCelebration.Kind.WEIGHT,
                    weightKg = prResult.weightKg,
                    reps = prResult.reps,
                    previousBestText = "${formatKg(prResult.previousBestKg)} kg"
                )
                PrResult.None -> null
            }
            if (celebration != null) {
                _state.update { it.copy(pr = celebration) }
            }
        }
    }

    fun dismissRest() { _state.update { it.copy(restSeconds = null) } }

    fun dismissPr() { _state.update { it.copy(pr = null) } }

    fun openSwap(sessionExerciseId: Long) {
        viewModelScope.launch {
            val ex = _state.value.exercises.firstOrNull { it.sessionExerciseId == sessionExerciseId } ?: return@launch
            val alts = exerciseRepository.getAlternatives(ex.exerciseId)
            _state.update { it.copy(swapSheet = SwapSheetState(sessionExerciseId, alts)) }
        }
    }

    fun closeSwap() { _state.update { it.copy(swapSheet = null) } }

    fun confirmSwap(newExerciseId: Long, alsoUpdatePlan: Boolean) {
        val sheet = _state.value.swapSheet ?: return
        viewModelScope.launch {
            swap.invoke(sheet.sessionExerciseId, newExerciseId, alsoUpdatePlan)
            _state.update { it.copy(swapSheet = null) }
            load(_state.value.sessionId)
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            finish(_state.value.sessionId)
            _state.update { it.copy(finished = true) }
        }
    }

    // ── Add extra set ──────────────────────────────────────────────────

    fun addSet(sessionExerciseId: Long) {
        _state.update { st ->
            st.copy(exercises = st.exercises.map { ex ->
                if (ex.sessionExerciseId != sessionExerciseId) ex
                else {
                    val newIndex = ex.sets.size
                    val lastInput = ex.sets.lastOrNull()?.input ?: SetInput("", "")
                    ex.copy(
                        targetSets = ex.targetSets + 1,
                        sets = ex.sets + SetRowState(
                            index = newIndex,
                            input = SetInput(lastInput.weightKg, lastInput.reps),
                            logged = false
                        )
                    )
                }
            })
        }
    }

    // ── Move exercise up/down ──────────────────────────────────────────

    fun moveExercise(sessionExerciseId: Long, direction: Int) {
        val exercises = _state.value.exercises
        val idx = exercises.indexOfFirst { it.sessionExerciseId == sessionExerciseId }
        if (idx < 0) return
        val newIdx = idx + direction
        if (newIdx < 0 || newIdx >= exercises.size) return

        val reordered = exercises.toMutableList()
        val item = reordered.removeAt(idx)
        reordered.add(newIdx, item)

        _state.update { it.copy(exercises = reordered) }

        // Persist to DB
        viewModelScope.launch {
            reordered.forEachIndexed { i, ex ->
                val session = sessionRepository.getSessionWithExercises(_state.value.sessionId)
                    ?: return@launch
                val se = session.exercises.firstOrNull {
                    it.sessionExercise.id == ex.sessionExerciseId
                }?.sessionExercise ?: return@forEachIndexed
                sessionRepository.updateSessionExercise(se.copy(orderIdx = i))
            }
        }
    }

    // ── Quick-parse text input ─────────────────────────────────────────

    /**
     * Parses "80x8 80x8f 80x7 80x6f" style input.
     * Each token: <weight>x<reps>[f] where f = "to failure" note.
     * Returns parsed sets. Excess sets beyond targetSets are added.
     */
    fun quickParse(sessionExerciseId: Long, text: String) {
        val tokens = text.trim().split(Regex("\\s+"))
        val parsed = tokens.mapNotNull { token ->
            val match = Regex("(\\d+(?:\\.\\d+)?)x(\\d+)(f?)").matchEntire(token.lowercase())
            match?.let {
                val w = it.groupValues[1]
                val r = it.groupValues[2]
                val note = if (it.groupValues[3] == "f") "to failure" else ""
                Triple(w, r, note)
            }
        }
        if (parsed.isEmpty()) return

        _state.update { st ->
            st.copy(exercises = st.exercises.map { ex ->
                if (ex.sessionExerciseId != sessionExerciseId) ex
                else {
                    // Extend sets list if needed
                    val neededSets = maxOf(ex.sets.size, parsed.size)
                    val extendedSets = if (parsed.size > ex.sets.size) {
                        val lastIdx = ex.sets.size
                        ex.sets + (lastIdx until parsed.size).map { idx ->
                            SetRowState(idx, SetInput("", ""), false)
                        }
                    } else ex.sets

                    val updatedSets = extendedSets.mapIndexed { i, row ->
                        if (i < parsed.size && !row.logged) {
                            val (w, r, n) = parsed[i]
                            row.copy(input = SetInput(w, r, n))
                        } else row
                    }
                    ex.copy(targetSets = neededSets, sets = updatedSets)
                }
            })
        }

        // Auto-log all parsed sets
        viewModelScope.launch {
            val ex = _state.value.exercises.firstOrNull {
                it.sessionExerciseId == sessionExerciseId
            } ?: return@launch
            for (i in parsed.indices) {
                val row = ex.sets.getOrNull(i) ?: continue
                if (row.logged) continue
                val weight = parsed[i].first.toDoubleOrNull() ?: continue
                val reps = parsed[i].second.toIntOrNull() ?: continue
                val note = parsed[i].third
                logSet.invoke(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = i,
                    weightKg = weight,
                    reps = reps,
                    note = note
                )
            }
            // Mark all parsed as logged
            _state.update { st ->
                st.copy(
                    restSeconds = ex.restSec,
                    restKey = st.restKey + 1,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sessionExerciseId) e
                        else e.copy(sets = e.sets.mapIndexed { i, r ->
                            if (i < parsed.size) r.copy(logged = true) else r
                        })
                    }
                )
            }
        }
    }

    // ── Quick-log all filled sets ──────────────────────────────────────

    fun quickLogAll(sessionExerciseId: Long) {
        val ex = _state.value.exercises.firstOrNull {
            it.sessionExerciseId == sessionExerciseId
        } ?: return

        val unlogged = ex.sets.filter {
            !it.logged &&
            it.input.weightKg.toDoubleOrNull() != null &&
            it.input.reps.toIntOrNull() != null
        }
        if (unlogged.isEmpty()) return

        viewModelScope.launch {
            for (row in unlogged) {
                val weight = row.input.weightKg.toDoubleOrNull() ?: continue
                val reps = row.input.reps.toIntOrNull() ?: continue
                logSet.invoke(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = row.index,
                    weightKg = weight,
                    reps = reps,
                    note = row.input.note
                )
            }
            _state.update { st ->
                st.copy(
                    restSeconds = ex.restSec,
                    restKey = st.restKey + 1,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sessionExerciseId) e
                        else e.copy(sets = e.sets.map { r ->
                            if (!r.logged &&
                                r.input.weightKg.toDoubleOrNull() != null &&
                                r.input.reps.toIntOrNull() != null
                            ) r.copy(logged = true) else r
                        })
                    }
                )
            }
        }
    }

    private fun formatKg(v: Double): String =
        if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
