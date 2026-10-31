package com.rayluxeo.aida.domain.report

import com.rayluxeo.aida.core.common.AppResult

/**
 * Computes one [BusinessReport] for a [type] over a [period], deterministically and on the device.
 *
 * A port in the domain, implemented by [DefaultReportEngine], so the same reading backs the Reports
 * screen, a spoken "how much did I earn this month", and a PDF export. Every figure it returns is
 * worked out in Kotlin over the repository ports — no figure is ever handed to a language model
 * (§4.2) — and every money total goes through BigDecimal arithmetic (§8).
 */
interface ReportEngine {
    suspend fun generate(type: ReportType, period: ReportPeriod): AppResult<BusinessReport>
}
