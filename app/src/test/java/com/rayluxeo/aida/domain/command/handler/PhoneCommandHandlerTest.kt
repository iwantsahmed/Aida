package com.rayluxeo.aida.domain.command.handler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ContactTarget
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.WritableCalendar
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ContactsRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The handler is tested against hand-written fakes of both ports rather than against Android's
 * providers: ContactsContract and CalendarContract cannot be exercised in a local unit test, and
 * what matters here is the decisions — whom Mira offers to call, when she asks for a permission
 * instead of failing, and the fact that she never places the call herself.
 *
 * Every instant comes from the fixed Dhaka clock, so the assertions hold on any build machine.
 */
import com.rayluxeo.aida.domain.model.CalendarLink
import com.rayluxeo.aida.domain.model.ContactField
import com.rayluxeo.aida.domain.model.MessagingApp
import com.rayluxeo.aida.domain.repository.CalendarLinkRepository
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate

class PhoneCommandHandlerTest {

    private val contacts = FakeContacts()
    private val calendar = FakeCalendar()
    private val links = FakeCalendarLinks()
    private val clock = FixedMiraClock(FixedMiraClock.dhaka(2026, 3, 10, 9))
    private val handler = DefaultPhoneCommandHandler(contacts, calendar, links, clock)

    private val tomorrowAtTen: Instant = FixedMiraClock.dhaka(2026, 3, 11, 10)
    private val tomorrowAtEleven: Instant = FixedMiraClock.dhaka(2026, 3, 11, 11)

    private val ahmedHossain = Contact(1L, "Ahmed Hossain", "+8801711000111", "ahmed@example.com")
    private val ahmedRahman = Contact(2L, "Ahmed Rahman", "+8801811000222", null)
    private val ahmedKarim = Contact(3L, "Ahmed Karim", null, "karim@example.com")

    // ------------------------------------------------------------------ calendar

    @Test
    fun `a missing calendar permission is an offer to grant it, not a failure`() = runTest {
        calendar.writable = false

        val response = succeeded(handler.createCalendarEvent(event()))

        assertThat(response).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CALENDAR))
        assertThat(calendar.written).isEmpty()
    }

    @Test
    fun `a device with nowhere to write an event says so as a validation failure`() = runTest {
        calendar.calendars = emptyList()

        assertThat(failedWith(handler.createCalendarEvent(event())))
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(calendar.written).isEmpty()
    }

    @Test
    fun `a calendar that cannot be read at all is reported rather than guessed around`() = runTest {
        calendar.failure = failure(MiraErrorCode.UNKNOWN, "provider unavailable")

        assertThat(failedWith(handler.createCalendarEvent(event()))).isEqualTo(MiraErrorCode.UNKNOWN)
    }

    @Test
    fun `an event is written into the primary calendar`() = runTest {
        val response = succeeded(
            handler.createCalendarEvent(
                AppCommand.CreateCalendarEvent(
                    title = "Ahmed meeting",
                    startsAt = tomorrowAtTen,
                    endsAt = tomorrowAtEleven,
                ),
            ),
        )

        assertThat(response)
            .isEqualTo(MiraResponse.CalendarEventCreated("Ahmed meeting", tomorrowAtTen))
        val written = calendar.written.single()
        assertThat(written.calendarId).isEqualTo(FakeCalendar.PERSONAL.id)
        assertThat(written.systemId).isNull()
        assertThat(written.endsAt).isEqualTo(tomorrowAtEleven)
    }

    @Test
    fun `an event with no end is still written, and the reply states the start it was given`() =
        runTest {
            val response = succeeded(
                handler.createCalendarEvent(
                    AppCommand.CreateCalendarEvent("Site visit", tomorrowAtTen, endsAt = null),
                ),
            )

            assertThat(response)
                .isEqualTo(MiraResponse.CalendarEventCreated("Site visit", tomorrowAtTen))
            assertThat(calendar.written.single().endsAt).isNull()
        }

    @Test
    fun `a refused write is a failure, not a quietly empty success`() = runTest {
        calendar.writeFailure = failure(MiraErrorCode.STORAGE, "provider refused the insert")

        assertThat(failedWith(handler.createCalendarEvent(event()))).isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------------------ finding people

    @Test
    fun `a missing contacts permission offers the grant and leaves the address book alone`() =
        runTest {
            contacts.permitted = false

            val response = succeeded(handler.findContact(AppCommand.FindContact("Ahmed")))

            assertThat(response).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
            assertThat(contacts.searchCount).isEqualTo(0)
        }

    @Test
    fun `a search returns everyone who matched, callable or not`() = runTest {
        contacts.people = listOf(ahmedHossain, ahmedRahman, ahmedKarim)

        val response = succeeded(handler.findContact(AppCommand.FindContact("Ahmed")))

        assertThat(response).isEqualTo(
            MiraResponse.ContactsFound(3, listOf(ahmedHossain, ahmedRahman, ahmedKarim)),
        )
    }

    @Test
    fun `finding nobody is an answer rather than a failure`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(handler.findContact(AppCommand.FindContact("Farhana")))

        assertThat(response).isEqualTo(MiraResponse.ContactsFound(0, emptyList()))
    }

    @Test
    fun `an address book that cannot be read is reported`() = runTest {
        contacts.failure = failure(MiraErrorCode.UNKNOWN, "provider unavailable")

        assertThat(failedWith(handler.findContact(AppCommand.FindContact("Ahmed"))))
            .isEqualTo(MiraErrorCode.UNKNOWN)
    }

    @Test
    fun `a grant withdrawn mid lookup still ends as an offer to grant it`() = runTest {
        contacts.failure = failure(MiraErrorCode.PERMISSION_REQUIRED, "revoked while running")

        val found = succeeded(handler.findContact(AppCommand.FindContact("Ahmed")))
        val call = succeeded(handler.prepareCall(AppCommand.PrepareCall(byName("Ahmed"))))

        assertThat(found).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
        assertThat(call).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
    }

    @Test
    fun `a calendar grant withdrawn mid write is an offer rather than a fault`() = runTest {
        calendar.writeFailure = failure(MiraErrorCode.PERMISSION_REQUIRED, "revoked while running")

        assertThat(succeeded(handler.createCalendarEvent(event())))
            .isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CALENDAR))
    }

    // ------------------------------------------------------------------ preparing a call

    @Test
    fun `preparing a call without the contacts permission offers the grant`() = runTest {
        contacts.permitted = false

        val response = succeeded(handler.prepareCall(AppCommand.PrepareCall(byName("Ahmed"))))

        assertThat(response).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
        assertThat(contacts.searchCount).isEqualTo(0)
    }

    @Test
    fun `one match with a number is handed over as a control, not dialled`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(handler.prepareCall(AppCommand.PrepareCall(byName("Ahmed"))))

        assertThat(response).isEqualTo(MiraResponse.CallPrepared(ahmedHossain))
        assertThat(contacts.searchCount).isEqualTo(1)
        assertThat(contacts.lastQuery).isEqualTo("Ahmed")
    }

    @Test
    fun `several people with numbers is a question, not an error`() = runTest {
        contacts.people = listOf(ahmedHossain, ahmedRahman)

        val response = succeeded(handler.prepareCall(AppCommand.PrepareCall(byName("Ahmed"))))

        assertThat(response).isEqualTo(
            MiraResponse.AmbiguousContact("Ahmed", listOf(ahmedHossain, ahmedRahman)),
        )
    }

    @Test
    fun `only one of several matches being callable settles the question`() = runTest {
        contacts.people = listOf(ahmedHossain, ahmedKarim)

        val response = succeeded(handler.prepareCall(AppCommand.PrepareCall(byName("Ahmed"))))

        assertThat(response).isEqualTo(MiraResponse.CallPrepared(ahmedHossain))
    }

    @Test
    fun `a contact with no number is never offered as a call`() = runTest {
        contacts.people = listOf(ahmedKarim)

        val result = handler.prepareCall(AppCommand.PrepareCall(byName("Ahmed")))

        assertThat(failedWith(result)).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat((result as AppResult.Failure).error.detail).contains("no phone number")
    }

    @Test
    fun `a name nobody matches is not found`() = runTest {
        contacts.people = listOf(ahmedHossain)

        assertThat(failedWith(handler.prepareCall(AppCommand.PrepareCall(byName("Farhana")))))
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a dictated number needs no address book and no permission`() = runTest {
        contacts.permitted = false

        val response = succeeded(
            handler.prepareCall(AppCommand.PrepareCall(ContactTarget.ByNumber(" +8801711000111 "))),
        )

        assertThat(response).isEqualTo(
            MiraResponse.CallPrepared(
                Contact(0L, "+8801711000111", "+8801711000111", email = null),
            ),
        )
        assertThat(contacts.searchCount).isEqualTo(0)
    }

    // ------------------------------------------------------------------ preparing a message

    @Test
    fun `preparing a message without the contacts permission offers the grant`() = runTest {
        contacts.permitted = false

        val response = succeeded(
            handler.prepareMessage(AppCommand.PrepareMessage(byName("Ahmed"), "on my way")),
        )

        assertThat(response).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
    }

    @Test
    fun `a message is composed and handed over with its words intact`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(
            handler.prepareMessage(AppCommand.PrepareMessage(byName("Ahmed"), "আসছি")),
        )

        assertThat(response).isEqualTo(MiraResponse.MessagePrepared(ahmedHossain, "আসছি"))
    }

    @Test
    fun `a message with no words is still prepared`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(
            handler.prepareMessage(AppCommand.PrepareMessage(byName("Ahmed"), body = null)),
        )

        assertThat(response).isEqualTo(MiraResponse.MessagePrepared(ahmedHossain, null))
    }

    @Test
    fun `an email address alone cannot be messaged`() = runTest {
        contacts.people = listOf(ahmedKarim)

        assertThat(
            failedWith(handler.prepareMessage(AppCommand.PrepareMessage(byName("Ahmed"), "hello"))),
        ).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `several reachable people is the same question for a message`() = runTest {
        contacts.people = listOf(ahmedHossain, ahmedRahman)

        val response = succeeded(
            handler.prepareMessage(AppCommand.PrepareMessage(byName("Ahmed"), "hello")),
        )

        assertThat(response).isEqualTo(
            MiraResponse.AmbiguousContact("Ahmed", listOf(ahmedHossain, ahmedRahman)),
        )
    }

    @Test
    fun `preparing a whatsapp message retains whatsapp messaging app`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(
            handler.prepareMessage(
                AppCommand.PrepareMessage(byName("Ahmed"), "ten minutes late", MessagingApp.WHATSAPP),
            ),
        )

        assertThat(response).isEqualTo(
            MiraResponse.MessagePrepared(ahmedHossain, "ten minutes late", MessagingApp.WHATSAPP),
        )
    }

    // ------------------------------------------------------------------ schedule queries & slots

    @Test
    fun `creating an event detects conflict with existing event`() = runTest {
        // Existing event from 10:00 to 11:00
        calendar.written += CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 99L,
            title = "Prior Sync",
            startsAt = tomorrowAtTen,
            endsAt = tomorrowAtEleven,
        )

        val response = succeeded(
            handler.createCalendarEvent(
                AppCommand.CreateCalendarEvent(
                    title = "New Consultation",
                    startsAt = tomorrowAtTen.plusSeconds(900),
                    endsAt = tomorrowAtEleven,
                ),
            ),
        )

        val created = response as MiraResponse.CalendarEventCreated
        assertThat(created.title).isEqualTo("New Consultation")
        assertThat(created.conflicts).contains("Prior Sync")
        assertThat(links.links).hasSize(1)
    }

    @Test
    fun `query schedule returns events in window`() = runTest {
        calendar.written += CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 101L,
            title = "Morning Sync",
            startsAt = tomorrowAtTen,
            endsAt = tomorrowAtEleven,
        )

        val response = succeeded(
            handler.querySchedule(
                AppCommand.QuerySchedule(from = tomorrowAtTen.minusSeconds(3600), to = tomorrowAtEleven.plusSeconds(3600)),
            ),
        )

        val report = response as MiraResponse.ScheduleReport
        assertThat(report.events).hasSize(1)
        assertThat(report.events.first().title).isEqualTo("Morning Sync")
    }

    @Test
    fun `find free slots returns open windows between 9am and 6pm`() = runTest {
        val date = LocalDate.of(2026, 3, 11)
        // Add event 10am to 11am and 2pm to 3pm
        calendar.written += CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 102L,
            title = "Morning Standup",
            startsAt = FixedMiraClock.dhaka(2026, 3, 11, 10),
            endsAt = FixedMiraClock.dhaka(2026, 3, 11, 11),
        )

        val response = succeeded(
            handler.findFreeSlots(AppCommand.FindFreeSlots(date, preferredDurationMinutes = 30)),
        )

        val report = response as MiraResponse.FreeSlotsReport
        assertThat(report.slots).isNotEmpty()
        // First slot should be 9am to 10am
        assertThat(report.slots.first().startsAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 9))
        assertThat(report.slots.first().endsAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 10))
    }

    // ------------------------------------------------------------------ editing & deleting Mira events

    @Test
    fun `updating an event not created by Mira is refused`() = runTest {
        calendar.written += CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 201L,
            title = "External Meeting",
            startsAt = tomorrowAtTen,
            endsAt = tomorrowAtEleven,
        )

        val result = handler.updateCalendarEvent(
            AppCommand.UpdateCalendarEvent(targetTitle = "External Meeting", newTitle = "Renamed"),
        )

        assertThat(failedWith(result)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `updating a Mira-created event succeeds and modifies calendar`() = runTest {
        val event = CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 202L,
            title = "Design Discussion",
            startsAt = tomorrowAtTen,
            endsAt = tomorrowAtEleven,
        )
        calendar.written += event
        links.link(
            CalendarLink(
                id = 1L,
                systemEventId = 202L,
                systemCalendarId = FakeCalendar.PERSONAL.id,
                title = "Design Discussion",
                startsAt = tomorrowAtTen,
                endsAt = tomorrowAtEleven,
                createdAt = tomorrowAtTen,
                updatedAt = tomorrowAtTen,
            )
        )

        val response = succeeded(
            handler.updateCalendarEvent(
                AppCommand.UpdateCalendarEvent(
                    targetTitle = "Design Discussion",
                    newTitle = "Architecture Discussion",
                ),
            ),
        )

        val updated = response as MiraResponse.CalendarEventUpdated
        assertThat(updated.title).isEqualTo("Architecture Discussion")
        assertThat(calendar.written.first { it.systemId == 202L }.title).isEqualTo("Architecture Discussion")
    }

    @Test
    fun `deleting an event not created by Mira is refused`() = runTest {
        calendar.written += CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 301L,
            title = "External Event",
            startsAt = tomorrowAtTen,
            endsAt = tomorrowAtEleven,
        )

        val result = handler.deleteCalendarEvent(
            AppCommand.DeleteCalendarEvent(targetTitle = "External Event"),
        )

        assertThat(failedWith(result)).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `deleting a Mira-created event removes it from calendar and unlinks`() = runTest {
        val event = CalendarEvent(
            calendarId = FakeCalendar.PERSONAL.id,
            systemId = 302L,
            title = "Quick Sync",
            startsAt = tomorrowAtTen,
            endsAt = tomorrowAtEleven,
        )
        calendar.written += event
        links.link(
            CalendarLink(
                id = 5L,
                systemEventId = 302L,
                systemCalendarId = FakeCalendar.PERSONAL.id,
                title = "Quick Sync",
                startsAt = tomorrowAtTen,
                endsAt = tomorrowAtEleven,
                createdAt = tomorrowAtTen,
                updatedAt = tomorrowAtTen,
            )
        )

        val response = succeeded(
            handler.deleteCalendarEvent(AppCommand.DeleteCalendarEvent(targetTitle = "Quick Sync")),
        )

        val deleted = response as MiraResponse.CalendarEventDeleted
        assertThat(deleted.title).isEqualTo("Quick Sync")
        assertThat(calendar.written.none { it.systemId == 302L }).isTrue()
        assertThat(links.links.none { it.id == 5L }).isTrue()
    }

    // ------------------------------------------------------------------ contact field details

    @Test
    fun `finding contact with PHONE field request returns ContactDetails`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(
            handler.findContact(AppCommand.FindContact("Ahmed", ContactField.PHONE)),
        )

        val details = response as MiraResponse.ContactDetails
        assertThat(details.contact).isEqualTo(ahmedHossain)
        assertThat(details.requestedField).isEqualTo(ContactField.PHONE)
    }

    @Test
    fun `finding contact with EMAIL field request returns ContactDetails`() = runTest {
        contacts.people = listOf(ahmedHossain)

        val response = succeeded(
            handler.findContact(AppCommand.FindContact("Ahmed", ContactField.EMAIL)),
        )

        val details = response as MiraResponse.ContactDetails
        assertThat(details.contact).isEqualTo(ahmedHossain)
        assertThat(details.requestedField).isEqualTo(ContactField.EMAIL)
    }

    // ------------------------------------------------------------------ helpers

    private fun byName(name: String): ContactTarget = ContactTarget.ByName(name)

    private fun event(): AppCommand.CreateCalendarEvent =
        AppCommand.CreateCalendarEvent("Ahmed meeting", tomorrowAtTen, tomorrowAtEleven)

    private fun succeeded(result: AppResult<MiraResponse>): MiraResponse {
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private fun failedWith(result: AppResult<MiraResponse>): MiraErrorCode {
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        return (result as AppResult.Failure).error.code
    }
}

/** Matches on the display name the way the real provider's filter URI does, loosely. */
private class FakeContacts : ContactsRepository {
    var permitted: Boolean = true
    var people: List<Contact> = emptyList()
    var failure: AppResult.Failure? = null
    var searchCount: Int = 0
    var lastQuery: String? = null

    override fun canRead(): Boolean = permitted

    override suspend fun search(query: String, limit: Int): AppResult<List<Contact>> {
        searchCount++
        lastQuery = query
        failure?.let { return it }
        return AppResult.Success(
            people.filter { it.displayName.contains(query, ignoreCase = true) }.take(limit),
        )
    }

    override suspend fun byId(systemId: Long): AppResult<Contact?> {
        failure?.let { return it }
        return AppResult.Success(people.firstOrNull { it.systemId == systemId })
    }
}

private class FakeCalendar : CalendarRepository {
    var writable: Boolean = true
    var calendars: List<WritableCalendar> = listOf(PERSONAL, WORK)

    /** Fails the calendar lookups, as a provider that is not answering would. */
    var failure: AppResult.Failure? = null

    /** Fails only the insert, which is the case where Mira has already chosen a calendar. */
    var writeFailure: AppResult.Failure? = null
    val written = mutableListOf<CalendarEvent>()

    /** No command reads the calendar yet; writing one is the whole of Mira's business with it. */
    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = writable

    override suspend fun writableCalendars(): AppResult<List<WritableCalendar>> =
        failure ?: AppResult.Success(calendars)

    override suspend fun defaultCalendarId(): AppResult<Long?> = failure
        ?: AppResult.Success(calendars.firstOrNull { it.isPrimary }?.id ?: calendars.firstOrNull()?.id)

    override suspend fun eventsBetween(from: Instant, to: Instant): AppResult<List<CalendarEvent>> =
        failure ?: AppResult.Success(
            written.filter {
                val eventEnd = it.endsAt ?: it.startsAt.plus(java.time.Duration.ofHours(1))
                it.startsAt < to && eventEnd > from
            },
        )

    override suspend fun createEvent(event: CalendarEvent): AppResult<CalendarEvent> {
        writeFailure?.let { return it }
        written += event
        val assigned = event.copy(systemId = event.systemId ?: SYSTEM_ID)
        return AppResult.Success(assigned)
    }

    override suspend fun updateEvent(event: CalendarEvent): AppResult<CalendarEvent> {
        writeFailure?.let { return it }
        val idx = written.indexOfFirst { it.systemId == event.systemId }
        if (idx != -1) written[idx] = event
        else written += event
        return AppResult.Success(event)
    }

    override suspend fun deleteEvent(systemEventId: Long): AppResult<Unit> {
        writeFailure?.let { return it }
        written.removeAll { it.systemId == systemEventId }
        return AppResult.Success(Unit)
    }

    override suspend fun searchEvents(query: String, from: Instant?, to: Instant?): AppResult<List<CalendarEvent>> {
        failure?.let { return it }
        return AppResult.Success(
            written.filter {
                it.title.contains(query, ignoreCase = true) &&
                    (from == null || (it.endsAt ?: it.startsAt) >= from) &&
                    (to == null || it.startsAt <= to)
            }
        )
    }

    companion object {
        val PERSONAL = WritableCalendar(11L, "Personal", "abu@example.com", isPrimary = true)
        val WORK = WritableCalendar(12L, "Work", "work@example.com", isPrimary = false)
        const val SYSTEM_ID = 77L
    }
}

private class FakeCalendarLinks : CalendarLinkRepository {
    val links = mutableListOf<CalendarLink>()

    override fun observeUpcoming() = emptyFlow<List<CalendarLink>>()
    override fun observeBetween(from: Instant, to: Instant) = emptyFlow<List<CalendarLink>>()
    override fun observeStale() = emptyFlow<List<CalendarLink>>()

    override suspend fun bySystemEventId(systemEventId: Long): AppResult<CalendarLink?> =
        AppResult.Success(links.firstOrNull { it.systemEventId == systemEventId })

    override suspend fun forMeeting(meetingId: Long) = AppResult.Success(emptyList<CalendarLink>())
    override suspend fun forTask(taskId: Long) = AppResult.Success(emptyList<CalendarLink>())
    override suspend fun forProject(projectId: Long) = AppResult.Success(emptyList<CalendarLink>())

    override suspend fun link(link: CalendarLink): AppResult<CalendarLink> {
        links += link
        return AppResult.Success(link)
    }

    override suspend fun update(link: CalendarLink): AppResult<CalendarLink> {
        val idx = links.indexOfFirst { it.id == link.id }
        if (idx != -1) links[idx] = link
        return AppResult.Success(link)
    }

    override suspend fun unlink(id: Long): AppResult<Unit> {
        links.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun purgeStale(): AppResult<Int> = AppResult.Success(0)
}
