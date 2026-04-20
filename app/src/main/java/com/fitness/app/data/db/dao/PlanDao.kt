package com.fitness.app.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.PlanDayEntity
import com.fitness.app.data.db.entities.PlanEntity
import com.fitness.app.data.db.entities.PlannedExerciseEntity
import kotlinx.coroutines.flow.Flow

data class PlannedExerciseWithExercise(
    @Embedded val planned: PlannedExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity
)

data class PlanDayWithExercises(
    @Embedded val day: PlanDayEntity,
    @Relation(
        entity = PlannedExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "planDayId"
    )
    val exercises: List<PlannedExerciseWithExercise>
)

data class PlanWithDays(
    @Embedded val plan: PlanEntity,
    @Relation(
        entity = PlanDayEntity::class,
        parentColumn = "id",
        entityColumn = "planId"
    )
    val days: List<PlanDayWithExercises>
)

@Dao
interface PlanDao {

    @Query("SELECT * FROM plans ORDER BY isTemplate DESC, name ASC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getById(id: Long): PlanEntity?

    @Transaction
    @Query("SELECT * FROM plans WHERE id = :id")
    fun observeWithDays(id: Long): Flow<PlanWithDays?>

    @Transaction
    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getWithDays(id: Long): PlanWithDays?

    @Transaction
    @Query("SELECT * FROM plan_days WHERE id = :planDayId")
    suspend fun getDayWithExercises(planDayId: Long): PlanDayWithExercises?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(day: PlanDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedExercise(planned: PlannedExerciseEntity): Long

    @Update
    suspend fun updatePlannedExercise(planned: PlannedExerciseEntity)

    @Query("DELETE FROM planned_exercises WHERE id = :id")
    suspend fun deletePlannedExercise(id: Long)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun count(): Int

    @Query("SELECT * FROM planned_exercises WHERE id = :id")
    suspend fun getPlannedExercise(id: Long): PlannedExerciseEntity?

    @Query("SELECT MAX(orderIdx) FROM planned_exercises WHERE planDayId = :planDayId")
    suspend fun maxOrderIdxForDay(planDayId: Long): Int?
}
