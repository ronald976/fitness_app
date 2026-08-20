package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.DeferredExerciseDao
import com.fitness.app.data.db.entities.DeferredExerciseEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DeferredExerciseRepository @Inject constructor(
    private val deferredExerciseDao: DeferredExerciseDao
) {
    suspend fun insert(row: DeferredExerciseEntity): Long = deferredExerciseDao.insert(row)

    suspend fun forUser(userId: Long): List<DeferredExerciseEntity> =
        deferredExerciseDao.forUser(userId)

    suspend fun deleteByIds(ids: List<Long>) = deferredExerciseDao.deleteByIds(ids)

    fun observeCountForUser(userId: Long): Flow<Int> =
        deferredExerciseDao.observeCountForUser(userId)
}
