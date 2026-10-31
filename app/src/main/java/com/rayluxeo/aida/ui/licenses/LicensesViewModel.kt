package com.rayluxeo.aida.ui.licenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.R
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the acknowledgements screen together: the language the labels are written in, and the one
 * bundled licence text Abu can open in full.
 *
 * The dependency list itself is a fixed set of proper names and so lives in the screen; nothing
 * here is computed or fetched. The single piece of real work is reading the bundled OFL text for
 * Noto Serif Bengali, which is done off the main thread and only when Abu asks for it (§13).
 */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
) : ViewModel() {

    private val fontLicense = MutableStateFlow<String?>(null)
    private val language = settings.settings.map { it.languageMode.displayLanguage() }

    val state: StateFlow<LicensesUiState> =
        combine(language, fontLicense) { language, fontLicense ->
            LicensesUiState(language = language, fontLicense = fontLicense)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LicensesUiState(),
        )

    /**
     * Read the bundled Noto Serif Bengali licence into [LicensesUiState.fontLicense]. It is a file
     * in the APK, so the read is done once and off the main thread; a second call while it is held
     * is a no-op.
     */
    fun loadFontLicense() {
        if (fontLicense.value != null) return
        viewModelScope.launch {
            fontLicense.value = withContext(Dispatchers.IO) {
                context.resources.openRawResource(R.raw.noto_serif_bengali_license)
                    .bufferedReader()
                    .use { it.readText() }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Everything the acknowledgements screen draws from. */
data class LicensesUiState(
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    /** The full OFL text, once Abu has opened it; null until then. */
    val fontLicense: String? = null,
)
