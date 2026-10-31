package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.PlaceTrigger
import java.time.DayOfWeek
import java.time.Duration
import org.junit.Test

/**
 * Guards the register rather than the wording: however long Abu's own titles are, a spoken reply
 * stays one breath long, on one line, and never gets excited.
 */
class SpokenReplyLengthTest {

    private val renderer = testRenderer()

    private val longTitle =
        "Remember to call Ahmed about the warehouse lease renewal before the quarter closes"

    /** One of every [MiraResponse] variant, each carrying the worst input it can realistically get. */
    private val everyVariant: List<MiraResponse> = buildList {
        add(MiraResponse.TaskCreated(longTitle, testInstant(TEST_NEXT_YEAR, 16, 30)))
        add(MiraResponse.TaskCreated(longTitle, null))
        add(MiraResponse.TaskCompleted(longTitle))
        add(MiraResponse.TasksFound(0, emptyList()))
        add(MiraResponse.TasksFound(1, listOf(longTitle)))
        add(MiraResponse.TasksFound(999, List(5) { longTitle }))
        add(MiraResponse.ReminderCreated(longTitle, testInstant(TEST_TOMORROW, 6, 5)))
        add(
            MiraResponse.BriefReady(
                testBrief(
                    dayPart = DayPart.MORNING,
                    openTaskCount = 999,
                    dueTodayCount = 999,
                    overdueCount = 999,
                    completedTodayCount = 999,
                    nextReminderTitle = longTitle,
                    nextReminderAt = testInstant(TEST_NEXT_YEAR, 16, 30),
                )
            )
        )
        add(MiraResponse.BriefReady(testBrief(dayPart = DayPart.NIGHT)))
        add(MiraResponse.CalculationResult("1200 + 50", "1,250"))
        add(MiraResponse.AmbiguousTask(longTitle, List(4) { longTitle }))
        add(MiraResponse.NotUnderstood(longTitle))
        add(MiraResponse.TaskUpdated(longTitle, testInstant(TEST_NEXT_YEAR, 16, 30)))
        add(MiraResponse.TaskUpdated(longTitle, null))
        add(MiraResponse.AlarmSet(longTitle, testInstant(TEST_TOMORROW, 6, 5)))
        add(MiraResponse.AlarmSet("", testInstant(TEST_NEXT_YEAR, 6, 5)))
        add(
            MiraResponse.AlarmSet(
                label = longTitle,
                triggerAt = testInstant(TEST_TOMORROW, 6, 5),
                repeatDays = DayOfWeek.entries.toSet(),
            )
        )
        add(
            MiraResponse.AlarmSet(
                label = longTitle,
                triggerAt = testInstant(TEST_TOMORROW, 6, 5),
                repeatDays = WEEKDAY_SET,
            )
        )
        // Six days: the longest repeat there is, because seven collapses into one word.
        add(
            MiraResponse.AlarmSet(
                label = longTitle,
                triggerAt = testInstant(TEST_TOMORROW, 6, 5),
                repeatDays = DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY,
            )
        )
        add(
            MiraResponse.PlaceReminderCreated(
                title = longTitle,
                placeLabel = longTitle,
                trigger = PlaceTrigger.ENTER,
                isWatching = true,
            )
        )
        add(
            MiraResponse.PlaceReminderCreated(
                title = longTitle,
                placeLabel = longTitle,
                trigger = PlaceTrigger.EXIT,
                isWatching = false,
            )
        )
        add(MiraResponse.PlaceUnknown(longTitle, List(4) { longTitle }))
        add(MiraResponse.PlaceUnknown(longTitle, emptyList()))
        add(
            MiraResponse.TimerStarted(
                label = longTitle,
                duration = Duration.ofHours(2).plusMinutes(30),
                endsAt = testInstant(TEST_NEXT_YEAR, 16, 30),
            )
        )
        add(
            MiraResponse.TimerStarted(
                label = "",
                duration = Duration.ofSeconds(45),
                endsAt = testInstant(TEST_TODAY, 9, 45),
            )
        )
        add(MiraResponse.NoteCreated(longTitle))
        add(MiraResponse.NoteCreated(null))
        add(MiraResponse.NotesFound(0, emptyList()))
        add(MiraResponse.NotesFound(999, List(5) { longTitle }))
        add(MiraResponse.MemoryStored(longTitle, longTitle))
        add(MiraResponse.MemoriesFound(0, emptyList()))
        add(MiraResponse.MemoriesFound(999, List(5) { longTitle }))
        add(MiraResponse.ClientCreated(longTitle))
        add(MiraResponse.ClientsFound(0, emptyList()))
        add(MiraResponse.ClientsFound(999, List(5) { longTitle }))
        add(MiraResponse.InvoiceDraftCreated("INV-2026-0042", longTitle, taka("150000")))
        add(MiraResponse.PaymentRecorded(taka("150000"), longTitle, "INV-2026-0042"))
        add(MiraResponse.PaymentRecorded(taka("1234.56"), null, "INV-2026-0042"))
        add(MiraResponse.PaymentRecorded(taka("1234.56"), null, null))
        add(MiraResponse.ExpenseRecorded(taka("99999999.99"), longTitle))
        add(MiraResponse.ExpenseRecorded(taka("500"), ""))
        add(MiraResponse.IncomeRecorded(taka("99999999.99"), longTitle))
        add(MiraResponse.IncomeRecorded(taka("500"), ""))
        add(MiraResponse.CalendarEventCreated(longTitle, testInstant(TEST_NEXT_YEAR, 16, 30)))
        add(MiraResponse.CalendarEventCreated(longTitle, testInstant(TEST_NEXT_YEAR, 16, 30), listOf("Prior meeting")))
        add(MiraResponse.ScheduleReport(testInstant(TEST_NEXT_YEAR, 9, 0), testInstant(TEST_NEXT_YEAR, 18, 0), emptyList()))
        add(MiraResponse.ScheduleReport(testInstant(TEST_NEXT_YEAR, 9, 0), testInstant(TEST_NEXT_YEAR, 18, 0), listOf(
            com.rayluxeo.aida.domain.model.CalendarEvent(1L, 100L, longTitle, testInstant(TEST_NEXT_YEAR, 10, 0), testInstant(TEST_NEXT_YEAR, 11, 0))
        )))
        add(MiraResponse.FreeSlotsReport(java.time.LocalDate.of(2026, 10, 15), emptyList()))
        add(MiraResponse.FreeSlotsReport(java.time.LocalDate.of(2026, 10, 15), listOf(
            com.rayluxeo.aida.domain.model.TimeWindow(testInstant(TEST_NEXT_YEAR, 14, 0), testInstant(TEST_NEXT_YEAR, 15, 0))
        )))
        add(MiraResponse.CalendarEventUpdated(longTitle, testInstant(TEST_NEXT_YEAR, 15, 0)))
        add(MiraResponse.CalendarEventDeleted(longTitle))
        add(MiraResponse.ContactsFound(0, emptyList()))
        add(MiraResponse.ContactsFound(999, List(4) { testContact(longTitle) }))
        add(MiraResponse.ContactDetails(testContact(longTitle), com.rayluxeo.aida.domain.model.ContactField.PHONE))
        add(MiraResponse.ContactDetails(testContact(longTitle), com.rayluxeo.aida.domain.model.ContactField.EMAIL))
        add(MiraResponse.ContactDetails(testContact(longTitle), com.rayluxeo.aida.domain.model.ContactField.ANY))
        add(MiraResponse.CallPrepared(testContact(longTitle)))
        add(MiraResponse.CallPrepared(testContact(longTitle, phoneNumber = null)))
        add(MiraResponse.MessagePrepared(testContact(longTitle), longTitle))
        add(MiraResponse.MessagePrepared(testContact(longTitle), null))
        add(MiraResponse.MessagePrepared(testContact(longTitle), longTitle, com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP))
        add(MiraResponse.AmbiguousClient(longTitle, List(4) { longTitle }))
        add(MiraResponse.AmbiguousClient(longTitle, emptyList()))
        add(MiraResponse.AmbiguousContact(longTitle, List(4) { testContact(longTitle) }))
        add(MiraResponse.AmbiguousContact(longTitle, emptyList()))
        addAll(MiraCapability.entries.map { MiraResponse.PermissionNeeded(it) })
        addAll(MiraErrorCode.entries.map { MiraResponse.Problem(it) })
    }

    @Test
    fun `no spoken reply runs long`() {
        forEachSpokenReply { spoken ->
            assertThat(spoken.length).isAtMost(MAX_SPOKEN_LENGTH)
            assertThat(spoken).isNotEmpty()
            assertThat(spoken).isEqualTo(spoken.trim())
        }
    }

    @Test
    fun `spoken replies stay on one line`() {
        forEachSpokenReply { spoken -> assertThat(spoken).doesNotContain("\n") }
    }

    @Test
    fun `Mira never exclaims`() {
        everyVariant.forEach { response ->
            SpokenLanguage.entries.forEach { language ->
                assertThat(renderer.speak(response, language)).doesNotContain("!")
                assertThat(renderer.display(response, language)).doesNotContain("!")
            }
        }
    }

    @Test
    fun `every variant has something to display`() {
        everyVariant.forEach { response ->
            SpokenLanguage.entries.forEach { language ->
                val shown = renderer.display(response, language)
                assertThat(shown).isNotEmpty()
                assertThat(shown).isEqualTo(shown.trim())
            }
        }
    }

    @Test
    fun `a long title is clipped for speech but kept whole in the transcript`() {
        val response = MiraResponse.TaskCompleted(longTitle)

        SpokenLanguage.entries.forEach { language ->
            assertThat(renderer.speak(response, language)).contains("…")
            assertThat(renderer.display(response, language)).contains(longTitle)
        }
    }

    private fun forEachSpokenReply(check: (String) -> Unit) {
        everyVariant.forEach { response ->
            SpokenLanguage.entries.forEach { language ->
                check(renderer.speak(response, language))
            }
        }
    }

    private companion object {
        /** Roughly ten seconds of speech. Anything longer stops being an answer. */
        const val MAX_SPOKEN_LENGTH = 180
    }
}
