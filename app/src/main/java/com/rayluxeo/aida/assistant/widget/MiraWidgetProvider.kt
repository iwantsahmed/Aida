package com.rayluxeo.aida.assistant.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.rayluxeo.aida.R
import com.rayluxeo.aida.assistant.MiraShortcuts

/**
 * Mira Home Screen Widgets (Compact & Daily).
 */
class MiraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(
            mapOf(
                COMPACT to wired(context, R.layout.widget_mira_compact),
                FULL to wired(context, R.layout.widget_mira),
            ),
        )
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    private fun wired(context: Context, @LayoutRes layoutId: Int): RemoteViews =
        RemoteViews(context.packageName, layoutId).apply {
            MiraShortcuts.WidgetControl.entries.forEach { control ->
                setOnClickPendingIntent(
                    control.viewId,
                    MiraShortcuts.widgetPendingIntent(context, control),
                )
            }
        }

    private companion object {
        val COMPACT = SizeF(184f, 68f)
        val FULL = SizeF(184f, 133f)
    }
}
