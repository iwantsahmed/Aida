package com.rayluxeo.aida.ai.gemini

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/** Fixtures only — the mapper is pure, so none of this touches the network. */
class GeminiCommandMapperTest {

    private val clock = FixedClock(
        at = Instant.parse("2026-09-24T10:00:00Z"),
        timeZone = ZoneId.of("Asia/Dhaka"),
    )
    private val mapper = GeminiCommandMapper(clock)

    @Test
    fun `a fully specified task carries every field across`() {
        val command = commandFrom(
            """
            {"command":"CREATE_TASK","title":"Call Ahmed","dueAt":"2026-09-25T18:30:00+06:00",
             "priority":"HIGH","notes":"about the flat","confidence":0.9}
            """,
        )
        assertThat(command).isEqualTo(
            AppCommand.CreateTask(
                title = "Call Ahmed",
                dueAt = Instant.parse("2026-09-25T12:30:00Z"),
                priority = TaskPriority.HIGH,
                notes = "about the flat",
            ),
        )
    }

    @Test
    fun `a bare task falls back to the model defaults`() {
        val command = commandFrom("""{"command":"CREATE_TASK","title":"Buy rice","confidence":0.8}""")
        assertThat(command).isEqualTo(AppCommand.CreateTask(title = "Buy rice"))
    }

    @Test
    fun `blank optional fields count as unspecified`() {
        val command = commandFrom(
            """{"command":"CREATE_TASK","title":"Buy rice","dueAt":"","priority":"","notes":" ","confidence":0.8}""",
        )
        assertThat(command).isEqualTo(AppCommand.CreateTask(title = "Buy rice"))
    }

    @Test
    fun `a task with no title is not half-built`() {
        assertThat(mapper.map("""{"command":"CREATE_TASK","confidence":0.9}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `an unparseable due time rejects the whole command`() {
        assertThat(
            mapper.map("""{"command":"CREATE_TASK","title":"Call Ahmed","dueAt":"next tuesday","confidence":0.9}"""),
        ).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `a priority Mira does not have rejects the whole command`() {
        assertThat(
            mapper.map("""{"command":"CREATE_TASK","title":"Call Ahmed","priority":"URGENT","confidence":0.9}"""),
        ).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `completion can only ever name a task, never an id`() {
        val command = commandFrom("""{"command":"COMPLETE_TASK","title":"call Ahmed","confidence":0.7}""")
        assertThat(command).isEqualTo(AppCommand.CompleteTask(TaskTarget.ByTitle("call Ahmed")))
    }

    @Test
    fun `a search carries its filter`() {
        val command = commandFrom("""{"command":"SEARCH_TASKS","query":"rice","filter":"OVERDUE","confidence":0.7}""")
        assertThat(command).isEqualTo(AppCommand.SearchTasks(query = "rice", filter = TaskFilter.OVERDUE))
    }

    @Test
    fun `a search with nothing specified defaults to open tasks`() {
        val command = commandFrom("""{"command":"SEARCH_TASKS","query":null,"filter":null,"confidence":0.7}""")
        assertThat(command).isEqualTo(AppCommand.SearchTasks(query = null, filter = TaskFilter.OPEN))
    }

    @Test
    fun `a filter Mira does not have rejects the whole command`() {
        assertThat(mapper.map("""{"command":"SEARCH_TASKS","filter":"LATE","confidence":0.7}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `a reminder resolves an offset timestamp`() {
        val command = commandFrom(
            """{"command":"CREATE_REMINDER","title":"Take medicine","triggerAt":"2026-09-24T22:00:00+06:00","confidence":0.95}""",
        )
        assertThat(command).isEqualTo(
            AppCommand.CreateReminder(title = "Take medicine", triggerAt = Instant.parse("2026-09-24T16:00:00Z")),
        )
    }

    @Test
    fun `a zone-less timestamp is read in Abu's own zone`() {
        val command = commandFrom(
            """{"command":"CREATE_REMINDER","title":"Take medicine","triggerAt":"2026-09-24T22:00:00","confidence":0.95}""",
        )
        assertThat(command).isEqualTo(
            AppCommand.CreateReminder(title = "Take medicine", triggerAt = Instant.parse("2026-09-24T16:00:00Z")),
        )
    }

    @Test
    fun `a reminder in the past is a resolution mistake, not a request`() {
        assertThat(
            mapper.map(
                """{"command":"CREATE_REMINDER","title":"Take medicine","triggerAt":"2026-09-23T22:00:00+06:00","confidence":0.95}""",
            ),
        ).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `a reminder with no time is refused`() {
        assertThat(mapper.map("""{"command":"CREATE_REMINDER","title":"Take medicine","confidence":0.95}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `the daily brief needs no fields`() {
        assertThat(commandFrom("""{"command":"GET_DAILY_BRIEF","confidence":0.99}"""))
            .isEqualTo(AppCommand.GetDailyBrief)
    }

    @Test
    fun `arithmetic comes back unevaluated`() {
        val command = commandFrom("""{"command":"PERFORM_CALCULATION","expression":"15% of 2400","confidence":0.9}""")
        assertThat(command).isEqualTo(AppCommand.PerformCalculation("15% of 2400"))
    }

    @Test
    fun `a calculation with no expression is refused`() {
        assertThat(mapper.map("""{"command":"PERFORM_CALCULATION","confidence":0.9}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `malformed JSON is a miss, not a crash`() {
        assertThat(mapper.map("""{"command": """)).isEqualTo(EngineResult.NotUnderstood)
        assertThat(mapper.map("I think you want to create a task.")).isEqualTo(EngineResult.NotUnderstood)
        assertThat(mapper.map("")).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `a command outside the v0 1 vocabulary is a miss`() {
        assertThat(mapper.map("""{"command":"DELETE_EVERYTHING","confidence":0.99}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `the model's own UNKNOWN is a miss`() {
        assertThat(mapper.map("""{"command":"UNKNOWN","confidence":0.4}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `an ANSWER carries the spoken reply across`() {
        assertThat(commandFrom("""{"command":"ANSWER","answer":"The capital of Japan is Tokyo.","confidence":0.9}"""))
            .isEqualTo(AppCommand.Conversational("The capital of Japan is Tokyo."))
    }

    @Test
    fun `an ANSWER with no words is a miss`() {
        assertThat(mapper.map("""{"command":"ANSWER","answer":"   ","confidence":0.9}"""))
            .isEqualTo(EngineResult.NotUnderstood)
        assertThat(mapper.map("""{"command":"ANSWER","confidence":0.9}"""))
            .isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `a lowercase command name is still recognised`() {
        assertThat(commandFrom("""{"command":"create_task","title":"Buy rice","confidence":0.8}"""))
            .isEqualTo(AppCommand.CreateTask(title = "Buy rice"))
    }

    @Test
    fun `an unstated confidence stays below the confirmation threshold`() {
        val result = mapper.map("""{"command":"CREATE_TASK","title":"Buy rice"}""")
        assertThat(result).isInstanceOf(EngineResult.Interpreted::class.java)
        assertThat((result as EngineResult.Interpreted).confidence).isLessThan(EngineResult.CONFIRM_THRESHOLD)
    }

    @Test
    fun `an out-of-range confidence is clamped`() {
        val high = mapper.map("""{"command":"GET_DAILY_BRIEF","confidence":4.2}""")
        assertThat((high as EngineResult.Interpreted).confidence).isWithin(TOLERANCE).of(1.0f)

        val low = mapper.map("""{"command":"GET_DAILY_BRIEF","confidence":-3.0}""")
        assertThat((low as EngineResult.Interpreted).confidence).isWithin(TOLERANCE).of(0.0f)
    }

    private fun commandFrom(json: String): AppCommand {
        val result = mapper.map(json)
        assertThat(result).isInstanceOf(EngineResult.Interpreted::class.java)
        return (result as EngineResult.Interpreted).command
    }

    private companion object {
        const val TOLERANCE = 1.0e-6f
    }
}
