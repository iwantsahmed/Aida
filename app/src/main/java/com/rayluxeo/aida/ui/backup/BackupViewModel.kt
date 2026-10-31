package com.rayluxeo.aida.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.BackupMetadata
import com.rayluxeo.aida.domain.repository.BackupRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class BackupUiState(
    val passphraseInput: String = "",
    val isCreatingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val lastMetadata: BackupMetadata? = null,
    val notice: String? = null,
    val isSuccess: Boolean = false,
    /**
     * True once a restore has replaced the files on disk. The restored data only takes hold after
     * the process restarts, so the screen surfaces a restart action while this is set.
     */
    val restartRequired: Boolean = false,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        // A read path, observed rather than fetched once: the language toggle lives on another
        // screen, and a backup screen that went on speaking English after Abu chose বাংলা would be
        // the defect this wording exists to fix.
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                _uiState.update { it.copy(language = preferences.languageMode.displayLanguage()) }
            }
        }
    }

    fun updatePassphrase(passphrase: String) {
        _uiState.update { it.copy(passphraseInput = passphrase) }
    }

    fun createBackup(outputStream: OutputStream) {
        val passphrase = _uiState.value.passphraseInput
        if (passphrase.isBlank()) {
            _uiState.update {
                it.copy(notice = words().passphraseRequiredForBackup, isSuccess = false)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingBackup = true,
                    notice = words().creatingBackup,
                    isSuccess = false,
                    restartRequired = false,
                )
            }
            when (val result = backupRepository.createEncryptedBackup(passphrase, outputStream)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreatingBackup = false,
                            lastMetadata = result.value,
                            notice = words().backupCreated,
                            isSuccess = true,
                        )
                    }
                }
                // The failure's detail is developer-facing (AppResult.kt) and never spoken; the
                // screen gets a worded, actionable sentence instead.
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isCreatingBackup = false,
                            notice = words().backupFailed,
                            isSuccess = false,
                        )
                    }
                }
            }
        }
    }

    fun restoreBackup(inputStream: InputStream) {
        val passphrase = _uiState.value.passphraseInput
        if (passphrase.isBlank()) {
            _uiState.update {
                it.copy(notice = words().passphraseRequiredForRestore, isSuccess = false)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoringBackup = true,
                    notice = words().restoringBackup,
                    isSuccess = false,
                    restartRequired = false,
                )
            }
            when (val result = backupRepository.restoreEncryptedBackup(passphrase, inputStream)) {
                is AppResult.Success -> {
                    // The files are replaced, but the live database and settings cache still hold
                    // the old state: Abu must restart for the restore to take hold.
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            lastMetadata = result.value,
                            notice = words().restoreRestartRequired,
                            isSuccess = true,
                            restartRequired = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            notice = words().restoreFailed,
                            isSuccess = false,
                        )
                    }
                }
            }
        }
    }

    fun exportJson(outputStream: OutputStream) {
        viewModelScope.launch {
            when (backupRepository.exportJson(outputStream)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(notice = words().jsonExported, isSuccess = true, restartRequired = false) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(notice = words().jsonExportFailed, isSuccess = false, restartRequired = false) }
            }
        }
    }

    fun exportCsv(entityName: String, outputStream: OutputStream) {
        viewModelScope.launch {
            when (backupRepository.exportCsv(entityName, outputStream)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(notice = words().csvExported(entityName), isSuccess = true, restartRequired = false) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(notice = words().csvExportFailed, isSuccess = false, restartRequired = false) }
            }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** The phrasebook for the language Abu is in right now, kept in step by the observer above. */
    private fun words(): BackupWording = backupWording(_uiState.value.language)
}
