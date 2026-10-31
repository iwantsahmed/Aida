package com.rayluxeo.aida.domain.command

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.TaskFilter
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/**
 * The validator is the one gate every engine's output passes through, Gemini's included, so its
 * rules are tested directly rather than only through the screens that happen to exercise them.
 */
class DefaultCommandValidatorTest {

    private val now: Instant = Instant.parse("2026-03-10T09:00:00Z")
    private val clock = object : MiraClock {
        override fun now(): Instant = now
        override fun zone(): ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val validator = DefaultCommandValidator(clock)

    private fun accepted(command: AppCommand): AppCommand {
        val result = validator.validate(command)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private fun rejectedCode(command: AppCommand): MiraErrorCode {
        val result = validator.validate(command)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        return (result as AppResult.Failure).error.code
    }

    @Test
    fun `a blank task title is refused`() {
        assertThat(rejectedCode(AppCommand.CreateTask(title = "   ")))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a task title is trimmed rather than refused for stray spacing`() {
        val command = accepted(AppCommand.CreateTask(title = "  call Ahmed  ")) as AppCommand.CreateTask
        assertThat(command.title).isEqualTo("call Ahmed")
    }

    @Test
    fun `an absurdly long task title is refused`() {
        assertThat(rejectedCode(AppCommand.CreateTask(title = "a".repeat(201))))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `completing by a non positive id is refused`() {
        assertThat(rejectedCode(AppCommand.CompleteTask(TaskTarget.ById(0L))))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `completing by a blank title is refused`() {
        assertThat(rejectedCode(AppCommand.CompleteTask(TaskTarget.ByTitle(" "))))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `completing by title trims the reference`() {
        val command = accepted(AppCommand.CompleteTask(TaskTarget.ByTitle(" call Ahmed ")))
        assertThat((command as AppCommand.CompleteTask).target)
            .isEqualTo(TaskTarget.ByTitle("call Ahmed"))
    }

    @Test
    fun `a blank search query becomes no query rather than an empty one`() {
        val command = accepted(AppCommand.SearchTasks(query = "   ", filter = TaskFilter.OPEN))
        assertThat((command as AppCommand.SearchTasks).query).isNull()
    }

    @Test
    fun `a reminder in the past is refused`() {
        val command = AppCommand.CreateReminder("call Ahmed", now.minusSeconds(1))
        assertThat(rejectedCode(command)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a reminder at exactly now is refused because it would never fire`() {
        assertThat(rejectedCode(AppCommand.CreateReminder("call Ahmed", now)))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a reminder in the future is accepted and trimmed`() {
        val command = accepted(AppCommand.CreateReminder("  call Ahmed ", now.plusSeconds(60)))
        assertThat((command as AppCommand.CreateReminder).title).isEqualTo("call Ahmed")
    }

    @Test
    fun `a one-time alarm in the past is refused`() {
        assertThat(rejectedCode(AppCommand.CreateAlarm("wake up", now.minusSeconds(60))))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    /**
     * A repeating alarm names a time of day, not a moment. Seven o'clock having gone by is the
     * ordinary case — it rings tomorrow — and refusing it would refuse the commonest alarm there is.
     */
    @Test
    fun `a repeating alarm whose time has already gone by today is accepted`() {
        val command = accepted(
            AppCommand.CreateAlarm(
                label = "fajr",
                triggerAt = now.minusSeconds(60),
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            )
        )
        assertThat((command as AppCommand.CreateAlarm).repeatDays)
            .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
    }

    /** "Wake me up at 7" names no subject; the screen supplies the word, so blank passes here. */
    @Test
    fun `an alarm Abu did not name is accepted and left blank`() {
        val command = accepted(AppCommand.CreateAlarm("   ", now.plusSeconds(60)))
        assertThat((command as AppCommand.CreateAlarm).label).isEmpty()
    }

    @Test
    fun `an alarm label is trimmed rather than refused for stray spacing`() {
        val command = accepted(AppCommand.CreateAlarm("  fajr  ", now.plusSeconds(60)))
        assertThat((command as AppCommand.CreateAlarm).label).isEqualTo("fajr")
    }

    @Test
    fun `an absurdly long alarm label is still refused`() {
        assertThat(rejectedCode(AppCommand.CreateAlarm("a".repeat(201), now.plusSeconds(60))))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a place reminder needs both something to do and somewhere to do it`() {
        assertThat(
            rejectedCode(AppCommand.CreatePlaceReminder("   ", "office", PlaceTrigger.ENTER))
        ).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(
            rejectedCode(AppCommand.CreatePlaceReminder("buy printer ink", "  ", PlaceTrigger.ENTER))
        ).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a place reminder is trimmed on both halves`() {
        val command = accepted(
            AppCommand.CreatePlaceReminder("  buy printer ink ", " the office ", PlaceTrigger.EXIT)
        ) as AppCommand.CreatePlaceReminder

        assertThat(command.title).isEqualTo("buy printer ink")
        assertThat(command.placeLabel).isEqualTo("the office")
        assertThat(command.trigger).isEqualTo(PlaceTrigger.EXIT)
    }

    /** A place reminder has no moment of its own, so the past-time rule must not reach it. */
    @Test
    fun `a place reminder is never refused for a time it does not have`() {
        assertThat(accepted(AppCommand.CreatePlaceReminder("buy ink", "office", PlaceTrigger.ENTER)))
            .isInstanceOf(AppCommand.CreatePlaceReminder::class.java)
    }

    @Test
    fun `a blank expression is refused`() {
        assertThat(rejectedCode(AppCommand.PerformCalculation("  ")))
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `the daily brief needs nothing and is always accepted`() {
        assertThat(accepted(AppCommand.GetDailyBrief)).isEqualTo(AppCommand.GetDailyBrief)
    }

    @Test
    fun `calendar event with end time before start time is refused`() {
        val command = AppCommand.CreateCalendarEvent(
            title = "Meeting",
            startsAt = now.plusSeconds(3600),
            endsAt = now.plusSeconds(1800),
        )
        assertThat(rejectedCode(command)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `updating calendar event with blank title is refused`() {
        val command = AppCommand.UpdateCalendarEvent(targetTitle = "  ")
        assertThat(rejectedCode(command)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `deleting calendar event with blank target is refused`() {
        val command = AppCommand.DeleteCalendarEvent(targetTitle = "   ")
        assertThat(rejectedCode(command)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `schedule query with inverted window is refused`() {
        val command = AppCommand.QuerySchedule(
            from = now.plusSeconds(3600),
            to = now.plusSeconds(1800),
        )
        assertThat(rejectedCode(command)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `only the commands that write are marked as mutating`() {
        assertThat(AppCommand.CreateTask("x").isMutating).isTrue()
        assertThat(AppCommand.CompleteTask(TaskTarget.ById(1)).isMutating).isTrue()
        assertThat(AppCommand.CreateReminder("x", now.plusSeconds(60)).isMutating).isTrue()
        assertThat(AppCommand.CreateCalendarEvent("x", now).isMutating).isTrue()
        assertThat(AppCommand.UpdateCalendarEvent("x").isMutating).isTrue()
        assertThat(AppCommand.DeleteCalendarEvent("x").isMutating).isTrue()
        assertThat(AppCommand.SearchTasks("x").isMutating).isFalse()
        assertThat(AppCommand.GetDailyBrief.isMutating).isFalse()
        assertThat(AppCommand.PerformCalculation("1+1").isMutating).isFalse()
        assertThat(AppCommand.QuerySchedule(now, now.plusSeconds(60)).isMutating).isFalse()
        assertThat(AppCommand.FindFreeSlots(java.time.LocalDate.now()).isMutating).isFalse()
        assertThat(AppCommand.FindContact("Ahmed").isMutating).isFalse()
        assertThat(AppCommand.PrepareCall(ContactTarget.ByName("Ahmed")).isMutating).isFalse()
        assertThat(AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), "hi").isMutating).isFalse()
    }
}
