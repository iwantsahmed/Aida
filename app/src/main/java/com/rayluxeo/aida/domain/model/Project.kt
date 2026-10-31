package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * A body of work, usually for a client.
 *
 * [uuid] is assigned by the repository on the first write and never changes afterwards, for the
 * same reason a client's does not: it is what identifies this project outside the database.
 *
 * [startedOn] and [dueOn] are dates, not instants. "Due on the 3rd" is a day in Abu's calendar and
 * stays the 3rd whatever timezone the phone is in, which is exactly what an [Instant] would fail
 * to promise.
 *
 * [currency] is the project's own, and [budget] — when there is one — is stated in it. The
 * constructor refuses any other pairing rather than leaving a budget whose currency depends on
 * which field you read.
 */
data class Project(
    val id: Long = 0L,
    /** Empty until the repository stores the project and assigns it. */
    val uuid: String = "",
    /** Null when the project is Abu's own. Deleting a client leaves this null, never orphaned. */
    val clientId: Long? = null,
    val name: String,
    val description: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val startedOn: LocalDate? = null,
    val dueOn: LocalDate? = null,
    val budget: Money? = null,
    val currency: Currency = Money.BDT,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(budget == null || budget.currency == currency) {
            "Budget is in ${budget?.currency?.currencyCode} but the project is in ${currency.currencyCode}"
        }
    }

    /** True while the project is still work rather than history. */
    val isOpen: Boolean
        get() = status !in CLOSED_STATUSES

    companion object {
        /**
         * The statuses that mean the work is over. Held here rather than repeated as literals in
         * a query, so "open" means one thing in the database and in the domain.
         */
        val CLOSED_STATUSES: Set<ProjectStatus> = setOf(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED)
    }
}

/** Which projects a screen is asking for. Deleted projects are in none of them. */
enum class ProjectFilter { ALL, OPEN, ACTIVE, COMPLETED, CANCELLED }
