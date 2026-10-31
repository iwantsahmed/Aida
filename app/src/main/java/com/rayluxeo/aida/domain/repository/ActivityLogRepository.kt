package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * What Mira did, in order.
 *
 * There is no update and no delete on this port, and that absence is the design: an entry is
 * evidence, and evidence that can be edited afterwards answers nothing. Entries also outlive
 * their subjects — [observeForSubject] still returns the history of an invoice that has since
 * been deleted, which is exactly when someone asks for it.
 */
interface ActivityLogRepository {

    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<ActivityEntry>>

    fun observeByCategory(category: ActivityCategory, limit: Int = DEFAULT_LIMIT): Flow<List<ActivityEntry>>

    fun observeForSubject(subject: ActivitySubject): Flow<List<ActivityEntry>>

    /** Everything that happened in a half-open window — `from` included, `to` excluded. */
    suspend fun between(from: Instant, to: Instant): AppResult<List<ActivityEntry>>

    suspend fun record(entry: ActivityEntry): AppResult<ActivityEntry>

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
