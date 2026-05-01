package com.fitness.app.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
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
    val logged: Boolean,
    val setLogId: Long? = null
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
    val prText: String?,
    val sets: List<SetRowState>
)

data class SwapSheetState(
    val sessionExerciseId: Long,
    val alternatives: List<ExerciseEntity>,
    val allExercises: List<ExerciseEntity>
)

data class AddExerciseSheetState(
    val allExercises: List<ExerciseEntity>
)

data class EditRestSheetState(
    val sessionExerciseId: Long,
    val currentRestSec: Int,
    val hasPlannedExercise: Boolean
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
    val addSheet: AddExerciseSheetState? = null,
    val editRestSheet: EditRestSheetState? = null,
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
                    val bestPrior = sessionRepository.bestPriorSetFor(userId, sxs.exercise.id)
                    val prText = bestPrior?.let { "🏆 PR: ${formatKg(it.weightKg)} kg × ${it.reps}" }

                    val targetSets = planned?.targetSets ?: (suggestion?.sets?.size ?: 3)
                    val existing = sxs.sets.sortedBy { it.setIndex }

                    // Include any logged sets beyond the planned target (e.g., extras
                    // added in a prior session) so the user sees everything they actually
                    // logged, not just the planned-target slots.
                    val maxLoggedIdx = existing.maxOfOrNull { it.setIndex } ?: -1
                    val rowCount = maxOf(targetSets, maxLoggedIdx + 1)
                    val rows = (0 until rowCount).map { idx ->
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
                        SetRowState(
                            index = idx,
                            input = input,
                            logged = logged != null,
                            setLogId = logged?.id
                        )
                    }

                    WorkoutExerciseUi(
                        sessionExerciseId = sxs.sessionExercise.id,
                        exerciseId = sxs.exercise.id,
                        exerciseName = sxs.exercise.name,
                        plannedExerciseId = sxs.sessionExercise.plannedExerciseId,
                        targetSets = rowCount,
                        repLow = planned?.repLow ?: 0,
                        repHigh = planned?.repHigh ?: 0,
                        restSec = planned?.restSec ?: 120,
                        suggestionNote = suggestion?.note,
                        prText = prText,
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
        val row = ex.sets.firstOrNull { it.index == setIndex } ?: return
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
                            if (r.index != setIndex) r
                            else r.copy(logged = true, setLogId = setId)
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
            val all = exerciseRepository.getAll()
            _state.update { it.copy(swapSheet = SwapSheetState(sessionExerciseId, alts, all)) }
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

    fun confirmSwapNew(name: String, alsoUpdatePlan: Boolean) {
        val sheet = _state.value.swapSheet ?: return
        viewModelScope.launch {
            val newId = exerciseRepository.findOrCreateCustom(name)
            if (newId <= 0L) return@launch
            swap.invoke(sheet.sessionExerciseId, newId, alsoUpdatePlan)
            _state.update { it.copy(swapSheet = null) }
            load(_state.value.sessionId)
        }
    }

    fun openAddExercise() {
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            _state.update { it.copy(addSheet = AddExerciseSheetState(all)) }
        }
    }

    fun closeAddExercise() { _state.update { it.copy(addSheet = null) } }

    fun confirmAddExercise(exerciseId: Long) {
        val sessionId = _state.value.sessionId
        if (sessionId <= 0L) return
        viewModelScope.launch {
            sessionRepository.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    plannedExerciseId = null,
                    actualExerciseId = exerciseId,
                    orderIdx = _state.value.exercises.size,
                    customLabel = null
                )
            )
            _state.update { it.copy(addSheet = null) }
            load(sessionId)
        }
    }

    fun confirmAddNewExercise(name: String) {
        viewModelScope.launch {
            val newId = exerciseRepository.findOrCreateCustom(name)
            if (newId <= 0L) return@launch
            confirmAddExercise(newId)
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            finish(_state.value.sessionId)
            _state.update { it.copy(finished = true) }
        }
    }

    fun cancelSession() {
        val id = _state.value.sessionId
        if (id <= 0L) {
            _state.update { it.copy(finished = true) }
            return
        }
        viewModelScope.launch {
            sessionRepository.deleteSessions(listOf(id))
            _state.update { it.copy(finished = true) }
        }
    }

    // ── Edit rest interval ─────────────────────────────────────────────

    fun openEditRest(sessionExerciseId: Long) {
        val ex = _state.value.exercises.firstOrNull {
            it.sessionExerciseId == sessionExerciseId
        } ?: return
        _state.update {
            it.copy(editRestSheet = EditRestSheetState(
                sessionExerciseId = sessionExerciseId,
                currentRestSec = ex.restSec,
                hasPlannedExercise = ex.plannedExerciseId != null
            ))
        }
    }

    fun closeEditRest() { _state.update { it.copy(editRestSheet = null) } }

    fun confirmEditRest(newSeconds: Int, alsoUpdatePlan: Boolean) {
        val sheet = _state.value.editRestSheet ?: return
        val seconds = newSeconds.coerceIn(5, 600)
        val ex = _state.value.exercises.firstOrNull {
            it.sessionExerciseId == sheet.sessionExerciseId
        }
        viewModelScope.launch {
            if (alsoUpdatePlan) {
                val plannedId = ex?.plannedExerciseId
                if (plannedId != null) {
                    val planned = planDao.getPlannedExercise(plannedId)
                    if (planned != null) {
                        planDao.updatePlannedExercise(planned.copy(restSec = seconds))
                    }
                }
            }
            _state.update { st ->
                st.copy(
                    editRestSheet = null,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sheet.sessionExerciseId) e
                        else e.copy(restSec = seconds)
                    }
                )
            }
        }
    }

    // ── Add extra set ──────────────────────────────────────────────────

    fun addSet(sessionExerciseId: Long) {
        _state.update { st ->
            st.copy(exercises = st.exercises.map { ex ->
                if (ex.sessionExerciseId != sessionExerciseId) ex
                else {
                    val newIndex = (ex.sets.maxOfOrNull { it.index } ?: -1) + 1
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

    // ── Remove set ─────────────────────────────────────────────────────

    fun removeSet(sessionExerciseId: Long, setIndex: Int) {
        val ex = _state.value.exercises.firstOrNull {
            it.sessionExerciseId == sessionExerciseId
        } ?: return
        val row = ex.sets.firstOrNull { it.index == setIndex } ?: return
        val setLogId = row.setLogId

        viewModelScope.launch {
            if (setLogId != null) {
                sessionRepository.deleteSet(setLogId)
            }
            _state.update { st ->
                st.copy(exercises = st.exercises.map { e ->
                    if (e.sessionExerciseId != sessionExerciseId) e
                    else {
                        val remaining = e.sets.filter { it.index != setIndex }
                        e.copy(
                            targetSets = remaining.size,
                            sets = remaining
                        )
                    }
                })
            }
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
        persistOrder(reordered)
    }

    /**
     * Double-click affordance on the up arrow: jump this exercise straight to the index of
     * the "current" exercise (first one with an unlogged set, or the last one if everything
     * is logged). No-ops when the exercise is already at or above current.
     */
    fun jumpExerciseToCurrent(sessionExerciseId: Long) {
        val exercises = _state.value.exercises
        val sourceIdx = exercises.indexOfFirst { it.sessionExerciseId == sessionExerciseId }
        if (sourceIdx < 0) return

        val currentIdx = exercises.indexOfFirst { ex -> ex.sets.any { !it.logged } }
            .let { if (it < 0) exercises.lastIndex else it }
        if (sourceIdx <= currentIdx) return

        val reordered = exercises.toMutableList()
        val item = reordered.removeAt(sourceIdx)
        reordered.add(currentIdx, item)

        _state.update { it.copy(exercises = reordered) }
        persistOrder(reordered)
    }

    private fun persistOrder(reordered: List<WorkoutExerciseUi>) {
        viewModelScope.launch {
            val session = sessionRepository.getSessionWithExercises(_state.value.sessionId)
                ?: return@launch
            val byId = session.exercises.associateBy { it.sessionExercise.id }
            reordered.forEachIndexed { i, ex ->
                val se = byId[ex.sessionExerciseId]?.sessionExercise ?: return@forEachIndexed
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
                    // Extend sets list if needed. Use max-existing-index + offset rather
                    // than list size so removed sets (which leave gaps in row.index) don't
                    // produce duplicate indices.
                    val neededSets = maxOf(ex.sets.size, parsed.size)
                    val extendedSets = if (parsed.size > ex.sets.size) {
                        val maxIdx = ex.sets.maxOfOrNull { it.index } ?: -1
                        val toAdd = parsed.size - ex.sets.size
                        ex.sets + (1..toAdd).map { offset ->
                            SetRowState(maxIdx + offset, SetInput("", ""), false)
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
            val newIds = mutableMapOf<Int, Long>()
            for (i in parsed.indices) {
                val row = ex.sets.getOrNull(i) ?: continue
                if (row.logged) continue
                val weight = parsed[i].first.toDoubleOrNull() ?: continue
                val reps = parsed[i].second.toIntOrNull() ?: continue
                val note = parsed[i].third
                val newId = logSet.invoke(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = row.index,
                    weightKg = weight,
                    reps = reps,
                    note = note
                )
                newIds[row.index] = newId
            }
            _state.update { st ->
                st.copy(
                    restSeconds = ex.restSec,
                    restKey = st.restKey + 1,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sessionExerciseId) e
                        else e.copy(sets = e.sets.map { r ->
                            val newId = newIds[r.index]
                            if (newId != null) r.copy(logged = true, setLogId = newId)
                            else r
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
            val newIds = mutableMapOf<Int, Long>()
            for (row in unlogged) {
                val weight = row.input.weightKg.toDoubleOrNull() ?: continue
                val reps = row.input.reps.toIntOrNull() ?: continue
                val newId = logSet.invoke(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = row.index,
                    weightKg = weight,
                    reps = reps,
                    note = row.input.note
                )
                newIds[row.index] = newId
            }
            _state.update { st ->
                st.copy(
                    restSeconds = ex.restSec,
                    restKey = st.restKey + 1,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sessionExerciseId) e
                        else e.copy(sets = e.sets.map { r ->
                            val newId = newIds[r.index]
                            if (newId != null) r.copy(logged = true, setLogId = newId)
                            else r
                        })
                    }
                )
            }
        }
    }

    private fun formatKg(v: Double): String =
        if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
