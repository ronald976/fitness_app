package com.fitness.app.data.repository

import com.fitness.app.data.db.dao.ExerciseDao
import com.fitness.app.data.db.entities.ExerciseEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    fun observeAll(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    suspend fun getById(id: Long): ExerciseEntity? = exerciseDao.getById(id)

    suspend fun getAlternatives(exerciseId: Long): List<ExerciseEntity> =
        exerciseDao.getAlternatives(exerciseId)
}
