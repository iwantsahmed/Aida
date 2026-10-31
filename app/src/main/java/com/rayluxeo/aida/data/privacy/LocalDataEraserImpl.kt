package com.rayluxeo.aida.data.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.crypto.VaultKeyProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Erases every local store in one pass.
 *
 * Uses Room's [androidx.room.RoomDatabase.clearAllTables] rather than calling forty per-repository
 * clears by hand: one deferred-foreign-key transaction empties every table (including the
 * external-content FTS shadow tables) while keeping the schema, which is both complete and immune
 * to the cascade and soft-delete rules those hand-written clears would have to reproduce. It is
 * NOT `fallbackToDestructiveMigration` — AGENTS.md §8's ban on destructive migration does not
 * apply to clearing rows.
 *
 * The media files live on disk under `filesDir`, outside the database, so they are deleted
 * separately. DataStore is cleared through its own API, never by deleting the file, because its
 * backing file sits inside `filesDir/datastore` and the live store must not be pulled out from
 * under itself. The single preferences file holds settings, voice, brief schedule and the sealed
 * Gemini key, so clearing it removes the credential too.
 */
@Singleton
class LocalDataEraserImpl @Inject constructor(
    private val database: MiraDatabase,
    private val preferences: DataStore<Preferences>,
    private val vaultKeyProvider: VaultKeyProvider,
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : com.rayluxeo.aida.domain.repository.LocalDataEraser {

    override suspend fun eraseEverything(): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            database.clearAllTables()
            preferences.edit { it.clear() }
            MEDIA_DIRECTORIES.forEach { File(context.filesDir, it).deleteRecursively() }
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            // Last: the stored secrets are already gone; dropping the key makes any stray
            // ciphertext permanently unreadable. A fresh key mints on the next vault write.
            vaultKeyProvider.destroyKey()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            failure(MiraErrorCode.STORAGE, detail = "erase all local data", cause = t)
        }
    }

    private companion object {
        /** On-disk media Room does not hold: handwriting pages, voice-note audio, document scans. */
        val MEDIA_DIRECTORIES = listOf("ink", "audio_recordings", "scans")
    }
}
