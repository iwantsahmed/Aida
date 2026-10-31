package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Setting a reminder against a place, attaching a place to a reminder, and taking it off again.
 *
 * What is worth asserting is what Abu could not notice going wrong: a refusal reported as though
 * the reminder were armed, a second place quietly appearing beside the first, a platform
 * registration left holding an id whose row has already gone, and a reminder left standing with
 * nothing at all watching for it.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261).
 */
class PlaceReminderUseCasesTest {

    private val clock = FixedMiraClock()
    private val calls = mutableListOf<String>()
    private val places = FakeReminderPlaceRepository(calls)
    private val scheduler = FakePlaceWatchScheduler(calls)

    private val reminders = FakeReminderRepository()
    private val reminderScheduler = FakeReminderScheduler()

    private val watch = WatchPlaceReminderUseCase(places, scheduler, clock)
    private val stopWatching = StopWatchingPlaceUseCase(places, scheduler)
    private val createPlaceReminder = CreatePlaceReminderUseCase(
        reminders = reminders,
        createReminder = CreateReminderUseCase(reminders, reminderScheduler, clock),
        watchPlace = watch,
        clock = clock,
    )

    @Test
    fun `a place needs a reminder to hang off`() = runTest {
        assertThat(attach(reminderId = 0L).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a place Abu did not name is refused rather than saved as blank`() = runTest {
        assertThat(attach(label = "   ").errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a coordinate that is not on the globe is refused`() = runTest {
        assertThat(attach(latitude = 91.0).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(attach(longitude = -181.0).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a radius tighter than the platform can resolve is refused rather than promised`() = runTest {
        assertThat(attach(radiusMeters = 10f).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(attach(radiusMeters = 100_000f).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `the label is trimmed and the place comes back registered when the platform takes it`() = runTest {
        val watched = attach(label = "  the office  ").value()

        assertThat(watched.place.label).isEqualTo("the office")
        assertThat(watched.outcome).isEqualTo(PlaceWatchResult.Watching)
        assertThat(watched.place.isRegistered).isTrue()
    }

    @Test
    fun `a refusal is an answer, not a failure, and the place is not reported as armed`() = runTest {
        scheduler.outcome = PlaceWatchResult.Refused(PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING)

        val watched = attach().value()

        assertThat(watched.outcome)
            .isEqualTo(PlaceWatchResult.Refused(PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING))
        assertThat(watched.place.isRegistered).isFalse()
        // The place keeps: the permission may arrive tomorrow, and Abu should not have to say it again.
        assertThat(places.stored).hasSize(1)
    }

    @Test
    fun `a full register is refused out loud rather than dropping the newest place`() = runTest {
        scheduler.outcome = PlaceWatchResult.Refused(PlaceWatchRefusal.LIMIT_REACHED)

        val watched = attach().value()

        assertThat(watched.outcome).isEqualTo(PlaceWatchResult.Refused(PlaceWatchRefusal.LIMIT_REACHED))
    }

    @Test
    fun `attaching a second place moves the first rather than adding to it`() = runTest {
        val first = attach(label = "the office").value()
        val second = attach(label = "the bank").value()

        assertThat(second.place.id).isEqualTo(first.place.id)
        assertThat(places.stored).hasSize(1)
        assertThat(places.stored.single().label).isEqualTo("the bank")
    }

    @Test
    fun `a storage failure never reaches the platform`() = runTest {
        places.failNextSave = true

        assertThat(attach().errorCode()).isEqualTo(MiraErrorCode.STORAGE)
        assertThat(calls).doesNotContain("watch")
    }

    @Test
    fun `the platform registration is released before the row it is keyed on goes`() = runTest {
        val watched = attach().value()
        calls.clear()

        assertThat(stopWatching(watched.place.reminderId).value()).isTrue()
        assertThat(calls).containsExactly("stopWatching", "delete").inOrder()
        assertThat(places.stored).isEmpty()
    }

    @Test
    fun `a reminder with no place is nothing to undo, not a failure`() = runTest {
        assertThat(stopWatching(404L).value()).isFalse()
        assertThat(calls).doesNotContain("stopWatching")
    }

    // ------------------------------------------------- setting one against a place Abu has saved

    @Test
    fun `a place reminder is stored as one, and nothing is armed against its clock`() = runTest {
        val watched = createAtOffice().value()

        val reminder = reminders.byId(watched.place.reminderId).value()
        assertThat(reminder.title).isEqualTo("Buy printer ink")
        assertThat(reminder.kind).isEqualTo(ReminderKind.PLACE)
        assertThat(reminder.state).isEqualTo(ReminderState.SCHEDULED)
        // No moment of its own: the column is not nullable, so it carries the moment Abu asked.
        assertThat(reminder.triggerAt).isEqualTo(clock.now())
        // The place scheduler owns the arming, and the clock one must leave a place reminder alone.
        assertThat(reminderScheduler.armed).isEmpty()
        assertThat(watched.outcome).isEqualTo(PlaceWatchResult.Watching)
    }

    /** Copied, not referenced: deleting the saved place afterwards must not move the reminder. */
    @Test
    fun `the reminder takes its own copy of the saved place's coordinate`() = runTest {
        val watched = createAtOffice().value()

        assertThat(watched.place.label).isEqualTo(OFFICE.label)
        assertThat(watched.place.latitude).isEqualTo(OFFICE.latitude)
        assertThat(watched.place.longitude).isEqualTo(OFFICE.longitude)
        assertThat(watched.place.radiusMeters).isEqualTo(OFFICE.radiusMeters)
        assertThat(watched.place.trigger).isEqualTo(PlaceTrigger.ENTER)
    }

    @Test
    fun `a refused watch still leaves the reminder standing, reported as not armed`() = runTest {
        scheduler.outcome = PlaceWatchResult.Refused(PlaceWatchRefusal.PERMISSION_MISSING)

        val watched = createAtOffice().value()

        assertThat(watched.place.isRegistered).isFalse()
        assertThat(reminders.byId(watched.place.reminderId).value().state)
            .isEqualTo(ReminderState.SCHEDULED)
    }

    @Test
    fun `a watch that fails outright takes the reminder back rather than orphaning it`() = runTest {
        places.failNextSave = true

        assertThat(createAtOffice().errorCode()).isEqualTo(MiraErrorCode.STORAGE)
        assertThat(reminders.scheduledRows()).isEmpty()
    }

    @Test
    fun `a blank title never becomes a reminder for a place to hang off`() = runTest {
        assertThat(createAtOffice(title = "   ").errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(reminders.scheduledRows()).isEmpty()
        assertThat(places.stored).isEmpty()
    }

    private suspend fun createAtOffice(
        title: String = "Buy printer ink",
        trigger: PlaceTrigger = PlaceTrigger.ENTER,
    ): AppResult<PlaceWatch> = createPlaceReminder(title, OFFICE, trigger)

    private suspend fun attach(
        reminderId: Long = 7L,
        label: String = "the office",
        latitude: Double = 23.7806,
        longitude: Double = 90.4074,
        radiusMeters: Float = 200f,
        trigger: PlaceTrigger = PlaceTrigger.ENTER,
    ): AppResult<PlaceWatch> = watch(reminderId, label, latitude, longitude, radiusMeters, trigger)

    private companion object {

        /** A place Abu saved once, standing in it, which is the only way one is ever created. */
        val OFFICE = SavedPlace(
            id = 1L,
            label = "Office",
            labelKey = SavedPlace.keyOf("Office"),
            latitude = 23.7806,
            longitude = 90.4074,
            radiusMeters = 200f,
            createdAt = FixedMiraClock.DEFAULT_NOW,
            updatedAt = FixedMiraClock.DEFAULT_NOW,
        )
    }
}

private fun <T> AppResult<T>.value(): T {
    assertThat(this).isInstanceOf(AppResult.Success::class.java)
    return (this as AppResult.Success).value
}


internal class FakeReminderPlaceRepository(
    private val calls: MutableList<String> = mutableListOf(),
) : ReminderPlaceRepository {

    val stored = mutableListOf<ReminderPlace>()
    var failNextSave = false

    private val stream = MutableStateFlow<List<ReminderPlace>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderPlace>> = stream

    override suspend fun byReminderId(reminderId: Long): AppResult<ReminderPlace?> =
        AppResult.Success(stored.firstOrNull { it.reminderId == reminderId })

    override suspend fun save(place: ReminderPlace): AppResult<ReminderPlace> {
        calls += "save"
        if (failNextSave) {
            failNextSave = false
            return failure(MiraErrorCode.STORAGE, "disk full")
        }
        val existing = stored.indexOfFirst { it.reminderId == place.reminderId }
        val saved = if (existing >= 0) {
            place.copy(id = stored[existing].id, isRegistered = stored[existing].isRegistered)
        } else {
            place.copy(id = nextId++)
        }
        if (existing >= 0) stored[existing] = saved else stored += saved
        stream.value = stored.toList()
        return AppResult.Success(saved)
    }

    override suspend fun setRegistered(id: Long, isRegistered: Boolean): AppResult<Unit> {
        calls += "setRegistered"
        val index = stored.indexOfFirst { it.id == id }
        if (index < 0) return failure(MiraErrorCode.NOT_FOUND, "no place $id")
        stored[index] = stored[index].copy(isRegistered = isRegistered)
        stream.value = stored.toList()
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        calls += "delete"
        if (!stored.removeAll { it.id == id }) return failure(MiraErrorCode.NOT_FOUND, "no place $id")
        stream.value = stored.toList()
        return AppResult.Success(Unit)
    }

    override suspend fun pending(): AppResult<List<ReminderPlace>> = AppResult.Success(stored.toList())

    override suspend fun countRegisteredOtherThan(placeId: Long): AppResult<Int> =
        AppResult.Success(stored.count { it.isRegistered && it.id != placeId })
}

/**
 * Stands in for the platform. It does not write `isRegistered` the way the real scheduler does,
 * which is the point: the use case must take what it reports back to Abu from the outcome rather
 * than from a row it hopes somebody else updated.
 */
internal class FakePlaceWatchScheduler(
    private val calls: MutableList<String> = mutableListOf(),
) : PlaceReminderScheduler {

    var outcome: PlaceWatchResult = PlaceWatchResult.Watching

    override val capacity: Int = 20

    override suspend fun watch(place: ReminderPlace): AppResult<PlaceWatchResult> {
        calls += "watch"
        return AppResult.Success(outcome)
    }

    override suspend fun stopWatching(placeId: Long): AppResult<Unit> {
        calls += "stopWatching"
        return AppResult.Success(Unit)
    }

    override suspend fun rewatchAll(): AppResult<Unit> {
        calls += "rewatchAll"
        return AppResult.Success(Unit)
    }
}
