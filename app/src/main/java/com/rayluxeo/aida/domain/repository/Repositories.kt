package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.VaultItem
import com.rayluxeo.aida.domain.model.VaultItemSummary
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun observeSummaries(): Flow<List<VaultItemSummary>>
    suspend fun reveal(id: Long): AppResult<VaultItem>
    suspend fun create(item: VaultItem): AppResult<VaultItemSummary>
    suspend fun update(item: VaultItem): AppResult<VaultItemSummary>
    suspend fun delete(id: Long): AppResult<Unit>
}

interface ConversationRepository {
    fun observeRecent(limit: Int = 100): Flow<List<ConversationTurn>>
    suspend fun append(turn: ConversationTurn): AppResult<ConversationTurn>
    suspend fun clear(): AppResult<Unit>
}

interface BriefRepository {
    suspend fun todayBrief(): AppResult<DailyBrief>
}
