package com.fitness.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    appStateRepository: AppStateRepository,
    sessionRepository: SessionRepository
) : ViewModel() {
    val sessions = appStateRepository.observe()
        .flatMapLatest { appState ->
            val userId = appState?.currentUserId
            if (userId == null) flowOf(emptyList())
            else sessionRepository.observeRecent(userId, 50)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SessionWithExercises>())
}
