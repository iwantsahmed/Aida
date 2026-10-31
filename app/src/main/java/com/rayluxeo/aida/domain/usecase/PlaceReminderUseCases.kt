package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import javax.inject.Inject

/**
 * A place Mira saved, and what the operating system said when she offered to watch it.
 *
 * Both halves travel together because saving succeeded and watching was refused is the ordinary
 * case, not an error: the place is Abu's and it keeps, while the watch depends on a permission and
 * a switch that may change tomorrow. A caller that showed only the first half would be telling him
 * a reminder is armed when nothing is listening.
 */
data class PlaceWatch(
    val place: ReminderPlace,
    val outcome: PlaceWatchResult,
)

/**
 * Attaches a place to a reminder that already exists.
 *
 * Deliberately not a "create a place reminder" use case. The reminder is Abu's instruction and
 * belongs to the reminder use cases whatever triggers it; this adds the trigger. Keeping the two
 * apart is what stops a place reminder becoming a second kind of reminder with its own rules.
 *
 * A reminder may hold one place, so calling this again moves the place rather than adding a
 * second — the row keeps its id, and the platform registration keyed on that id is replaced.
 */
class WatchPlaceReminderUseCase @Inject constructor(
    private val places: ReminderPlaceRepository,
    private val scheduler: PlaceReminderScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        reminderId: Long,
        label: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        trigger: PlaceTrigger = PlaceTrigger.ENTER,
    ): AppResult<PlaceWatch> {
        if (reminderId <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot attach a place to an unsaved reminder")
        }
        val clean = label.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Place label was blank")
        if (!PlaceBounds.isOnGlobe(latitude, longitude)) {
            return failure(MiraErrorCode.VALIDATION, "Coordinate $latitude, $longitude is not on the globe")
        }
        if (!PlaceBounds.holdsRadius(radiusMeters)) {
            return failure(MiraErrorCode.VALIDATION, "Radius ${radiusMeters}m is outside what a place alert can hold")
        }

        val now = clock.now()
        val place = ReminderPlace(
            reminderId = reminderId,
            label = clean,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            trigger = trigger,
            createdAt = now,
            updatedAt = now,
        )
        return places.save(place).flatMap { saved ->
            scheduler.watch(saved).map { outcome ->
                // Taken from the answer rather than re-read: the scheduler has just written the
                // flag, and asking the database again would only risk reporting the older value.
                PlaceWatch(
                    place = saved.copy(isRegistered = outcome is PlaceWatchResult.Watching),
                    outcome = outcome,
                )
            }
        }
    }
}

/**
 * Sets a reminder that waits at a place Abu has already named.
 *
 * It composes rather than reimplements: [CreateReminderUseCase] writes the reminder, exactly as
 * it does for every other kind, and [WatchPlaceReminderUseCase] attaches the coordinate and asks
 * the platform to watch it. Nothing about a place reminder is a second set of rules for what a
 * reminder is — which is the whole reason those two are separate use cases.
 *
 * [SavedPlace] is taken by value rather than by name. Resolving what Abu called a place is the
 * handler's job and it may end in Mira saying which places she knows; by the time it reaches here
 * the place is settled, and its coordinate is COPIED onto the reminder so that deleting or moving
 * the saved place afterwards cannot move a reminder he already set.
 *
 * The reminder's `triggerAt` is [MiraClock.now]. A place reminder has no moment of its own, the
 * column is not nullable, and nothing arms against it: [CreateReminderUseCase] exempts
 * [ReminderKind.PLACE] from the "must be in the future" rule and the time scheduler skips it on
 * [Reminder.waitsForPlace]. Now is simply the honest stamp for "from when Abu asked".
 */
class CreatePlaceReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val createReminder: CreateReminderUseCase,
    private val watchPlace: WatchPlaceReminderUseCase,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        title: String,
        place: SavedPlace,
        trigger: PlaceTrigger,
        origin: RecordOrigin = RecordOrigin.MANUAL,
    ): AppResult<PlaceWatch> =
        createReminder(
            title = title,
            triggerAt = clock.now(),
            kind = ReminderKind.PLACE,
            origin = origin,
        ).flatMap { reminder ->
            val watched = watchPlace(
                reminderId = reminder.id,
                label = place.label,
                latitude = place.latitude,
                longitude = place.longitude,
                radiusMeters = place.radiusMeters,
                trigger = trigger,
            )
            // The same withdrawal `CreateReminderUseCase.armOrWithdraw` makes when arming fails,
            // and for the same reason: a reminder stored with nothing watching for it would sit in
            // Abu's list promising an arrival that nothing is listening for. A REFUSED watch is
            // not this case — that is a successful outcome carrying `isRegistered = false`, and
            // the reminder keeps, because the permission may arrive tomorrow.
            if (watched is AppResult.Failure) reminders.cancel(reminder.id)
            watched
        }
}

/**
 * Takes the place off a reminder, and the alert off the platform.
 *
 * The order matters. The registration is keyed on the place's id, so releasing it before the row
 * goes is the only way to be sure nothing is left armed against a row that no longer exists.
 * Cancelling or completing a reminder should come through here first for the same reason.
 */
class StopWatchingPlaceUseCase @Inject constructor(
    private val places: ReminderPlaceRepository,
    private val scheduler: PlaceReminderScheduler,
) {

    /** False when the reminder had no place, which is ordinary rather than a failure. */
    suspend operator fun invoke(reminderId: Long): AppResult<Boolean> =
        places.byReminderId(reminderId).flatMap { place ->
            if (place == null) {
                AppResult.Success(false)
            } else {
                scheduler.stopWatching(place.id)
                    .flatMap { places.delete(place.id) }
                    .map { true }
            }
        }
}
