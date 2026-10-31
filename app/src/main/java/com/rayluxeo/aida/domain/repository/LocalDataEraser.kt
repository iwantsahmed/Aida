package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult

/**
 * Wipes every local store Mira keeps on this device — all database rows, all preferences, the
 * recorded media files (ink, audio, scans, cached PDFs) and the vault key — leaving nothing to
 * recover. This is the "erase everything" the privacy dashboard offers; it is implemented in the
 * data layer because only that layer knows Room, DataStore, the Keystore and the filesystem exist.
 *
 * There is deliberately no selective variant: this is the whole-device wipe Abu confirms, and
 * there is no backup behind it. After it succeeds, onboarding has been forgotten too, so the app
 * returns to first run.
 */
interface LocalDataEraser {
    suspend fun eraseEverything(): AppResult<Unit>
}
