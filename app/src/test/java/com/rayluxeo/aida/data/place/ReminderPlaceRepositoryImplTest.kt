package com.rayluxeo.aida.data.place

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.dao.ReminderPlaceDao
import com.rayluxeo.aida.data.db.entity.ReminderPlaceEntity
import com.rayluxeo.aida.data.repository.ReminderPlaceRepositoryImpl
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.ReminderState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The place store, against a hand-written DAO rather than a real database: `MiraDatabase` does not
 * hand out a [ReminderPlaceDao] yet, and the rules worth asserting here are the repository's own.
 * The one piece of SQL logic that is not — [ReminderPlaceDao.upsertForReminder] — is a concrete
 * method on the DAO and runs for real below.
 *
 * No Hilt: the local-test transform does not run under AGP 9 (google/dagger#5261).
 */
class ReminderPlaceRepositoryImplTest {

    private val clock = FixedMiraClock()
    private val dao = FakeReminderPlaceDao()
    private val places = ReminderPlaceRepositoryImpl(
        dao = dao,
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    @Test
    fun `a saved place starts unregistered, because nothing has offered it to the platform yet`() = runTest {
        val saved = places.save(office(reminderId = 7L)).value()

        assertThat(saved.id).isGreaterThan(0L)
        assertThat(saved.isRegistered).isFalse()
        assertThat(saved.label).isEqualTo("the office")
        assertThat(saved.trigger).isEqualTo(PlaceTrigger.ENTER)
    }

    @Test
    fun `moving a place keeps the row the platform registration is keyed on`() = runTest {
        val first = places.save(office(reminderId = 7L)).value()
        dao.setRegistered(first.id, isRegistered = true, updatedAt = 1L)

        val moved = places.save(
            office(reminderId = 7L).copy(label = "the new office", latitude = 23.81, longitude = 90.41),
        ).value()

        assertThat(moved.id).isEqualTo(first.id)
        assertThat(moved.label).isEqualTo("the new office")
        assertThat(moved.latitude).isEqualTo(23.81)
        assertThat(moved.createdAt).isEqualTo(first.createdAt)
        // The flag is the scheduler's, so an edit must not quietly clear it.
        assertThat(moved.isRegistered).isTrue()
        assertThat(places.observeAll().first()).hasSize(1)
    }

    @Test
    fun `a reminder holds at most one place`() = runTest {
        places.save(office(reminderId = 7L))
        places.save(office(reminderId = 7L).copy(label = "elsewhere"))
        places.save(office(reminderId = 8L))

        assertThat(places.observeAll().first()).hasSize(2)
        assertThat(places.byReminderId(7L).value()?.label).isEqualTo("elsewhere")
    }

    @Test
    fun `a reminder with no place answers null rather than failing`() = runTest {
        assertThat(places.byReminderId(99L).value()).isNull()
    }

    @Test
    fun `marking a place that is not there reports NOT_FOUND instead of passing silently`() = runTest {
        assertThat(places.setRegistered(404L, isRegistered = true).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(places.delete(404L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `only places whose reminder is still scheduled are worth re-registering`() = runTest {
        dao.reminderStates[7L] = ReminderState.SCHEDULED
        dao.reminderStates[8L] = ReminderState.FIRED
        dao.reminderStates[9L] = ReminderState.CANCELLED
        places.save(office(reminderId = 7L))
        places.save(office(reminderId = 8L))
        places.save(office(reminderId = 9L))

        val pending = places.pending().value()

        assertThat(pending.map { it.reminderId }).containsExactly(7L)
    }

    @Test
    fun `the place being armed is left out of its own count`() = runTest {
        val first = places.save(office(reminderId = 7L)).value()
        val second = places.save(office(reminderId = 8L)).value()
        places.setRegistered(first.id, isRegistered = true)
        places.setRegistered(second.id, isRegistered = true)

        assertThat(places.countRegisteredOtherThan(first.id).value()).isEqualTo(1)
        assertThat(places.countRegisteredOtherThan(0L).value()).isEqualTo(2)
    }

    @Test
    fun `deleting a place takes it out of the list`() = runTest {
        val saved = places.save(office(reminderId = 7L)).value()

        assertThat(places.delete(saved.id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(places.observeAll().first()).isEmpty()
    }

    private fun office(reminderId: Long): ReminderPlace = ReminderPlace(
        reminderId = reminderId,
        label = "the office",
        latitude = 23.7806,
        longitude = 90.4074,
        radiusMeters = 200f,
        trigger = PlaceTrigger.ENTER,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )
}

private fun <T> AppResult<T>.value(): T {
    assertThat(this).isInstanceOf(AppResult.Success::class.java)
    return (this as AppResult.Success).value
}

private fun AppResult<*>.errorCode(): MiraErrorCode {
    assertThat(this).isInstanceOf(AppResult.Failure::class.java)
    return (this as AppResult.Failure).error.code
}

/**
 * Stands in for Room. [ReminderPlaceDao.upsertForReminder] is deliberately not overridden, so the
 * read-then-write that keeps a moved place on its original row is the production one.
 */
private class FakeReminderPlaceDao : ReminderPlaceDao() {

    /** The reminder each place hangs off, which is the join `listForReminderState` does in SQL. */
    val reminderStates: MutableMap<Long, ReminderState> = mutableMapOf()

    private val rows = linkedMapOf<Long, ReminderPlaceEntity>()
    private val stream = MutableStateFlow<List<ReminderPlaceEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderPlaceEntity>> = stream

    override suspend fun findById(id: Long): ReminderPlaceEntity? = rows[id]

    override suspend fun findByReminderId(reminderId: Long): ReminderPlaceEntity? =
        rows.values.firstOrNull { it.reminderId == reminderId }

    override suspend fun listForReminderState(state: ReminderState): List<ReminderPlaceEntity> =
        rows.values.filter { reminderStates[it.reminderId] == state }.sortedBy { it.id }

    override suspend fun countRegisteredExcluding(excludingId: Long): Int =
        rows.values.count { it.isRegistered && it.id != excludingId }

    override suspend fun insert(place: ReminderPlaceEntity): Long {
        val id = nextId++
        put(place.copy(id = id))
        return id
    }

    override suspend fun update(place: ReminderPlaceEntity): Int {
        if (!rows.containsKey(place.id)) return 0
        put(place)
        return 1
    }

    override suspend fun setRegistered(id: Long, isRegistered: Boolean, updatedAt: Long): Int {
        val row = rows[id] ?: return 0
        put(row.copy(isRegistered = isRegistered, updatedAtEpochMillis = updatedAt))
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        if (rows.remove(id) == null) return 0
        stream.value = rows.values.toList()
        return 1
    }

    private fun put(row: ReminderPlaceEntity) {
        rows[row.id] = row
        stream.value = rows.values.toList()
    }
}
