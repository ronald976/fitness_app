package com.fitness.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_prefs",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserPrefsEntity(
    @PrimaryKey val userId: Long,
    val activePlanId: Long? = null,
    val unit: String = "KG",
    val defaultRestSec: Int = 120,
    val progressionStrategy: String = "DOUBLE_PROGRESSION"
)
