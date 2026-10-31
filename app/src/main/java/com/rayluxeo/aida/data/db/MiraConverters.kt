package com.rayluxeo.aida.data.db

import androidx.room.TypeConverter
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.AlarmState
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.MemorySource
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.model.VaultCategory

/**
 * Enums are stored by name, not ordinal, so reordering a domain enum can never silently
 * relabel Abu's existing rows. An unrecognised name decays to the enum's own default instead
 * of throwing, because one bad row must not take the whole list down.
 */
class MiraConverters {

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = decode(value, TaskPriority.NORMAL)

    @TypeConverter
    fun fromRecordOrigin(value: RecordOrigin): String = value.name

    @TypeConverter
    fun toRecordOrigin(value: String): RecordOrigin = decode(value, RecordOrigin.MANUAL)

    @TypeConverter
    fun fromReminderState(value: ReminderState): String = value.name

    @TypeConverter
    fun toReminderState(value: String): ReminderState = decode(value, ReminderState.SCHEDULED)

    @TypeConverter
    fun fromVaultCategory(value: VaultCategory): String = value.name

    @TypeConverter
    fun toVaultCategory(value: String): VaultCategory = decode(value, VaultCategory.NOTE)

    @TypeConverter
    fun fromTurnRole(value: TurnRole): String = value.name

    @TypeConverter
    fun toTurnRole(value: String): TurnRole = decode(value, TurnRole.ABU)

    @TypeConverter
    fun fromInputSource(value: InputSource): String = value.name

    @TypeConverter
    fun toInputSource(value: String): InputSource = decode(value, InputSource.TEXT)


    @TypeConverter
    fun fromProjectStatus(value: ProjectStatus): String = value.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus = decode(value, ProjectStatus.ACTIVE)

    @TypeConverter
    fun fromInvoiceStatus(value: InvoiceStatus): String = value.name

    @TypeConverter
    fun toInvoiceStatus(value: String): InvoiceStatus = when (value.uppercase()) {
        "CANCELLED" -> InvoiceStatus.VOID
        else -> decode(value, InvoiceStatus.DRAFT)
    }

    @TypeConverter
    fun fromQuoteStatus(value: QuoteStatus): String = value.name

    @TypeConverter
    fun toQuoteStatus(value: String): QuoteStatus = when (value.uppercase()) {
        "REJECTED" -> QuoteStatus.DECLINED
        else -> decode(value, QuoteStatus.DRAFT)
    }

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = decode(value, PaymentMethod.OTHER)

    @TypeConverter
    fun fromSubscriptionCycle(value: SubscriptionCycle): String = value.name

    @TypeConverter
    fun toSubscriptionCycle(value: String): SubscriptionCycle = decode(value, SubscriptionCycle.MONTHLY)

    /**
     * Nullable on both sides because a budget with no category covers every category, and that
     * absence is meaningful rather than a missing value.
     */
    @TypeConverter
    fun fromExpenseCategory(value: ExpenseCategory?): String? = value?.name

    @TypeConverter
    fun toExpenseCategory(value: String?): ExpenseCategory? =
        value?.let { decode(it, ExpenseCategory.OTHER) }

    @TypeConverter
    fun fromTimerState(value: TimerState): String = value.name

    @TypeConverter
    fun toTimerState(value: String): TimerState = decode(value, TimerState.FINISHED)

    @TypeConverter
    fun fromAlarmState(value: AlarmState): String = value.name

    @TypeConverter
    fun toAlarmState(value: String): AlarmState = decode(value, AlarmState.SCHEDULED)

    @TypeConverter
    fun fromMemorySource(value: MemorySource): String = value.name

    @TypeConverter
    fun toMemorySource(value: String): MemorySource = decode(value, MemorySource.INFERRED)

    @TypeConverter
    fun fromMemoryCategory(value: com.rayluxeo.aida.domain.model.MemoryCategory): String = value.name

    @TypeConverter
    fun toMemoryCategory(value: String): com.rayluxeo.aida.domain.model.MemoryCategory =
        decode(value, com.rayluxeo.aida.domain.model.MemoryCategory.GENERAL_FACT)

    @TypeConverter
    fun fromFileSource(value: FileSource): String = value.name

    @TypeConverter
    fun toFileSource(value: String): FileSource = decode(value, FileSource.IMPORT)

    @TypeConverter
    fun fromAutomationTrigger(value: AutomationTrigger): String = value.name

    @TypeConverter
    fun toAutomationTrigger(value: String): AutomationTrigger = decode(value, AutomationTrigger.MANUAL)

    @TypeConverter
    fun fromAutomationOutcome(value: AutomationOutcome): String = value.name

    @TypeConverter
    fun toAutomationOutcome(value: String): AutomationOutcome = decode(value, AutomationOutcome.FAILED)

    @TypeConverter
    fun fromActivityCategory(value: ActivityCategory): String = value.name

    @TypeConverter
    fun toActivityCategory(value: String): ActivityCategory = decode(value, ActivityCategory.OTHER)

    private inline fun <reified T : Enum<T>> decode(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
