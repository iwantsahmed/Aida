package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.SavedPlace
import kotlinx.coroutines.flow.Flow

/**
 * Abu's named places.
 *
 * Small and unlikely to grow: a place directory is a handful of rows he maintains by hand, so
 * there is no paging, no search and no soft delete — see [SavedPlace] for why the last one is
 * deliberate rather than an omission.
 */
interface SavedPlaceRepository {

    /** Alphabetical by label, so the Places list and Mira's "you have saved…" agree on order. */
    fun observeAll(): Flow<List<SavedPlace>>

    suspend fun all(): AppResult<List<SavedPlace>>

    /**
     * Resolves what Abu called a place in a sentence.
     *
     * @param labelKey a key from [SavedPlace.keyOf], never a raw label — passing the raw form is
     *   how a lookup silently stops matching the moment he capitalises something.
     * @return null when no place answers to that name, which is ordinary: Mira then says which
     *   places she does know rather than failing blankly.
     */
    suspend fun byLabelKey(labelKey: String): AppResult<SavedPlace?>

    suspend fun byId(id: Long): AppResult<SavedPlace>

    /** Fails with `VALIDATION` when the key is already taken, so two places cannot share a name. */
    suspend fun create(place: SavedPlace): AppResult<SavedPlace>

    suspend fun update(place: SavedPlace): AppResult<SavedPlace>

    suspend fun delete(id: Long): AppResult<Unit>
}
