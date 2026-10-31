package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.ReminderDao
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.task.RecurrenceCodec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val recurrences: RecurrenceCodec,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : ReminderRepository {

    override fun observeUpcoming(): Flow<List<Reminder>> =
        dao.observeInState(ReminderState.SCHEDULED)
            .map { entities -> entities.map { it.toDomain(recurrences) } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> =
        dao.observeInStateOfKind(ReminderState.SCHEDULED, kind)
            .map { entities -> entities.map { it.toDomain(recurrences) } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun listOfKinds(kinds: Collection<ReminderKind>): AppResult<List<Reminder>> =
        storageCall(dispatchers, "list reminders by kind") {
            dao.listOfKinds(kinds).map { it.toDomain(recurrences) }
        }

    override suspend fun byId(id: Long): AppResult<Reminder> {
        val found = storageCall(dispatchers, "load reminder $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain(recurrences)?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")
        }
    }

    override suspend fun create(reminder: Reminder): AppResult<Reminder> =
        storageCall(dispatchers, "create reminder") {
            val saved = reminder.copy(id = 0L, state = ReminderState.SCHEDULED)
            val id = dao.insert(saved.toEntity(recurrences))
            saved.copy(id = id)
        }

    /**
     * Storage only. Arming is the caller's business, because the same write serves a snooze, an
     * advanced occurrence and a state change, and each of those arms a different moment — or, for
     * a reminder that has finished, none at all.
     */
    override suspend fun update(reminder: Reminder): AppResult<Reminder> {
        if (reminder.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot update an unsaved reminder")
        }
        val changed = storageCall(dispatchers, "update reminder ${reminder.id}") {
            dao.update(reminder.toEntity(recurrences))
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) AppResult.Success(reminder)
                else failure(MiraErrorCode.NOT_FOUND, "No reminder with id ${reminder.id}")
        }
    }

    override suspend fun markFired(id: Long): AppResult<Unit> = setState(id, ReminderState.FIRED)

    override suspend fun cancel(id: Long): AppResult<Unit> = setState(id, ReminderState.CANCELLED)

    override suspend fun nextScheduled(): AppResult<Reminder?> =
        storageCall(dispatchers, "load next reminder") {
            dao.nextInState(ReminderState.SCHEDULED, clock.now().toEpochMilli())
                ?.toDomain(recurrences)
        }

    /**
     * Alarms do not survive a reboot or a package replacement, and the platform cancels every
     * future exact alarm the moment that permission is withdrawn. The scheduled rows are the
     * durable record and the OS alarm is rebuilt from them. Reminders whose moment already passed
     * are re-armed too: AlarmManager fires those immediately, which is the right answer when the
     * phone was off through the trigger time.
     *
     * Place reminders are skipped rather than failed. They have no moment to arm — the place
     * scheduler re-registers them — and counting them as failures here would make a healthy
     * restore look broken.
     */
    override suspend fun rescheduleAll(): AppResult<Int> {
        val pending = storageCall(dispatchers, "list scheduled reminders") {
            dao.listInState(ReminderState.SCHEDULED).map { it.toDomain(recurrences) }
        }
        return when (pending) {
            is AppResult.Failure -> pending
            is AppResult.Success -> {
                var armed = 0
                for (reminder in pending.value) {
                    if (reminder.waitsForPlace) continue
                    if (scheduler.schedule(reminder) is AppResult.Success) armed++
                }
                AppResult.Success(armed)
            }
        }
    }

    private suspend fun setState(id: Long, state: ReminderState): AppResult<Unit> {
        val changed = storageCall(dispatchers, "set reminder $id to $state") { dao.setState(id, state) }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")
        }
    }
}
