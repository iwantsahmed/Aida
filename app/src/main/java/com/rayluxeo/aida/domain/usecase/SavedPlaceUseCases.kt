package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.location.CurrentPlaceFinder
import com.rayluxeo.aida.domain.location.PlaceFix
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import javax.inject.Inject

/**
 * What came of asking Mira to remember where Abu is standing.
 *
 * Both halves travel together for the same reason [PlaceWatch] carries its outcome beside its
 * place: "there was no fix" is an ordinary answer rather than an error. Location may be switched
 * off, the permission may never have been granted, and both are things Abu fixes in a tap and
 * tries again. Folding that into a failure would have Mira apologising for a fault when what he
 * needs is the switch that answers it — and a caller that could only see [Saved] would have
 * nothing at all to show him.
 */
sealed interface PlaceSaveOutcome {

    data class Saved(val place: SavedPlace) : PlaceSaveOutcome

    data class NoFix(val reason: PlaceWatchRefusal) : PlaceSaveOutcome
}

/**
 * Puts a name to a coordinate.
 *
 * [SavedPlace.labelKey] is never a parameter. It is derived from the label here, on every write,
 * so the unique index and the lookup agree by construction; a caller that could pass its own key
 * would be a second spelling rule, and the one that disagreed would be the one Abu spoke to.
 */
class SavePlaceUseCase @Inject constructor(
    private val places: SavedPlaceRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        label: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = DEFAULT_RADIUS_METERS,
    ): AppResult<SavedPlace> {
        val clean = label.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Place label was blank")
        if (!PlaceBounds.isOnGlobe(latitude, longitude)) {
            return failure(MiraErrorCode.VALIDATION, "Coordinate $latitude, $longitude is not on the globe")
        }
        if (!PlaceBounds.holdsRadius(radiusMeters)) {
            return failure(MiraErrorCode.VALIDATION, "Radius ${radiusMeters}m is outside what a place alert can hold")
        }

        val key = SavedPlace.keyOf(clean)
        return places.byLabelKey(key).flatMap { taken ->
            // Asked before the write rather than left to the unique index alone, so the refusal
            // can name the place already answering to it. The index remains the real guard.
            if (taken != null) {
                failure(MiraErrorCode.VALIDATION, "A place called ${taken.label} is already saved")
            } else {
                val now = clock.now()
                places.create(
                    SavedPlace(
                        label = clean,
                        labelKey = key,
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = radiusMeters,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    companion object {

        /**
         * A fix taken indoors is routinely tens of metres out, so a tighter default would mean
         * Mira failing to notice Abu arrive — the failure he cannot see. A hundred and fifty
         * metres is about a street rather than a district, and he can narrow it himself.
         */
        const val DEFAULT_RADIUS_METERS = 150f
    }
}

/**
 * Saves the spot Abu is standing on.
 *
 * Standing there is the only way a coordinate enters Mira: there is no geocoder and there will
 * not be one, because looking an address up means sending his places to somebody's server (§3,
 * docs/PRIVACY.md). The fix is taken once, on his instruction, and never watched.
 */
class SavePlaceHereUseCase @Inject constructor(
    private val finder: CurrentPlaceFinder,
    private val save: SavePlaceUseCase,
) {

    suspend operator fun invoke(
        label: String,
        radiusMeters: Float = SavePlaceUseCase.DEFAULT_RADIUS_METERS,
    ): AppResult<PlaceSaveOutcome> {
        // Refused before the radio is touched. A name that cannot be stored is not a reason to
        // take a location fix, and Mira asks for one only when she can use the answer.
        if (label.isBlank()) return failure(MiraErrorCode.VALIDATION, "Place label was blank")

        return when (val fix = finder.here()) {
            is PlaceFix.Unavailable -> AppResult.Success(PlaceSaveOutcome.NoFix(fix.reason))
            is PlaceFix.Found -> save(label, fix.latitude, fix.longitude, radiusMeters)
                .map { PlaceSaveOutcome.Saved(it) }
        }
    }
}

/**
 * Renames a place, and re-derives its key with it.
 *
 * The key is what a spoken place name is matched against, so a rename that left the old one
 * behind would leave Abu saying the new name to a place that still only answers to the old.
 */
class RenamePlaceUseCase @Inject constructor(
    private val places: SavedPlaceRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long, label: String): AppResult<SavedPlace> {
        val clean = label.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Place label was blank")

        val key = SavedPlace.keyOf(clean)
        return places.byId(id).flatMap { existing ->
            places.byLabelKey(key).flatMap { taken ->
                // Renaming a place to the name it already has is a no-op Abu is allowed to make;
                // only another place holding the key is a clash.
                if (taken != null && taken.id != existing.id) {
                    failure(MiraErrorCode.VALIDATION, "A place called ${taken.label} is already saved")
                } else {
                    places.update(
                        existing.copy(label = clean, labelKey = key, updatedAt = clock.now()),
                    )
                }
            }
        }
    }
}

/**
 * Forgets a place.
 *
 * Nothing cascades. A reminder waiting there keeps its own copy of the coordinate in
 * [com.rayluxeo.aida.domain.model.ReminderPlace], so forgetting the directory entry cannot
 * silently move or disarm a reminder Abu has already set.
 */
class DeletePlaceUseCase @Inject constructor(
    private val places: SavedPlaceRepository,
) {

    suspend operator fun invoke(id: Long): AppResult<Unit> {
        if (id <= 0L) return failure(MiraErrorCode.VALIDATION, "Place id must be positive")
        return places.delete(id)
    }
}
