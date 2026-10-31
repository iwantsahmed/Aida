package com.rayluxeo.aida.ui.scanner

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.data.scanner.ScanImageStorage
import com.rayluxeo.aida.domain.scanner.OcrResult
import com.rayluxeo.aida.domain.scanner.ParsedBusinessCard
import com.rayluxeo.aida.domain.scanner.ParsedDocument
import com.rayluxeo.aida.domain.scanner.ParsedHandwriting
import com.rayluxeo.aida.domain.scanner.ParsedReceipt
import com.rayluxeo.aida.domain.scanner.ScanType
import com.rayluxeo.aida.domain.usecase.ProcessScanUseCase
import com.rayluxeo.aida.domain.usecase.SaveBusinessCardClientUseCase
import com.rayluxeo.aida.domain.usecase.SaveBusinessCardNoteUseCase
import com.rayluxeo.aida.domain.usecase.SaveScanDocumentUseCase
import com.rayluxeo.aida.domain.usecase.SaveScanExpenseUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class ScannerUiState(
    val scanType: ScanType = ScanType.RECEIPT,
    val capturedImagePath: String? = null,
    val relativePath: String? = null,
    val imageSizeBytes: Long = 0L,
    val isCleanedImage: Boolean = false,
    // The pre-clean original, kept so the Clean Image switch can be turned back off.
    val preCleanImagePath: String? = null,
    val preCleanRelativePath: String? = null,
    val preCleanImageSizeBytes: Long = 0L,
    val isProcessingOcr: Boolean = false,
    val ocrResult: OcrResult? = null,
    val parsedReceipt: ParsedReceipt? = null,
    val parsedBusinessCard: ParsedBusinessCard? = null,
    val parsedDocument: ParsedDocument? = null,
    val parsedHandwriting: ParsedHandwriting? = null,
    // Receipt form
    val merchant: String = "",
    val amountText: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val spentAtDate: LocalDate? = null,
    val isBillable: Boolean = false,
    // Business card form
    val cardName: String = "",
    val cardCompany: String = "",
    val cardPhone: String = "",
    val cardEmail: String = "",
    val cardWebsite: String = "",
    val cardAddress: String = "",
    // Document / Handwriting / Screenshot
    val documentTitle: String = "",
    val extractedText: String = "",
    val selectedClientId: Long? = null,
    val selectedProjectId: Long? = null,
    val notice: String? = null,
    val isSavedSuccess: Boolean = false,
    /** The language the screen words itself in, read straight from Abu's setting. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val processScanUseCase: ProcessScanUseCase,
    private val saveScanExpenseUseCase: SaveScanExpenseUseCase,
    private val saveBusinessCardClientUseCase: SaveBusinessCardClientUseCase,
    private val saveBusinessCardNoteUseCase: SaveBusinessCardNoteUseCase,
    private val saveScanDocumentUseCase: SaveScanDocumentUseCase,
    private val scanImageStorage: ScanImageStorage,
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState(spentAtDate = clock.today()))
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val clients: StateFlow<List<Client>> = clientRepository.observe(ClientFilter.ACTIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = projectRepository.observe(ProjectFilter.ACTIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // The screen words itself in Abu's chosen language; a read-only pass-through of the setting
        // carries no rule of its own, so it needs no use case. It settles on English until the first
        // value arrives, exactly as the Files screen does.
        viewModelScope.launch {
            settings.settings
                .map { it.languageMode.displayLanguage() }
                .collect { language -> _uiState.update { it.copy(language = language) } }
        }
    }

    /** The phrasebook for every sentence Mira says back here, in Abu's current language. */
    private fun words() = scannerWording(_uiState.value.language)

    fun setScanType(scanType: ScanType) {
        _uiState.update { it.copy(scanType = scanType, notice = null) }
        val currentText = _uiState.value.extractedText
        if (currentText.isNotBlank()) {
            reparseText(currentText, scanType)
        }
    }

    fun onImageCaptured(imagePath: String, relativePath: String, sizeBytes: Long) {
        _uiState.update {
            it.copy(
                capturedImagePath = imagePath,
                relativePath = relativePath,
                imageSizeBytes = sizeBytes,
                notice = null,
                isSavedSuccess = false,
            )
        }
        runOcrForPath(imagePath)
    }

    fun toggleCleanImage() {
        val current = _uiState.value
        val currentPath = current.capturedImagePath ?: return

        viewModelScope.launch {
            if (!current.isCleanedImage) {
                // Apply the binarization filter, keeping the original so this can be undone.
                val originalBitmap = BitmapFactory.decodeFile(currentPath) ?: return@launch
                val cleanedBitmap = scanImageStorage.cleanImageForOcr(originalBitmap)
                val stored = scanImageStorage.saveBitmap(cleanedBitmap, prefix = "cleaned")
                _uiState.update {
                    it.copy(
                        preCleanImagePath = it.capturedImagePath,
                        preCleanRelativePath = it.relativePath,
                        preCleanImageSizeBytes = it.imageSizeBytes,
                        capturedImagePath = stored.absolutePath,
                        relativePath = stored.relativePath,
                        imageSizeBytes = stored.sizeBytes,
                        isCleanedImage = true,
                    )
                }
                runOcrForPath(stored.absolutePath)
            } else {
                // Turn cleaning off: restore the original image and re-read it.
                val original = current.preCleanImagePath ?: return@launch
                _uiState.update {
                    it.copy(
                        capturedImagePath = it.preCleanImagePath,
                        relativePath = it.preCleanRelativePath,
                        imageSizeBytes = it.preCleanImageSizeBytes,
                        isCleanedImage = false,
                    )
                }
                runOcrForPath(original)
            }
        }
    }

    fun cropImage(leftFraction: Float, topFraction: Float, rightFraction: Float, bottomFraction: Float) {
        val currentPath = _uiState.value.capturedImagePath ?: return
        viewModelScope.launch {
            val originalBitmap = BitmapFactory.decodeFile(currentPath) ?: return@launch
            val croppedBitmap = scanImageStorage.cropBitmap(
                originalBitmap,
                leftFraction,
                topFraction,
                rightFraction,
                bottomFraction,
            )
            val stored = scanImageStorage.saveBitmap(croppedBitmap, prefix = "cropped")
            _uiState.update {
                it.copy(
                    capturedImagePath = stored.absolutePath,
                    relativePath = stored.relativePath,
                    imageSizeBytes = stored.sizeBytes,
                )
            }
            runOcrForPath(stored.absolutePath)
        }
    }

    private fun runOcrForPath(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true) }
            when (val result = processScanUseCase.executeOcr(path)) {
                is AppResult.Success -> {
                    val ocrResult = result.value
                    _uiState.update {
                        it.copy(
                            isProcessingOcr = false,
                            ocrResult = ocrResult,
                            extractedText = ocrResult.text,
                        )
                    }
                    reparseText(ocrResult.text, _uiState.value.scanType)
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isProcessingOcr = false,
                            notice = words().ocrFailed(result.error.detail),
                        )
                    }
                }
            }
        }
    }

    private fun reparseText(ocrText: String, type: ScanType) {
        when (type) {
            ScanType.RECEIPT, ScanType.INVOICE -> {
                val parsed = processScanUseCase.parseReceipt(ocrText)
                _uiState.update {
                    it.copy(
                        parsedReceipt = parsed,
                        merchant = parsed.merchant ?: "",
                        amountText = parsed.amount?.amount?.toPlainString() ?: "",
                        category = parsed.category,
                        spentAtDate = parsed.date ?: clock.today(),
                    )
                }
            }
            ScanType.BUSINESS_CARD -> {
                val parsed = processScanUseCase.parseBusinessCard(ocrText)
                _uiState.update {
                    it.copy(
                        parsedBusinessCard = parsed,
                        cardName = parsed.name ?: "",
                        cardCompany = parsed.company ?: "",
                        cardPhone = parsed.phone ?: "",
                        cardEmail = parsed.email ?: "",
                        cardWebsite = parsed.website ?: "",
                        cardAddress = parsed.address ?: "",
                    )
                }
            }
            ScanType.DOCUMENT, ScanType.SCREENSHOT -> {
                val parsed = processScanUseCase.parseDocument(ocrText)
                _uiState.update {
                    it.copy(
                        parsedDocument = parsed,
                        documentTitle = parsed.title ?: words().defaultDocumentTitle,
                        extractedText = parsed.extractedText,
                    )
                }
            }
            ScanType.HANDWRITING -> {
                val parsed = processScanUseCase.parseHandwriting(ocrText)
                _uiState.update {
                    it.copy(
                        parsedHandwriting = parsed,
                        documentTitle = words().defaultHandwrittenTitle,
                        extractedText = parsed.recognizedText,
                    )
                }
            }
        }
    }

    // Form editing callbacks
    fun updateMerchant(merchant: String) = _uiState.update { it.copy(merchant = merchant) }
    fun updateAmount(amount: String) = _uiState.update { it.copy(amountText = amount) }
    fun updateCategory(category: ExpenseCategory) = _uiState.update { it.copy(category = category) }
    fun updateSpentAtDate(date: LocalDate?) = _uiState.update { it.copy(spentAtDate = date) }
    fun updateIsBillable(billable: Boolean) = _uiState.update { it.copy(isBillable = billable) }

    fun updateCardName(name: String) = _uiState.update { it.copy(cardName = name) }
    fun updateCardCompany(company: String) = _uiState.update { it.copy(cardCompany = company) }
    fun updateCardPhone(phone: String) = _uiState.update { it.copy(cardPhone = phone) }
    fun updateCardEmail(email: String) = _uiState.update { it.copy(cardEmail = email) }
    fun updateCardWebsite(website: String) = _uiState.update { it.copy(cardWebsite = website) }
    fun updateCardAddress(address: String) = _uiState.update { it.copy(cardAddress = address) }

    fun updateDocumentTitle(title: String) = _uiState.update { it.copy(documentTitle = title) }
    fun updateExtractedText(text: String) = _uiState.update { it.copy(extractedText = text) }
    fun setSelectedClient(clientId: Long?) = _uiState.update { it.copy(selectedClientId = clientId) }
    fun setSelectedProject(projectId: Long?) = _uiState.update { it.copy(selectedProjectId = projectId) }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    // Save actions
    fun saveExpense() {
        val state = _uiState.value
        val words = words()
        val relPath = state.relativePath ?: run {
            _uiState.update { it.copy(notice = words.captureReceiptFirst) }
            return
        }
        val merchantName = state.merchant.ifBlank { words.defaultMerchant }
        val amountDec = state.amountText.toBigDecimalOrNull() ?: run {
            _uiState.update { it.copy(notice = words.enterValidAmount) }
            return
        }

        val money = Money.of(amountDec.toPlainString())

        viewModelScope.launch {
            val result = saveScanExpenseUseCase(
                displayName = words.receiptFileName(merchantName),
                relativePath = relPath,
                sizeBytes = state.imageSizeBytes,
                merchant = merchantName,
                amount = money,
                category = state.category,
                spentAtDate = state.spentAtDate,
                rawText = state.extractedText,
                clientId = state.selectedClientId,
                projectId = state.selectedProjectId,
                isBillable = state.isBillable,
            )

            when (result) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notice = words.expenseSaved(money.amount.toPlainString(), merchantName),
                            isSavedSuccess = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.failedToSaveExpense(result.error.detail)) }
                }
            }
        }
    }

    fun createClientFromCard() {
        val state = _uiState.value
        val words = words()
        val parsed = ParsedBusinessCard(
            name = state.cardName,
            company = state.cardCompany,
            phone = state.cardPhone,
            email = state.cardEmail,
            website = state.cardWebsite,
            address = state.cardAddress,
            rawText = state.extractedText,
        )

        viewModelScope.launch {
            when (val result = saveBusinessCardClientUseCase(parsed)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notice = words.clientCreated(result.value.name),
                            isSavedSuccess = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.failedToCreateClient(result.error.detail)) }
                }
            }
        }
    }

    fun createNoteFromCard() {
        val state = _uiState.value
        val words = words()
        val relPath = state.relativePath ?: ""
        val parsed = ParsedBusinessCard(
            name = state.cardName,
            company = state.cardCompany,
            phone = state.cardPhone,
            email = state.cardEmail,
            website = state.cardWebsite,
            address = state.cardAddress,
            rawText = state.extractedText,
        )

        viewModelScope.launch {
            val fileName = words.businessCardFileName(clock.now().toEpochMilli())
            when (val result = saveBusinessCardNoteUseCase(fileName, relPath, state.imageSizeBytes, parsed)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notice = words.noteSaved(result.value.title ?: words.defaultSavedNoteTitle),
                            isSavedSuccess = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.failedToSaveNote(result.error.detail)) }
                }
            }
        }
    }

    fun saveDocumentFile() {
        val state = _uiState.value
        val words = words()
        val relPath = state.relativePath ?: run {
            _uiState.update { it.copy(notice = words.captureDocumentFirst) }
            return
        }

        viewModelScope.launch {
            val title = state.documentTitle.ifBlank { words.defaultDocumentTitle }
            when (val result = saveScanDocumentUseCase.saveDocumentFile(
                displayName = title,
                relativePath = relPath,
                sizeBytes = state.imageSizeBytes,
                extractedText = state.extractedText,
                clientId = state.selectedClientId,
                projectId = state.selectedProjectId,
            )) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notice = words.documentSaved(title),
                            isSavedSuccess = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.failedToSaveDocument(result.error.detail)) }
                }
            }
        }
    }

    fun saveDocumentAsNote() {
        val state = _uiState.value
        val words = words()
        viewModelScope.launch {
            val title = state.documentTitle.ifBlank { words.defaultSavedNoteTitle }
            when (val result = saveScanDocumentUseCase.saveAsNote(
                title = title,
                extractedText = state.extractedText,
                fileRecord = null,
                clientId = state.selectedClientId,
                projectId = state.selectedProjectId,
            )) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notice = words.noteSaved(title),
                            isSavedSuccess = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.failedToSaveNote(result.error.detail)) }
                }
            }
        }
    }
}
