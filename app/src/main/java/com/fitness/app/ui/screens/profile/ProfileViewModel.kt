package com.fitness.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.entities.UserEntity
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val users: List<UserEntity> = emptyList(),
    val currentUserId: Long? = null
) {
    val currentUser: UserEntity? get() = users.firstOrNull { it.id == currentUserId }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    userRepository: UserRepository
) : ViewModel() {
    val state = combine(
        userRepository.observeAll(),
        appStateRepository.observe()
    ) { users, appState ->
        ProfileUiState(users = users, currentUserId = appState?.currentUserId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun selectUser(userId: Long) {
        viewModelScope.launch { appStateRepository.setCurrentUser(userId) }
    }
}
