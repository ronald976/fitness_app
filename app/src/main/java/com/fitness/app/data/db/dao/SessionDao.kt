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
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import kotlinx.coroutines.flow.Flow

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "actualExerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetLogEntity>
)

data class SessionWithExercises(
    @Embedded val session: SessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val exercises: List<SessionExerciseWithSets>
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLog(set: SetLogEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Update
    suspend fun updateSessionExercise(sessionExercise: SessionExerciseEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionWithExercises(id: Long): SessionWithExercises?

    @Transaction
    @Query("""
        SELECT * FROM sessions
        WHERE completedAt IS NOT NULL AND userId = :userId
        ORDER BY startedAt DESC
        LIMIT :limit
    """)
    fun observeRecent(userId: Long, limit: Int = 50): Flow<List<SessionWithExercises>>

    @Query("""
        SELECT * FROM sessions
        WHERE completedAt IS NULL AND userId = :userId
        ORDER BY startedAt DESC LIMIT 1
    """)
    suspend fun findActiveSession(userId: Long): SessionEntity?

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

    /**
     * Most recent completed SessionExercise for a given planned exercise (scoped to user), with sets.
     * Used by the progression strategy.
     */
    @Transaction
    @Query("""
        SELECT se.* FROM session_exercises se
        INNER JOIN sessions s ON s.id = se.sessionId
        WHERE se.plannedExerciseId = :plannedExerciseId
          AND s.userId = :userId
          AND s.completedAt IS NOT NULL
        ORDER BY s.completedAt DESC
        LIMIT 1
    """)
    suspend fun lastSessionExerciseFor(userId: Long, plannedExerciseId: Long): SessionExerciseWithSets?

    @Query("SELECT * FROM set_logs WHERE sessionExerciseId = :sessionExerciseId ORDER BY setIndex ASC")
    fun observeSetsFor(sessionExerciseId: Long): Flow<List<SetLogEntity>>

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: Long)

    /**
     * All prior (non-warmup) sets for a given exercise by a user, excluding the named session
     * (used to compare a just-logged set against history). Newest first.
     */
    @Query("""
        SELECT sl.* FROM set_logs sl
        INNER JOIN session_exercises se ON se.id = sl.sessionExerciseId
        INNER JOIN sessions s ON s.id = se.sessionId
        WHERE se.actualExerciseId = :exerciseId
          AND s.userId = :userId
          AND sl.isWarmup = 0
          AND sl.id != :excludeSetId
        ORDER BY sl.completedAt DESC
    """)
    suspend fun priorSetsForExercise(
        userId: Long,
        exerciseId: Long,
        excludeSetId: Long
    ): List<SetLogEntity>
}
