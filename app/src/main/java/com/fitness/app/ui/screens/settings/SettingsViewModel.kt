package com.fitness.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val chimeEnabled: Boolean = true,
    val defaultRestSec: Int = 75,
    val units: String = "KG"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    val state = combine(
        prefs.chimeEnabled,
        prefs.defaultRestSec,
        prefs.units
    ) { chime, rest, units ->
        SettingsUiState(chime, rest, units)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setChimeEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setChimeEnabled(value) }
    }

    fun setDefaultRestSec(value: Int) {
        viewModelScope.launch { prefs.setDefaultRestSec(value) }
    }

    fun setUnits(value: String) {
        viewModelScope.launch { prefs.setUnits(value) }
    }
}
