package com.fitness.app.ui.screens.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import com.fitness.app.data.repository.ExerciseRepository
import com.fitness.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetDraft(val weight: String, val reps: String, val note: String)

data class NewSetDraft(
    val tempId: Long,
    val weight: String,
    val reps: String,
    val note: String
)

data class AddExerciseSheetData(val allExercises: List<ExerciseEntity>)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"]!!

    val session: StateFlow<SessionWithExercises?> =
        sessionRepository.observeSession(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editingExerciseId = MutableStateFlow<Long?>(null)
    val editingExerciseId = _editingExerciseId.asStateFlow()

    private val _drafts = MutableStateFlow<Map<Long, SetDraft>>(emptyMap())
    val drafts = _drafts.asStateFlow()

    private val _deleted = MutableStateFlow<Set<Long>>(emptySet())
    val deleted = _deleted.asStateFlow()

    private val _newSets = MutableStateFlow<List<NewSetDraft>>(emptyList())
    val newSets = _newSets.asStateFlow()

    private val _addExerciseSheet = MutableStateFlow<AddExerciseSheetData?>(null)
    val addExerciseSheet = _addExerciseSheet.asStateFlow()

    fun startEditing(sessionExerciseId: Long) {
        _drafts.value = emptyMap()
        _deleted.value = emptySet()
        _newSets.value = emptyList()
        _editingExerciseId.value = sessionExerciseId
    }

    fun cancelEditing() {
        _drafts.value = emptyMap()
        _deleted.value = emptySet()
        _newSets.value = emptyList()
        _editingExerciseId.value = null
    }

    fun setDraft(setId: Long, weight: String? = null, reps: String? = null, note: String? = null) {
        _drafts.update { current ->
            val src = sessionSetById(setId)
            val existing = current[setId] ?: SetDraft(
                weight = src?.let { formatKg(it.weightKg) } ?: "",
                reps = src?.reps?.toString() ?: "",
                note = src?.note ?: ""
            )
            current + (setId to existing.copy(
                weight = weight ?: existing.weight,
                reps = reps ?: existing.reps,
                note = note ?: existing.note
            ))
        }
    }

    fun toggleDeleted(setId: Long) {
        _deleted.update { if (setId in it) it - setId else it + setId }
    }

    fun addNewSet() {
        val editingId = _editingExerciseId.value ?: return
        val ex = sessionExerciseById(editingId) ?: return
        val lastNew = _newSets.value.lastOrNull()
        val lastExisting = ex.sets.maxByOrNull { it.setIndex }
        val seedWeight = lastNew?.weight
            ?: lastExisting?.weightKg?.let { formatKg(it) }
            ?: ""
        val seedReps = lastNew?.reps
            ?: lastExisting?.reps?.takeIf { it > 0 }?.toString()
            ?: ""
        val tempId = -System.nanoTime()
        _newSets.update { it + NewSetDraft(tempId, seedWeight, seedReps, "") }
    }

    fun setNewSetDraft(
        tempId: Long,
        weight: String? = null,
        reps: String? = null,
        note: String? = null
    ) {
        _newSets.update { list ->
            list.map { ns ->
                if (ns.tempId != tempId) ns
                else ns.copy(
                    weight = weight ?: ns.weight,
                    reps = reps ?: ns.reps,
                    note = note ?: ns.note
                )
            }
        }
    }

    fun removeNewSet(tempId: Long) {
        _newSets.update { it.filter { ns -> ns.tempId != tempId } }
    }

    fun saveExerciseEdits() {
        val editingId = _editingExerciseId.value ?: return
        val sessionData = session.value ?: return
        val ex = sessionData.exercises.firstOrNull {
            it.sessionExercise.id == editingId
        } ?: return
        val byId = ex.sets.associateBy { it.id }
        val draftsSnapshot = _drafts.value
        val deletedSnapshot = _deleted.value
        val newSetsSnapshot = _newSets.value
        val completedAt = sessionData.session.completedAt ?: sessionData.session.startedAt

        viewModelScope.launch {
            for ((id, draft) in draftsSnapshot) {
                if (id in deletedSnapshot) continue
                val original = byId[id] ?: continue
                val newWeight = draft.weight.toDoubleOrNull() ?: original.weightKg
                val newReps = draft.reps.toIntOrNull() ?: original.reps
                if (newWeight == original.weightKg &&
                    newReps == original.reps &&
                    draft.note == original.note
                ) continue
                sessionRepository.insertSetLog(
                    original.copy(
                        weightKg = newWeight,
                        reps = newReps,
                        note = draft.note
                    )
                )
            }
            for (id in deletedSnapshot) {
                sessionRepository.deleteSet(id)
            }
            val nextIndexStart = (ex.sets.maxOfOrNull { it.setIndex } ?: -1) + 1
            newSetsSnapshot
                .filter { it.weight.toDoubleOrNull() != null && it.reps.toIntOrNull() != null }
                .forEachIndexed { i, ns ->
                    sessionRepository.insertSetLog(
                        SetLogEntity(
                            sessionExerciseId = editingId,
                            setIndex = nextIndexStart + i,
                            weightKg = ns.weight.toDouble(),
                            reps = ns.reps.toInt(),
                            note = ns.note,
                            completedAt = completedAt
                        )
                    )
                }
            cancelEditing()
        }
    }

    // ── Add exercise to session ────────────────────────────────────────

    fun openAddExercise() {
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            _addExerciseSheet.value = AddExerciseSheetData(allExercises = all)
        }
    }

    fun closeAddExercise() { _addExerciseSheet.value = null }

    fun confirmAddExercise(exerciseId: Long) {
        val sessionData = session.value ?: return
        val nextOrder = (sessionData.exercises.maxOfOrNull {
            it.sessionExercise.orderIdx
        } ?: -1) + 1
        viewModelScope.launch {
            sessionRepository.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    plannedExerciseId = null,
                    actualExerciseId = exerciseId,
                    orderIdx = nextOrder,
                    customLabel = null
                )
            )
            _addExerciseSheet.value = null
        }
    }

    fun confirmAddNewExercise(name: String) {
        viewModelScope.launch {
            val newId = exerciseRepository.findOrCreateCustom(name)
            if (newId <= 0L) return@launch
            confirmAddExercise(newId)
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    private fun sessionExerciseById(id: Long) =
        session.value?.exercises?.firstOrNull { it.sessionExercise.id == id }

    private fun sessionSetById(setId: Long) =
        session.value?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == setId }

    private fun formatKg(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
