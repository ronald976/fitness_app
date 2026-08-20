package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

sealed class PrResult {
    object None : PrResult()
    /** Same weight as a previous best, but more reps. */
    data class RepPr(val weightKg: Double, val reps: Int, val previousReps: Int) : PrResult()
    /** Heavier than any previous set, with reps within the valid rep range for this exercise. */
    data class WeightPr(val weightKg: Double, val reps: Int, val previousBestKg: Double) : PrResult()
    /** This session's whole working sequence beats every prior session's (by total volume). */
    data class SessionVolumePr(
        val totalVolumeKg: Double,
        val previousBestVolumeKg: Double,
        val setCount: Int
    ) : PrResult()
}

/** Weight/reps pair used for session-sequence comparisons. */
data class WorkingSet(val weightKg: Double, val reps: Int)

/**
 * Detects whether a just-logged set counts as a PR for its exercise by the current user.
 *
 * Rules:
 *  - **Rep PR** — the user has lifted this exact weight before, and the new set beats the best prior
 *    rep count at *this weight or heavier*. The "or heavier" part matters: matching (or barely
 *    beating) an old rep count at a lighter weight is a step backwards, not a record.
 *  - **Weight PR** — the new weight is strictly greater than any prior working weight for this exercise,
 *    AND the rep count falls within the exercise's target range (defaults to >= 3 if no plan is backing the set).
 *    A heavier 1- or 2-rep squeeze is not counted, per user preference.
 *  - **Session-volume PR** — no single set is a PR, but this session's working sequence as a whole
 *    is strictly better than any prior session's: higher total volume (capped at the best 4 working
 *    sets per session, so extra/warmup-ish sets can't inflate it) without dropping below the
 *    all-time top weight (3×80×8 doesn't beat a lone 100×1).
 */
class DetectPrUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val planDao: PlanDao
) {
    suspend operator fun invoke(
        userId: Long,
        exerciseId: Long,
        plannedExerciseId: Long?,
        loggedSetId: Long,
        sessionId: Long = -1L,
        sessionExerciseId: Long = -1L
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

        val singleSet = detectSingleSetPr(
            logged = WorkingSet(loggedSet.weightKg, loggedSet.reps),
            priors = priors.map { WorkingSet(it.weightKg, it.reps) },
            targetRepRange = plannedExerciseRange(plannedExerciseId)
        )
        if (singleSet != PrResult.None) return singleSet

        if (sessionId <= 0L || sessionExerciseId <= 0L) return PrResult.None
        return detectSessionVolumePr(userId, exerciseId, sessionId, sessionExerciseId)
    }

    private suspend fun detectSessionVolumePr(
        userId: Long,
        exerciseId: Long,
        sessionId: Long,
        sessionExerciseId: Long
    ): PrResult {
        val current = sessionRepository.setsFor(sessionExerciseId)
            .filter { !it.isWarmup && it.reps > 0 && it.weightKg > 0 && !it.excludeFromPr }
            .map { WorkingSet(it.weightKg, it.reps) }
        val priorBySession = sessionRepository
            .workingSetsBySession(userId, exerciseId, excludeSessionId = sessionId)
            .groupBy({ it.sessionId }, { WorkingSet(it.weightKg, it.reps) })
        return detectSequencePr(current, priorBySession.values)
    }

    private suspend fun plannedExerciseRange(id: Long?): IntRange? {
        if (id == null) return null
        val pe = planDao.getPlannedExercise(id) ?: return null
        return pe.repLow..pe.repHigh
    }

    companion object {
        private val DEFAULT_VALID_RANGE = 3..Int.MAX_VALUE

        /**
         * Pure single-set comparison against [priors] (all of this user's prior sets for the
         * exercise, warmups and excluded sets already filtered out).
         *
         * The rep-PR test deliberately looks at every prior set at **this weight or heavier**,
         * not just the ones at exactly this weight. Backing the weight off and repeating an old
         * rep count is easier than what the user already did, so it isn't a record — only
         * genuinely out-repping everything they've done at this load or above is.
         */
        fun detectSingleSetPr(
            logged: WorkingSet,
            priors: List<WorkingSet>,
            targetRepRange: IntRange?
        ): PrResult {
            if (priors.isEmpty()) return PrResult.None

            // A rep PR needs a baseline at this exact weight — without one there's nothing
            // to compare reps against, and it's a weight PR (or nothing) instead.
            if (priors.any { it.weightKg == logged.weightKg }) {
                val bestRepsAtOrAbove = priors
                    .filter { it.weightKg >= logged.weightKg }
                    .maxOfOrNull { it.reps } ?: 0
                if (logged.reps > bestRepsAtOrAbove) {
                    return PrResult.RepPr(logged.weightKg, logged.reps, bestRepsAtOrAbove)
                }
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

        /** Only the best [MAX_SEQUENCE_SETS] sets per session count toward sequence volume,
         *  so logging extra back-off/warmup sets can't fake a record. */
        const val MAX_SEQUENCE_SETS = 4

        /**
         * Pure sequence comparison: the current session's working sets are a PR when their
         * total volume (best 4 sets) strictly exceeds every prior session's, AND the current
         * top weight is at least the all-time top weight — so e.g. 3×80×8 never "beats" a
         * session history containing a 100×1.
         */
        fun detectSequencePr(
            current: List<WorkingSet>,
            priorSessions: Collection<List<WorkingSet>>
        ): PrResult {
            if (priorSessions.isEmpty() || priorSessions.all { it.isEmpty() }) return PrResult.None

            val currentTop = current
                .sortedByDescending { it.weightKg * it.reps }
                .take(MAX_SEQUENCE_SETS)
            // A single set isn't a sequence — single-set improvements are covered by
            // the rep/weight PR rules above.
            if (currentTop.size < 2) return PrResult.None

            val currentVolume = currentTop.sumOf { it.weightKg * it.reps }
            val currentMaxWeight = currentTop.maxOf { it.weightKg }

            val bestPriorVolume = priorSessions.maxOf { session ->
                session.sortedByDescending { it.weightKg * it.reps }
                    .take(MAX_SEQUENCE_SETS)
                    .sumOf { it.weightKg * it.reps }
            }
            val allTimeMaxWeight = priorSessions.flatten().maxOf { it.weightKg }

            return if (currentVolume > bestPriorVolume && currentMaxWeight >= allTimeMaxWeight) {
                PrResult.SessionVolumePr(
                    totalVolumeKg = currentVolume,
                    previousBestVolumeKg = bestPriorVolume,
                    setCount = currentTop.size
                )
            } else PrResult.None
        }
    }
}
