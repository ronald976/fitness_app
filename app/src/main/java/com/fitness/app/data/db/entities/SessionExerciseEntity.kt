package com.fitness.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlannedExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedExerciseId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["actualExerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("plannedExerciseId"), Index("actualExerciseId")]
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val plannedExerciseId: Long?,
    val actualExerciseId: Long,
    val orderIdx: Int,
    val customLabel: String? = null
)
