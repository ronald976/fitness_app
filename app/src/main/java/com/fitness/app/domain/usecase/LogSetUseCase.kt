package com.fitness.app.domain.usecase

import com.fitness.app.data.db.entities.SetLogEntity
import com.fitness.app.data.repository.SessionRepository
import javax.inject.Inject

class LogSetUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        sessionExerciseId: Long,
        setIndex: Int,
        weightKg: Double,
        reps: Int,
        rpe: Double? = null,
        isWarmup: Boolean = false,
        note: String = ""
    ): Long {
        return sessionRepository.insertSetLog(
            SetLogEntity(
                sessionExerciseId = sessionExerciseId,
                setIndex = setIndex,
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                isWarmup = isWarmup,
                note = note,
                completedAt = System.currentTimeMillis()
            )
        )
    }
}
