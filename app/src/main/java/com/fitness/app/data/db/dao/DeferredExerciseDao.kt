package com.fitness.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fitness.app.data.db.entities.DeferredExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeferredExerciseDao {

    @Insert
    suspend fun insert(row: DeferredExerciseEntity): Long

    @Query("SELECT * FROM deferred_exercises WHERE userId = :userId ORDER BY createdAt ASC")
    suspend fun forUser(userId: Long): List<DeferredExerciseEntity>

    @Query("DELETE FROM deferred_exercises WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM deferred_exercises WHERE userId = :userId")
    fun observeCountForUser(userId: Long): Flow<Int>
}
