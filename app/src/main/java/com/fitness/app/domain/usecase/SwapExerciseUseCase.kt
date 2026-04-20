package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.dao.SessionDao
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

class SwapExerciseUseCase @Inject constructor(
    private val sessionDao: SessionDao,
    private val planDao: PlanDao,
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository
) {
    /**
     * Swap the current session exercise to a new exercise.
     * If [alsoUpdatePlan] is true and the row is plan-backed, the planned exercise is
     * rewritten to the new exercise so future sessions default to it.
     */
    suspend operator fun invoke(
        sessionExerciseId: Long,
        newExerciseId: Long,
        alsoUpdatePlan: Boolean
    ) {
        val sessionExercise = sessionDao.getSessionExercise(sessionExerciseId) ?: return
        sessionRepository.updateSessionExercise(
            sessionExercise.copy(actualExerciseId = newExerciseId)
        )

        if (alsoUpdatePlan && sessionExercise.plannedExerciseId != null) {
            val planned = planDao.getPlannedExercise(sessionExercise.plannedExerciseId) ?: return
            planRepository.updatePlannedExercise(planned.copy(exerciseId = newExerciseId))
        }
    }
}
