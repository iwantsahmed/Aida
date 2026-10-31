package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.InvoiceDeliveryRecord
import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting and retrieving invoice delivery and sharing history.
 */
interface InvoiceDeliveryRepository {

    /** Observe all delivery records, latest first. */
    fun observeAll(): Flow<List<InvoiceDeliveryRecord>>

    /** Observe delivery history for a specific invoice. */
    fun observeForInvoice(invoiceId: Long): Flow<List<InvoiceDeliveryRecord>>

    /** Record a new delivery attempt or status transition. */
    suspend fun record(delivery: InvoiceDeliveryRecord): AppResult<InvoiceDeliveryRecord>
}
