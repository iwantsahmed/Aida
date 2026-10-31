package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Contact

/**
 * Read-through access to the phone's address book.
 *
 * Nothing here writes, and nothing here is cached: Mira reads a name when she is asked about one
 * and keeps nothing afterwards. Every call can fail with PERMISSION_REQUIRED, which the caller
 * turns into an offer to grant rather than an apology — see
 * [com.rayluxeo.aida.domain.command.MiraResponse.PermissionNeeded].
 */
interface ContactsRepository {

    /** False when READ_CONTACTS has not been granted. Cheap; never touches the provider. */
    fun canRead(): Boolean

    /**
     * Matches on display name, loosely enough that "ahmed" finds "Ahmed Hossain". Returns every
     * match rather than guessing, so the caller can ask which one Abu meant.
     */
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): AppResult<List<Contact>>

    suspend fun byId(systemId: Long): AppResult<Contact?>

    companion object {
        const val DEFAULT_LIMIT = 10
    }
}
