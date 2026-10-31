package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Abu's own details, as one record or none.
 *
 * The nullable read is the contract: until he has set a profile there is no profile, and an empty
 * name printed on an invoice would be worse than a screen that asks him for one. Writing is
 * [save] rather than create-or-update, because nothing outside storage should have to know which
 * of the two this is.
 */
interface UserProfileRepository {

    fun observe(): Flow<UserProfile?>

    suspend fun current(): AppResult<UserProfile?>

    suspend fun save(profile: UserProfile): AppResult<UserProfile>
}
