package com.fitness.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.UserDao
import com.fitness.app.data.importer.LogImporter
import com.fitness.app.data.preferences.AppPreferences
import com.fitness.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val chimeEnabled: Boolean = true,
    val defaultRestSec: Int = 75,
    val units: String = "KG"
)

data class ImportResult(val message: String, val isError: Boolean)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val logImporter: LogImporter,
    private val sessionRepository: SessionRepository,
    private val userDao: UserDao
) : ViewModel() {

    val state = combine(
        prefs.chimeEnabled,
        prefs.defaultRestSec,
        prefs.units
    ) { chime, rest, units ->
        SettingsUiState(chime, rest, units)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult = _importResult.asStateFlow()

    fun setChimeEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setChimeEnabled(value) }
    }

    fun setDefaultRestSec(value: Int) {
        viewModelScope.launch { prefs.setDefaultRestSec(value) }
    }

    fun setUnits(value: String) {
        viewModelScope.launch { prefs.setUnits(value) }
    }

    /**
     * Wipes Ron's existing sessions and re-runs [LogImporter] from the bundled .txt logs.
     * Destructive for Ron's data only — testUser is untouched. The .txt files are the
     * canonical source, so a re-import is the supported way to re-sync after editing them.
     */
    fun reimportRonHistory() {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                val ron = userDao.getByName("Ron")
                if (ron == null) {
                    _importResult.value = ImportResult(
                        "User 'Ron' not found", isError = true
                    )
                    return@launch
                }
                sessionRepository.deleteAllForUser(ron.id)
                logImporter.importFor(ron.id)
                _importResult.value = ImportResult(
                    "Re-imported Ron's history from text logs", isError = false
                )
            } catch (t: Throwable) {
                _importResult.value = ImportResult(
                    "Import failed: ${t.message ?: t.javaClass.simpleName}", isError = true
                )
            } finally {
                _importing.value = false
            }
        }
    }

    fun clearImportResult() { _importResult.value = null }
}
