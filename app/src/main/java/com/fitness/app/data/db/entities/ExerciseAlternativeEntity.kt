package com.fitness.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exercise_alternatives",
    primaryKeys = ["exerciseId", "alternativeExerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["alternativeExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("alternativeExerciseId")]
)
data class ExerciseAlternativeEntity(
    val exerciseId: Long,
    val alternativeExerciseId: Long,
    val orderIdx: Int
)
