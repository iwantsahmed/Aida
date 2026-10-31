package com.rayluxeo.aida.data.place

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.platform.openTestDatabase
import com.rayluxeo.aida.data.repository.SavedPlaceRepositoryImpl
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Against a real in-memory database rather than a fake DAO, because the one rule worth asserting
 * hardest here belongs to SQLite: the unique index on `labelKey` is what refuses a second place
 * under a name already taken, and a hand-written fake would only be asserting that the fake
 * remembers to refuse.
 *
 * No Hilt: the local-test transform does not run under AGP 9 (google/dagger#5261).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedPlaceRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a saved place comes back off disk as it went on`() = runTest {
        val places = repository()

        val saved = places.create(office()).value()

        assertThat(saved.id).isGreaterThan(0L)
        val read = places.byId(saved.id).value()
        assertThat(read.label).isEqualTo("The Office")
        assertThat(read.labelKey).isEqualTo("office")
        assertThat(read.latitude).isEqualTo(23.7806)
        assertThat(read.longitude).isEqualTo(90.4074)
        assertThat(read.radiusMeters).isEqualTo(200f)
        assertThat(read.createdAt).isEqualTo(clock.now())
        assertThat(read.updatedAt).isEqualTo(clock.now())
        assertThat(places.all().value()).containsExactly(read)
    }

    @Test
    fun `the key is derived from the label rather than taken from the caller`() = runTest {
        val places = repository()

        // A caller that set the label and forgot the key would otherwise store a place findable
        // under a name nobody would ever say.
        val saved = places.create(office().copy(labelKey = "nonsense")).value()

        assertThat(saved.labelKey).isEqualTo("office")
        assertThat(places.byLabelKey("nonsense").value()).isNull()
    }

    @Test
    fun `a name already taken is Abu's mistake, not the storage failing`() = runTest {
        val places = repository()
        places.create(office()).value()

        val second = places.create(office().copy(label = "office", latitude = 23.81))

        assertThat(second.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        // The first place is untouched, which is the whole reason the insert ABORTs.
        assertThat(places.byLabelKey("office").value()?.latitude).isEqualTo(23.7806)
        assertThat(places.all().value()).hasSize(1)
    }

    @Test
    fun `renaming a place onto a name already taken is refused the same way`() = runTest {
        val places = repository()
        places.create(office()).value()
        val home = places.create(home()).value()

        val clash = places.update(home.copy(label = "the office"))

        assertThat(clash.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(places.byId(home.id).value().label).isEqualTo("Home")
    }

    @Test
    fun `a place answers to the name Abu says, whatever case he saved it in`() = runTest {
        val places = repository()
        places.create(office()).value()

        // What a use case would hand over after hearing "amar office e".
        val found = places.byLabelKey(SavedPlace.keyOf("MY OFFICE")).value()

        assertThat(found).isNotNull()
        assertThat(found?.label).isEqualTo("The Office")
        assertThat(places.byLabelKey(SavedPlace.keyOf("the  Office ")).value()?.id)
            .isEqualTo(found?.id)
    }

    @Test
    fun `a name no place answers to is nothing rather than a failure`() = runTest {
        val places = repository()
        places.create(office()).value()

        assertThat(places.byLabelKey(SavedPlace.keyOf("the studio")).value()).isNull()
    }

    @Test
    fun `the list is alphabetical by label, not by the order they were saved`() = runTest {
        val places = repository()
        places.create(office()).value()
        places.create(home()).value()
        places.create(office().copy(label = "airport")).value()

        assertThat(places.observeAll().first().map { it.label })
            .containsExactly("airport", "Home", "The Office")
            .inOrder()
        assertThat(places.all().value().map { it.label })
            .containsExactly("airport", "Home", "The Office")
            .inOrder()
    }

    @Test
    fun `renaming a place moves the key it is found by`() = runTest {
        val places = repository()
        val saved = places.create(office()).value()
        clock.moveTo(LocalDateTime.of(2026, 3, 6, 9, 0))

        val renamed = places.update(saved.copy(label = "The Studio")).value()

        assertThat(renamed.labelKey).isEqualTo("studio")
        assertThat(renamed.updatedAt).isEqualTo(clock.now())
        assertThat(renamed.createdAt).isEqualTo(saved.createdAt)
        assertThat(places.byLabelKey("office").value()).isNull()
        assertThat(places.byLabelKey("studio").value()?.id).isEqualTo(saved.id)
    }

    @Test
    fun `a place that is not there reports NOT_FOUND rather than passing silently`() = runTest {
        val places = repository()

        assertThat(places.byId(404L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(places.update(office().copy(id = 404L)).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `deleting a place frees the name it was holding`() = runTest {
        val places = repository()
        val saved = places.create(office()).value()

        assertThat(places.delete(saved.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(places.all().value()).isEmpty()
        // There is no tombstone, so the name is immediately his to use again — which is half the
        // reason this table has no soft delete.
        assertThat(places.create(office()).value().labelKey).isEqualTo("office")
        assertThat(places.all().value()).hasSize(1)
    }

    @Test
    fun `deleting a place that is already gone is the state Abu asked for`() = runTest {
        assertThat(repository().delete(404L)).isInstanceOf(AppResult.Success::class.java)
    }

    private fun repository(): SavedPlaceRepository = SavedPlaceRepositoryImpl(
        dao = database.savedPlaceDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun office(): SavedPlace = SavedPlace(
        label = "The Office",
        labelKey = SavedPlace.keyOf("The Office"),
        latitude = 23.7806,
        longitude = 90.4074,
        radiusMeters = 200f,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun home(): SavedPlace = office().copy(
        label = "Home",
        labelKey = SavedPlace.keyOf("Home"),
        latitude = 23.75,
        longitude = 90.39,
    )

    /**
     * Members rather than top-level helpers, because `ReminderPlaceRepositoryImplTest` shares this
     * package and already has a pair by these names.
     */
    private fun <T> AppResult<T>.value(): T {
        assertThat(this).isInstanceOf(AppResult.Success::class.java)
        return (this as AppResult.Success).value
    }

    private fun AppResult<*>.errorCode(): MiraErrorCode {
        assertThat(this).isInstanceOf(AppResult.Failure::class.java)
        return (this as AppResult.Failure).error.code
    }
}
