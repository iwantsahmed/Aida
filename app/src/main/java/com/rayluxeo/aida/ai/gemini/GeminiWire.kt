package com.rayluxeo.aida.ai.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The exact command names Gemini is allowed to return. The list is closed on purpose: it is the
 * v0.1 vocabulary and nothing else, so the model cannot talk Mira into a verb she cannot carry out.
 */
internal enum class GeminiCommandName {
    CREATE_TASK,
    COMPLETE_TASK,
    SEARCH_TASKS,
    CREATE_REMINDER,
    GET_DAILY_BRIEF,
    PERFORM_CALCULATION,
    // Not one of Mira's verbs: a plain spoken answer to a question or remark. The reply rides in the
    // `answer` field. UNKNOWN still exists for an utterance even this cannot help with.
    ANSWER,
    UNKNOWN,
}

/** The one object Gemini is asked to produce. Every field beyond `command` is optional per command. */
@Serializable
internal data class GeminiCommandPayload(
    val command: String? = null,
    val title: String? = null,
    val dueAt: String? = null,
    val priority: String? = null,
    val notes: String? = null,
    val query: String? = null,
    val filter: String? = null,
    val triggerAt: String? = null,
    val expression: String? = null,
    val answer: String? = null,
    val confidence: Float? = null,
)

@Serializable
internal data class GenerateContentRequest(
    val systemInstruction: Content,
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
)

@Serializable
internal data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable
internal data class Part(val text: String = "")

// No thinkingConfig is carried. Mira runs on gemini-flash-lite-latest (the one model this free key
// reaches without a 404), and the 3.x Flash-Lite line rejects an explicit `thinkingBudget: 0` with a
// 400 — the generation that was meant to suppress deliberation instead failed outright. The model
// keeps its own minimal thinking either way, which is why the read budget in GeminiClient is sized
// for it rather than for an instant reply.
@Serializable
internal data class GenerationConfig(
    val responseMimeType: String,
    val responseSchema: JsonObject,
    val temperature: Double,
    val candidateCount: Int,
    val maxOutputTokens: Int,
)

@Serializable
internal data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

@Serializable
internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

internal object GeminiJson {
    /** `explicitNulls = false` keeps unset fields off the wire rather than sending a wall of nulls. */
    val forRequests: Json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    /** Gemini adds response fields over time; unknown ones must not turn a good answer into a failure. */
    val forResponses: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

/**
 * The structured-output schema handed to Gemini. It mirrors the six v0.1 commands field for
 * field, which is what stops the model from inventing a verb or a parameter Mira cannot execute.
 */
internal object MiraCommandSchema {

    val json: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("command") {
                put("type", "STRING")
                put("description", "Which command the utterance asks for. UNKNOWN if none of them fit.")
                putJsonArray("enum") { GeminiCommandName.entries.forEach { add(it.name) } }
            }
            put(
                "title",
                text("The task or reminder title for CREATE_TASK and CREATE_REMINDER, or the task to " +
                    "match for COMPLETE_TASK. Keep the user's own wording."),
            )
            put(
                "dueAt",
                text("Optional due time for CREATE_TASK as an ISO-8601 timestamp with an offset, " +
                    "for example 2026-09-24T18:30:00+06:00."),
            )
            put(
                "priority",
                enumeration(
                    "Optional priority for CREATE_TASK. Leave empty unless the user said so.",
                    listOf("LOW", "NORMAL", "HIGH"),
                ),
            )
            put("notes", text("Optional extra detail for CREATE_TASK."))
            put("query", text("Optional search words for SEARCH_TASKS. Leave empty to list everything."))
            put(
                "filter",
                enumeration(
                    "Which tasks SEARCH_TASKS should look at. Defaults to OPEN.",
                    listOf("ALL", "OPEN", "DONE", "DUE_TODAY", "OVERDUE"),
                ),
            )
            put(
                "triggerAt",
                text("When CREATE_REMINDER should fire, as an ISO-8601 timestamp with an offset. " +
                    "It must be in the future."),
            )
            put(
                "expression",
                text("The arithmetic for PERFORM_CALCULATION, copied across unevaluated, " +
                    "for example \"15% of 2400\"."),
            )
            put(
                "answer",
                text("For ANSWER only: the reply to speak back, in the user's own language. Empty " +
                    "for every other command."),
            )
            putJsonObject("confidence") {
                put("type", "NUMBER")
                put("description", "How sure you are that this is what the user meant, from 0 to 1.")
            }
        }
        putJsonArray("required") {
            add("command")
            add("confidence")
        }
        putJsonArray("propertyOrdering") {
            add("command")
            add("title")
            add("dueAt")
            add("priority")
            add("notes")
            add("query")
            add("filter")
            add("triggerAt")
            add("expression")
            add("answer")
            add("confidence")
        }
    }

    private fun text(description: String): JsonObject = buildJsonObject {
        put("type", "STRING")
        put("description", description)
        put("nullable", true)
    }

    private fun enumeration(description: String, values: List<String>): JsonObject = buildJsonObject {
        put("type", "STRING")
        put("description", description)
        put("nullable", true)
        putJsonArray("enum") { values.forEach { add(it) } }
    }
}
