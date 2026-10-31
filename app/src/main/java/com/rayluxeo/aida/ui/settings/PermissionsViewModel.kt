package com.rayluxeo.aida.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The permissions screen carries no state of its own — a grant is read live from the OS on every
 * resume, never cached (a cached answer goes stale the moment Abu changes it in system settings).
 * The one thing it needs from storage is the language to say it all in.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {

    val language: StateFlow<SpokenLanguage> = settings.settings
        .map { it.languageMode.displayLanguage() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SpokenLanguage.ENGLISH)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
