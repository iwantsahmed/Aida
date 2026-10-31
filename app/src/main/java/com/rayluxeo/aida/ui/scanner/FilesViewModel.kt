package com.rayluxeo.aida.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) : ViewModel() {

    /**
     * The language the screen words itself in, read straight from Abu's setting. A read-only
     * pass-through of [SettingsRepository] carries no rule of its own, so it needs no use case; it
     * settles on English until the first value arrives, exactly as the shell does.
     */
    val language: StateFlow<SpokenLanguage> = settings.settings
        .map { it.languageMode.displayLanguage() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpokenLanguage.ENGLISH)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFile = MutableStateFlow<FileRecord?>(null)
    val selectedFile: StateFlow<FileRecord?> = _selectedFile.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** The file just binned, so the undo notice can restore the same row. */
    private val _pendingUndoFileId = MutableStateFlow<Long?>(null)
    val pendingUndoFileId: StateFlow<Long?> = _pendingUndoFileId.asStateFlow()

    val clients: StateFlow<List<Client>> = clientRepository.observe(ClientFilter.ACTIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = projectRepository.observe(ProjectFilter.ACTIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<FileRecord>> = combine(
        fileRepository.observeAll(),
        _searchQuery,
    ) { allFiles, query ->
        if (query.isBlank()) {
            allFiles
        } else {
            val q = query.trim().lowercase()
            allFiles.filter { file ->
                file.displayName.lowercase().contains(q) ||
                    (file.extractedText?.lowercase()?.contains(q) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFile(file: FileRecord?) {
        _selectedFile.value = file
    }

    fun linkClient(fileId: Long, clientId: Long?) {
        viewModelScope.launch {
            val file = (fileRepository.byId(fileId) as? AppResult.Success)?.value ?: return@launch
            val updated = file.copy(clientId = clientId)
            fileRepository.update(updated)
            if (_selectedFile.value?.id == fileId) {
                _selectedFile.value = updated
            }
        }
    }

    fun linkProject(fileId: Long, projectId: Long?) {
        viewModelScope.launch {
            val file = (fileRepository.byId(fileId) as? AppResult.Success)?.value ?: return@launch
            val updated = file.copy(projectId = projectId)
            fileRepository.update(updated)
            if (_selectedFile.value?.id == fileId) {
                _selectedFile.value = updated
            }
        }
    }

    fun rename(fileId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val file = (fileRepository.byId(fileId) as? AppResult.Success)?.value ?: return@launch
            val updated = file.copy(displayName = trimmed, updatedAt = clock.now())
            fileRepository.update(updated)
            if (_selectedFile.value?.id == fileId) {
                _selectedFile.value = updated
            }
            _notice.value = filesWording(currentLanguage()).renamed
        }
    }

    fun deleteFile(fileId: Long) {
        viewModelScope.launch {
            fileRepository.softDelete(fileId)
            if (_selectedFile.value?.id == fileId) {
                _selectedFile.value = null
            }
            _pendingUndoFileId.value = fileId
            _notice.value = filesWording(currentLanguage()).fileMovedToBin
        }
    }

    private suspend fun currentLanguage(): SpokenLanguage =
        settings.current().languageMode.displayLanguage()

    fun restoreFile(fileId: Long) {
        viewModelScope.launch {
            fileRepository.restore(fileId)
            _pendingUndoFileId.value = null
            _notice.value = null
        }
    }

    fun dismissNotice() {
        _notice.value = null
        _pendingUndoFileId.value = null
    }
}
