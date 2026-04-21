package com.fitness.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fitness.app.data.db.dao.AppStateDao
import com.fitness.app.data.db.dao.ExerciseDao
import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.dao.SessionDao
import com.fitness.app.data.db.dao.UserDao
import com.fitness.app.data.db.dao.UserPrefsDao
import com.fitness.app.data.db.entities.AppStateEntity
import com.fitness.app.data.db.entities.ExerciseAlternativeEntity
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.PlanDayEntity
import com.fitness.app.data.db.entities.PlanEntity
import com.fitness.app.data.db.entities.PlannedExerciseEntity
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import com.fitness.app.data.db.entities.UserEntity
import com.fitness.app.data.db.entities.UserPrefsEntity

@Database(
    entities = [
        UserEntity::class,
        AppStateEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        ExerciseEntity::class,
        ExerciseAlternativeEntity::class,
        PlannedExerciseEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        UserPrefsEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun userPrefsDao(): UserPrefsDao
    abstract fun userDao(): UserDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        const val NAME = "fitness.db"
    }
}
