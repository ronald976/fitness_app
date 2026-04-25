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

    suspend fun getAll(): List<ExerciseEntity> = exerciseDao.getAll()

    suspend fun getById(id: Long): ExerciseEntity? = exerciseDao.getById(id)

    suspend fun getAlternatives(exerciseId: Long): List<ExerciseEntity> =
        exerciseDao.getAlternatives(exerciseId)

    /**
     * Creates a new custom exercise with the given name, or returns an existing one whose
     * name matches case-insensitively (after trim) to avoid silent duplicates.
     */
    suspend fun findOrCreateCustom(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val existing = exerciseDao.getAll().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        return exerciseDao.insert(
            ExerciseEntity(
                name = trimmed,
                primaryMuscle = "Other",
                equipment = "Other",
                isCustom = true
            )
        )
    }
}
