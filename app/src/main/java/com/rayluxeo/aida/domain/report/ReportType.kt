package com.rayluxeo.aida.domain.report

/**
 * The reports Mira can draw, each a self-contained reading of Abu's business over a [ReportPeriod].
 *
 * The list is fixed and the engine's `when` is exhaustive over it, so a report cannot be named here
 * without a computation being written for it — the same compile-time guarantee the command
 * dispatcher gives. Human wording lives in the presentation layer, never here (§4).
 */
enum class ReportType {

    /** Money in: income rows plus invoice payments received in the window. */
    INCOME,

    /** Money out: every expense in the window. */
    EXPENSES,

    /** Income minus expenses — the one number that says whether the window was green. */
    NET_SUMMARY,

    /** Invoices not fully paid, and how much is still owed, as of the window's end. */
    OUTSTANDING_INVOICES,

    /** Payments actually received in the window, by when they landed. */
    PAYMENTS_RECEIVED,

    /** Revenue broken down by client, largest first. */
    CLIENT_REVENUE,

    /** Revenue broken down by project, largest first. */
    PROJECT_REVENUE,

    /** Expenses broken down by category, largest first. */
    EXPENSE_CATEGORIES,

    /** How many tasks were completed versus opened in the window. */
    TASK_COMPLETION,

    /** Tasks past their due date and still open. */
    OVERDUE_TASKS,

    /** Recurring subscription costs, as a per-window total. */
    SUBSCRIPTION_COSTS,

    /** Projects by their current status, so stalled work is visible. */
    PROJECT_STATUS,
}
