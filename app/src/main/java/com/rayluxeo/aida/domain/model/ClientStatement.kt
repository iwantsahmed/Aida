package com.rayluxeo.aida.domain.model

/**
 * A client's standing at a glance: what Abu has billed them, what they have paid, and what is still
 * owed, taken across every invoice that has actually been issued to them.
 *
 * The three totals are nullable and guarded by [currencyMixed] for the same reason the reports are
 * (`DefaultReportEngine`): Mira will not add taka to dollars. When a client has issued invoices in
 * more than one currency she reports that rather than a wrong sum, and when they have none issued yet
 * [invoiceCount] is zero and the totals are absent — there is nothing to total, in no currency.
 * Otherwise all three are present and in the one currency the client was billed in.
 */
data class ClientStatement(
    val clientName: String,
    /** Issued invoices only — drafts and voided invoices are not a client's obligation. */
    val invoiceCount: Int,
    val totalInvoiced: Money?,
    val totalPaid: Money?,
    val outstanding: Money?,
    val currencyMixed: Boolean,
)
