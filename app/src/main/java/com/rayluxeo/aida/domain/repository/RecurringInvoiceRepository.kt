package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.RecurringInvoiceProfile
import kotlinx.coroutines.flow.Flow

/**
 * Persists schedules for generating recurring draft invoices.
 */
interface RecurringInvoiceRepository {
    fun observeAll(): Flow<List<RecurringInvoiceProfile>>
    suspend fun getAll(): AppResult<List<RecurringInvoiceProfile>>
    suspend fun save(profile: RecurringInvoiceProfile): AppResult<RecurringInvoiceProfile>
    suspend fun saveAll(profiles: List<RecurringInvoiceProfile>): AppResult<Unit>
    suspend fun delete(id: String): AppResult<Unit>
}
