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

    @Transaction
    @Query("""
        SELECT * FROM sessions
        WHERE completedAt IS NOT NULL AND userId = :userId
        ORDER BY startedAt ASC
    """)
    suspend fun allCompletedForExport(userId: Long): List<SessionWithExercises>

    @Query("""
        SELECT * FROM sessions
        WHERE completedAt IS NULL AND userId = :userId
        ORDER BY startedAt DESC LIMIT 1
    """)
    suspend fun findActiveSession(userId: Long): SessionEntity?

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

    /**
     * Most recent completed SessionExercise for a given exercise (scoped to user), with sets.
     * Used by the progression strategy. Matches by `actualExerciseId` so imported history
     * (which has `plannedExerciseId = null`) still drives suggestions.
     * Skips session-exercises whose only sets are warmups or unfinished (reps = 0) entries.
     */
    @Transaction
    @Query("""
        SELECT se.* FROM session_exercises se
        INNER JOIN sessions s ON s.id = se.sessionId
        WHERE se.actualExerciseId = :exerciseId
          AND s.userId = :userId
          AND s.completedAt IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM set_logs sl
              WHERE sl.sessionExerciseId = se.id
                AND sl.isWarmup = 0
                AND sl.reps > 0
          )
        ORDER BY s.completedAt DESC, se.orderIdx ASC
        LIMIT 1
    """)
    suspend fun lastSessionExerciseFor(userId: Long, exerciseId: Long): SessionExerciseWithSets?

    @Query("SELECT * FROM set_logs WHERE sessionExerciseId = :sessionExerciseId ORDER BY setIndex ASC")
    fun observeSetsFor(sessionExerciseId: Long): Flow<List<SetLogEntity>>

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<Long>)

    /**
     * The user's all-time best set for an exercise, ranked by score = weight × reps
     * (tie-broken by heavier weight, then earliest completion).
     * Excludes warmups and unfinished (reps = 0) entries.
     */
    @Query("""
        SELECT sl.* FROM set_logs sl
        INNER JOIN session_exercises se ON se.id = sl.sessionExerciseId
        INNER JOIN sessions s ON s.id = se.sessionId
        WHERE se.actualExerciseId = :exerciseId
          AND s.userId = :userId
          AND s.completedAt IS NOT NULL
          AND sl.isWarmup = 0
          AND sl.reps > 0
          AND sl.weightKg > 0
        ORDER BY (sl.weightKg * sl.reps) DESC, sl.weightKg DESC, sl.completedAt ASC
        LIMIT 1
    """)
    suspend fun bestPriorSetFor(userId: Long, exerciseId: Long): SetLogEntity?

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

    // ── Dashboard queries ─────────────────────────────────────────────────

    /**
     * Every non-warmup set with exercise metadata, for a user's completed sessions.
     * Used by the dashboard to compute progression, volume, balance, PRs, etc.
     */
    @Query("""
        SELECT sl.id, sl.setIndex, sl.weightKg, sl.reps, sl.isWarmup,
               sl.completedAt, sl.sessionExerciseId,
               s.startedAt AS sessionStartedAt, s.sessionType,
               e.id AS exerciseId, e.name AS exerciseName, e.primaryMuscle
        FROM set_logs sl
        INNER JOIN session_exercises se ON se.id = sl.sessionExerciseId
        INNER JOIN sessions s ON s.id = se.sessionId
        INNER JOIN exercises e ON e.id = se.actualExerciseId
        WHERE s.userId = :userId
          AND s.completedAt IS NOT NULL
          AND sl.isWarmup = 0
          AND sl.weightKg > 0
          AND sl.reps > 0
        ORDER BY s.startedAt ASC, se.orderIdx ASC, sl.setIndex ASC
    """)
    suspend fun allSetsForDashboard(userId: Long): List<DashboardSetRow>

    /** Distinct training dates for calendar/frequency. */
    @Query("""
        SELECT DISTINCT s.startedAt / 86400000 AS dayEpoch, s.sessionType
        FROM sessions s
        WHERE s.userId = :userId AND s.completedAt IS NOT NULL
        ORDER BY dayEpoch ASC
    """)
    suspend fun trainingDays(userId: Long): List<TrainingDayRow>
}

/** Flat row for dashboard aggregation — avoids loading full session graph. */
data class DashboardSetRow(
    val id: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val completedAt: Long,
    val sessionExerciseId: Long,
    val sessionStartedAt: Long,
    val sessionType: String?,
    val exerciseId: Long,
    val exerciseName: String,
    val primaryMuscle: String
)

data class TrainingDayRow(
    val dayEpoch: Long,
    val sessionType: String?
)
