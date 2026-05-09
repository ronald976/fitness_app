package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.SessionDao
import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

/**
 * Removes an exercise from the active session, optionally also removing it from the
 * underlying plan day so future sessions don't bring it back. Mirrors the pattern of
 * [SwapExerciseUseCase] — delete from the session unconditionally, mutate the plan only
 * when the user opts in via the "Also update plan" toggle.
 *
 * The session_exercises FK cascades into set_logs, so the call below also removes any
 * already-logged sets for this exercise in this session.
 */
class RemoveExerciseUseCase @Inject constructor(
    private val sessionDao: SessionDao,
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository
) {
    suspend operator fun invoke(
        sessionExerciseId: Long,
        alsoUpdatePlan: Boolean
    ) {
        val sessionExercise = sessionDao.getSessionExercise(sessionExerciseId) ?: return
        sessionRepository.deleteSessionExercise(sessionExerciseId)

        if (alsoUpdatePlan && sessionExercise.plannedExerciseId != null) {
            planRepository.deletePlannedExercise(sessionExercise.plannedExerciseId)
        }
    }
}
