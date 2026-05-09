package com.fitness.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionExerciseId")]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val note: String = "",
    val completedAt: Long,
    /** When true, this set is excluded from PR/best-set queries. Set via outlier review. */
    val excludeFromPr: Boolean = false,
    /** True once the user has either confirmed (Keep) or excluded (Exclude) this set
     *  from the outlier review flow — suppresses re-prompting for the same set. */
    val prReviewed: Boolean = false
)
