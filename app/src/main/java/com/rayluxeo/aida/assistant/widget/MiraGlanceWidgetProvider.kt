package com.rayluxeo.aida.assistant.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The "Mira — Today" glance provider. It holds no data and reads none: every callback hands off to
 * [WidgetRefreshScheduler], which runs [WidgetRefreshWorker] off the launcher's thread. A provider
 * that touched Room would be a disk read on the home screen, which is exactly the StrictMode sin this
 * split avoids.
 *
 * onReceive is deliberately NOT overridden. The Hilt Gradle plugin rewrites it to inject the field
 * and then run AppWidgetProvider's own dispatch, which calls [onUpdate]/[onEnabled]/[onDisabled]
 * below. Writing onReceive here — or calling super.onReceive from Kotlin — would fight that (§7.9).
 */
@AndroidEntryPoint
class MiraGlanceWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var refresh: WidgetRefreshScheduler

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refresh.refreshNow()
    }

    override fun onEnabled(context: Context) {
        // First instance placed: ensure the periodic refresh exists and draw once straight away.
        refresh.ensurePeriodic()
        refresh.refreshNow()
    }

    override fun onDisabled(context: Context) {
        // Last instance removed: stop the periodic work so nothing runs for a widget no one has.
        refresh.cancelPeriodic()
    }
}
