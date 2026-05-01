package com.fitness.app.ui.screens.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.SessionWithExercises
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

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"]!!

    val session: StateFlow<SessionWithExercises?> =
        sessionRepository.observeSession(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editMode = MutableStateFlow(false)
    val editMode = _editMode.asStateFlow()

    private val _drafts = MutableStateFlow<Map<Long, SetDraft>>(emptyMap())
    val drafts = _drafts.asStateFlow()

    private val _deleted = MutableStateFlow<Set<Long>>(emptySet())
    val deleted = _deleted.asStateFlow()

    fun startEditing() { _editMode.value = true }

    fun cancelEditing() {
        _drafts.value = emptyMap()
        _deleted.value = emptySet()
        _editMode.value = false
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

    fun hasChanges(): Boolean = _drafts.value.isNotEmpty() || _deleted.value.isNotEmpty()

    fun saveEdits() {
        val sessionData = session.value ?: return
        val byId = sessionData.exercises.flatMap { it.sets }.associateBy { it.id }
        val draftsSnapshot = _drafts.value
        val deletedSnapshot = _deleted.value

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
            _drafts.value = emptyMap()
            _deleted.value = emptySet()
            _editMode.value = false
        }
    }

    private fun sessionSetById(setId: Long) =
        session.value?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == setId }

    private fun formatKg(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
