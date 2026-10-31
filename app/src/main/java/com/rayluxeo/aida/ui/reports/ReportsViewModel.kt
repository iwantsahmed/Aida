package com.rayluxeo.aida.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.report.BusinessReport
import com.rayluxeo.aida.domain.report.ReportEngine
import com.rayluxeo.aida.domain.report.ReportPeriod
import com.rayluxeo.aida.domain.report.ReportType
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reports, recomputed whenever Abu changes the window.
 *
 * The engine is a domain port, read-only, so it is observed here directly rather than wrapped in a
 * pass-through use case that would add a file and no meaning (§4). Every figure is already worked
 * out deterministically by the engine; this view model only chooses the period and holds the
 * results for the screen.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val engine: ReportEngine,
    private val exporter: ReportExporter,
    private val dispatchers: DispatcherProvider,
    settings: SettingsRepository,
) : ViewModel() {

    private val period = MutableStateFlow<ReportPeriod>(ReportPeriod.ThisMonth)
    private val reports = MutableStateFlow<List<BusinessReport>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val export = MutableStateFlow(ExportStatus.IDLE)
    private val language = settings.settings.map { it.languageMode.displayLanguage() }

    val state: StateFlow<ReportsUiState> =
        combine(period, reports, loading, export, language) { period, reports, loading, export, language ->
            ReportsUiState(
                period = period,
                reports = reports,
                isLoading = loading,
                export = export,
                language = language,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ReportsUiState(),
        )

    init {
        recompute()
    }

    /** Switch to one of the preset windows. */
    fun selectPeriod(newPeriod: ReportPeriod) {
        if (newPeriod == period.value) return
        period.value = newPeriod
        recompute()
    }

    /** Apply a custom range Abu picked. [from] and [to] are inclusive; order is normalised. */
    fun selectCustom(from: LocalDate, to: LocalDate) {
        val ordered = if (to.isBefore(from)) ReportPeriod.Custom(to, from) else ReportPeriod.Custom(from, to)
        period.value = ordered
        recompute()
    }

    /** Write the current reports out as a PDF to the destination Abu chose through the file picker. */
    fun exportPdf(out: OutputStream) = runExport(out, exporter::writePdf)

    /** Write the current reports out as CSV to the destination Abu chose through the file picker. */
    fun exportCsv(out: OutputStream) = runExport(out, exporter::writeCsv)

    /** Clear the export notice once Abu has seen it. */
    fun dismissExport() {
        export.value = ExportStatus.IDLE
    }

    private fun runExport(out: OutputStream, write: (ReportDoc, OutputStream) -> AppResult<Unit>) {
        val current = reports.value
        if (current.isEmpty()) {
            export.value = ExportStatus.FAILED
            return
        }
        val doc = buildReportDoc(current, reportsWording(state.value.language))
        export.value = ExportStatus.RUNNING
        viewModelScope.launch {
            val result = withContext(dispatchers.io) { write(doc, out) }
            export.value = if (result is AppResult.Success) ExportStatus.READY else ExportStatus.FAILED
        }
    }

    private fun recompute() {
        viewModelScope.launch {
            loading.value = true
            val chosen = period.value
            reports.value = ReportType.entries.mapNotNull { type ->
                (engine.generate(type, chosen) as? AppResult.Success)?.value
            }
            loading.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Where an export has got to, so the screen can show one calm line and nothing fake. */
enum class ExportStatus { IDLE, RUNNING, READY, FAILED }

/** Everything the Reports screen draws from. */
data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.ThisMonth,
    val reports: List<BusinessReport> = emptyList(),
    val isLoading: Boolean = true,
    val export: ExportStatus = ExportStatus.IDLE,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)
