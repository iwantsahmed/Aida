package com.rayluxeo.aida.assistant.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rayluxeo.aida.R
import com.rayluxeo.aida.assistant.MiraShortcuts
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.usecase.GetWidgetGlanceUseCase
import com.rayluxeo.aida.ui.response.WidgetGlanceLines
import com.rayluxeo.aida.ui.response.widgetGlanceLines
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Binds the live "Mira — Today" glance. The provider never reads Room; all of it happens here, off
 * the launcher's thread, so the home screen is never the place a disk read lands (StrictMode-clean).
 *
 * It reads the glance through [GetWidgetGlanceUseCase] (the same brief Home uses), words it in Abu's
 * language, and honours the vault: when Money or Invoices is locked, the money line is suppressed so
 * a figure the lock exists to hide is not left sitting on an unlocked home screen (PRIVACY.md).
 *
 * It never returns [Result.failure] — that outcome gives up for good, and nothing here is permanently
 * wrong: a read that failed this minute will succeed the next, and the widget keeps its last good
 * numbers in the meantime rather than blanking. When nothing is placed it does no read at all, so an
 * unused widget costs nothing.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val getWidgetGlance: GetWidgetGlanceUseCase,
    private val settings: SettingsRepository,
    private val timeFormatter: TaskTimeFormatter,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        refresh()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unexpected: Exception) {
        // A read threw where it returns failures as values; a bad minute, never a dead widget.
        Result.retry()
    }

    private suspend fun refresh(): Result {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, MiraGlanceWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return Result.success()

        val prefs = settings.current()
        val language = prefs.languageMode.displayLanguage()
        val includeMoney = !(prefs.protectMoney || prefs.protectInvoices)

        val glance = when (val result = getWidgetGlance()) {
            is AppResult.Failure -> return Result.success()
            is AppResult.Success -> result.value
        }

        val lines = widgetGlanceLines(
            glance = glance,
            language = language,
            includeMoney = includeMoney,
            momentLabel = { instant -> timeFormatter.momentLabel(instant, language) },
        )

        val views = RemoteViews(
            mapOf(
                COMPACT to render(R.layout.widget_glance_compact, lines, compact = true),
                FULL to render(R.layout.widget_glance, lines, compact = false),
            ),
        )
        ids.forEach { id -> manager.updateAppWidget(id, views) }
        return Result.success()
    }

    private fun render(@LayoutRes layout: Int, lines: WidgetGlanceLines, compact: Boolean): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_glance_tasks, lines.tasks)
            bindLine(R.id.widget_glance_money, lines.money)
            setOnClickPendingIntent(
                R.id.widget_glance_body,
                MiraShortcuts.entryPendingIntent(context, MiraShortcuts.Entry.TODAY),
            )
            if (!compact) {
                bindLine(R.id.widget_glance_event, lines.nextEvent)
                bindLine(R.id.widget_glance_reminder, lines.nextReminder)
                wireButton(R.id.widget_glance_talk, MiraShortcuts.Entry.TALK)
                wireButton(R.id.widget_glance_task, MiraShortcuts.Entry.NEW_TASK)
                wireButton(R.id.widget_glance_ink, MiraShortcuts.Entry.QUICK_INK)
            }
        }

    private fun RemoteViews.bindLine(@IdRes id: Int, text: String?) {
        if (text.isNullOrBlank()) {
            setViewVisibility(id, View.GONE)
        } else {
            setTextViewText(id, text)
            setViewVisibility(id, View.VISIBLE)
        }
    }

    private fun RemoteViews.wireButton(@IdRes id: Int, entry: MiraShortcuts.Entry) {
        setOnClickPendingIntent(id, MiraShortcuts.entryPendingIntent(context, entry))
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "com.rayluxeo.aida.work.WIDGET_REFRESH_PERIODIC"
        const val UNIQUE_ONESHOT_WORK_NAME = "com.rayluxeo.aida.work.WIDGET_REFRESH_ONESHOT"

        /** Below this height the launcher draws the compact layout; at or above it, the full one. */
        private val COMPACT = SizeF(180f, 72f)
        private val FULL = SizeF(180f, 116f)
    }
}
