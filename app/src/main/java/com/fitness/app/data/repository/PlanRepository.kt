package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.dao.PlanDayWithExercises
import com.fitness.app.data.db.dao.PlanWithDays
import com.fitness.app.data.db.entities.PlanDayEntity
import com.fitness.app.data.db.entities.PlanEntity
import com.fitness.app.data.db.entities.PlannedExerciseEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PlanRepository @Inject constructor(
    private val planDao: PlanDao
) {
    fun observeAll(): Flow<List<PlanEntity>> = planDao.observeAll()

    fun observePlan(id: Long): Flow<PlanWithDays?> = planDao.observeWithDays(id)

    suspend fun getPlan(id: Long): PlanWithDays? = planDao.getWithDays(id)

    suspend fun getDay(planDayId: Long): PlanDayWithExercises? =
        planDao.getDayWithExercises(planDayId)

    suspend fun clonePlan(source: PlanWithDays, newName: String): Long {
        val now = System.currentTimeMillis()
        val newPlanId = planDao.insertPlan(
            PlanEntity(
                name = newName,
                description = source.plan.description,
                isTemplate = false,
                createdAt = now
            )
        )
        source.days.sortedBy { it.day.dayIndex }.forEachIndexed { dayIdx, dwe ->
            val newDayId = planDao.insertDay(
                PlanDayEntity(
                    planId = newPlanId,
                    dayIndex = dayIdx,
                    name = dwe.day.name
                )
            )
            dwe.exercises.sortedBy { it.planned.orderIdx }.forEachIndexed { exIdx, pwe ->
                planDao.insertPlannedExercise(
                    pwe.planned.copy(
                        id = 0,
                        planDayId = newDayId,
                        orderIdx = exIdx
                    )
                )
            }
        }
        return newPlanId
    }

    suspend fun updatePlannedExercise(planned: PlannedExerciseEntity) {
        planDao.updatePlannedExercise(planned)
    }

    suspend fun addPlannedExerciseToDay(
        planDayId: Long,
        exerciseId: Long,
        targetSets: Int,
        repLow: Int,
        repHigh: Int,
        restSec: Int,
        weightIncrementKg: Double
    ): Long {
        val nextOrder = (planDao.maxOrderIdxForDay(planDayId) ?: -1) + 1
        return planDao.insertPlannedExercise(
            PlannedExerciseEntity(
                planDayId = planDayId,
                exerciseId = exerciseId,
                orderIdx = nextOrder,
                targetSets = targetSets,
                repLow = repLow,
                repHigh = repHigh,
                restSec = restSec,
                weightIncrementKg = weightIncrementKg
            )
        )
    }

    suspend fun deletePlannedExercise(id: Long) = planDao.deletePlannedExercise(id)

    suspend fun reorderDay(planDayId: Long, orderedPlannedIds: List<Long>) {
        orderedPlannedIds.forEachIndexed { idx, id ->
            val pe = planDao.getPlannedExercise(id) ?: return@forEachIndexed
            if (pe.orderIdx != idx) planDao.updatePlannedExercise(pe.copy(orderIdx = idx))
        }
    }
}
