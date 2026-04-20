package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.entities.SetLogEntity
import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

sealed class PrResult {
    object None : PrResult()
    /** Same weight as a previous best, but more reps. */
    data class RepPr(val weightKg: Double, val reps: Int, val previousReps: Int) : PrResult()
    /** Heavier than any previous set, with reps within the valid rep range for this exercise. */
    data class WeightPr(val weightKg: Double, val reps: Int, val previousBestKg: Double) : PrResult()
}

/**
 * Detects whether a just-logged set counts as a PR for its exercise by the current user.
 *
 * Rules:
 *  - **Rep PR** — the user has lifted this exact weight before, and the new set exceeds the best prior rep count at that weight.
 *  - **Weight PR** — the new weight is strictly greater than any prior working weight for this exercise,
 *    AND the rep count falls within the exercise's target range (defaults to >= 3 if no plan is backing the set).
 *    A heavier 1- or 2-rep squeeze is not counted, per user preference.
 */
class DetectPrUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val planDao: PlanDao
) {
    suspend operator fun invoke(
        userId: Long,
        exerciseId: Long,
        plannedExerciseId: Long?,
        loggedSetId: Long
    ): PrResult {
        val loggedSet = sessionRepository.priorSetsForExercise(
            userId = userId,
            exerciseId = exerciseId,
            excludeSetId = -1L
        ).firstOrNull { it.id == loggedSetId } ?: return PrResult.None

        val priors = sessionRepository.priorSetsForExercise(
            userId = userId,
            exerciseId = exerciseId,
            excludeSetId = loggedSetId
        )

        return detect(loggedSet, priors, plannedExerciseRange(plannedExerciseId))
    }

    private suspend fun plannedExerciseRange(id: Long?): IntRange? {
        if (id == null) return null
        val pe = planDao.getPlannedExercise(id) ?: return null
        return pe.repLow..pe.repHigh
    }

    private fun detect(
        logged: SetLogEntity,
        priors: List<SetLogEntity>,
        targetRepRange: IntRange?
    ): PrResult {
        if (priors.isEmpty()) return PrResult.None

        val sameWeight = priors.filter { it.weightKg == logged.weightKg }
        val priorRepsAtWeight = sameWeight.maxOfOrNull { it.reps } ?: 0
        if (sameWeight.isNotEmpty() && logged.reps > priorRepsAtWeight) {
            return PrResult.RepPr(logged.weightKg, logged.reps, priorRepsAtWeight)
        }

        val priorMaxWeight = priors.maxOf { it.weightKg }
        if (logged.weightKg > priorMaxWeight) {
            val validRange = targetRepRange ?: DEFAULT_VALID_RANGE
            if (logged.reps >= validRange.first) {
                return PrResult.WeightPr(logged.weightKg, logged.reps, priorMaxWeight)
            }
        }

        return PrResult.None
    }

    private companion object {
        val DEFAULT_VALID_RANGE = 3..Int.MAX_VALUE
    }
}
