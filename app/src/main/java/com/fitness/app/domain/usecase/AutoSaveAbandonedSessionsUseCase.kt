package com.fitness.app.domain.usecase

import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

/**
 * Rescues workouts the user logged but never pressed Finish on (e.g. the app was
 * swiped away mid-session). Runs at app start, before any new session exists:
 *  - unfinished sessions WITH logged sets are completed in place, stamped with the
 *    time of their last logged set, so they show up in history like a normal workout;
 *  - unfinished sessions with NO sets are silently deleted (abandoned empty shells).
 *
 * Intentional discards are unaffected — the active-workout cancel flow deletes the
 * session immediately, so there is nothing left for this sweep to save.
 */
class AutoSaveAbandonedSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(userId: Long) {
        val abandoned = sessionRepository.unfinishedSessions(userId)
        if (abandoned.isEmpty()) return

        val emptyOnes = mutableListOf<Long>()
        for (sws in abandoned) {
            val lastSetAt = sws.exercises
                .flatMap { it.sets }
                .maxOfOrNull { it.completedAt }
            if (lastSetAt == null) {
                emptyOnes += sws.session.id
            } else {
                sessionRepository.updateSession(sws.session.copy(completedAt = lastSetAt))
            }
        }
        if (emptyOnes.isNotEmpty()) sessionRepository.deleteSessions(emptyOnes)
    }
}
