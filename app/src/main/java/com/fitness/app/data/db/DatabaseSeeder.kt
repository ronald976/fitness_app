package com.fitness.app.data.db

import android.content.Context
import android.util.Log
import com.fitness.app.data.db.entities.AppStateEntity
import com.fitness.app.data.db.entities.ExerciseAlternativeEntity
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.PlanDayEntity
import com.fitness.app.data.db.entities.PlanEntity
import com.fitness.app.data.db.entities.PlannedExerciseEntity
import com.fitness.app.data.db.entities.UserEntity
import com.fitness.app.data.db.entities.UserPrefsEntity
import com.fitness.app.data.importer.LogImporter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SeedExercise(
    val slug: String,
    val name: String,
    val primaryMuscle: String,
    val equipment: String,
    val notes: String = "",
    val alternatives: List<String> = emptyList()
)

@Serializable
private data class SeedExercisesFile(val exercises: List<SeedExercise>)

@Serializable
private data class SeedPlannedExercise(
    val exerciseSlug: String,
    val targetSets: Int,
    val repLow: Int,
    val repHigh: Int,
    val restSec: Int,
    val weightIncrementKg: Double,
    /** Optional pairing key — exercises sharing the same key (per day) start as a superset. */
    val supersetGroup: String? = null
)

@Serializable
private data class SeedDay(
    val name: String,
    val exercises: List<SeedPlannedExercise>
)

@Serializable
private data class SeedPlan(
    val name: String,
    val description: String,
    val days: List<SeedDay>
)

@Serializable
private data class SeedPlansFile(val plans: List<SeedPlan>)

class DatabaseSeeder(
    private val context: Context,
    private val db: FitnessDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfEmpty() {
        if (db.exerciseDao().count() > 0) return

        val slugToId = seedExercises()
        val planNameToId = seedPlans(slugToId)
        seedUsersAndImport(planNameToId)
    }

    private suspend fun seedExercises(): Map<String, Long> {
        val bytes = context.assets.open("seed/exercises.json").use { it.readBytes() }
        val parsed = json.decodeFromString<SeedExercisesFile>(String(bytes))

        val slugToId = mutableMapOf<String, Long>()
        for (e in parsed.exercises) {
            val id = db.exerciseDao().insert(
                ExerciseEntity(
                    name = e.name,
                    primaryMuscle = e.primaryMuscle,
                    equipment = e.equipment,
                    notes = e.notes,
                    isCustom = false
                )
            )
            slugToId[e.slug] = id
        }

        for (e in parsed.exercises) {
            val sourceId = slugToId[e.slug] ?: continue
            e.alternatives.forEachIndexed { idx, altSlug ->
                val altId = slugToId[altSlug] ?: return@forEachIndexed
                db.exerciseDao().insertAlternative(
                    ExerciseAlternativeEntity(
                        exerciseId = sourceId,
                        alternativeExerciseId = altId,
                        orderIdx = idx
                    )
                )
            }
        }
        return slugToId
    }

    private suspend fun seedPlans(slugToId: Map<String, Long>): Map<String, Long> {
        val bytes = context.assets.open("seed/plans.json").use { it.readBytes() }
        val parsed = json.decodeFromString<SeedPlansFile>(String(bytes))
        val now = System.currentTimeMillis()
        val planNameToId = mutableMapOf<String, Long>()

        for (plan in parsed.plans) {
            val planId = db.planDao().insertPlan(
                PlanEntity(
                    name = plan.name,
                    description = plan.description,
                    isTemplate = true,
                    createdAt = now
                )
            )
            planNameToId[plan.name] = planId
            plan.days.forEachIndexed { dayIdx, day ->
                val dayId = db.planDao().insertDay(
                    PlanDayEntity(
                        planId = planId,
                        dayIndex = dayIdx,
                        name = day.name
                    )
                )
                // Translate per-day string `supersetGroup` keys ("calves", "shoulders-arms", ...)
                // into stable Long ids that the PlannedExerciseEntity expects. Same key in the
                // same day → same id; null stays null.
                val groupKeyToId = mutableMapOf<String, Long>()
                day.exercises.forEachIndexed { exIdx, ex ->
                    val exerciseId = slugToId[ex.exerciseSlug]
                        ?: error("Seed plan ${plan.name} references unknown exercise slug: ${ex.exerciseSlug}")
                    val groupId = ex.supersetGroup?.let { key ->
                        groupKeyToId.getOrPut(key) { dayId * 100L + exIdx }
                    }
                    db.planDao().insertPlannedExercise(
                        PlannedExerciseEntity(
                            planDayId = dayId,
                            exerciseId = exerciseId,
                            orderIdx = exIdx,
                            targetSets = ex.targetSets,
                            repLow = ex.repLow,
                            repHigh = ex.repHigh,
                            restSec = ex.restSec,
                            weightIncrementKg = ex.weightIncrementKg,
                            supersetGroupId = groupId
                        )
                    )
                }
            }
        }
        return planNameToId
    }

    private suspend fun seedUsersAndImport(planNameToId: Map<String, Long>) {
        val ronId = db.userDao().insert(UserEntity(name = "Ron"))
        val testId = db.userDao().insert(UserEntity(name = "testUser"))

        val upperLowerPlanId = planNameToId["Upper / Lower (4-day)"]
        db.userPrefsDao().upsert(
            UserPrefsEntity(userId = ronId, activePlanId = upperLowerPlanId)
        )
        db.userPrefsDao().upsert(UserPrefsEntity(userId = testId))

        db.appStateDao().upsert(AppStateEntity(id = 0, currentUserId = ronId))

        // Import historical logs for Ron only. testUser stays clean so the fresh-install
        // experience can be sanity-checked against it. .txt logs are the canonical source;
        // the xlsx in data/ is a derived, human-readable view of the same data, and Android
        // can't read xlsx without Stax (which the platform doesn't ship).
        try {
            LogImporter(context, db).importFor(ronId)
            Log.i("DatabaseSeeder", "Seeded Ron from .txt logs")
        } catch (t: Throwable) {
            Log.e("DatabaseSeeder", "Failed to seed Ron from .txt logs", t)
        }
    }
}
