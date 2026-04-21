package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.SessionDao
import com.fitness.app.data.db.dao.SessionExerciseWithSets
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {
    fun observeRecent(userId: Long, limit: Int = 50): Flow<List<SessionWithExercises>> =
        sessionDao.observeRecent(userId, limit)

    suspend fun allCompletedForExport(userId: Long): List<SessionWithExercises> =
        sessionDao.allCompletedForExport(userId)

    fun observeSession(id: Long): Flow<SessionWithExercises?> =
        sessionDao.observeSession(id)

    suspend fun getSessionWithExercises(id: Long): SessionWithExercises? =
        sessionDao.getSessionWithExercises(id)

    suspend fun findActiveSession(userId: Long): SessionEntity? =
        sessionDao.findActiveSession(userId)

    suspend fun insertSession(session: SessionEntity): Long = sessionDao.insertSession(session)

    suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity): Long =
        sessionDao.insertSessionExercise(sessionExercise)

    suspend fun updateSessionExercise(sessionExercise: SessionExerciseEntity) {
        sessionDao.updateSessionExercise(sessionExercise)
    }

    suspend fun updateSession(session: SessionEntity) = sessionDao.updateSession(session)

    suspend fun insertSetLog(set: SetLogEntity): Long = sessionDao.insertSetLog(set)

    suspend fun deleteSet(id: Long) = sessionDao.deleteSet(id)

    suspend fun lastSessionExerciseFor(userId: Long, plannedExerciseId: Long): SessionExerciseWithSets? =
        sessionDao.lastSessionExerciseFor(userId, plannedExerciseId)

    fun observeSetsFor(sessionExerciseId: Long): Flow<List<SetLogEntity>> =
        sessionDao.observeSetsFor(sessionExerciseId)

    suspend fun priorSetsForExercise(
        userId: Long,
        exerciseId: Long,
        excludeSetId: Long
    ): List<SetLogEntity> = sessionDao.priorSetsForExercise(userId, exerciseId, excludeSetId)
}
