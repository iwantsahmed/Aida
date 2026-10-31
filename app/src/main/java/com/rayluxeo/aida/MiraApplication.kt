package com.rayluxeo.aida

import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler
import com.rayluxeo.aida.domain.brief.BriefScheduler
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.usecase.KeepRemindersArmedUseCase
import com.rayluxeo.aida.core.common.DispatcherProvider
import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rayluxeo.aida.assistant.widget.WidgetRefreshScheduler
import com.rayluxeo.aida.data.work.RecordTriggerWorkScheduler
import com.rayluxeo.aida.data.work.RecurringInvoiceWorkScheduler
import com.rayluxeo.aida.data.work.ReminderWorkScheduler
import com.rayluxeo.aida.voice.MiraEarconPlayer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiraApplication : Application(), Configuration.Provider {

    /**
     * Injected into the application rather than resolved where it is used, because the tone buffers
     * outlive every screen: they are built once and replayed, so that the activation tone lands on
     * the press rather than after it.
     */
    @Inject
    lateinit var earcons: MiraEarconPlayer

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sweeps: ReminderWorkScheduler

    @Inject
    lateinit var widgetRefresh: WidgetRefreshScheduler

    @Inject
    lateinit var recordTriggers: RecordTriggerWorkScheduler

    @Inject
    lateinit var recurringInvoices: RecurringInvoiceWorkScheduler

    @Inject
    lateinit var keepArmed: KeepRemindersArmedUseCase

    @Inject
    lateinit var reminders: ReminderRepository

    @Inject
    lateinit var prayerScheduler: PrayerScheduler

    @Inject
    lateinit var briefScheduler: BriefScheduler

    @Inject
    lateinit var alarmScheduler: AlarmClockScheduler

    @Inject
    lateinit var automationAlarms: AutomationAlarmScheduler

    @Inject
    lateinit var dispatchers: DispatcherProvider

    /**
     * WorkManager cannot build a `@HiltWorker` without this: the default factory calls a
     * no-argument constructor, and the worker has none. The manifest also removes WorkManager's
     * own startup initializer, because providing the configuration here means initialising it on
     * demand rather than letting `androidx.startup` do it first with the wrong factory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * The daily sweep is enqueued on every start, which is safe because it is unique work with
     * KEEP: enqueueing it again does not double it or reset its schedule. Doing it here rather
     * than from a screen is what makes it survive a reinstall and exist before Abu opens anything.
     */
    override fun onCreate() {
        super.onCreate()
        sweeps.ensureDailySweep()
        // Same reasoning as the sweep: enqueued on every start (unique, KEEP), so the glance keeps
        // refreshing across a reinstall and before Abu opens anything. A no-op when no widget is placed.
        widgetRefresh.ensurePeriodic()
        // The daily record-driven automation check, beside the reminder sweep for the same reasons.
        recordTriggers.ensureDailyCheck()
        // The daily recurring-invoice sweep — what turns a recurring profile into an actual monthly
        // draft without Abu opening the Recurring tab to tap "run now".
        recurringInvoices.ensureDailyCheck()

        // Watched for the life of the app. Also actively re-arms reminders, prayer alarms,
        // briefs, alarms, and automations on startup so they survive installs/updates even before
        // a reboot or permission toggle event occurs.
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            runCatching { reminders.rescheduleAll() }
            runCatching { prayerScheduler.reschedule() }
            runCatching { briefScheduler.rescheduleAll() }
            runCatching { alarmScheduler.rescheduleAll() }
            runCatching { automationAlarms.rescheduleAll() }
            keepArmed()
        }
    }

    /**
     * Mira gives her audio buffers back when her UI is no longer on screen.
     *
     * The three tones are static [android.media.AudioTrack] buffers held for instant replay, which
     * is the right trade while Abu is using her and the wrong one once he has gone elsewhere. They
     * are rebuilt on first use, so letting them go costs nothing but the next tone.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) earcons.release()
    }
}
