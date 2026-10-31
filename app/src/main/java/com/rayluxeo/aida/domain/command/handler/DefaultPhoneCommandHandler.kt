package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ContactTarget
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.CalendarLink
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.ContactField
import com.rayluxeo.aida.domain.model.TimeWindow
import com.rayluxeo.aida.domain.repository.CalendarLinkRepository
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ContactsRepository
import java.time.Duration
import javax.inject.Inject

/**
 * The two parts of the phone Mira reaches into: its calendar and its address book.
 *
 * **Mira prepares, she never places.** A call and a message are resolved to a contact and handed
 * to the screen as a control Abu presses. Nothing here dials and nothing here sends, because a
 * misheard name acting on its own is a phone call to a stranger.
 *
 * Two outcomes that look like failures are not treated as any:
 * a missing Android permission is [MiraResponse.PermissionNeeded] as a SUCCESS — Mira understood
 * perfectly and the screen should offer the grant rather than apologise — and several people
 * matching one name is [MiraResponse.AmbiguousContact], which is the normal shape of an address
 * book rather than a fault.
 */
class DefaultPhoneCommandHandler @Inject constructor(
    private val contacts: ContactsRepository,
    private val calendar: CalendarRepository,
    private val calendarLinks: CalendarLinkRepository,
    private val clock: MiraClock,
) : PhoneCommandHandler {

    override suspend fun createCalendarEvent(
        command: AppCommand.CreateCalendarEvent,
    ): AppResult<MiraResponse> {
        if (!calendar.canWrite()) return permissionNeeded(MiraCapability.CALENDAR)
        val target = when (val found = calendar.defaultCalendarId()) {
            is AppResult.Failure -> return found.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> found.value
        } ?: return failure(
            MiraErrorCode.VALIDATION,
            "No calendar on this device accepts new events",
        )
        val conflictEnd = command.endsAt ?: command.startsAt.plus(DEFAULT_DURATION)

        // Conflict warning: check if any event overlaps with this time window
        val conflicts = when (val existing = calendar.eventsBetween(command.startsAt, conflictEnd)) {
            is AppResult.Success -> existing.value.map { it.title }
            is AppResult.Failure -> emptyList()
        }

        val written = calendar.createEvent(
            CalendarEvent(
                systemId = null,
                calendarId = target,
                title = command.title,
                startsAt = command.startsAt,
                endsAt = command.endsAt,
                reminderMinutes = command.reminderMinutes,
            ),
        )
        return when (written) {
            is AppResult.Failure -> written.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> {
                val created = written.value
                val sysId = created.systemId
                if (sysId != null) {
                    calendarLinks.link(
                        CalendarLink(
                            systemEventId = sysId,
                            systemCalendarId = target,
                            title = created.title,
                            startsAt = created.startsAt,
                            endsAt = created.endsAt,
                            createdAt = clock.now(),
                            updatedAt = clock.now(),
                        ),
                    )
                }
                AppResult.Success(
                    MiraResponse.CalendarEventCreated(
                        title = created.title,
                        startsAt = created.startsAt,
                        conflicts = conflicts,
                    ),
                )
            }
        }
    }

    override suspend fun querySchedule(
        command: AppCommand.QuerySchedule,
    ): AppResult<MiraResponse> {
        if (!calendar.canRead()) return permissionNeeded(MiraCapability.CALENDAR)
        val eventsResult = calendar.eventsBetween(command.from, command.to)
        return when (eventsResult) {
            is AppResult.Failure -> eventsResult.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> {
                val filtered = if (!command.query.isNullOrBlank()) {
                    eventsResult.value.filter { it.title.contains(command.query, ignoreCase = true) }
                } else {
                    eventsResult.value
                }
                AppResult.Success(
                    MiraResponse.ScheduleReport(
                        from = command.from,
                        to = command.to,
                        events = filtered,
                    ),
                )
            }
        }
    }

    override suspend fun findFreeSlots(
        command: AppCommand.FindFreeSlots,
    ): AppResult<MiraResponse> {
        if (!calendar.canRead()) return permissionNeeded(MiraCapability.CALENDAR)
        val zone = clock.zone()
        val dayStart = command.date.atTime(9, 0).atZone(zone).toInstant()
        val dayEnd = command.date.atTime(18, 0).atZone(zone).toInstant()
        val eventsResult = calendar.eventsBetween(dayStart, dayEnd)
        return when (eventsResult) {
            is AppResult.Failure -> eventsResult.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> {
                val events = eventsResult.value.sortedBy { it.startsAt }
                val freeSlots = mutableListOf<TimeWindow>()
                var current = dayStart
                val minDuration = Duration.ofMinutes(command.preferredDurationMinutes.toLong())

                for (event in events) {
                    val evStart = maxOf(event.startsAt, dayStart)
                    val evEnd = minOf(event.endsAt ?: event.startsAt.plus(DEFAULT_DURATION), dayEnd)
                    if (evStart.isAfter(current)) {
                        val gap = Duration.between(current, evStart)
                        if (gap >= minDuration) {
                            freeSlots += TimeWindow(current, evStart)
                        }
                    }
                    if (evEnd.isAfter(current)) {
                        current = evEnd
                    }
                }
                if (dayEnd.isAfter(current)) {
                    val gap = Duration.between(current, dayEnd)
                    if (gap >= minDuration) {
                        freeSlots += TimeWindow(current, dayEnd)
                    }
                }

                AppResult.Success(
                    MiraResponse.FreeSlotsReport(
                        date = command.date,
                        slots = freeSlots,
                    ),
                )
            }
        }
    }

    override suspend fun updateCalendarEvent(
        command: AppCommand.UpdateCalendarEvent,
    ): AppResult<MiraResponse> {
        if (!calendar.canWrite()) return permissionNeeded(MiraCapability.CALENDAR)
        val searchStart = clock.now().minus(Duration.ofDays(1))
        val searchEnd = clock.now().plus(Duration.ofDays(90))
        val existing = when (val found = calendar.eventsBetween(searchStart, searchEnd)) {
            is AppResult.Failure -> return found.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> found.value.filter { it.title.contains(command.targetTitle, ignoreCase = true) }
        }
        if (existing.isEmpty()) {
            return failure(MiraErrorCode.NOT_FOUND, "No calendar event matches \"${command.targetTitle}\"")
        }
        val target = existing.first()
        val sysId = target.systemId ?: return failure(MiraErrorCode.NOT_FOUND, "Event has no system id")

        // Only Mira-created events may be modified
        val link = when (val linkResult = calendarLinks.bySystemEventId(sysId)) {
            is AppResult.Success -> linkResult.value
            is AppResult.Failure -> null
        }
        if (link == null) {
            return failure(MiraErrorCode.VALIDATION, "Only Aida-created events can be modified")
        }

        val updatedEvent = target.copy(
            title = command.newTitle ?: target.title,
            startsAt = command.newStartsAt ?: target.startsAt,
            endsAt = command.newEndsAt ?: target.endsAt,
        )
        val updateResult = calendar.updateEvent(updatedEvent)
        return when (updateResult) {
            is AppResult.Failure -> updateResult.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> {
                calendarLinks.update(
                    link.copy(
                        title = updatedEvent.title,
                        startsAt = updatedEvent.startsAt,
                        endsAt = updatedEvent.endsAt,
                        updatedAt = clock.now(),
                    ),
                )
                AppResult.Success(
                    MiraResponse.CalendarEventUpdated(
                        title = updatedEvent.title,
                        startsAt = updatedEvent.startsAt,
                    ),
                )
            }
        }
    }

    override suspend fun deleteCalendarEvent(
        command: AppCommand.DeleteCalendarEvent,
    ): AppResult<MiraResponse> {
        if (!calendar.canWrite()) return permissionNeeded(MiraCapability.CALENDAR)
        val searchStart = clock.now().minus(Duration.ofDays(1))
        val searchEnd = clock.now().plus(Duration.ofDays(90))
        val existing = when (val found = calendar.eventsBetween(searchStart, searchEnd)) {
            is AppResult.Failure -> return found.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> found.value.filter { it.title.contains(command.targetTitle, ignoreCase = true) }
        }
        if (existing.isEmpty()) {
            return failure(MiraErrorCode.NOT_FOUND, "No calendar event matches \"${command.targetTitle}\"")
        }
        val target = existing.first()
        val sysId = target.systemId ?: return failure(MiraErrorCode.NOT_FOUND, "Event has no system id")

        // Only Mira-created events may be deleted
        val link = when (val linkResult = calendarLinks.bySystemEventId(sysId)) {
            is AppResult.Success -> linkResult.value
            is AppResult.Failure -> null
        }
        if (link == null) {
            return failure(MiraErrorCode.VALIDATION, "Only Aida-created events can be deleted")
        }

        val deleteResult = calendar.deleteEvent(sysId)
        return when (deleteResult) {
            is AppResult.Failure -> deleteResult.orPermissionNeeded(MiraCapability.CALENDAR)
            is AppResult.Success -> {
                calendarLinks.unlink(link.id)
                AppResult.Success(MiraResponse.CalendarEventDeleted(target.title))
            }
        }
    }

    override suspend fun findContact(command: AppCommand.FindContact): AppResult<MiraResponse> {
        if (!contacts.canRead()) return permissionNeeded(MiraCapability.CONTACTS)
        return when (val found = contacts.search(command.query)) {
            is AppResult.Failure -> found.orPermissionNeeded(MiraCapability.CONTACTS)
            is AppResult.Success -> {
                val list = found.value
                when {
                    list.isEmpty() -> AppResult.Success(MiraResponse.ContactsFound(0, emptyList()))
                    list.size == 1 && command.requestedField != ContactField.ANY -> {
                        AppResult.Success(MiraResponse.ContactDetails(list.first(), command.requestedField))
                    }
                    else -> AppResult.Success(MiraResponse.ContactsFound(list.size, list))
                }
            }
        }
    }

    override suspend fun prepareCall(command: AppCommand.PrepareCall): AppResult<MiraResponse> =
        when (val resolved = resolve(command.contact)) {
            is Resolution.One -> AppResult.Success(MiraResponse.CallPrepared(resolved.contact))
            is Resolution.Several ->
                AppResult.Success(MiraResponse.AmbiguousContact(resolved.query, resolved.candidates))

            is Resolution.None -> resolved.outcome
        }

    override suspend fun prepareMessage(
        command: AppCommand.PrepareMessage,
    ): AppResult<MiraResponse> = when (val resolved = resolve(command.contact)) {
        is Resolution.One ->
            AppResult.Success(MiraResponse.MessagePrepared(resolved.contact, command.body, command.app))

        is Resolution.Several ->
            AppResult.Success(MiraResponse.AmbiguousContact(resolved.query, resolved.candidates))

        is Resolution.None -> resolved.outcome
    }

    /**
     * Who the command points at, once the address book has had its say.
     *
     * A call and a message resolve identically because both travel over the same number — Mira has
     * no channel to someone she has only an email address for.
     */
    private sealed interface Resolution {
        data class One(val contact: Contact) : Resolution
        data class Several(val query: String, val candidates: List<Contact>) : Resolution

        /** Nothing to hand over: a permission to ask for, or no reachable number. */
        data class None(val outcome: AppResult<MiraResponse>) : Resolution
    }

    private suspend fun resolve(target: ContactTarget): Resolution = when (target) {
        is ContactTarget.ByNumber -> Resolution.One(dictated(target.number))
        is ContactTarget.ByName -> resolveByName(target.name)
    }

    private suspend fun resolveByName(name: String): Resolution {
        if (!contacts.canRead()) return Resolution.None(permissionNeeded(MiraCapability.CONTACTS))
        val found = when (val result = contacts.search(name)) {
            is AppResult.Failure ->
                return Resolution.None(result.orPermissionNeeded(MiraCapability.CONTACTS))

            is AppResult.Success -> result.value
        }
        // Ambiguity is only ambiguity among the people who can actually be reached: three Ahmeds
        // with one number between them is not a question worth asking Abu.
        val reachable = found.filter { it.isCallable }
        return when {
            reachable.size == 1 -> Resolution.One(reachable.first())
            reachable.size > 1 -> Resolution.Several(name, reachable)
            found.isEmpty() -> Resolution.None(
                failure(MiraErrorCode.NOT_FOUND, "No contact matches \"$name\""),
            )
            // The person is there; the number is what is missing. Offering a control that cannot
            // dial would be worse than saying so.
            else -> Resolution.None(
                failure(MiraErrorCode.NOT_FOUND, "Contact matching \"$name\" has no phone number"),
            )
        }
    }

    /**
     * A number Abu read out is not a row in the address book, so it carries no system id and needs
     * no permission to reach: there is nothing to look up.
     */
    private fun dictated(number: String): Contact {
        val tidied = number.trim()
        return Contact(
            systemId = NOT_IN_ADDRESS_BOOK,
            displayName = tidied,
            phoneNumber = tidied,
            email = null,
        )
    }

    private fun permissionNeeded(capability: MiraCapability): AppResult<MiraResponse> =
        AppResult.Success(MiraResponse.PermissionNeeded(capability))

    /**
     * Android can take a grant back while Mira is running, so a lookup that passed the permission
     * check can still come back refused. That is the same situation as never having had the grant,
     * and Abu should be offered it rather than told the address book broke.
     */
    private fun AppResult.Failure.orPermissionNeeded(
        capability: MiraCapability,
    ): AppResult<MiraResponse> =
        if (error.code == MiraErrorCode.PERMISSION_REQUIRED) permissionNeeded(capability) else this

    private companion object {
        /** ContactsContract never assigns 0, so it cannot collide with a real contact. */
        const val NOT_IN_ADDRESS_BOOK = 0L
        val DEFAULT_DURATION: Duration = Duration.ofHours(1)
    }
}
