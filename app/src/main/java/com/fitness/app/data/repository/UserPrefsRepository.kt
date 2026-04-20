package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.UserPrefsDao
import com.fitness.app.data.db.entities.UserPrefsEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserPrefsRepository @Inject constructor(
    private val dao: UserPrefsDao
) {
    fun observe(userId: Long): Flow<UserPrefsEntity?> = dao.observe(userId)

    suspend fun get(userId: Long): UserPrefsEntity =
        dao.get(userId) ?: UserPrefsEntity(userId = userId).also { dao.upsert(it) }

    suspend fun upsert(prefs: UserPrefsEntity) = dao.upsert(prefs)

    suspend fun setActivePlan(userId: Long, planId: Long?) {
        val existing = get(userId)
        dao.upsert(existing.copy(activePlanId = planId))
    }
}
