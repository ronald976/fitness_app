package com.fitness.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An exercise the user pushed out of a session to do next time ("Push to next session").
 * Consumed exactly once: the next started session appends it and the row is deleted.
 *
 * [plannedExerciseId] carries the plan's rep-range / rest targets so the deferred exercise
 * still shows its progression targets next time; SET_NULL keeps the deferral alive if the
 * plan row is later edited away. [exerciseId] uses CASCADE — a pending deferral has no value
 * once the exercise itself is gone.
 */
@Entity(
    tableName = "deferred_exercises",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlannedExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedExerciseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("userId"), Index("exerciseId"), Index("plannedExerciseId")]
)
data class DeferredExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val exerciseId: Long,
    val plannedExerciseId: Long?,
    val createdAt: Long
)
