package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.scanner.BusinessCardParser
import com.rayluxeo.aida.domain.scanner.OcrResult
import com.rayluxeo.aida.domain.scanner.ParsedBusinessCard
import com.rayluxeo.aida.domain.scanner.ParsedDocument
import com.rayluxeo.aida.domain.scanner.ParsedHandwriting
import com.rayluxeo.aida.domain.scanner.ParsedReceipt
import com.rayluxeo.aida.domain.scanner.ReceiptParser
import com.rayluxeo.aida.domain.scanner.ScanType
import com.rayluxeo.aida.domain.scanner.TextRecognizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ProcessScanUseCase @Inject constructor(
    private val textRecognizer: TextRecognizer,
) {
    suspend fun executeOcr(imageAbsolutePath: String): AppResult<OcrResult> {
        return textRecognizer.recognizeTextFromPath(imageAbsolutePath)
    }

    fun parseReceipt(ocrText: String, defaultCurrencyCode: String = "BDT"): ParsedReceipt {
        return ReceiptParser.parse(ocrText, defaultCurrencyCode)
    }

    fun parseBusinessCard(ocrText: String): ParsedBusinessCard {
        return BusinessCardParser.parse(ocrText)
    }

    fun parseDocument(ocrText: String): ParsedDocument {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = lines.firstOrNull() ?: "Scanned Document"
        return ParsedDocument(title = title, extractedText = ocrText)
    }

    fun parseHandwriting(ocrText: String): ParsedHandwriting {
        val isLow = ocrText.isBlank() || ocrText.length < 5
        return ParsedHandwriting(recognizedText = ocrText, isRecognitionLowConfidence = isLow)
    }
}

class SaveScanExpenseUseCase @Inject constructor(
    private val recordExpenseUseCase: RecordExpenseUseCase,
    private val fileRepository: FileRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        displayName: String,
        relativePath: String,
        sizeBytes: Long,
        merchant: String,
        amount: Money,
        category: ExpenseCategory,
        spentAtDate: LocalDate?,
        rawText: String,
        clientId: Long? = null,
        projectId: Long? = null,
        isBillable: Boolean = false,
    ): AppResult<Expense> {
        val now = clock.now()
        val fileRecord = FileRecord(
            displayName = displayName,
            relativePath = relativePath,
            mimeType = "image/jpeg",
            sizeBytes = sizeBytes,
            source = FileSource.SCAN,
            extractedText = rawText,
            clientId = clientId,
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
        )

        val regResult = fileRepository.registerScan(fileRecord, pageCount = 1)
        val fileId = (regResult as? AppResult.Success)?.value?.id

        val spentAt = spentAtDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant() ?: now

        return recordExpenseUseCase(
            amount = amount,
            description = merchant,
            category = category,
            clientId = clientId,
            projectId = projectId,
            receiptFileId = fileId,
            isBillable = isBillable,
            spentAt = spentAt,
        )
    }
}

class SaveBusinessCardClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        parsed: ParsedBusinessCard,
    ): AppResult<Client> {
        val name = parsed.name?.takeIf { it.isNotBlank() }
            ?: parsed.company?.takeIf { it.isNotBlank() }
            ?: return failure(MiraErrorCode.VALIDATION, "A client name or company is required")

        val now = clock.now()
        val client = Client(
            name = name,
            companyName = parsed.company?.takeIf { it != name },
            email = parsed.email,
            phone = parsed.phone,
            addressLine = parsed.address,
            notes = "Added from Business Card Scan.\nWebsite: ${parsed.website ?: "N/A"}\n\n${parsed.rawText}",
            createdAt = now,
            updatedAt = now,
        )

        return clientRepository.create(client)
    }
}

class SaveBusinessCardNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository,
    private val fileRepository: FileRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        displayName: String,
        relativePath: String,
        sizeBytes: Long,
        parsed: ParsedBusinessCard,
    ): AppResult<Note> {
        val now = clock.now()
        val fileRecord = FileRecord(
            displayName = displayName,
            relativePath = relativePath,
            mimeType = "image/jpeg",
            sizeBytes = sizeBytes,
            source = FileSource.SCAN,
            extractedText = parsed.rawText,
            createdAt = now,
            updatedAt = now,
        )

        val regResult = fileRepository.registerScan(fileRecord, pageCount = 1)
        val fileId = (regResult as? AppResult.Success)?.value?.id

        val title = "Business Card: " + (parsed.name ?: parsed.company ?: "Contact")
        val body = buildString {
            appendLine("Name: ${parsed.name ?: "N/A"}")
            appendLine("Company: ${parsed.company ?: "N/A"}")
            appendLine("Phone: ${parsed.phone ?: "N/A"}")
            appendLine("Email: ${parsed.email ?: "N/A"}")
            appendLine("Website: ${parsed.website ?: "N/A"}")
            appendLine("Address: ${parsed.address ?: "N/A"}")
            appendLine()
            appendLine("--- OCR Text ---")
            append(parsed.rawText)
        }

        val note = Note(
            title = title,
            body = body,
            createdAt = now,
            updatedAt = now,
        )

        val createResult = noteRepository.create(note)
        val createdNote = (createResult as? AppResult.Success)?.value ?: return createResult

        if (fileId != null) {
            noteRepository.attach(createdNote.id, fileId)
        }

        return AppResult.Success(createdNote)
    }
}

class SaveScanDocumentUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val noteRepository: NoteRepository,
    private val clock: MiraClock,
) {
    suspend fun saveDocumentFile(
        displayName: String,
        relativePath: String,
        sizeBytes: Long,
        extractedText: String,
        clientId: Long? = null,
        projectId: Long? = null,
    ): AppResult<FileRecord> {
        val now = clock.now()
        val fileRecord = FileRecord(
            displayName = displayName,
            relativePath = relativePath,
            mimeType = "image/jpeg",
            sizeBytes = sizeBytes,
            source = FileSource.SCAN,
            extractedText = extractedText,
            clientId = clientId,
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
        )

        return fileRepository.registerScan(fileRecord, pageCount = 1)
    }

    suspend fun saveAsNote(
        title: String,
        extractedText: String,
        fileRecord: FileRecord?,
        clientId: Long? = null,
        projectId: Long? = null,
    ): AppResult<Note> {
        val now = clock.now()
        val note = Note(
            title = title.takeIf { it.isNotBlank() } ?: "Scanned Note",
            body = extractedText,
            clientId = clientId,
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
        )

        val createResult = noteRepository.create(note)
        val createdNote = (createResult as? AppResult.Success)?.value ?: return createResult

        if (fileRecord != null && fileRecord.id > 0L) {
            noteRepository.attach(createdNote.id, fileRecord.id)
        }

        return AppResult.Success(createdNote)
    }
}
