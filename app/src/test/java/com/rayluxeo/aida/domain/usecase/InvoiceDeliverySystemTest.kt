package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.InvoiceDeliveryRecord
import com.rayluxeo.aida.domain.model.InvoiceDeliveryStatus
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.InvoiceDeliveryRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InvoiceDeliverySystemTest {

    private val clock = FixedMiraClock(FixedMiraClock.dhaka(2026, 10, 8, 14))
    private val fakeDeliveryRepo = FakeInvoiceDeliveryRepository()
    private val fakeInvoiceRepo = FakeDeliveryTestInvoices()
    private val fakeActivityLogRepo = FakeDeliveryTestActivityLog()

    private val prepareUseCase = PrepareInvoiceDeliveryUseCase(com.rayluxeo.aida.ui.invoices.delivery.InvoiceDeliveryMessages())
    private val recordUseCase = RecordInvoiceDeliveryUseCase(
        deliveryRepository = fakeDeliveryRepo,
        invoiceRepository = fakeInvoiceRepo,
        activityLogRepository = fakeActivityLogRepo,
        clock = clock,
    )

    private val sampleClient = Client(
        id = 101L,
        name = "Ahmed",
        companyName = "Ahmed Enterprises",
        email = "ahmed@example.com",
        phone = "+8801700112233",
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private val sampleInvoice = Invoice(
        id = 41L,
        number = "INV-2026-041",
        clientId = 101L,
        status = InvoiceStatus.DRAFT,
        issuedOn = LocalDate.of(2026, 10, 1),
        dueOn = LocalDate.of(2026, 10, 15),
        subtotal = Money.of("45000"),
        discount = Money.zero(),
        tax = Money.zero(),
        total = Money.of("45000"),
        amountPaid = Money.zero(),
        notes = "Net 14 payment terms",
        lines = listOf(
            InvoiceLine(
                id = 1L,
                description = "Website development",
                quantity = BigDecimal.ONE,
                unitPrice = Money.of("45000"),
                taxRatePercent = BigDecimal.ZERO,
                lineTotal = Money.of("45000"),
                position = 0,
            ),
        ),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    @Test
    fun `prepare delivery drafts email with recipient, subject, and professional message`() {
        // The use case resolves the recipient and formats the amount; the subject/body are worded by
        // the real InvoiceDeliveryMessages composer (English here), which this verifies end to end.
        val draft = prepareUseCase(
            invoice = sampleInvoice,
            client = sampleClient,
            method = InvoiceDeliveryMethod.EMAIL,
            profile = UserProfile(
                displayName = "Abu Sayed Ahmed",
                businessName = "Rayluxeo Studio",
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
            attachmentFile = File("/tmp/invoice_INV_2026_041.pdf"),
        )

        assertThat(draft.invoiceId).isEqualTo(41L)
        assertThat(draft.invoiceNumber).isEqualTo("INV-2026-041")
        assertThat(draft.clientName).isEqualTo("Ahmed")
        assertThat(draft.recipient).isEqualTo("ahmed@example.com")
        assertThat(draft.method).isEqualTo(InvoiceDeliveryMethod.EMAIL)
        assertThat(draft.subject).contains("INV-2026-041")
        assertThat(draft.subject).contains("Rayluxeo Studio")
        assertThat(draft.message).contains("Dear Ahmed")
        assertThat(draft.message).contains("INV-2026-041")
        assertThat(draft.attachmentPath).isEqualTo("/tmp/invoice_INV_2026_041.pdf")
    }

    @Test
    fun `prepare delivery drafts WhatsApp message with markdown formatting`() {
        val draft = prepareUseCase(
            invoice = sampleInvoice,
            client = sampleClient,
            method = InvoiceDeliveryMethod.WHATSAPP,
            profile = UserProfile(
                displayName = "Abu Sayed Ahmed",
                businessName = "Rayluxeo",
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        )

        assertThat(draft.recipient).isEqualTo("+8801700112233")
        assertThat(draft.method).isEqualTo(InvoiceDeliveryMethod.WHATSAPP)
        assertThat(draft.message).contains("Hello Ahmed")
        assertThat(draft.message).contains("*INV-2026-041*")
    }

    @Test
    fun `recording confirmed delivery transitions invoice from DRAFT to SENT and records audit log`() = runTest {
        fakeInvoiceRepo.add(sampleInvoice)

        val draft = prepareUseCase(
            invoice = sampleInvoice,
            client = sampleClient,
            method = InvoiceDeliveryMethod.EMAIL,
        )

        // 1. Record PREPARED
        val prepResult = recordUseCase(draft, InvoiceDeliveryStatus.PREPARED)
        assertThat(prepResult).isInstanceOf(AppResult.Success::class.java)

        // Invoice status is still DRAFT
        val invoiceAfterPrep = (fakeInvoiceRepo.byId(41L) as AppResult.Success).value
        assertThat(invoiceAfterPrep.status).isEqualTo(InvoiceStatus.DRAFT)

        // 2. Explicit Confirmation -> Record SENT
        val sentResult = recordUseCase(draft, InvoiceDeliveryStatus.SENT)
        assertThat(sentResult).isInstanceOf(AppResult.Success::class.java)

        // Invoice status transitioned to SENT
        val invoiceAfterSent = (fakeInvoiceRepo.byId(41L) as AppResult.Success).value
        assertThat(invoiceAfterSent.status).isEqualTo(InvoiceStatus.SENT)

        // Verify Delivery Repository history
        val history = fakeDeliveryRepo.getAll()
        assertThat(history).hasSize(2)
        assertThat(history.map { it.status }).containsExactly(
            InvoiceDeliveryStatus.PREPARED,
            InvoiceDeliveryStatus.SENT,
        ).inOrder()

        // Verify Activity Log
        val activity = fakeActivityLogRepo.recorded
        assertThat(activity).hasSize(2)
        assertThat(activity[1].action).isEqualTo("invoice.delivery.sent")
        assertThat(activity[1].category).isEqualTo(ActivityCategory.MONEY)
        assertThat(activity[1].subject?.table).isEqualTo("invoices")
        assertThat(activity[1].subject?.id).isEqualTo(41L)
    }

    @Test
    fun `cancelled delivery records CANCELLED status without mutating invoice status`() = runTest {
        fakeInvoiceRepo.add(sampleInvoice)

        val draft = prepareUseCase(
            invoice = sampleInvoice,
            client = sampleClient,
            method = InvoiceDeliveryMethod.WHATSAPP,
        )

        recordUseCase(draft, InvoiceDeliveryStatus.CANCELLED)

        // Invoice remains DRAFT
        val invoice = (fakeInvoiceRepo.byId(41L) as AppResult.Success).value
        assertThat(invoice.status).isEqualTo(InvoiceStatus.DRAFT)

        val history = fakeDeliveryRepo.getAll()
        assertThat(history).hasSize(1)
        assertThat(history[0].status).isEqualTo(InvoiceDeliveryStatus.CANCELLED)

        val activity = fakeActivityLogRepo.recorded
        assertThat(activity).hasSize(1)
        assertThat(activity[0].action).isEqualTo("invoice.delivery.cancelled")
    }

    private class FakeInvoiceDeliveryRepository : InvoiceDeliveryRepository {
        private val records = MutableStateFlow<List<InvoiceDeliveryRecord>>(emptyList())

        fun getAll(): List<InvoiceDeliveryRecord> = records.value

        override fun observeAll(): Flow<List<InvoiceDeliveryRecord>> = records

        override fun observeForInvoice(invoiceId: Long): Flow<List<InvoiceDeliveryRecord>> =
            records.map { it.filter { r -> r.invoiceId == invoiceId } }

        override suspend fun record(delivery: InvoiceDeliveryRecord): AppResult<InvoiceDeliveryRecord> {
            records.value = records.value + delivery
            return AppResult.Success(delivery)
        }
    }

    private class FakeDeliveryTestInvoices : InvoiceRepository {
        private val storage = mutableMapOf<Long, Invoice>()

        fun add(invoice: Invoice) {
            storage[invoice.id] = invoice
        }

        override fun observeAll(): Flow<List<Invoice>> = MutableStateFlow(storage.values.toList())
        override fun observeForClient(clientId: Long): Flow<List<Invoice>> = MutableStateFlow(storage.values.filter { it.clientId == clientId })
        override fun observeUnpaid(): Flow<List<Invoice>> = MutableStateFlow(storage.values.filter { it.status != InvoiceStatus.PAID && it.status != InvoiceStatus.VOID })
        override fun observeOverdue(): Flow<List<Invoice>> = MutableStateFlow(storage.values.filter { it.status == InvoiceStatus.OVERDUE })
        override suspend fun byId(id: Long): AppResult<Invoice> = storage[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND, "Not found"))
        override suspend fun byNumber(number: String): AppResult<Invoice> = storage.values.firstOrNull { it.number == number }?.let { AppResult.Success(it) } ?: AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND, "Not found"))
        override suspend fun search(query: String?): AppResult<List<Invoice>> = AppResult.Success(storage.values.toList())
        override suspend fun create(invoice: Invoice): AppResult<Invoice> { storage[invoice.id] = invoice; return AppResult.Success(invoice) }
        override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> { storage[invoice.id] = invoice; return AppResult.Success(invoice) }
        override suspend fun update(invoice: Invoice): AppResult<Invoice> { storage[invoice.id] = invoice; return AppResult.Success(invoice) }
        override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> {
            val curr = storage[id] ?: return AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND, "Not found"))
            val updated = curr.copy(status = status)
            storage[id] = updated
            return AppResult.Success(updated)
        }
        override suspend fun softDelete(id: Long): AppResult<Unit> { storage.remove(id); return AppResult.Success(Unit) }
        override suspend fun restore(id: Long): AppResult<Invoice> = byId(id)
        override suspend fun delete(id: Long): AppResult<Unit> { storage.remove(id); return AppResult.Success(Unit) }
    }

    private class FakeDeliveryTestActivityLog : ActivityLogRepository {
        val recorded = mutableListOf<ActivityEntry>()

        override fun observeRecent(limit: Int): Flow<List<ActivityEntry>> = MutableStateFlow(recorded)
        override fun observeByCategory(category: ActivityCategory, limit: Int): Flow<List<ActivityEntry>> = MutableStateFlow(recorded.filter { it.category == category })
        override fun observeForSubject(subject: ActivitySubject): Flow<List<ActivityEntry>> = MutableStateFlow(recorded.filter { it.subject == subject })
        override suspend fun between(from: Instant, to: Instant): AppResult<List<ActivityEntry>> = AppResult.Success(recorded)
        override suspend fun record(entry: ActivityEntry): AppResult<ActivityEntry> {
            recorded.add(entry)
            return AppResult.Success(entry)
        }
    }
}
