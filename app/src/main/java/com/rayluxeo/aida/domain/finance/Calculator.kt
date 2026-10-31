package com.rayluxeo.aida.domain.finance

import com.rayluxeo.aida.core.common.AppResult

/**
 * Deterministic arithmetic. A language model is never asked to do sums for Abu —
 * it may only hand an expression to this evaluator, which computes on BigDecimal.
 */
interface Calculator {
    /** Returns the formatted result, or a VALIDATION failure describing what could not be parsed. */
    fun evaluate(expression: String): AppResult<String>
}
