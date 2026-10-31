package com.rayluxeo.aida.domain.scanner

import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import java.time.LocalDate

data class OcrBlock(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

data class OcrResult(
    val text: String,
    val blocks: List<OcrBlock> = emptyList(),
)

data class ParsedReceipt(
    val merchant: String?,
    val date: LocalDate?,
    val amount: Money?,
    val category: ExpenseCategory,
    val rawText: String,
)

data class ParsedBusinessCard(
    val name: String?,
    val company: String?,
    val phone: String?,
    val email: String?,
    val website: String?,
    val address: String?,
    val rawText: String,
)

data class ParsedInvoice(
    val issuer: String?,
    val invoiceNumber: String?,
    val date: LocalDate?,
    val totalAmount: Money?,
    val rawText: String,
)

data class ParsedDocument(
    val title: String?,
    val extractedText: String,
)

data class ParsedHandwriting(
    val recognizedText: String,
    val isRecognitionLowConfidence: Boolean,
)
