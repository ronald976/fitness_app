package com.fitness.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitness.app.data.db.DatabaseSeeder
import com.fitness.app.data.db.FitnessDatabase
import com.fitness.app.data.db.dao.AppStateDao
import com.fitness.app.data.db.dao.ExerciseDao
import com.fitness.app.data.db.dao.PlanDao
import com.fitness.app.data.db.dao.SessionDao
import com.fitness.app.data.db.dao.UserDao
import com.fitness.app.data.db.dao.UserPrefsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v11 added superset grouping — adds a nullable supersetGroupId column to the
     *  planned_exercises and session_exercises tables. Migrate non-destructively so users
     *  keep their logged sessions. */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE planned_exercises ADD COLUMN supersetGroupId INTEGER")
            db.execSQL("ALTER TABLE session_exercises ADD COLUMN supersetGroupId INTEGER")
        }
    }

    /** v12 ships fresh seed data (new exercises, plan-level superset pairings, missing
     *  history entries). Schema is unchanged, so the migration just re-imports by clearing
     *  the rows we own; once empty, [DatabaseSeeder.seedIfEmpty] re-runs on the next open. */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Order matters: child rows first to avoid FK violations.
            db.execSQL("DELETE FROM set_logs")
            db.execSQL("DELETE FROM session_exercises")
            db.execSQL("DELETE FROM sessions")
            db.execSQL("DELETE FROM planned_exercises")
            db.execSQL("DELETE FROM plan_days")
            db.execSQL("DELETE FROM plans")
            db.execSQL("DELETE FROM exercise_alternatives")
            db.execSQL("DELETE FROM exercises")
            db.execSQL("DELETE FROM user_prefs")
            db.execSQL("DELETE FROM app_state")
            db.execSQL("DELETE FROM users")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitnessDatabase {
        lateinit var db: FitnessDatabase
        db = Room.databaseBuilder(context, FitnessDatabase::class.java, FitnessDatabase.NAME)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(conn: SupportSQLiteDatabase) {
                    // onOpen fires on every open, including the first one right after onCreate,
                    // so a single seed path here handles fresh installs and restored backups
                    // without the race that two parallel callbacks would cause.
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        DatabaseSeeder(context, db).seedIfEmpty()
                    }
                }
            })
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
            .fallbackToDestructiveMigration()
            .build()
        return db
    }

    @Provides fun providePlanDao(db: FitnessDatabase): PlanDao = db.planDao()
    @Provides fun provideExerciseDao(db: FitnessDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideSessionDao(db: FitnessDatabase): SessionDao = db.sessionDao()
    @Provides fun provideUserPrefsDao(db: FitnessDatabase): UserPrefsDao = db.userPrefsDao()
    @Provides fun provideUserDao(db: FitnessDatabase): UserDao = db.userDao()
    @Provides fun provideAppStateDao(db: FitnessDatabase): AppStateDao = db.appStateDao()
}
