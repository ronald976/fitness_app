package com.fitness.app.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.ExerciseRepository
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.domain.suggestion.Suggestion
import com.fitness.app.domain.usecase.DetectPrUseCase
import com.fitness.app.domain.usecase.FinishSessionUseCase
import com.fitness.app.domain.usecase.GetSuggestionUseCase
import com.fitness.app.domain.usecase.LogSetUseCase
import com.fitness.app.domain.usecase.PrResult
import com.fitness.app.domain.usecase.RemoveExerciseUseCase
import com.fitness.app.domain.usecase.SwapExerciseUseCase
import com.fitness.app.ui.components.formatSetSummary
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
    val lastSummary: String?,
    val sets: List<SetRowState>,
    val supersetGroupId: Long? = null
)

data class PairExerciseSheetState(
    val sessionExerciseId: Long,
    val candidates: List<PairCandidate>
) {
    data class PairCandidate(val sessionExerciseId: Long, val name: String)
}

data class SwapSheetState(
    val sessionExerciseId: Long,
    val alternatives: List<ExerciseEntity>,
    val allExercises: List<ExerciseEntity>
)

data class AddExerciseSheetState(
    val allExercises: List<ExerciseEntity>,
    /** Whether this session has a backing plan day, so the "Also add to plan" toggle
     *  has somewhere to write. False for ad-hoc / custom sessions. */
    val canAddToPlan: Boolean
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
    val sessionStartedAt: Long = 0L,
    val planDayId: Long? = null,
    val exercises: List<WorkoutExerciseUi> = emptyList(),
    val restSeconds: Int? = null,
    val restKey: Int = 0,
    val swapSheet: SwapSheetState? = null,
    val addSheet: AddExerciseSheetState? = null,
    val editRestSheet: EditRestSheetState? = null,
    val pairSheet: PairExerciseSheetState? = null,
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
    private val remove: RemoveExerciseUseCase,
    private val finish: FinishSessionUseCase,
    private val getSuggestion: GetSuggestionUseCase,
    private val detectPr: DetectPrUseCase,
    private val planRepository: PlanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutUiState())
    val state = _state.asStateFlow()

    private companion object {
        /** Short rest used when a paired (superset) exercise is logged — just enough to
         *  walk to the partner station. */
        const val SUPERSET_REST_SEC = 10
    }

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
                    // Compact summary of what the user logged for this exercise in their
                    // previous session, so they have a baseline to beat without scrolling
                    // back through history.
                    val lastSession = sessionRepository.lastSessionExerciseFor(userId, sxs.exercise.id)
                    val lastSummary = lastSession?.let { ls ->
                        // Don't repeat the current session's sets back at the user.
                        if (ls.sessionExercise.sessionId == sessionId) null
                        else formatSetSummary(ls.sets)?.let { "Last: $it" }
                    }

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
                        restSec = planned?.restSec ?: 75,
                        suggestionNote = suggestion?.note,
                        prText = prText,
                        lastSummary = lastSummary,
                        sets = rows,
                        supersetGroupId = sxs.sessionExercise.supersetGroupId
                    )
                }

            _state.update {
                it.copy(
                    sessionId = sessionId,
                    userId = userId,
                    sessionStartedAt = session.session.startedAt,
                    planDayId = session.session.planDayId,
                    exercises = uiExercises
                )
            }
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
        // Blank weight/reps log as 0 — the user is acknowledging "I did this set" for
        // bodyweight or quick-cable cases. PR / progression queries already filter out
        // reps == 0 and weight == 0, so these don't poison stats.
        val weight = row.input.weightKg.toDoubleOrNull() ?: 0.0
        val reps = row.input.reps.toIntOrNull() ?: 0
        val existingId = row.setLogId
        val isEdit = existingId != null

        viewModelScope.launch {
            val setId: Long = if (existingId != null) {
                sessionRepository.updateSetValues(
                    id = existingId,
                    weightKg = weight,
                    reps = reps,
                    note = row.input.note
                )
                existingId
            } else {
                logSet.invoke(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = setIndex,
                    weightKg = weight,
                    reps = reps,
                    note = row.input.note
                )
            }
            // Paired exercises run as a superset — short rest to walk to the partner.
            // Edits don't restart the rest timer: the user is fixing a typo, not finishing
            // a fresh set, so kicking off a 90s countdown would be wrong.
            _state.update { st ->
                st.copy(
                    restSeconds = if (isEdit) st.restSeconds else
                        if (ex.supersetGroupId != null) SUPERSET_REST_SEC else ex.restSec,
                    restKey = if (isEdit) st.restKey else st.restKey + 1,
                    exercises = st.exercises.map { e ->
                        if (e.sessionExerciseId != sessionExerciseId) e
                        else e.copy(sets = e.sets.map { r ->
                            if (r.index != setIndex) r
                            else r.copy(logged = true, setLogId = setId)
                        })
                    }
                )
            }

            // After an edit, refresh the card's PR badge and last-session summary, since
            // the value the user just changed may have moved both.
            if (isEdit) load(_state.value.sessionId)

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

    /** Replace the current rest with [newSeconds] and re-key so the timer card and the
     *  notification both restart at the new total. Called by ±10s buttons. */
    fun setRestSeconds(newSeconds: Int) {
        _state.update {
            it.copy(
                restSeconds = newSeconds.coerceIn(5, 600),
                restKey = it.restKey + 1
            )
        }
    }

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

    /** Remove the currently-shown swap-target exercise from the session, optionally also
     *  removing it from the underlying plan day. Reuses the same sheet state so the
     *  Change Exercise sheet can host both swap and remove. */
    fun confirmRemoveExercise(alsoUpdatePlan: Boolean) {
        val sheet = _state.value.swapSheet ?: return
        viewModelScope.launch {
            remove.invoke(sheet.sessionExerciseId, alsoUpdatePlan)
            _state.update { it.copy(swapSheet = null) }
            load(_state.value.sessionId)
        }
    }

    fun openAddExercise() {
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            _state.update {
                it.copy(addSheet = AddExerciseSheetState(
                    allExercises = all,
                    canAddToPlan = it.planDayId != null
                ))
            }
        }
    }

    fun closeAddExercise() { _state.update { it.copy(addSheet = null) } }

    fun confirmAddExercise(exerciseId: Long, alsoAddToPlan: Boolean = false) {
        val sessionId = _state.value.sessionId
        if (sessionId <= 0L) return
        val planDayId = _state.value.planDayId
        viewModelScope.launch {
            // If the user opted in (and we have a plan day), insert the planned-exercise
            // first so we can link the new session-exercise to it via plannedExerciseId.
            // Defaults match PlanEditViewModel.addExercise so the row feels consistent.
            val plannedId: Long? = if (alsoAddToPlan && planDayId != null) {
                planRepository.addPlannedExerciseToDay(
                    planDayId = planDayId,
                    exerciseId = exerciseId,
                    targetSets = 3,
                    repLow = 6,
                    repHigh = 10,
                    restSec = 75,
                    weightIncrementKg = 2.5
                )
            } else null

            sessionRepository.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    plannedExerciseId = plannedId,
                    actualExerciseId = exerciseId,
                    orderIdx = _state.value.exercises.size,
                    customLabel = null
                )
            )
            _state.update { it.copy(addSheet = null) }
            load(sessionId)
        }
    }

    fun confirmAddNewExercise(name: String, alsoAddToPlan: Boolean = false) {
        viewModelScope.launch {
            val newId = exerciseRepository.findOrCreateCustom(name)
            if (newId <= 0L) return@launch
            confirmAddExercise(newId, alsoAddToPlan)
        }
    }

    /**
     * Free-text "quick-add" entry mid-workout. Mirrors the shorthand used in the historical
     * .txt logs so muscle memory carries over:
     *   "abs x3"             → adds Abs with 3 placeholder sets (✓ Completed rows)
     *   "leg press 200x10"   → adds Leg Press with one logged 200kg × 10 set
     *   "leg press 200x10 200x8 200x8" → three logged sets
     *   "cables x6"          → adds the cable lat-raise + overhead tricep-ext superset, 6 sets each
     *   "dumbbells x6"       → same idea but dumbbell lat raise + overhead tricep ext
     * Bare "<name>" (no xN) just adds the exercise with no logged sets, like the existing picker.
     */
    fun quickAddExercise(text: String) {
        val parsed = parseQuickAdd(text) ?: return
        viewModelScope.launch {
            val sessionId = _state.value.sessionId
            if (sessionId <= 0L) return@launch
            val nameLc = parsed.name.lowercase()
            val pair: Pair<String, String>? = when (nameLc) {
                "cable", "cables" -> "Cable Lateral Raise" to "Overhead Tricep Extension"
                "dumbbell", "dumbbells" -> "Dumbbell Lateral Raise" to "Overhead Tricep Extension"
                else -> null
            }
            val baseOrder = _state.value.exercises.size
            if (pair != null) {
                val groupId = System.nanoTime()
                val id1 = resolveExerciseByName(pair.first)
                val id2 = resolveExerciseByName(pair.second)
                if (id1 <= 0L || id2 <= 0L) return@launch
                val seId1 = sessionRepository.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId, plannedExerciseId = null,
                        actualExerciseId = id1, orderIdx = baseOrder,
                        customLabel = parsed.name, supersetGroupId = groupId
                    )
                )
                val seId2 = sessionRepository.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId, plannedExerciseId = null,
                        actualExerciseId = id2, orderIdx = baseOrder + 1,
                        customLabel = parsed.name, supersetGroupId = groupId
                    )
                )
                insertQuickSets(seId1, parsed.sets)
                insertQuickSets(seId2, parsed.sets)
            } else {
                val exerciseId = resolveExerciseByName(parsed.name)
                if (exerciseId <= 0L) return@launch
                val seId = sessionRepository.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId, plannedExerciseId = null,
                        actualExerciseId = exerciseId, orderIdx = baseOrder,
                        customLabel = null, supersetGroupId = null
                    )
                )
                insertQuickSets(seId, parsed.sets)
            }
            load(sessionId)
        }
    }

    private suspend fun resolveExerciseByName(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val all = exerciseRepository.getAll()
        all.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        all.firstOrNull { it.name.contains(trimmed, ignoreCase = true) }?.let { return it.id }
        return exerciseRepository.findOrCreateCustom(trimmed)
    }

    private suspend fun insertQuickSets(sessionExerciseId: Long, sets: List<QuickAddSet>) {
        sets.forEachIndexed { idx, s ->
            logSet.invoke(
                sessionExerciseId = sessionExerciseId,
                setIndex = idx,
                weightKg = s.weightKg,
                reps = s.reps,
                note = ""
            )
        }
    }

    private data class QuickAddSet(val weightKg: Double, val reps: Int)
    private data class QuickAddInput(val name: String, val sets: List<QuickAddSet>)

    private fun parseQuickAdd(text: String): QuickAddInput? {
        val s = text.trim()
        if (s.isEmpty()) return null

        // Whole-input shorthand: "<name> xN" → N placeholder sets (weight=0, reps=0).
        val placeholder = Regex("""^(.+?)\s*x(\d+)\s*$""", RegexOption.IGNORE_CASE)
            .matchEntire(s)
        if (placeholder != null) {
            val name = placeholder.groupValues[1].trim()
            val n = placeholder.groupValues[2].toInt().coerceIn(1, 20)
            if (name.isNotEmpty() && !looksLikeWeightedSetToken(name)) {
                return QuickAddInput(name, List(n) { QuickAddSet(0.0, 0) })
            }
        }

        // Otherwise: peel trailing "WxR" tokens off the end as weighted sets, rest is the name.
        val parts = s.split(Regex("\\s+"))
        val weighted = mutableListOf<QuickAddSet>()
        var splitIdx = parts.size
        val weightedRe = Regex("""^(\d+(?:\.\d+)?)x(\d+)f?$""", RegexOption.IGNORE_CASE)
        for (i in parts.indices.reversed()) {
            val m = weightedRe.matchEntire(parts[i]) ?: break
            weighted.add(0, QuickAddSet(m.groupValues[1].toDouble(), m.groupValues[2].toInt()))
            splitIdx = i
        }
        val name = parts.take(splitIdx).joinToString(" ").trim()
        if (name.isEmpty()) return null
        return QuickAddInput(name, weighted)
    }

    private fun looksLikeWeightedSetToken(s: String): Boolean =
        Regex("""^\d+(?:\.\d+)?x\d+f?$""", RegexOption.IGNORE_CASE).matches(s)

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

    // ── Superset pairing ───────────────────────────────────────────────

    fun openPair(sessionExerciseId: Long) {
        val candidates = _state.value.exercises
            .filter {
                it.sessionExerciseId != sessionExerciseId && it.supersetGroupId == null
            }
            .map {
                PairExerciseSheetState.PairCandidate(
                    sessionExerciseId = it.sessionExerciseId,
                    name = it.exerciseName
                )
            }
        _state.update {
            it.copy(pairSheet = PairExerciseSheetState(sessionExerciseId, candidates))
        }
    }

    fun closePair() { _state.update { it.copy(pairSheet = null) } }

    fun confirmPair(partnerSessionExerciseId: Long) {
        val sheet = _state.value.pairSheet ?: return
        val groupId = System.nanoTime()
        val a = sheet.sessionExerciseId
        val b = partnerSessionExerciseId

        viewModelScope.launch {
            val sessionData = sessionRepository.getSessionWithExercises(_state.value.sessionId)
                ?: return@launch
            val seA = sessionData.exercises.firstOrNull { it.sessionExercise.id == a }?.sessionExercise
            val seB = sessionData.exercises.firstOrNull { it.sessionExercise.id == b }?.sessionExercise
            if (seA == null || seB == null) return@launch
            sessionRepository.updateSessionExercise(seA.copy(supersetGroupId = groupId))
            sessionRepository.updateSessionExercise(seB.copy(supersetGroupId = groupId))

            // Persist the pairing to the underlying plan as well, so the same superset
            // shows up automatically next time this plan day is started.
            listOfNotNull(seA.plannedExerciseId, seB.plannedExerciseId).forEach { plannedId ->
                planDao.getPlannedExercise(plannedId)?.let { pe ->
                    planDao.updatePlannedExercise(pe.copy(supersetGroupId = groupId))
                }
            }

            // Tag the pair in memory and slot B right after A so the cards are adjacent.
            _state.update { st ->
                val tagged = st.exercises.map {
                    if (it.sessionExerciseId == a || it.sessionExerciseId == b)
                        it.copy(supersetGroupId = groupId)
                    else it
                }
                val mut = tagged.toMutableList()
                val bIdx = mut.indexOfFirst { it.sessionExerciseId == b }
                val bItem = mut.removeAt(bIdx)
                val aIdx = mut.indexOfFirst { it.sessionExerciseId == a }
                mut.add(aIdx + 1, bItem)
                st.copy(pairSheet = null, exercises = mut)
            }
            persistOrder(_state.value.exercises)
        }
    }

    fun unpair(sessionExerciseId: Long) {
        val ex = _state.value.exercises.firstOrNull {
            it.sessionExerciseId == sessionExerciseId
        } ?: return
        val groupId = ex.supersetGroupId ?: return
        val partnerId = _state.value.exercises.firstOrNull {
            it.supersetGroupId == groupId && it.sessionExerciseId != sessionExerciseId
        }?.sessionExerciseId

        viewModelScope.launch {
            val sessionData = sessionRepository.getSessionWithExercises(_state.value.sessionId)
                ?: return@launch
            listOfNotNull(sessionExerciseId, partnerId).forEach { id ->
                val se = sessionData.exercises
                    .firstOrNull { it.sessionExercise.id == id }?.sessionExercise
                if (se != null) {
                    sessionRepository.updateSessionExercise(se.copy(supersetGroupId = null))
                    // Mirror the unpairing to the plan, so future sessions don't re-pair.
                    se.plannedExerciseId?.let { pid ->
                        planDao.getPlannedExercise(pid)?.let { pe ->
                            planDao.updatePlannedExercise(pe.copy(supersetGroupId = null))
                        }
                    }
                }
            }
            _state.update { st ->
                st.copy(exercises = st.exercises.map {
                    if (it.supersetGroupId == groupId) it.copy(supersetGroupId = null) else it
                })
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

    // ── Edit a logged set ──────────────────────────────────────────────

    /** Flip a logged row back to its editable state, keeping its setLogId so the next log
     *  click updates the existing record instead of inserting a new one. */
    fun editSet(sessionExerciseId: Long, setIndex: Int) {
        _state.update { st ->
            st.copy(exercises = st.exercises.map { ex ->
                if (ex.sessionExerciseId != sessionExerciseId) ex
                else ex.copy(sets = ex.sets.map { row ->
                    if (row.index != setIndex || !row.logged) row
                    else row.copy(logged = false)
                })
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
        val ex = exercises[idx]

        val reordered = if (ex.supersetGroupId != null) {
            val partnerIdx = exercises.indexOfFirst {
                it.supersetGroupId == ex.supersetGroupId &&
                it.sessionExerciseId != sessionExerciseId
            }
            if (partnerIdx < 0) movePairOrSingle(exercises, idx, null, direction)
            else movePairOrSingle(exercises, idx, partnerIdx, direction)
        } else {
            movePairOrSingle(exercises, idx, null, direction)
        } ?: return

        _state.update { it.copy(exercises = reordered) }
        persistOrder(reordered)
    }

    /**
     * Double-click affordance on the up arrow: jump this exercise straight to the index of
     * the "current" exercise (first one with an unlogged set, or the last one if everything
     * is logged). Paired exercises jump together. No-op when already at/above current.
     */
    fun jumpExerciseToCurrent(sessionExerciseId: Long) {
        val exercises = _state.value.exercises
        val sourceIdx = exercises.indexOfFirst { it.sessionExerciseId == sessionExerciseId }
        if (sourceIdx < 0) return
        val ex = exercises[sourceIdx]

        val currentIdx = exercises.indexOfFirst { e -> e.sets.any { !it.logged } }
            .let { if (it < 0) exercises.lastIndex else it }
        if (sourceIdx <= currentIdx) return

        val partnerIdx = ex.supersetGroupId?.let { gid ->
            exercises.indexOfFirst {
                it.supersetGroupId == gid && it.sessionExerciseId != sessionExerciseId
            }.takeIf { it >= 0 }
        }

        val reordered = exercises.toMutableList()
        if (partnerIdx == null) {
            val item = reordered.removeAt(sourceIdx)
            reordered.add(currentIdx, item)
        } else {
            val low = minOf(sourceIdx, partnerIdx)
            val high = maxOf(sourceIdx, partnerIdx)
            val pair = listOf(reordered[low], reordered[high])
            // Remove from highest index first so the lower one keeps its position.
            reordered.removeAt(high)
            reordered.removeAt(low)
            // currentIdx may have shifted by up to 2 if pair was above it.
            val adjustedTarget = (currentIdx - listOf(low, high).count { it < currentIdx })
                .coerceAtLeast(0)
            reordered.addAll(adjustedTarget, pair)
        }

        _state.update { it.copy(exercises = reordered) }
        persistOrder(reordered)
    }

    /** Move a single exercise (partnerIdx null) or a paired adjacent unit by one slot.
     *  Returns the reordered list, or null if the move would push something off the ends. */
    private fun movePairOrSingle(
        list: List<WorkoutExerciseUi>,
        idx: Int,
        partnerIdx: Int?,
        direction: Int
    ): List<WorkoutExerciseUi>? {
        if (partnerIdx == null) {
            val newIdx = idx + direction
            if (newIdx < 0 || newIdx >= list.size) return null
            val mut = list.toMutableList()
            val item = mut.removeAt(idx)
            mut.add(newIdx, item)
            return mut
        }
        val low = minOf(idx, partnerIdx)
        val high = maxOf(idx, partnerIdx)
        val newLow = low + direction
        val newHigh = high + direction
        if (newLow < 0 || newHigh >= list.size) return null
        val mut = list.toMutableList()
        if (direction > 0) {
            // moving down — shift the higher one first so the lower keeps its index
            val highItem = mut.removeAt(high)
            mut.add(high + direction, highItem)
            val lowItem = mut.removeAt(low)
            mut.add(low + direction, lowItem)
        } else {
            // moving up — shift the lower one first so the higher keeps its index
            val lowItem = mut.removeAt(low)
            mut.add(low + direction, lowItem)
            val highItem = mut.removeAt(high)
            mut.add(high + direction, highItem)
        }
        return mut
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
            val restAfter = if (ex.supersetGroupId != null) SUPERSET_REST_SEC else ex.restSec
            _state.update { st ->
                st.copy(
                    restSeconds = restAfter,
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
            val restAfter = if (ex.supersetGroupId != null) SUPERSET_REST_SEC else ex.restSec
            _state.update { st ->
                st.copy(
                    restSeconds = restAfter,
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
