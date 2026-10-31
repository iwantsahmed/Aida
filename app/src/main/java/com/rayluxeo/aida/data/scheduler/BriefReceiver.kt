package com.rayluxeo.aida.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefNotificationText
import com.rayluxeo.aida.domain.brief.BriefNotificationWording
import com.rayluxeo.aida.domain.brief.BriefScheduleRepository
import com.rayluxeo.aida.domain.brief.BriefScheduler
import com.rayluxeo.aida.domain.brief.DailyBriefEngine
import com.rayluxeo.aida.domain.brief.EveningReviewEngine
import com.rayluxeo.aida.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where a scheduled brief lands.
 *
 * Unlike [ReminderReceiver] nothing can be posted straight away: a brief only exists once the
 * engine has read Abu's records, so the whole of the work happens under [goAsync].
 *
 * The alarm is re-armed for tomorrow whatever the engine returns, including a failure. An alarm
 * that fires once and is never replaced is the defect this feature would die of, and it would die
 * silently — Abu would simply stop hearing from Mira in the morning and have nothing to report.
 */
@AndroidEntryPoint
class BriefReceiver : BroadcastReceiver() {

    @Inject lateinit var dailyBrief: DailyBriefEngine

    @Inject lateinit var eveningReview: EveningReviewEngine

    @Inject lateinit var wording: BriefNotificationWording

    @Inject lateinit var notifications: MiraNotifications

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var schedules: BriefScheduleRepository

    @Inject lateinit var scheduler: BriefScheduler

    @Inject lateinit var clock: MiraClock

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val kind = kindOf(intent) ?: return

        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:brief_receiver")?.apply {
            acquire(30_000L)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                post(kind)
            } finally {
                // Nested so that neither the re-arm nor the finish can be lost to a failure in
                // the other: the broadcast must be released even if the alarm cannot be set, and
                // tomorrow's brief must be armed even if building today's threw.
                try {
                    scheduler.apply(schedules.current())
                } finally {
                    pending.finish()
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
            }
        }
    }

    private suspend fun post(kind: BriefKind) {
        val language = unpromptedLanguage(settings.current().languageMode)
        notifications.showBrief(kind, text(kind, language), wording.channel(language))
    }

    /**
     * A failure becomes a short "could not look" line rather than silence. Mira going quiet on a
     * morning she could not read the database is indistinguishable from a quiet day, and those are
     * two different facts.
     */
    private suspend fun text(kind: BriefKind, language: SpokenLanguage): BriefNotificationText =
        when (kind) {
            BriefKind.MORNING -> when (val report = dailyBrief.today()) {
                is AppResult.Success -> wording.morning(report.value, language)
                is AppResult.Failure -> wording.unavailable(kind, language)
            }

            BriefKind.EVENING -> when (val report = eveningReview.review(clock.today())) {
                is AppResult.Success -> wording.evening(report.value, language)
                is AppResult.Failure -> wording.unavailable(kind, language)
            }
        }

    /** An unreadable extra is dropped rather than guessed at: a brief of the wrong half of the day is worse than none. */
    private fun kindOf(intent: Intent): BriefKind? {
        val name = intent.getStringExtra(EXTRA_KIND) ?: return null
        return BriefKind.entries.firstOrNull { it.name == name }
    }

    companion object {
        const val ACTION_FIRE = "com.rayluxeo.aida.action.BRIEF_FIRE"
        const val EXTRA_KIND = "com.rayluxeo.aida.extra.BRIEF_KIND"
    }
}
