package com.fitness.app.domain.usecase

import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

class FinishSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: Long, notes: String = "") {
        val existing = sessionRepository.getSessionWithExercises(sessionId)?.session ?: return
        sessionRepository.updateSession(
            existing.copy(
                completedAt = System.currentTimeMillis(),
                notes = notes.ifBlank { existing.notes }
            )
        )
    }
}
