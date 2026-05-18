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

    /** v13 adds two flags on set_logs to support the outlier-PR review flow:
     *  excludeFromPr (filter the set out of best/PR queries) and prReviewed
     *  (the user has decided about it, so don't re-prompt). */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE set_logs ADD COLUMN excludeFromPr INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE set_logs ADD COLUMN prReviewed INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v14 adds distinct seed exercises for smith/free-weight variants and cable curls.
     *  The schema is unchanged; existing installs get the rows and their obvious
     *  alternatives without clearing workout history. */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (exerciseCount(db) == 0) return

            insertExercise(db, "Incline Smith Machine Press", "Chest", "Smith")
            insertExercise(db, "Smith Machine Bench Press", "Chest", "Smith")
            insertExercise(db, "Smith Machine Row", "Back", "Smith")
            insertExercise(db, "Smith Machine Hip Thrust", "Legs", "Smith")
            insertExercise(db, "Smith Machine Overhead Press", "Shoulders", "Smith")
            insertExercise(db, "Cable Rear Delt Fly", "Shoulders", "Cable")
            insertExercise(db, "Dumbbell Shrug", "Shoulders", "Dumbbell")
            insertExercise(db, "Assisted Pull-up", "Back", "Machine")
            insertExercise(db, "Assisted Chin-up", "Back", "Machine")
            insertExercise(db, "Cable Bicep Curl", "Arms", "Cable")
            insertExercise(db, "Cable Overhead Tricep Extension", "Arms", "Cable")
            insertExercise(db, "Barbell Overhead Tricep Extension", "Arms", "Barbell")
            insertExercise(db, "Dumbbell Misc", "Other", "Dumbbell")

            db.execSQL("UPDATE exercises SET equipment = 'Free Weight' WHERE name IN ('Leg Press (Free Weight)', 'Calf Raise (Free Weight)')")
            db.execSQL("UPDATE exercises SET equipment = 'Machine' WHERE name IN ('Lat Pulldown', 'Unilateral Lat Pulldown')")
            mergeExerciseAlias(db, "Leg Press", "Leg Press (Machine)")
            mergeExerciseAlias(db, "Leg press free weight", "Leg Press (Free Weight)")
            mergeExerciseAlias(db, "Leg Press Free Weight", "Leg Press (Free Weight)")
            mergeExerciseAlias(db, "Standing Calf Raise", "Calf Raise (Machine)")
            mergeExerciseAlias(db, "Calf raise free weight", "Calf Raise (Free Weight)")
            mergeExerciseAlias(db, "Calf Raise Free Weight", "Calf Raise (Free Weight)")
            db.execSQL("""
                UPDATE planned_exercises
                SET exerciseId = (SELECT id FROM exercises WHERE name = 'Cable Overhead Tricep Extension')
                WHERE exerciseId = (SELECT id FROM exercises WHERE name = 'Overhead Tricep Extension')
                  AND supersetGroupId IS NOT NULL
            """.trimIndent())
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = 'Cable Overhead Tricep Extension')
                WHERE actualExerciseId = (SELECT id FROM exercises WHERE name = 'Overhead Tricep Extension')
                  AND lower(customLabel) LIKE '%cable%'
            """.trimIndent())
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = 'Barbell Overhead Tricep Extension')
                WHERE actualExerciseId = (SELECT id FROM exercises WHERE name = 'Overhead Tricep Extension')
                  AND lower(customLabel) LIKE '%barbell%'
            """.trimIndent())
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = 'Assisted Chin-up')
                WHERE actualExerciseId = (SELECT id FROM exercises WHERE name = 'Pull-up')
                  AND lower(customLabel) LIKE '%chin%'
                  AND (lower(customLabel) LIKE '%ass%' OR lower(customLabel) LIKE '%machine%')
            """.trimIndent())
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = 'Assisted Pull-up')
                WHERE actualExerciseId = (SELECT id FROM exercises WHERE name = 'Pull-up')
                  AND lower(customLabel) LIKE '%pull%'
                  AND (lower(customLabel) LIKE '%ass%' OR lower(customLabel) LIKE '%machine%')
            """.trimIndent())
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = 'Chin-up')
                WHERE actualExerciseId = (SELECT id FROM exercises WHERE name = 'Pull-up')
                  AND lower(customLabel) LIKE '%chin%'
            """.trimIndent())
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = 'Dumbbell Shrug')
                WHERE actualExerciseId = (SELECT id FROM exercises WHERE name = 'Barbell Shrug')
                  AND lower(customLabel) LIKE '%dumbbell%'
            """.trimIndent())

            insertAlternative(db, "Barbell Bench Press", "Smith Machine Bench Press", 0)
            insertAlternative(db, "Smith Machine Bench Press", "Barbell Bench Press", 0)
            insertAlternative(db, "Smith Machine Bench Press", "Dumbbell Bench Press", 1)
            insertAlternative(db, "Smith Machine Bench Press", "Machine Chest Press", 2)
            insertAlternative(db, "Dumbbell Bench Press", "Smith Machine Bench Press", 1)
            insertAlternative(db, "Machine Chest Press", "Smith Machine Bench Press", 1)

            insertAlternative(db, "Incline Barbell Bench Press", "Incline Smith Machine Press", 0)
            insertAlternative(db, "Incline Smith Machine Press", "Incline Barbell Bench Press", 0)
            insertAlternative(db, "Incline Smith Machine Press", "Incline Dumbbell Press", 1)
            insertAlternative(db, "Incline Smith Machine Press", "Machine Chest Press", 2)
            insertAlternative(db, "Incline Dumbbell Press", "Incline Smith Machine Press", 1)

            insertAlternative(db, "Barbell Row", "Smith Machine Row", 0)
            insertAlternative(db, "Smith Machine Row", "Barbell Row", 0)
            insertAlternative(db, "Smith Machine Row", "Dumbbell Row", 1)
            insertAlternative(db, "Smith Machine Row", "Seated Cable Row", 2)
            insertAlternative(db, "Dumbbell Row", "Smith Machine Row", 1)
            insertAlternative(db, "Chest-Supported Row", "Smith Machine Row", 2)

            insertAlternative(db, "Barbell Hip Thrust", "Smith Machine Hip Thrust", 0)
            insertAlternative(db, "Smith Machine Hip Thrust", "Barbell Hip Thrust", 0)

            insertAlternative(db, "Barbell Overhead Press", "Smith Machine Overhead Press", 0)
            insertAlternative(db, "Smith Machine Overhead Press", "Barbell Overhead Press", 0)
            insertAlternative(db, "Smith Machine Overhead Press", "Seated Dumbbell Press", 1)
            insertAlternative(db, "Smith Machine Overhead Press", "Machine Shoulder Press", 2)
            insertAlternative(db, "Machine Shoulder Press", "Smith Machine Overhead Press", 1)

            insertAlternative(db, "Pull-up", "Assisted Pull-up", 0)
            insertAlternative(db, "Chin-up", "Assisted Chin-up", 0)
            insertAlternative(db, "Assisted Pull-up", "Pull-up", 0)
            insertAlternative(db, "Assisted Pull-up", "Assisted Chin-up", 1)
            insertAlternative(db, "Assisted Pull-up", "Lat Pulldown", 2)
            insertAlternative(db, "Assisted Chin-up", "Chin-up", 0)
            insertAlternative(db, "Assisted Chin-up", "Assisted Pull-up", 1)
            insertAlternative(db, "Assisted Chin-up", "Lat Pulldown", 2)

            insertAlternative(db, "Rear Delt Fly", "Cable Rear Delt Fly", 0)
            insertAlternative(db, "Cable Rear Delt Fly", "Rear Delt Fly", 0)
            insertAlternative(db, "Cable Rear Delt Fly", "Face Pull", 1)
            insertAlternative(db, "Face Pull", "Cable Rear Delt Fly", 0)
            insertAlternative(db, "Barbell Shrug", "Cable Rear Delt Fly", 1)
            insertAlternative(db, "Barbell Shrug", "Dumbbell Shrug", 0)
            insertAlternative(db, "Dumbbell Shrug", "Barbell Shrug", 0)
            insertAlternative(db, "Dumbbell Shrug", "Rear Delt Fly", 1)

            insertAlternative(db, "Barbell Curl", "Cable Bicep Curl", 2)
            insertAlternative(db, "EZ Bar Curl", "Cable Bicep Curl", 2)
            insertAlternative(db, "Dumbbell Curl", "Cable Bicep Curl", 2)
            insertAlternative(db, "Cable Bicep Curl", "Barbell Curl", 0)
            insertAlternative(db, "Cable Bicep Curl", "Dumbbell Curl", 1)
            insertAlternative(db, "Cable Bicep Curl", "EZ Bar Curl", 2)

            insertAlternative(db, "Cable Tricep Pushdown", "Cable Overhead Tricep Extension", 0)
            insertAlternative(db, "Overhead Tricep Extension", "Cable Overhead Tricep Extension", 0)
            insertAlternative(db, "Overhead Tricep Extension", "Barbell Overhead Tricep Extension", 1)
            insertAlternative(db, "Cable Overhead Tricep Extension", "Overhead Tricep Extension", 0)
            insertAlternative(db, "Cable Overhead Tricep Extension", "Cable Tricep Pushdown", 1)
            insertAlternative(db, "Cable Overhead Tricep Extension", "Skullcrusher", 2)
            insertAlternative(db, "Barbell Overhead Tricep Extension", "Overhead Tricep Extension", 0)
            insertAlternative(db, "Barbell Overhead Tricep Extension", "Skullcrusher", 1)
            insertAlternative(db, "Skullcrusher", "Barbell Overhead Tricep Extension", 1)

            insertAlternative(db, "Dumbbell Misc", "Dumbbell Curl", 0)
            insertAlternative(db, "Dumbbell Misc", "Dumbbell Lateral Raise", 1)
            insertAlternative(db, "Dumbbell Misc", "Dumbbell Row", 2)
        }

        private fun exerciseCount(db: SupportSQLiteDatabase): Int =
            db.query("SELECT COUNT(*) FROM exercises").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

        private fun mergeExerciseAlias(
            db: SupportSQLiteDatabase,
            aliasName: String,
            canonicalName: String
        ) {
            db.execSQL("""
                UPDATE session_exercises
                SET actualExerciseId = (SELECT id FROM exercises WHERE name = '$canonicalName')
                WHERE actualExerciseId IN (SELECT id FROM exercises WHERE name = '$aliasName')
            """.trimIndent())
            db.execSQL("""
                UPDATE planned_exercises
                SET exerciseId = (SELECT id FROM exercises WHERE name = '$canonicalName')
                WHERE exerciseId IN (SELECT id FROM exercises WHERE name = '$aliasName')
            """.trimIndent())
            db.execSQL("""
                DELETE FROM exercise_alternatives
                WHERE exerciseId IN (SELECT id FROM exercises WHERE name = '$aliasName')
                   OR alternativeExerciseId IN (SELECT id FROM exercises WHERE name = '$aliasName')
            """.trimIndent())
            db.execSQL("""
                DELETE FROM exercises
                WHERE name = '$aliasName'
                  AND id NOT IN (SELECT actualExerciseId FROM session_exercises)
                  AND id NOT IN (SELECT exerciseId FROM planned_exercises)
            """.trimIndent())
        }

        private fun insertExercise(
            db: SupportSQLiteDatabase,
            name: String,
            primaryMuscle: String,
            equipment: String
        ) {
            db.execSQL("""
                INSERT INTO exercises (name, primaryMuscle, equipment, notes, isCustom)
                SELECT '$name', '$primaryMuscle', '$equipment', '', 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM exercises WHERE lower(name) = lower('$name')
                )
            """.trimIndent())
        }

        private fun insertAlternative(
            db: SupportSQLiteDatabase,
            exerciseName: String,
            alternativeName: String,
            orderIdx: Int
        ) {
            db.execSQL("""
                INSERT OR REPLACE INTO exercise_alternatives (
                    exerciseId,
                    alternativeExerciseId,
                    orderIdx
                )
                SELECT source.id, alt.id, $orderIdx
                FROM exercises source, exercises alt
                WHERE source.name = '$exerciseName'
                  AND alt.name = '$alternativeName'
            """.trimIndent())
        }
    }

    /** v15 ships the May 2026 Upper/Lower schedule (Lower A, Upper A, Lower B, Upper B order),
     *  adds the Incline Smith Machine Press (3) variant as a seed exercise, and brings in the
     *  8/5-16/5 log entries. Schema is unchanged; the migration clears the rows we own so
     *  [DatabaseSeeder.seedIfEmpty] re-runs on the next open and re-imports from the .txt logs. */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
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
