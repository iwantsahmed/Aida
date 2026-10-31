package com.rayluxeo.aida.domain.attention

import kotlinx.coroutines.flow.Flow

/**
 * Tracks attention items Abu has explicitly dismissed so they do not continue bothering him.
 */
interface AttentionDismissalRepository {

    fun observeDismissedIds(): Flow<Set<String>>

    suspend fun getDismissedIds(): Set<String>

    suspend fun dismiss(id: String)

    suspend fun clearDismissed()
}
