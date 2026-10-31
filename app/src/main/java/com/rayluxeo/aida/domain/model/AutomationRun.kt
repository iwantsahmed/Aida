package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * One attempt at running an automation: appended when the run opens, closed when it ends.
 *
 * [outcome] starts at [AutomationOutcome.FAILED] and is only moved by finishing the run, so a run
 * the process death cut short can never be read back as a success. [finishedAt] is what tells the
 * two apart — null means still in flight, which is not the same as failed.
 */
data class AutomationRun(
    val id: Long = 0L,
    val automationId: Long,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val outcome: AutomationOutcome = AutomationOutcome.FAILED,
    /** Developer-facing detail for the run history. Never spoken to Abu verbatim. */
    val detail: String? = null,
) {
    /** True while the run has not been closed, including one abandoned by the process dying. */
    val isInFlight: Boolean get() = finishedAt == null
}
