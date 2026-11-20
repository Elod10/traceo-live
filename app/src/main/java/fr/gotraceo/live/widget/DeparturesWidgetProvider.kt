package fr.gotraceo.live.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import fr.gotraceo.live.data.Prefs

/** Widget « Prochains passages » : entrée automatique, et base des formats de [WidgetProviders]. */
open class DeparturesWidgetProvider : AppWidgetProvider() {

    /** Vrai si Android attend la fin de ce broadcast en 10 s au lieu de 60 (cf. [fr.gotraceo.live.data.RefreshSchedule.broadcastBudgetMs]). */
    private var foreground = false

    override fun onReceive(context: Context, intent: Intent) {
        foreground = intent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.render(context, it) }
        WidgetBroadcasts.runAsync(this, context, foreground) {
            WidgetUpdater.refresh(context, appWidgetIds, force = false)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        WidgetUpdater.render(context, appWidgetId)
        // Un widget À proximité agrandi affiche plus de lignes : il faut leurs passages.
        if (AutoMode.wantsMoreLines(context, appWidgetId)) {
            WidgetBroadcasts.runAsync(this, context, foreground) {
                WidgetUpdater.refresh(context, intArrayOf(appWidgetId), force = true)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = Prefs(context)
        appWidgetIds.forEach {
            prefs.deleteWidget(it)
            PassageNotifications.cancel(context, it)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        // Appelé pour chaque format retiré : d'autres widgets peuvent encore avoir besoin du tick.
        WidgetUpdater.scheduleTick(context)
    }
}
