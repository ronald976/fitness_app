package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.DashboardSetRow
import com.fitness.app.data.db.dao.OutlierSetRow
import com.fitness.app.data.db.dao.PrCandidateSetRow
import com.fitness.app.data.db.dao.SessionDao
import com.fitness.app.data.db.dao.SessionExerciseWithSets
import com.fitness.app.data.db.dao.SessionSetRow
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.db.dao.TrainingDayRow
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

    suspend fun unfinishedSessions(userId: Long): List<SessionWithExercises> =
        sessionDao.unfinishedSessions(userId)

    suspend fun insertSession(session: SessionEntity): Long = sessionDao.insertSession(session)

    suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity): Long =
        sessionDao.insertSessionExercise(sessionExercise)

    suspend fun updateSessionExercise(sessionExercise: SessionExerciseEntity) {
        sessionDao.updateSessionExercise(sessionExercise)
    }

    suspend fun updateSession(session: SessionEntity) = sessionDao.updateSession(session)

    suspend fun insertSetLog(set: SetLogEntity): Long = sessionDao.insertSetLog(set)

    suspend fun deleteSet(id: Long) = sessionDao.deleteSet(id)

    suspend fun deleteSessionExercise(id: Long) = sessionDao.deleteSessionExercise(id)

    suspend fun deleteSessions(ids: List<Long>) = sessionDao.deleteSessions(ids)

    suspend fun deleteAllForUser(userId: Long) = sessionDao.deleteAllForUser(userId)

    suspend fun lastSessionExerciseFor(userId: Long, exerciseId: Long): SessionExerciseWithSets? =
        sessionDao.lastSessionExerciseFor(userId, exerciseId)

    suspend fun bestPriorSetFor(userId: Long, exerciseId: Long): SetLogEntity? =
        sessionDao.bestPriorSetFor(userId, exerciseId)

    suspend fun topPrSetsFor(userId: Long, exerciseId: Long, limit: Int = 10): List<PrCandidateSetRow> =
        sessionDao.topPrSetsFor(userId, exerciseId, limit)

    fun observeSetsFor(sessionExerciseId: Long): Flow<List<SetLogEntity>> =
        sessionDao.observeSetsFor(sessionExerciseId)

    suspend fun setsFor(sessionExerciseId: Long): List<SetLogEntity> =
        sessionDao.setsFor(sessionExerciseId)

    suspend fun workingSetsBySession(
        userId: Long,
        exerciseId: Long,
        excludeSessionId: Long
    ): List<SessionSetRow> = sessionDao.workingSetsBySession(userId, exerciseId, excludeSessionId)

    suspend fun priorSetsForExercise(
        userId: Long,
        exerciseId: Long,
        excludeSetId: Long
    ): List<SetLogEntity> = sessionDao.priorSetsForExercise(userId, exerciseId, excludeSetId)

    suspend fun allSetsForDashboard(userId: Long): List<DashboardSetRow> =
        sessionDao.allSetsForDashboard(userId)

    suspend fun allSetsForOutlierReview(userId: Long): List<OutlierSetRow> =
        sessionDao.allSetsForOutlierReview(userId)

    suspend fun setOutlierFlags(id: Long, exclude: Boolean, reviewed: Boolean) =
        sessionDao.setOutlierFlags(id, exclude, reviewed)

    suspend fun updateSetValues(id: Long, weightKg: Double, reps: Int, note: String) =
        sessionDao.updateSetValues(id, weightKg, reps, note)

    suspend fun trainingDays(userId: Long): List<TrainingDayRow> =
        sessionDao.trainingDays(userId)
}
