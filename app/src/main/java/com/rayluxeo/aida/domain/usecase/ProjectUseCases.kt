package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.repository.ProjectRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Every write to a project goes through one of these, so the rule that a project must have a name
 * and that its timestamps come from [MiraClock] lives in one place rather than in whichever screen
 * happens to create one (§4). The repository still owns the transaction and the uuid; a use case
 * owns the decision to write and the shape of what is written.
 */
class CreateProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
    private val clock: MiraClock,
) {
    /**
     * A blank name is refused here rather than at the database, so the refusal is a value Abu's
     * screen can word in his language instead of an exception. [budget] is already in its own
     * currency; the project is stored in that same currency so [Project]'s own invariant holds.
     */
    suspend operator fun invoke(
        name: String,
        clientId: Long? = null,
        description: String? = null,
        status: ProjectStatus = ProjectStatus.ACTIVE,
        startedOn: LocalDate? = null,
        dueOn: LocalDate? = null,
        budget: Money? = null,
    ): AppResult<Project> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "A project needs a name")
        }
        val now = clock.now()
        val project = Project(
            clientId = clientId,
            name = cleanName,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            status = status,
            startedOn = startedOn,
            dueOn = dueOn,
            budget = budget,
            currency = budget?.currency ?: Money.BDT,
            createdAt = now,
            updatedAt = now,
        )
        return repository.create(project)
    }
}

/** Changes a project's details. The uuid, the creation time and the bin state are the repository's. */
class UpdateProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(project: Project): AppResult<Project> {
        val cleanName = project.name.trim()
        if (cleanName.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "A project needs a name")
        }
        return repository.update(project.copy(name = cleanName, updatedAt = clock.now()))
    }
}

/**
 * Moves a project between statuses. This — not deletion — is how work is closed: a completed or
 * cancelled project is history Abu keeps, so "archive" means [ProjectStatus.COMPLETED] or
 * [ProjectStatus.CANCELLED], never the bin.
 */
class SetProjectStatusUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(id: Long, status: ProjectStatus): AppResult<Project> =
        repository.setStatus(id, status)
}

/** Moves a project to the bin. Tasks and invoices that pointed at it keep their record (§8). */
class SoftDeleteProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> = repository.softDelete(id)
}

/** Brings a project back from the bin, for the undo beat right after a delete. */
class RestoreProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(id: Long): AppResult<Project> = repository.restore(id)
}
