package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.serialization.SerializationException

/**
 * Turns Gemini's JSON into an [AppCommand], or into nothing at all.
 *
 * The rule throughout is that a half-understood utterance is worse than an unrecognised one: a
 * missing title, an unparseable timestamp or a priority Mira does not have all produce
 * [EngineResult.NotUnderstood], which sends Abu back to whatever the on-device engine concluded.
 * Whatever does come out still passes through the ordinary CommandValidator afterwards — this
 * mapper is a filter, not a gate.
 */
class GeminiCommandMapper @Inject constructor(
    private val clock: MiraClock,
) {

    fun map(rawJson: String): EngineResult {
        val payload = try {
            GeminiJson.forResponses.decodeFromString(GeminiCommandPayload.serializer(), rawJson.trim())
        } catch (error: SerializationException) {
            return EngineResult.NotUnderstood
        }

        val name = payload.command.cleaned()
            ?.uppercase()
            ?.let { text -> GeminiCommandName.entries.firstOrNull { it.name == text } }
            ?: return EngineResult.NotUnderstood

        val command = when (name) {
            GeminiCommandName.CREATE_TASK -> createTask(payload)
            GeminiCommandName.COMPLETE_TASK -> completeTask(payload)
            GeminiCommandName.SEARCH_TASKS -> searchTasks(payload)
            GeminiCommandName.CREATE_REMINDER -> createReminder(payload)
            GeminiCommandName.GET_DAILY_BRIEF -> AppCommand.GetDailyBrief
            GeminiCommandName.PERFORM_CALCULATION -> performCalculation(payload)
            GeminiCommandName.ANSWER -> answer(payload)
            GeminiCommandName.UNKNOWN -> null
        } ?: return EngineResult.NotUnderstood

        return EngineResult.Interpreted(command, confidenceOf(payload))
    }

    private fun createTask(payload: GeminiCommandPayload): AppCommand? {
        val title = payload.title.cleaned() ?: return null
        val dueAt = payload.dueAt.cleaned()?.let { parseInstant(it) ?: return null }
        val priority = payload.priority.cleaned()?.let { text ->
            TaskPriority.entries.firstOrNull { it.name == text.uppercase() } ?: return null
        } ?: TaskPriority.NORMAL
        return AppCommand.CreateTask(
            title = title,
            dueAt = dueAt,
            priority = priority,
            notes = payload.notes.cleaned(),
        )
    }

    /** Gemini never sees the database, so it can only ever name a task, never identify one by id. */
    private fun completeTask(payload: GeminiCommandPayload): AppCommand? {
        val title = payload.title.cleaned() ?: return null
        return AppCommand.CompleteTask(TaskTarget.ByTitle(title))
    }

    private fun searchTasks(payload: GeminiCommandPayload): AppCommand? {
        val filter = payload.filter.cleaned()?.let { text ->
            TaskFilter.entries.firstOrNull { it.name == text.uppercase() } ?: return null
        } ?: TaskFilter.OPEN
        return AppCommand.SearchTasks(query = payload.query.cleaned(), filter = filter)
    }

    private fun createReminder(payload: GeminiCommandPayload): AppCommand? {
        val title = payload.title.cleaned() ?: return null
        val triggerAt = parseInstant(payload.triggerAt.cleaned() ?: return null) ?: return null
        // A reminder the model placed in the past is a resolution mistake, not a request.
        if (!triggerAt.isAfter(clock.now())) return null
        return AppCommand.CreateReminder(title = title, triggerAt = triggerAt)
    }

    private fun performCalculation(payload: GeminiCommandPayload): AppCommand? {
        val expression = payload.expression.cleaned() ?: return null
        return AppCommand.PerformCalculation(expression)
    }

    /** A spoken reply with no words in it is not an answer; it goes back as a miss. */
    private fun answer(payload: GeminiCommandPayload): AppCommand? {
        val text = payload.answer.cleaned() ?: return null
        return AppCommand.Conversational(text)
    }

    /**
     * An offset is what the schema asks for. A zone-less local time is still accepted and read in
     * Abu's own zone, because the model is told the local time and sometimes answers in kind.
     */
    private fun parseInstant(text: String): Instant? {
        try {
            return OffsetDateTime.parse(text).toInstant()
        } catch (error: DateTimeParseException) {
            return try {
                LocalDateTime.parse(text).atZone(clock.zone()).toInstant()
            } catch (fallbackError: DateTimeParseException) {
                null
            }
        }
    }

    /** An unstated confidence stays below the confirmation threshold, so Mira asks rather than acts. */
    private fun confidenceOf(payload: GeminiCommandPayload): Float =
        (payload.confidence ?: UNSTATED_CONFIDENCE).coerceIn(0f, 1f)

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val UNSTATED_CONFIDENCE = 0.5f
    }
}
