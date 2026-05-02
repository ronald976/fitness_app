package com.fitness.app.domain.usecase

import com.fitness.app.data.repository.PlanRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import javax.inject.Inject

class StartSessionUseCase @Inject constructor(
    private val planRepository: PlanRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(userId: Long, planDayId: Long): Long {
        val day = planRepository.getDay(planDayId)
            ?: error("Plan day $planDayId not found")

        val sessionId = sessionRepository.insertSession(
            SessionEntity(
                userId = userId,
                planDayId = planDayId,
                startedAt = System.currentTimeMillis(),
                completedAt = null
            )
        )

        day.exercises
            .sortedBy { it.planned.orderIdx }
            .forEachIndexed { idx, pwe ->
                sessionRepository.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        plannedExerciseId = pwe.planned.id,
                        actualExerciseId = pwe.exercise.id,
                        orderIdx = idx,
                        supersetGroupId = pwe.planned.supersetGroupId
                    )
                )
            }

        return sessionId
    }
}
