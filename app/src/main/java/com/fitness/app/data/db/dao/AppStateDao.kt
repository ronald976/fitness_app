package com.fitness.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitness.app.data.db.entities.AppStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStateDao {

    @Query("SELECT * FROM app_state WHERE id = 0")
    fun observe(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 0")
    suspend fun get(): AppStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AppStateEntity)

    @Query("UPDATE app_state SET currentUserId = :userId WHERE id = 0")
    suspend fun setCurrentUser(userId: Long?)
}
