package com.rayluxeo.aida.data.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.rayluxeo.aida.MainActivity
import com.rayluxeo.aida.R
import com.rayluxeo.aida.assistant.MiraShortcuts
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefNotificationText
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.SalahNotificationWording
import com.rayluxeo.aida.domain.scheduler.ReminderNotificationAction
import com.rayluxeo.aida.domain.scheduler.ReminderNotificationWording
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mira's only notification surface, over seven channels: the reminders Abu asked her to hold, the
 * briefs he asked her to send, the alarms he set, the timers he started, and — for Salah — the
 * prayer times, the gentle after-prayer check-ins, and the Tahajjud night reminder.
 *
 * They are separate channels so that silencing one leaves the others alone. A morning brief is not
 * worth losing a reminder over, an alarm clock must still be able to wake him on a morning he has
 * muted everything else, and a working-day check-in is a different promise from the prayer time it
 * follows — so each prayer surface is its own channel he can quiet on its own.
 *
 * A channel is created on every post because `createNotificationChannel` is idempotent for an
 * unchanged id — cheaper than tracking whether the app has run since install, and correct after
 * the user clears app data.
 *
 * If POST_NOTIFICATIONS has not been granted the platform drops the post silently; nothing here
 * throws, and Settings is where that state is surfaced to Abu.
 *
 * **The sound comes from the channel, never from Mira.** Android 17 hardened background playback:
 * audio started from a non-foreground state fails silently and an audio-focus request is refused
 * outright. An alarm going off is exactly that case, so the alarm tone is attached to the channel
 * and the system plays it. Anything here that reached for a MediaPlayer would be silent on the
 * one morning it mattered.
 */
@Singleton
class MiraNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wording: ReminderNotificationWording,
    private val salahWording: SalahNotificationWording,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) {

    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * [labels] carry the channel's name and description in Abu's language. Android draws both in
     * its own settings screen beside the briefs channel, so an English name here would be a seam
     * he can see. Creating a channel twice is a no-op, which is what makes this safe to call on
     * every post.
     */
    fun ensureChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = labels.body
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the reminder, with the buttons its [kind] can honestly offer.
     *
     * [title] may be blank — a reminder fires from an intent extra so that a database which will
     * not open cannot stop it, and an extra can be missing. Naming it is done here rather than at
     * the receiver because this is where Abu's language is known, and a title chosen without the
     * language is English on a Bengali phone. It falls back to what the reminder is about
     * ("Task", "Invoice"), and for a plain one-time reminder to the name Android already gives the
     * channel it arrives on.
     *
     * [language] is optional because the caller usually has it already, having just read it to
     * name the channel. When it is not passed Mira reads Abu's setting herself rather than
     * defaulting to English: a notification whose buttons are in the wrong language is a defect no
     * caller should be able to cause by forgetting an argument.
     *
     * [linkedTaskId] decides whether Complete appears at all. A task reminder whose task has been
     * deleted has nothing left to mark done, and a button that cannot work is worse than no button.
     */
    suspend fun showReminder(
        reminderId: Long,
        title: String,
        labels: BriefNotificationText,
        kind: ReminderKind = ReminderKind.ONE_TIME,
        linkedTaskId: Long? = null,
        linkedInvoiceId: Long? = null,
        linkedClientId: Long? = null,
        language: SpokenLanguage? = null,
    ) {
        // Abu can switch off a whole category of reminder in Settings. A category he has silenced
        // is not posted at all — the channel stays, so turning it back on needs no re-grant.
        if (!isCategoryEnabled(kind)) return
        ensureChannel(labels)
        val spoken = language ?: abusLanguage()
        val userName = abusName()
        val notificationId = reminderId.toInt()
        val onTap = openDeepLinkTarget(notificationId, linkedTaskId, linkedInvoiceId, linkedClientId)
        val kindLine = wording.kindLine(kind, spoken)
        val shownTitle = title.trim().ifEmpty { kindLine ?: labels.title }
        val inQuietHours = isQuietHoursNow()
        val priority = if (inQuietHours) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(shownTitle)
            .setContentIntent(onTap)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(priority)
            .setAutoCancel(true)

        if (userName.isNotEmpty()) {
            builder.setSubText(userName)
        }

        if (inQuietHours) {
            builder.setSound(null)
        }
        kindLine?.takeIf { it != shownTitle }?.let { builder.setContentText(it) }

        val actions = ReminderNotificationAction
            .forKind(kind, hasLinkedTask = linkedTaskId != null)
            .take(ReminderNotificationAction.MAX_SHOWN)
        for (action in actions) {
            builder.addAction(
                NO_ACTION_ICON,
                wording.actionLabel(action, spoken),
                reminderActionIntent(action, reminderId, linkedTaskId, notificationId, onTap),
            )
        }
        notifySafely(notificationId, builder.build())
    }

    /**
     * Whether Abu still wants this kind of reminder. Tasks, invoices and subscriptions each have
     * their own switch; every other reminder kind rides the general "Reminders" switch, which is
     * the one a plain one-time or recurring reminder answers to.
     */
    private suspend fun isCategoryEnabled(kind: ReminderKind): Boolean {
        val s = settings.current()
        return when (kind) {
            ReminderKind.TASK -> s.notifyTasks
            ReminderKind.INVOICE -> s.notifyInvoices
            ReminderKind.SUBSCRIPTION -> s.notifySubscriptions
            ReminderKind.ONE_TIME,
            ReminderKind.RECURRING,
            ReminderKind.CLIENT_FOLLOW_UP,
            ReminderKind.BIRTHDAY,
            ReminderKind.PROJECT_DEADLINE,
            ReminderKind.PLACE,
            -> s.notifyReminders
        }
    }

    suspend fun isQuietHoursNow(): Boolean {
        val s = settings.current()
        if (!s.quietHoursEnabled) return false
        val now = clock.localNow().toLocalTime()
        val nowMinute = now.hour * 60 + now.minute
        val start = s.quietHoursStartMinute
        val end = s.quietHoursEndMinute
        return if (start < end) {
            nowMinute in start until end
        } else {
            nowMinute >= start || nowMinute < end
        }
    }

    /**
     * An alarm that is sounding now.
     *
     * Ongoing and not auto-cancelling, because swiping an alarm away by accident while reaching
     * for the shade is how a person ends up late. Dismiss is a button, so ending it is deliberate.
     */
    suspend fun showAlarm(alarmId: Long, label: String, language: SpokenLanguage? = null) {
        val spoken = language ?: abusLanguage()
        val userName = abusName()
        ensureAlarmChannel(wording.alarmChannel(spoken))
        val text = wording.alarm(label, spoken)
        val notificationId = alarmNotificationId(alarmId)
        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(text.title)
            .setContentIntent(openDay(notificationId))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                NO_ACTION_ICON,
                wording.actionLabel(ReminderNotificationAction.SNOOZE_TEN_MINUTES, spoken),
                alarmSnoozeIntent(alarmId, notificationId),
            )
            .addAction(NO_ACTION_ICON, wording.dismiss(spoken), dismissIntent(notificationId))
        if (userName.isNotEmpty()) builder.setSubText(userName)
        if (text.body.isNotEmpty()) builder.setContentText(text.body)
        notifySafely(notificationId, builder.build())
    }

    /**
     * A countdown that has run out. [ranOutWhileOff] is the reboot case, where Mira is reporting
     * it late and says so — a timer that just ended and one Abu missed an hour ago are different
     * facts, and he acts differently on each.
     */
    suspend fun showTimerFinished(
        timerId: Long,
        label: String,
        ranOutWhileOff: Boolean = false,
        language: SpokenLanguage? = null,
    ) {
        val spoken = language ?: abusLanguage()
        val userName = abusName()
        ensureTimerChannel(wording.timerChannel(spoken))
        val text = wording.timerFinished(label, ranOutWhileOff, spoken)
        val notificationId = TimerCodes.notificationId(timerId)
        val builder = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(text.title)
            .setContentIntent(openDay(notificationId))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(NO_ACTION_ICON, wording.dismiss(spoken), dismissIntent(notificationId))
        if (userName.isNotEmpty()) builder.setSubText(userName)
        if (text.body.isNotEmpty()) builder.setContentText(text.body)
        notifySafely(notificationId, builder.build())
    }

    /**
     * A prayer's time has arrived. [language] is optional because the caller usually has it in hand
     * already; when it is absent Mira reads Abu's setting rather than defaulting to English, so a
     * forgotten argument can never post a prayer notification in the wrong language.
     *
     * It states the fact and nothing more — "It's time for the Asr prayer." — and carries no
     * action: a prayer time is an announcement, and whether it was prayed is asked later by the
     * check-in, never implied here.
     */
    suspend fun showPrayerTime(prayer: Prayer, language: SpokenLanguage? = null) {
        val spoken = language ?: abusLanguage()
        val userName = abusName()
        ensurePrayerTimeChannel(salahWording.prayerTimeChannel(spoken))
        val notificationId = PrayerCodes.prayerTimeNotificationId(prayer)
        val baseTitle = salahWording.prayerTimeTitle(prayer, spoken)
        val title = if (userName.isNotEmpty()) "$baseTitle · $userName" else baseTitle
        val baseBody = salahWording.prayerTimeBody(prayer, spoken)
        val body = if (userName.isNotEmpty()) "$userName, $baseBody" else baseBody

        val hadith = com.rayluxeo.aida.domain.salah.WaqtHadithProvider.forPrayer(prayer)
        val expandedText = buildString {
            append(body)
            append("\n\n")
            append("«").append(hadith.arabic).append("»")
            append("\n\n")
            append(hadith.bangla)
            append("\n— ").append(hadith.reference)
        }

        val builder = NotificationCompat.Builder(context, PRAYER_TIME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(expandedText)
                    .setSummaryText(hadith.reference),
            )
            .setContentIntent(openDay(notificationId))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (userName.isNotEmpty()) builder.setSubText(userName)
        notifySafely(notificationId, builder.build())
    }

    /**
     * The gentle after-prayer ask, some minutes past the time. Its three buttons — Yes, Not yet,
     * Remind me — are the whole of it: the question is the title, never an accusation, and [date] is
     * folded into its notification id so an ask left unanswered yesterday is not replaced by today's.
     *
     * The buttons are broadcasts to [PrayerActionReceiver], so answering from the shade logs the
     * prayer and leaves, rather than dragging Mira open. "Not yet" logs the in-between state, never
     * a miss — declining to say yes is not an admission of failure (Abu's instruction).
     */
    suspend fun showPrayerCheckIn(prayer: Prayer, date: LocalDate, language: SpokenLanguage? = null) {
        val spoken = language ?: abusLanguage()
        val userName = abusName()
        ensureCheckInChannel(salahWording.checkInChannel(spoken))
        val notificationId = PrayerCodes.checkInNotificationId(prayer, date)
        val baseQuestion = salahWording.checkInQuestion(prayer, spoken)
        val question = if (userName.isNotEmpty()) "$userName, $baseQuestion" else baseQuestion
        val builder = NotificationCompat.Builder(context, PRAYER_CHECKIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(question)
            .setContentIntent(openDay(notificationId))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(
                NO_ACTION_ICON,
                salahWording.yes(spoken),
                checkInActionIntent(PrayerActionReceiver.ACTION_YES, prayer, date, notificationId, CHECK_IN_YES_SLOT),
            )
            .addAction(
                NO_ACTION_ICON,
                salahWording.notYet(spoken),
                checkInActionIntent(PrayerActionReceiver.ACTION_NOT_YET, prayer, date, notificationId, CHECK_IN_NOT_YET_SLOT),
            )
            .addAction(
                NO_ACTION_ICON,
                salahWording.remindMe(spoken),
                checkInActionIntent(PrayerActionReceiver.ACTION_REMIND, prayer, date, notificationId, CHECK_IN_REMIND_SLOT),
            )
        if (userName.isNotEmpty()) builder.setSubText(userName)
        notifySafely(notificationId, builder.build())
    }

    /**
     * The Tahajjud window has begun. It rides the alarm-sound channel so it can wake Abu — the sound
     * is the channel's, never Mira's, for the Android 17 reason set out on this class — and it is
     * offered, never demanded. [windowLabel] is Fajr in `HH:mm`, so Mira can say how long the window
     * lasts; it may be blank, and then she says only that the window has opened.
     */
    suspend fun showTahajjud(windowLabel: String, language: SpokenLanguage? = null) {
        val spoken = language ?: abusLanguage()
        val userName = abusName()
        ensureTahajjudChannel(salahWording.tahajjudChannel(spoken))
        val notificationId = PrayerCodes.tahajjudNotificationId()
        val baseTitle = salahWording.tahajjudTitle(spoken)
        val title = if (userName.isNotEmpty()) "$baseTitle · $userName" else baseTitle
        val baseBody = salahWording.tahajjudBody(windowLabel, spoken)
        val body = if (userName.isNotEmpty()) "$userName, $baseBody" else baseBody
        val builder = NotificationCompat.Builder(context, TAHAJJUD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openDay(notificationId))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
        if (userName.isNotEmpty()) builder.setSubText(userName)
        notifySafely(notificationId, builder.build())
    }

    /**
     * Takes a notification off the shade.
     *
     * Acting on a notification has to remove it, or Complete leaves a card behind still claiming
     * the task is due.
     */
    fun cancel(notificationId: Int) = manager.cancel(notificationId)

    /**
     * Posts a notification safely, ignoring any SecurityException if the POST_NOTIFICATIONS
     * permission was revoked at runtime, or any OEM-specific manager exception.
     */
    private fun notifySafely(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted or revoked
        } catch (_: Exception) {
            // OEM or platform failure
        }
    }

    /**
     * Briefs get a channel of their own so Abu can silence the morning one without losing a
     * reminder he asked for, and at IMPORTANCE_DEFAULT rather than HIGH: a brief is Mira
     * reporting, not Mira interrupting, and it has no business taking over the screen.
     *
     * [labels] carries the channel's name and description in Abu's language — the words Android
     * shows him in system settings, which is a user-facing path like any other.
     */
    private fun ensureBriefChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            BRIEF_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = labels.body
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The alarm channel carries the device's own alarm tone with `USAGE_ALARM`, which is what puts
     * it on the alarm volume stream and past Do Not Disturb's ordinary filtering. The sound is the
     * channel's rather than Mira's on purpose — see the note on this class about Android 17.
     */
    private fun ensureAlarmChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = labels.body
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio())
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Timers sound like alarms but are their own channel, because a working afternoon of Pomodoro
     * timers and the alarm that has to wake Abu tomorrow are not the same promise, and he must be
     * able to silence one without the other.
     */
    private fun ensureTimerChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            TIMER_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = labels.body
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio())
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The prayer-times channel. IMPORTANCE_HIGH with vibration, because a prayer's time arriving is
     * a thing Abu asked to be told about promptly, not a quiet status line. Its own channel so he
     * can tune or silence it without touching the check-ins or his reminders.
     */
    private fun ensurePrayerTimeChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            PRAYER_TIME_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = labels.body
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The check-ins channel, at IMPORTANCE_DEFAULT rather than HIGH: the gentle after-prayer ask is
     * Mira enquiring, not interrupting, and it has no business taking over the screen.
     */
    private fun ensureCheckInChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            PRAYER_CHECKIN_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = labels.body
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The Tahajjud channel carries the device's alarm tone with `USAGE_ALARM`, the same as the
     * alarm clock, because its one job is to wake Abu in the night when he has asked to be woken.
     * The sound is the channel's rather than Mira's on purpose — see the note on this class about
     * Android 17 — and it sits past Do Not Disturb's ordinary filtering for the same reason an
     * alarm does.
     */
    private fun ensureTahajjudChannel(labels: BriefNotificationText) {
        val channel = NotificationChannel(
            TAHAJJUD_CHANNEL_ID,
            labels.title,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = labels.body
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio())
        }
        manager.createNotificationChannel(channel)
    }

    private fun alarmAudio(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * One notification per [kind], replaced rather than stacked: yesterday's morning brief is of
     * no use beside today's.
     */
    fun showBrief(kind: BriefKind, text: BriefNotificationText, channel: BriefNotificationText) {
        ensureBriefChannel(channel)
        val notification = NotificationCompat.Builder(context, BRIEF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            // The brief is several lines and the collapsed row shows one, so it is expandable:
            // a truncated brief would send Abu into the app to read what Mira already knew.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(openHome(kind))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notifySafely(notificationId(kind), notification)
    }

    private suspend fun abusLanguage(): SpokenLanguage =
        unpromptedLanguage(settings.current().languageMode)

    private suspend fun abusName(): String =
        settings.current().userName.trim()

    /**
     * Complete and Snooze are broadcasts, not activities: acting from the shade must do the thing
     * and leave, rather than dragging Mira open over whatever Abu was doing. Open and Ask Mira are
     * the two that genuinely want the app.
     */
    private fun reminderActionIntent(
        action: ReminderNotificationAction,
        reminderId: Long,
        linkedTaskId: Long?,
        notificationId: Int,
        onTap: PendingIntent,
    ): PendingIntent = when (action) {
        ReminderNotificationAction.COMPLETE -> broadcast(
            action = NotificationActionReceiver.ACTION_COMPLETE,
            requestCode = requestCode(notificationId, action.ordinal),
        ) {
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, linkedTaskId ?: 0L)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        ReminderNotificationAction.SNOOZE_TEN_MINUTES -> snoozeIntent(
            reminderId = reminderId,
            minutes = SnoozeReminderUseCase.SHORT.toMinutes(),
            notificationId = notificationId,
            slot = action.ordinal,
        )

        ReminderNotificationAction.SNOOZE_ONE_HOUR -> snoozeIntent(
            reminderId = reminderId,
            minutes = SnoozeReminderUseCase.LONG.toMinutes(),
            notificationId = notificationId,
            slot = action.ordinal,
        )

        ReminderNotificationAction.OPEN -> onTap

        // Bare: the talk intent is read for its action alone, by the navigation shell, which
        // switches to Mira. Ids put here would be written and never looked at.
        ReminderNotificationAction.ASK_MIRA -> PendingIntent.getActivity(
            context,
            requestCode(notificationId, action.ordinal),
            MiraShortcuts.talkIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun snoozeIntent(
        reminderId: Long,
        minutes: Long,
        notificationId: Int,
        slot: Int,
    ): PendingIntent = broadcast(
        action = NotificationActionReceiver.ACTION_SNOOZE,
        requestCode = requestCode(notificationId, slot),
    ) {
        putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
        putExtra(NotificationActionReceiver.EXTRA_MINUTES, minutes)
        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
    }

    private fun alarmSnoozeIntent(alarmId: Long, notificationId: Int): PendingIntent = broadcast(
        action = NotificationActionReceiver.ACTION_SNOOZE_ALARM,
        requestCode = requestCode(notificationId, ALARM_SNOOZE_SLOT),
    ) {
        putExtra(NotificationActionReceiver.EXTRA_ALARM_ID, alarmId)
        putExtra(NotificationActionReceiver.EXTRA_MINUTES, SnoozeReminderUseCase.SHORT.toMinutes())
        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
    }

    private fun dismissIntent(notificationId: Int): PendingIntent = broadcast(
        action = NotificationActionReceiver.ACTION_DISMISS,
        requestCode = requestCode(notificationId, DISMISS_SLOT),
    ) {
        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
    }

    private fun broadcast(
        action: String,
        requestCode: Int,
        extras: Intent.() -> Unit,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, NotificationActionReceiver::class.java).setAction(action).apply(extras),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * One check-in button, a broadcast to [PrayerActionReceiver]. The three buttons share a
     * notification id and so must be spread by [slot]: a PendingIntent is identified by its request
     * code and intent but **not its extras**, so without a slot of its own the second button would
     * replace the first and both would answer with the same thing. The target is a different
     * receiver from the reminder actions, so these codes cannot collide with those.
     */
    private fun checkInActionIntent(
        action: String,
        prayer: Prayer,
        date: LocalDate,
        notificationId: Int,
        slot: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(notificationId, slot),
        Intent(context, PrayerActionReceiver::class.java).setAction(action).apply {
            putExtra(PrayerActionReceiver.EXTRA_PRAYER, prayer.name)
            putExtra(PrayerActionReceiver.EXTRA_EPOCH_DAY, date.toEpochDay())
            putExtra(PrayerActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openDeepLinkTarget(
        notificationId: Int,
        linkedTaskId: Long? = null,
        linkedInvoiceId: Long? = null,
        linkedClientId: Long? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when {
                linkedTaskId != null -> {
                    action = MiraShortcuts.ACTION_NEW_TASK
                    putExtra(MiraShortcuts.EXTRA_TARGET_ID, linkedTaskId)
                }
                linkedInvoiceId != null -> {
                    action = MiraShortcuts.ACTION_NEW_INVOICE
                    putExtra(MiraShortcuts.EXTRA_TARGET_ID, linkedInvoiceId)
                }
                linkedClientId != null -> {
                    action = MiraShortcuts.ACTION_CLIENT
                    putExtra(MiraShortcuts.EXTRA_TARGET_ID, linkedClientId)
                }
                else -> {
                    action = MiraShortcuts.ACTION_TODAY
                }
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode(notificationId, OPEN_SLOT),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Where a tap lands: the day on Home.
     *
     * A reminder opens there because the task, the invoice, the deadline and the follow-up one can
     * be about all surface on the day; an alarm or a finished timer has no record behind it at all.
     * The intent carries its action and nothing else, because [MainActivity] reads only the action
     * and hands it to the navigation shell.
     */
    private fun openDay(notificationId: Int): PendingIntent = openDeepLinkTarget(notificationId)

    /**
     * A brief is about the day, so it opens the day. The action is the one the launcher shortcut
     * already uses, which the navigation shell reads and switches to Home for — without it a tap
     * would drop Abu wherever he left Mira last.
     */
    private fun openHome(kind: BriefKind): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(kind),
        Intent(context, MainActivity::class.java).apply {
            action = MiraShortcuts.ACTION_TODAY
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Negative, because reminder notifications are keyed on a Room id and those are positive. */
    private fun notificationId(kind: BriefKind): Int = when (kind) {
        BriefKind.MORNING -> MORNING_NOTIFICATION_ID
        BriefKind.EVENING -> EVENING_NOTIFICATION_ID
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "mira_reminders"
        const val ALARM_CHANNEL_ID = "mira_alarms"
        const val TIMER_CHANNEL_ID = "mira_timers"

        const val PRAYER_TIME_CHANNEL_ID = "mira_prayer_times"
        const val PRAYER_CHECKIN_CHANNEL_ID = "mira_prayer_checkin"
        const val TAHAJJUD_CHANNEL_ID = "mira_tahajjud"

        private const val BRIEF_CHANNEL_ID = "mira_briefs"
        private const val MORNING_NOTIFICATION_ID = -101
        private const val EVENING_NOTIFICATION_ID = -102

        /**
         * Far below the timer ids, which start at -1000 and count down, so the two spaces cannot
         * meet until Abu has set two million alarms.
         */
        private const val ALARM_NOTIFICATION_ID_BASE = -2_000_000L

        /**
         * A PendingIntent is identified by its request code and its intent, and **extras are not
         * part of that identity**. Two snoozes on one reminder differ only by their extras, so
         * without a slot of their own the second would quietly replace the first and both buttons
         * would push the reminder away by the same span.
         *
         * Slots are spread by [SLOTS_PER_NOTIFICATION], which bounds the safe notification id at
         * roughly 268 million — far past any id Room will hand out on one phone.
         */
        private const val SLOTS_PER_NOTIFICATION = 8

        /** Beyond the action ordinals, so neither can collide with the other. */
        private const val OPEN_SLOT = 5
        private const val ALARM_SNOOZE_SLOT = 6
        private const val DISMISS_SLOT = 7

        /**
         * The three check-in buttons' slots. They need only be distinct from each other — the
         * intents target [PrayerActionReceiver], a different component from the reminder actions
         * above, so they share no PendingIntent identity with them whatever the slot.
         */
        private const val CHECK_IN_YES_SLOT = 0
        private const val CHECK_IN_NOT_YET_SLOT = 1
        private const val CHECK_IN_REMIND_SLOT = 2

        /** Android draws an action's label without its icon on every template Mira uses. */
        private const val NO_ACTION_ICON = 0

        fun alarmNotificationId(alarmId: Long): Int = (ALARM_NOTIFICATION_ID_BASE - alarmId).toInt()

        private fun requestCode(notificationId: Int, slot: Int): Int =
            notificationId * SLOTS_PER_NOTIFICATION + slot
    }
}
