package com.fitness.app.domain.usecase

import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.repository.DeferredExerciseRepository
import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

/**
 * Appends any exercises the user pushed to "next session" onto a freshly started [sessionId],
 * then clears the deferral queue. Exercises already present in the session (by actualExerciseId)
 * are skipped — appending a second card would split the volume and double the suggestion — but
 * their deferred rows are still deleted, so a push is consumed exactly once regardless.
 */
class ConsumeDeferredExercisesUseCase @Inject constructor(
    private val deferredRepository: DeferredExerciseRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(userId: Long, sessionId: Long) {
        val deferred = deferredRepository.forUser(userId)
        if (deferred.isEmpty()) return

        val session = sessionRepository.getSessionWithExercises(sessionId) ?: return
        val present = session.exercises
            .map { it.sessionExercise.actualExerciseId }
            .toMutableSet()
        var orderIdx = session.exercises.size

        for (d in deferred) {
            if (present.add(d.exerciseId)) {
                sessionRepository.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        plannedExerciseId = d.plannedExerciseId,
                        actualExerciseId = d.exerciseId,
                        orderIdx = orderIdx++,
                        supersetGroupId = null
                    )
                )
            }
        }
        deferredRepository.deleteByIds(deferred.map { it.id })
    }
}
