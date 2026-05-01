package com.fitness.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

/**
 * App-wide settings persisted in DataStore. These are independent of [UserPrefsEntity]
 * (which is per-user and tied to the DB), so they survive a destructive DB migration and
 * apply uniformly regardless of which user is currently selected.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    val chimeEnabled: Flow<Boolean> = ds.data.map { it[KEY_CHIME] ?: true }
    val defaultRestSec: Flow<Int> = ds.data.map { it[KEY_DEFAULT_REST] ?: 75 }
    val units: Flow<String> = ds.data.map { it[KEY_UNITS] ?: "KG" }

    suspend fun chimeEnabledNow(): Boolean = chimeEnabled.first()
    suspend fun defaultRestSecNow(): Int = defaultRestSec.first()

    suspend fun setChimeEnabled(value: Boolean) {
        ds.edit { it[KEY_CHIME] = value }
    }

    suspend fun setDefaultRestSec(value: Int) {
        ds.edit { it[KEY_DEFAULT_REST] = value.coerceIn(15, 600) }
    }

    suspend fun setUnits(value: String) {
        ds.edit { it[KEY_UNITS] = value }
    }

    private companion object {
        val KEY_CHIME = booleanPreferencesKey("chime_enabled")
        val KEY_DEFAULT_REST = intPreferencesKey("default_rest_sec")
        val KEY_UNITS = stringPreferencesKey("units")
    }
}
