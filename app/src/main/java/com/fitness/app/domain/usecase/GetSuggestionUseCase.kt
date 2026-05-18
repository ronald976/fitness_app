package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.domain.suggestion.PreviousSet
import com.fitness.app.domain.suggestion.ProgressionStrategy
import com.fitness.app.domain.suggestion.Suggestion
import com.fitness.app.domain.suggestion.TargetSpec
import javax.inject.Inject

class GetSuggestionUseCase @Inject constructor(
    private val planDao: PlanDao,
    private val sessionRepository: SessionRepository,
    private val strategy: ProgressionStrategy
) {
    suspend operator fun invoke(
        userId: Long,
        plannedExerciseId: Long,
        actualExerciseId: Long? = null
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

        return strategy.suggest(target, previous)
    }
}
