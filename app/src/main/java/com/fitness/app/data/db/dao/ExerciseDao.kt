package com.fitness.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitness.app.data.db.entities.ExerciseAlternativeEntity
import com.fitness.app.data.db.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN exercise_alternatives a ON a.alternativeExerciseId = e.id
        WHERE a.exerciseId = :exerciseId
        ORDER BY a.orderIdx ASC
    """)
    suspend fun getAlternatives(exerciseId: Long): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlternative(alternative: ExerciseAlternativeEntity)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int
}
