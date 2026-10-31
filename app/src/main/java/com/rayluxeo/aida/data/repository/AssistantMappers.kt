package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.entity.AutomationEntity
import com.rayluxeo.aida.data.db.entity.AutomationRunEntity
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.model.GlobalDeviceAction
import com.rayluxeo.aida.domain.model.TaskPriority
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

internal fun AutomationEntity.toDomain(): Automation? {
    val triggerConfig = decodeTrigger(trigger, triggerConfigJson) ?: return null
    val actionObj = decodeAction(actionConfigJson) ?: return null
    return Automation(
        id = id,
        name = name,
        description = description,
        trigger = triggerConfig,
        action = actionObj,
        isEnabled = isEnabled,
        lastRunAt = lastRunAtEpochMillis?.let(Instant::ofEpochMilli),
        deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun Automation.toEntity(): AutomationEntity = AutomationEntity(
    id = id,
    name = name,
    description = description,
    trigger = trigger.kind,
    triggerConfigJson = trigger.encode(),
    actionConfigJson = action.encode(),
    isEnabled = isEnabled,
    lastRunAtEpochMillis = lastRunAt?.toEpochMilli(),
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun AutomationRunEntity.toDomain(): AutomationRun = AutomationRun(
    id = id,
    automationId = automationId,
    startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
    finishedAt = finishedAtEpochMillis?.let(Instant::ofEpochMilli),
    outcome = outcome,
    detail = detail,
)

internal fun AutomationRun.toEntity(): AutomationRunEntity = AutomationRunEntity(
    id = id,
    automationId = automationId,
    startedAtEpochMillis = startedAt.toEpochMilli(),
    finishedAtEpochMillis = finishedAt?.toEpochMilli(),
    outcome = outcome,
    detail = detail,
)

private fun AutomationTriggerConfig.encode(): String = when (this) {
    AutomationTriggerConfig.Manual,
    AutomationTriggerConfig.TaskDue,
    AutomationTriggerConfig.TaskOverdue,
    AutomationTriggerConfig.InvoiceDue,
    AutomationTriggerConfig.InvoiceOverdue,
    AutomationTriggerConfig.SubscriptionRenewal,
    AutomationTriggerConfig.ProjectDeadline,
    AutomationTriggerConfig.AppStart,
    AutomationTriggerConfig.PowerConnected,
    AutomationTriggerConfig.PowerDisconnected -> EMPTY_CONFIG

    is AutomationTriggerConfig.TimeOfDay -> buildJsonObject {
        put("minuteOfDay", minuteOfDay)
        putJsonArray("daysOfWeek") { daysOfWeek.map { it.value }.sorted().forEach { add(it) } }
    }.toString()

    is AutomationTriggerConfig.Interval -> buildJsonObject {
        put("everyMinutes", everyMinutes)
    }.toString()

    is AutomationTriggerConfig.Place -> buildJsonObject {
        put("label", label)
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMetres", radiusMetres)
    }.toString()

    is AutomationTriggerConfig.CalendarEvent -> buildJsonObject {
        put("minutesBefore", minutesBefore)
        titleContains?.let { put("titleContains", it) }
    }.toString()

    is AutomationTriggerConfig.NotificationPosted -> buildJsonObject {
        packageName?.let { put("packageName", it) }
        titleContains?.let { put("titleContains", it) }
    }.toString()

    is AutomationTriggerConfig.DateTrigger -> buildJsonObject {
        put("date", date.toString())
        put("minuteOfDay", minuteOfDay)
    }.toString()

    is AutomationTriggerConfig.DayOfWeekTrigger -> buildJsonObject {
        put("dayOfWeek", dayOfWeek.value)
        put("minuteOfDay", minuteOfDay)
    }.toString()
}

private fun decodeTrigger(kind: AutomationTrigger, json: String): AutomationTriggerConfig? {
    val config = parseObject(json) ?: return null
    return when (kind) {
        AutomationTrigger.MANUAL -> AutomationTriggerConfig.Manual
        AutomationTrigger.TASK_DUE -> AutomationTriggerConfig.TaskDue
        AutomationTrigger.TASK_OVERDUE -> AutomationTriggerConfig.TaskOverdue
        AutomationTrigger.INVOICE_DUE -> AutomationTriggerConfig.InvoiceDue
        AutomationTrigger.INVOICE_OVERDUE -> AutomationTriggerConfig.InvoiceOverdue
        AutomationTrigger.SUBSCRIPTION_RENEWAL -> AutomationTriggerConfig.SubscriptionRenewal
        AutomationTrigger.PROJECT_DEADLINE -> AutomationTriggerConfig.ProjectDeadline
        AutomationTrigger.APP_START -> AutomationTriggerConfig.AppStart
        AutomationTrigger.POWER_CONNECTED -> AutomationTriggerConfig.PowerConnected
        AutomationTrigger.POWER_DISCONNECTED -> AutomationTriggerConfig.PowerDisconnected

        AutomationTrigger.TIME_OF_DAY -> {
            val minuteOfDay = config.int("minuteOfDay")?.takeIf { it in 0 until MINUTES_IN_DAY } ?: return null
            AutomationTriggerConfig.TimeOfDay(
                minuteOfDay = minuteOfDay,
                daysOfWeek = config.daysOfWeek("daysOfWeek") ?: return null,
            )
        }

        AutomationTrigger.INTERVAL -> {
            val everyMinutes = config.int("everyMinutes")?.takeIf { it > 0 } ?: return null
            AutomationTriggerConfig.Interval(everyMinutes)
        }

        AutomationTrigger.PLACE -> AutomationTriggerConfig.Place(
            label = config.string("label")?.takeIf { it.isNotBlank() } ?: return null,
            latitude = config.double("latitude")?.takeIf { it in -90.0..90.0 } ?: return null,
            longitude = config.double("longitude")?.takeIf { it in -180.0..180.0 } ?: return null,
            radiusMetres = config.int("radiusMetres")?.takeIf { it > 0 } ?: return null,
        )

        AutomationTrigger.CALENDAR_EVENT -> AutomationTriggerConfig.CalendarEvent(
            minutesBefore = config.int("minutesBefore")?.takeIf { it >= 0 } ?: return null,
            titleContains = config.string("titleContains"),
        )

        AutomationTrigger.NOTIFICATION_POSTED -> AutomationTriggerConfig.NotificationPosted(
            packageName = config.string("packageName")?.takeIf { it.isNotBlank() },
            titleContains = config.string("titleContains")?.takeIf { it.isNotBlank() },
        )

        AutomationTrigger.DATE -> {
            val dateStr = config.string("date") ?: return null
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return null
            val minuteOfDay = config.int("minuteOfDay")?.takeIf { it in 0 until MINUTES_IN_DAY } ?: return null
            AutomationTriggerConfig.DateTrigger(date = date, minuteOfDay = minuteOfDay)
        }

        AutomationTrigger.DAY_OF_WEEK -> {
            val dayVal = config.int("dayOfWeek") ?: return null
            val dayOfWeek = runCatching { DayOfWeek.of(dayVal) }.getOrNull() ?: return null
            val minuteOfDay = config.int("minuteOfDay")?.takeIf { it in 0 until MINUTES_IN_DAY } ?: return null
            AutomationTriggerConfig.DayOfWeekTrigger(dayOfWeek = dayOfWeek, minuteOfDay = minuteOfDay)
        }
    }
}

private fun AutomationAction.encode(): String = when (this) {
    is AutomationAction.RunUtterance -> buildJsonObject {
        put("kind", RUN_UTTERANCE)
        put("utterance", utterance)
    }.toString()

    is AutomationAction.Notify -> buildJsonObject {
        put("kind", NOTIFY)
        put("title", title)
        body?.let { put("body", it) }
    }.toString()

    is AutomationAction.OpenLink -> buildJsonObject {
        put("kind", OPEN_LINK)
        put("url", url)
    }.toString()

    is AutomationAction.PerformGlobalAction -> buildJsonObject {
        put("kind", PERFORM_GLOBAL_ACTION)
        put("action", action.name)
    }.toString()

    is AutomationAction.LaunchApp -> buildJsonObject {
        put("kind", LAUNCH_APP)
        put("packageName", packageName)
        put("label", label)
    }.toString()

    is AutomationAction.CreateTask -> buildJsonObject {
        put("kind", CREATE_TASK)
        put("title", title)
        put("priority", priority.name)
    }.toString()

    is AutomationAction.CreateReminder -> buildJsonObject {
        put("kind", CREATE_REMINDER)
        put("title", title)
        put("offsetMinutes", offsetMinutes)
    }.toString()

    is AutomationAction.PrepareFollowUp -> buildJsonObject {
        put("kind", PREPARE_FOLLOW_UP)
        put("targetName", targetName)
        notes?.let { put("notes", it) }
    }.toString()

    is AutomationAction.PrepareInvoiceReminder -> buildJsonObject {
        put("kind", PREPARE_INVOICE_REMINDER)
        put("invoiceNumber", invoiceNumber)
    }.toString()

    is AutomationAction.ChangeTaskPriority -> buildJsonObject {
        put("kind", CHANGE_TASK_PRIORITY)
        put("taskId", taskId)
        put("priority", priority.name)
    }.toString()

    AutomationAction.GenerateDailyBrief -> buildJsonObject {
        put("kind", GENERATE_DAILY_BRIEF)
    }.toString()

    AutomationAction.GenerateEveningReview -> buildJsonObject {
        put("kind", GENERATE_EVENING_REVIEW)
    }.toString()
}

private fun decodeAction(json: String): AutomationAction? {
    val config = parseObject(json) ?: return null
    return when (config.string("kind")) {
        RUN_UTTERANCE -> config.string("utterance")
            ?.takeIf { it.isNotBlank() }
            ?.let(AutomationAction::RunUtterance)

        NOTIFY -> config.string("title")
            ?.takeIf { it.isNotBlank() }
            ?.let { AutomationAction.Notify(title = it, body = config.string("body")) }

        OPEN_LINK -> config.string("url")
            ?.takeIf { it.isNotBlank() }
            ?.let(AutomationAction::OpenLink)

        CREATE_TASK -> config.string("title")
            ?.takeIf { it.isNotBlank() }
            ?.let { title ->
                val priority = config.string("priority")?.let { p -> runCatching { TaskPriority.valueOf(p) }.getOrNull() } ?: TaskPriority.NORMAL
                AutomationAction.CreateTask(title = title, priority = priority)
            }

        CREATE_REMINDER -> config.string("title")
            ?.takeIf { it.isNotBlank() }
            ?.let { title ->
                AutomationAction.CreateReminder(title = title, offsetMinutes = config.int("offsetMinutes") ?: 0)
            }

        PREPARE_FOLLOW_UP -> config.string("targetName")
            ?.takeIf { it.isNotBlank() }
            ?.let { target ->
                AutomationAction.PrepareFollowUp(targetName = target, notes = config.string("notes"))
            }

        PREPARE_INVOICE_REMINDER -> config.string("invoiceNumber")
            ?.takeIf { it.isNotBlank() }
            ?.let { num ->
                AutomationAction.PrepareInvoiceReminder(invoiceNumber = num)
            }

        CHANGE_TASK_PRIORITY -> config.long("taskId")?.let { id ->
            val priority = config.string("priority")?.let { p -> runCatching { TaskPriority.valueOf(p) }.getOrNull() } ?: TaskPriority.NORMAL
            AutomationAction.ChangeTaskPriority(taskId = id, priority = priority)
        }

        PERFORM_GLOBAL_ACTION -> config.string("action")
            ?.let { runCatching { GlobalDeviceAction.valueOf(it) }.getOrNull() }
            ?.let(AutomationAction::PerformGlobalAction)

        LAUNCH_APP -> config.string("packageName")
            ?.takeIf { it.isNotBlank() }
            ?.let { pkg -> AutomationAction.LaunchApp(packageName = pkg, label = config.string("label").orEmpty()) }

        GENERATE_DAILY_BRIEF -> AutomationAction.GenerateDailyBrief

        GENERATE_EVENING_REVIEW -> AutomationAction.GenerateEveningReview

        else -> null
    }
}

private fun parseObject(json: String): JsonObject? = try {
    Json.parseToJsonElement(json) as? JsonObject
} catch (malformed: SerializationException) {
    null
}

private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content

private fun JsonObject.daysOfWeek(name: String): Set<DayOfWeek>? {
    val array = this[name] as? JsonArray ?: return emptySet()
    val values = array.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
    return values.mapNotNull { dayValue ->
        runCatching { DayOfWeek.of(dayValue) }.getOrNull()
    }.toSet()
}

private const val EMPTY_CONFIG = "{}"
private const val MINUTES_IN_DAY = 24 * 60

private const val RUN_UTTERANCE = "RUN_UTTERANCE"
private const val NOTIFY = "NOTIFY"
private const val OPEN_LINK = "OPEN_LINK"
private const val CREATE_TASK = "CREATE_TASK"
private const val CREATE_REMINDER = "CREATE_REMINDER"
private const val PREPARE_FOLLOW_UP = "PREPARE_FOLLOW_UP"
private const val PREPARE_INVOICE_REMINDER = "PREPARE_INVOICE_REMINDER"
private const val CHANGE_TASK_PRIORITY = "CHANGE_TASK_PRIORITY"
private const val PERFORM_GLOBAL_ACTION = "PERFORM_GLOBAL_ACTION"
private const val LAUNCH_APP = "LAUNCH_APP"
private const val GENERATE_DAILY_BRIEF = "GENERATE_DAILY_BRIEF"
private const val GENERATE_EVENING_REVIEW = "GENERATE_EVENING_REVIEW"
