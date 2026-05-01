package com.fitness.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.importer.TextLogExporter
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.data.xlsx.XlsxExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val sessionRepository: SessionRepository,
    private val xlsxExporter: XlsxExporter,
    private val textLogExporter: TextLogExporter
) : ViewModel() {
    private val rawSessions = appStateRepository.observe()
        .flatMapLatest { appState ->
            val userId = appState?.currentUserId
            if (userId == null) flowOf(emptyList())
            else sessionRepository.observeRecent(userId, 50)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SessionWithExercises>())

    private val _filterExerciseId = MutableStateFlow<Long?>(null)
    val filterExerciseId = _filterExerciseId.asStateFlow()

    val sessions = combine(rawSessions, _filterExerciseId) { all, filter ->
        if (filter == null) all
        else all.filter { sws ->
            sws.exercises.any { it.exercise.id == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SessionWithExercises>())

    /** Distinct exercises that show up in any session — used to populate the filter picker. */
    val historyExercises = rawSessions.map { all ->
        all.flatMap { sws -> sws.exercises.map { it.exercise } }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ExerciseEntity>())

    fun setFilter(exerciseId: Long?) { _filterExerciseId.value = exerciseId }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected = _selected.asStateFlow()

    fun toggleSelected(sessionId: Long) {
        _selected.update { current ->
            if (sessionId in current) current - sessionId else current + sessionId
        }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun deleteSelected(onDone: () -> Unit = {}) {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) {
            onDone()
            return
        }
        viewModelScope.launch {
            sessionRepository.deleteSessions(ids)
            _selected.value = emptySet()
            onDone()
        }
    }

    fun exportXlsx(target: File, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val userId = appStateRepository.observe().first()?.currentUserId ?: return@launch
            val result = target.outputStream().use { xlsxExporter.export(it, userId) }
            onResult(result.sessionsExported, result.setsExported)
        }
    }

    fun exportTxt(target: File, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val userId = appStateRepository.observe().first()?.currentUserId ?: return@launch
            val result = target.outputStream().use { textLogExporter.export(it, userId) }
            onResult(result.sessionsExported, result.setsExported)
        }
    }
}
