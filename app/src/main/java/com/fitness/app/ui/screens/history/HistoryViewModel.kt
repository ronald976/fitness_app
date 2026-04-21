package com.fitness.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.data.xlsx.XlsxExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    sessionRepository: SessionRepository,
    private val xlsxExporter: XlsxExporter
) : ViewModel() {
    val sessions = appStateRepository.observe()
        .flatMapLatest { appState ->
            val userId = appState?.currentUserId
            if (userId == null) flowOf(emptyList())
            else sessionRepository.observeRecent(userId, 50)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SessionWithExercises>())

    fun exportXlsx(target: File, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val userId = appStateRepository.observe().first()?.currentUserId ?: return@launch
            val result = target.outputStream().use { xlsxExporter.export(it, userId) }
            onResult(result.sessionsExported, result.setsExported)
        }
    }
}
