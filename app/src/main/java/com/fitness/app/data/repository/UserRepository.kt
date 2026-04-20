package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.UserDao
import com.fitness.app.data.db.entities.UserEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserRepository @Inject constructor(
    private val dao: UserDao
) {
    fun observeAll(): Flow<List<UserEntity>> = dao.observeAll()
    suspend fun getAll(): List<UserEntity> = dao.getAll()
    suspend fun getById(id: Long): UserEntity? = dao.getById(id)
    suspend fun getByName(name: String): UserEntity? = dao.getByName(name)
    suspend fun insert(user: UserEntity): Long = dao.insert(user)
}
