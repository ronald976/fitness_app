package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.dao.SessionExerciseWithSets
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.domain.suggestion.FatigueContext
import com.fitness.app.domain.suggestion.FatigueSlot
import com.fitness.app.domain.suggestion.PreviousSet
import com.fitness.app.domain.suggestion.ProgressionStrategy
import com.fitness.app.domain.suggestion.SessionPosition
import com.fitness.app.domain.suggestion.Suggestion
import com.fitness.app.domain.suggestion.TargetSpec
import com.fitness.app.domain.suggestion.sessionPositions
import javax.inject.Inject

class GetSuggestionUseCase @Inject constructor(
    private val planDao: PlanDao,
    private val sessionRepository: SessionRepository,
    private val strategy: ProgressionStrategy
) {
    /**
     * [todayPosition] is where this exercise sits in the session being planned — the caller
     * knows the running order, so it passes it in. The matching position for the *previous*
     * session is looked up here, since that's a property of the history this use case already
     * owns. Callers with no ordering context can leave it at the default and get the plain
     * progression.
     */
    suspend operator fun invoke(
        userId: Long,
        plannedExerciseId: Long,
        actualExerciseId: Long? = null,
        todayPosition: SessionPosition = SessionPosition()
    ): Suggestion? {
        val planned = planDao.getPlannedExercise(plannedExerciseId) ?: return null
        val target = TargetSpec(
            targetSets = planned.targetSets,
            repLow = planned.repLow,
            repHigh = planned.repHigh,
            weightIncrementKg = planned.weightIncrementKg
        )

        val last = sessionRepository.lastSessionExerciseFor(userId, actualExerciseId ?: planned.exerciseId)
        val previous = last?.sets
            ?.filter { !it.isWarmup && it.reps > 0 }
            ?.sortedBy { it.setIndex }
            ?.map { PreviousSet(weightKg = it.weightKg, reps = it.reps) }
            .orEmpty()

        val context = FatigueContext(
            today = todayPosition,
            previous = last?.let { positionInItsSession(it) } ?: SessionPosition()
        )

        return strategy.suggest(target, previous, context)
    }

    /**
     * Rebuild the session [last] was logged in and find where it sat, counting the working
     * sets actually recorded (not planned) for earlier same-muscle exercises. Returns the
     * default position if the session can't be resolved, which makes the fatigue comparison
     * a no-op rather than a wrong guess.
     */
    private suspend fun positionInItsSession(last: SessionExerciseWithSets): SessionPosition {
        val sessionId = last.sessionExercise.sessionId
        val session = sessionRepository.getSessionWithExercises(sessionId) ?: return SessionPosition()
        val ordered = session.exercises.sortedBy { it.sessionExercise.orderIdx }
        val idx = ordered.indexOfFirst { it.sessionExercise.id == last.sessionExercise.id }
        if (idx < 0) return SessionPosition()

        val slots = ordered.map { sxs ->
            FatigueSlot(
                primaryMuscle = sxs.exercise.primaryMuscle,
                workingSets = sxs.sets.count { !it.isWarmup && it.reps > 0 }
            )
        }
        return sessionPositions(slots)[idx]
    }
}
