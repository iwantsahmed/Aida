package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.ai.gemini.GeminiAvailabilityManager
import com.rayluxeo.aida.ai.gemini.GeminiClient
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.GeminiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepositoryImpl @Inject constructor(
    private val client: GeminiClient,
    private val credentials: GeminiCredentialStore,
    private val availabilityManager: GeminiAvailabilityManager,
) : GeminiRepository {

    override suspend fun generateContent(prompt: String, systemInstruction: String?): AppResult<String> {
        if (!availabilityManager.isAvailable()) {
            return failure(MiraErrorCode.ENGINE_UNAVAILABLE, availabilityManager.fallbackMessage())
        }
        val apiKey = credentials.apiKey() ?: return failure(MiraErrorCode.AUTHENTICATION_FAILED, "No Gemini API key stored")
        val instruction = systemInstruction ?: "You are Aida, an intelligent assistant. Provide concise, confident, and helpful answers."
        return client.generate(apiKey, instruction, prompt)
    }

    override suspend fun summarizeText(text: String): AppResult<String> {
        val prompt = "Summarize the following text clearly and concisely:\n\n$text"
        return generateContent(prompt)
    }

    override suspend fun summarizeDocument(title: String, content: String): AppResult<String> {
        val prompt = "Summarize this document titled '$title':\n\n$content"
        return generateContent(prompt)
    }

    override suspend fun summarizeMeeting(title: String, notes: String): AppResult<String> {
        val prompt = "Summarize key discussion points and action items for meeting '$title':\n\n$notes"
        return generateContent(prompt)
    }

    override suspend fun draftMessage(recipient: String, context: String): AppResult<String> {
        val prompt = "Draft a professional message to $recipient regarding:\n\n$context"
        return generateContent(prompt)
    }

    override suspend fun draftInvoiceDescription(lineItems: String): AppResult<String> {
        val prompt = "Write a clear, professional invoice description for line items:\n\n$lineItems"
        return generateContent(prompt)
    }

    override suspend fun answerContextualQuestion(question: String, context: String): AppResult<String> {
        val prompt = "Based on the following context:\n$context\n\nAnswer this question: $question"
        return generateContent(prompt)
    }
}
