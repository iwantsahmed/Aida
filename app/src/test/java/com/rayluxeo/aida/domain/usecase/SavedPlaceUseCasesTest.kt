package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.location.CurrentPlaceFinder
import com.rayluxeo.aida.domain.location.PlaceFix
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Abu's place directory.
 *
 * What is worth asserting is what he could not notice going wrong: a key that stops agreeing with
 * the label it was derived from, so the name he says no longer finds the place he saved; a second
 * place quietly taking a name already in use; and "there was no fix" arriving as a fault rather
 * than as the switch that answers it.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261).
 */
class SavedPlaceUseCasesTest {

    private val clock = FixedMiraClock()
    private val places = FakeSavedPlaceRepository()
    private val finder = FakeCurrentPlaceFinder()

    private val save = SavePlaceUseCase(places, clock)
    private val saveHere = SavePlaceHereUseCase(finder, save)
    private val rename = RenamePlaceUseCase(places, clock)
    private val delete = DeletePlaceUseCase(places)

    @Test
    fun `a place keeps Abu's spelling and carries a key derived from it`() = runTest {
        val saved = save("  The Office  ", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()

        assertThat(saved.label).isEqualTo("The Office")
        assertThat(saved.labelKey).isEqualTo(SavedPlace.keyOf("The Office"))
        assertThat(saved.radiusMeters).isEqualTo(SavePlaceUseCase.DEFAULT_RADIUS_METERS)
        assertThat(saved.createdAt).isEqualTo(clock.now())
    }

    @Test
    fun `a place Abu did not name is refused rather than saved as blank`() = runTest {
        assertThat(save("   ", DHAKA_LATITUDE, DHAKA_LONGITUDE).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(places.stored).isEmpty()
    }

    /** "Office" and "the office" are the same name to [SavedPlace.keyOf], and must clash as one. */
    @Test
    fun `a second place cannot take a name that already answers to one`() = runTest {
        save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()

        val again = save("the office", 23.7000, 90.4000)

        assertThat(again.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(places.stored).hasSize(1)
    }

    @Test
    fun `a coordinate that is not on the globe is refused`() = runTest {
        assertThat(save("Office", 91.0, DHAKA_LONGITUDE).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(save("Office", DHAKA_LATITUDE, -181.0).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(places.stored).isEmpty()
    }

    /** The same bounds a reminder's own radius is held to, because they are one set of numbers. */
    @Test
    fun `a radius outside what a place alert can hold is refused`() = runTest {
        assertThat(save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE, radiusMeters = 10f).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE, radiusMeters = 100_000f).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `saving here stores the fix that came back`() = runTest {
        finder.fix = PlaceFix.Found(DHAKA_LATITUDE, DHAKA_LONGITUDE, accuracyMeters = 12f)

        val outcome = saveHere("Office").value()

        assertThat(outcome).isInstanceOf(PlaceSaveOutcome.Saved::class.java)
        val saved = (outcome as PlaceSaveOutcome.Saved).place
        assertThat(saved.latitude).isEqualTo(DHAKA_LATITUDE)
        assertThat(saved.longitude).isEqualTo(DHAKA_LONGITUDE)
        assertThat(places.stored).hasSize(1)
    }

    @Test
    fun `no fix is an answer Abu can act on, not a failure`() = runTest {
        finder.fix = PlaceFix.Unavailable(PlaceWatchRefusal.LOCATION_DISABLED)

        val outcome = saveHere("Office").value()

        assertThat(outcome).isEqualTo(PlaceSaveOutcome.NoFix(PlaceWatchRefusal.LOCATION_DISABLED))
        assertThat(places.stored).isEmpty()
    }

    @Test
    fun `a name that cannot be stored is never a reason to take a location fix`() = runTest {
        assertThat(saveHere("   ").errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(finder.fixesTaken).isEqualTo(0)
    }

    @Test
    fun `renaming re-derives the key, so the new name is the one that answers`() = runTest {
        val saved = save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()

        val renamed = rename(saved.id, "  Studio  ").value()

        assertThat(renamed.label).isEqualTo("Studio")
        assertThat(renamed.labelKey).isEqualTo(SavedPlace.keyOf("Studio"))
        assertThat(places.byLabelKey(SavedPlace.keyOf("the studio")).value()?.id).isEqualTo(saved.id)
        assertThat(places.byLabelKey(SavedPlace.keyOf("office")).value()).isNull()
    }

    @Test
    fun `renaming onto a name another place already holds is refused`() = runTest {
        save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()
        val bank = save("Bank", 23.7500, 90.3900).value()

        assertThat(rename(bank.id, "the office").errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(places.byId(bank.id).value().label).isEqualTo("Bank")
    }

    /** Re-saving a place under the name it already has is Abu correcting its capitals, not a clash. */
    @Test
    fun `renaming a place to a spelling of its own name is allowed`() = runTest {
        val saved = save("office", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()

        val renamed = rename(saved.id, "The Office").value()

        assertThat(renamed.label).isEqualTo("The Office")
        assertThat(places.stored).hasSize(1)
    }

    @Test
    fun `a blank rename is refused rather than clearing the name`() = runTest {
        val saved = save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()

        assertThat(rename(saved.id, "  ").errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(places.byId(saved.id).value().label).isEqualTo("Office")
    }

    @Test
    fun `deleting a place forgets it`() = runTest {
        val saved = save("Office", DHAKA_LATITUDE, DHAKA_LONGITUDE).value()

        assertThat(delete(saved.id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(places.stored).isEmpty()
    }

    @Test
    fun `deleting a place that was never saved is a failure rather than a silent no-op`() = runTest {
        assertThat(delete(404L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private companion object {
        const val DHAKA_LATITUDE = 23.7806
        const val DHAKA_LONGITUDE = 90.4074
    }
}

private fun <T> AppResult<T>.value(): T {
    assertThat(this).isInstanceOf(AppResult.Success::class.java)
    return (this as AppResult.Success).value
}

/**
 * Behaves like the real directory in the one way the use cases lean on it: the key is unique, and
 * a write that would take a name twice is refused rather than silently keeping the last one.
 */
internal class FakeSavedPlaceRepository : SavedPlaceRepository {

    val stored = mutableListOf<SavedPlace>()

    private val stream = MutableStateFlow<List<SavedPlace>>(emptyList())
    private var nextId = 1L

    fun seed(label: String, latitude: Double = 23.7806, longitude: Double = 90.4074): SavedPlace {
        val saved = SavedPlace(
            id = nextId++,
            label = label,
            labelKey = SavedPlace.keyOf(label),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = SavePlaceUseCase.DEFAULT_RADIUS_METERS,
            createdAt = FixedMiraClock.DEFAULT_NOW,
            updatedAt = FixedMiraClock.DEFAULT_NOW,
        )
        stored += saved
        stream.value = stored.toList()
        return saved
    }

    override fun observeAll(): Flow<List<SavedPlace>> = stream

    override suspend fun all(): AppResult<List<SavedPlace>> =
        AppResult.Success(stored.sortedBy { it.label })

    override suspend fun byLabelKey(labelKey: String): AppResult<SavedPlace?> =
        AppResult.Success(stored.firstOrNull { it.labelKey == labelKey })

    override suspend fun byId(id: Long): AppResult<SavedPlace> =
        stored.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No place with id $id")

    override suspend fun create(place: SavedPlace): AppResult<SavedPlace> {
        if (stored.any { it.labelKey == place.labelKey }) {
            return failure(MiraErrorCode.VALIDATION, "A place already answers to ${place.labelKey}")
        }
        val saved = place.copy(id = nextId++)
        stored += saved
        stream.value = stored.toList()
        return AppResult.Success(saved)
    }

    override suspend fun update(place: SavedPlace): AppResult<SavedPlace> {
        val at = stored.indexOfFirst { it.id == place.id }
        if (at < 0) return failure(MiraErrorCode.NOT_FOUND, "No place with id ${place.id}")
        if (stored.any { it.labelKey == place.labelKey && it.id != place.id }) {
            return failure(MiraErrorCode.VALIDATION, "A place already answers to ${place.labelKey}")
        }
        stored[at] = place
        stream.value = stored.toList()
        return AppResult.Success(place)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        if (!stored.removeAll { it.id == id }) return failure(MiraErrorCode.NOT_FOUND, "No place with id $id")
        stream.value = stored.toList()
        return AppResult.Success(Unit)
    }
}

/** Stands in for the platform's one-shot fix, and counts whether it was asked at all. */
internal class FakeCurrentPlaceFinder : CurrentPlaceFinder {

    var fix: PlaceFix = PlaceFix.Found(23.7806, 90.4074, accuracyMeters = 20f)
    var fixesTaken = 0

    override suspend fun here(): PlaceFix {
        fixesTaken++
        return fix
    }
}
