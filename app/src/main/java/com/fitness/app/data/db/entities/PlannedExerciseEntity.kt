package com.fitness.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["planDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("planDayId"), Index("exerciseId")]
)
data class PlannedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planDayId: Long,
    val exerciseId: Long,
    val orderIdx: Int,
    val targetSets: Int,
    val repLow: Int,
    val repHigh: Int,
    val restSec: Int,
    val weightIncrementKg: Double
)
