package com.fitness.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitness.app.data.db.entities.UserPrefsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPrefsDao {

    @Query("SELECT * FROM user_prefs WHERE userId = :userId")
    fun observe(userId: Long): Flow<UserPrefsEntity?>

    @Query("SELECT * FROM user_prefs WHERE userId = :userId")
    suspend fun get(userId: Long): UserPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: UserPrefsEntity)

    @Query("UPDATE user_prefs SET activePlanId = :planId WHERE userId = :userId")
    suspend fun setActivePlan(userId: Long, planId: Long?)
}
