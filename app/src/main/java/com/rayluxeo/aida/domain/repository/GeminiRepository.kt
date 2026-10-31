package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult

interface GeminiRepository {
    suspend fun generateContent(prompt: String, systemInstruction: String? = null): AppResult<String>
    suspend fun summarizeText(text: String): AppResult<String>
    suspend fun summarizeDocument(title: String, content: String): AppResult<String>
    suspend fun summarizeMeeting(title: String, notes: String): AppResult<String>
    suspend fun draftMessage(recipient: String, context: String): AppResult<String>
    suspend fun draftInvoiceDescription(lineItems: String): AppResult<String>
    suspend fun answerContextualQuestion(question: String, context: String): AppResult<String>
}
