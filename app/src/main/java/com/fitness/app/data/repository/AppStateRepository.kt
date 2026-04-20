package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.AppStateDao
import com.fitness.app.data.db.entities.AppStateEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AppStateRepository @Inject constructor(
    private val dao: AppStateDao
) {
    fun observe(): Flow<AppStateEntity?> = dao.observe()
    suspend fun get(): AppStateEntity? = dao.get()
    suspend fun upsert(state: AppStateEntity) = dao.upsert(state)
    suspend fun setCurrentUser(userId: Long?) = dao.setCurrentUser(userId)
}
