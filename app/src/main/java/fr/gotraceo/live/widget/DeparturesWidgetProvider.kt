package fr.gotraceo.live.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.RefreshSchedule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Widget « Prochains passages » : entrée automatique, et base des formats de [WidgetProviders]. */
open class DeparturesWidgetProvider : AppWidgetProvider() {

    /** Vrai si Android attend la fin de ce broadcast en 10 s au lieu de 60 (cf. [RefreshSchedule.broadcastBudgetMs]). */
    private var foreground = false

    override fun onReceive(context: Context, intent: Intent) {
        foreground = intent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val ids = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf(id) else WidgetUpdater.widgetIds(context)
                runAsync(context, userAction = true) {
                    WidgetUpdater.refresh(context, ids, force = true)
                }
            }
            WidgetUpdater.ACTION_TICK -> runAsync(context) {
                val ids = WidgetUpdater.widgetIds(context)
                // Les widgets qui suivent la position la relisent d'abord, puis actualisent leurs passages.
                val followed = AutoMode.followedNow(context, ids, System.currentTimeMillis())
                followed.forEach { AutoMode.follow(context, it) }
                WidgetUpdater.refresh(context, ids.filter { it !in followed }.toIntArray(), force = false)
            }
            // Le filet de sécurité n'arrive que si rien n'a reprogrammé le tick depuis plusieurs minutes :
            // au réveil de l'appareil, en même temps que le tick en retard, ou parce que le tick s'est perdu.
            // Dans les deux cas il suffit de le reprogrammer : le travail, c'est le tick qui le fait.
            WidgetUpdater.ACTION_WATCHDOG -> {
                Log.i(TAG, "Filet de sécurité : aucun tick reprogrammé depuis ${RefreshSchedule.WATCHDOG_INTERVAL_MS / 60_000} min, il l'est à nouveau")
                WidgetUpdater.scheduleTick(context)
            }
            // Notification balayée : elle ne revient pas à l'actualisation suivante.
            PassageNotifications.ACTION_DISMISSED -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) PassageNotifications.onDismissed(context, id)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.render(context, it) }
        runAsync(context) {
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
            runAsync(context) { WidgetUpdater.refresh(context, intArrayOf(appWidgetId), force = true) }
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

    /**
     * Exécute un traitement asynchrone dans le cadre du broadcast (goAsync), et rend la main à
     * Android avant qu'il ne s'impatiente, même si une requête réseau traîne : passé son délai, il
     * tue le processus sans prévenir, et avec lui tout ce qui était en cours.
     *
     * Le tick suivant est programmé avant le travail : si le processus meurt quand même en route,
     * il est déjà chez Android.
     *
     * @param userAction vrai pour une actualisation demandée par l'usager, qu'Android attend plus vite
     */
    private fun runAsync(context: Context, userAction: Boolean = false, block: suspend () -> Unit) {
        val appContext = context.applicationContext
        WidgetUpdater.scheduleTick(appContext)
        val budgetMs = RefreshSchedule.broadcastBudgetMs(userAction, foreground)
        // Null hors d'un broadcast : rien à rendre alors.
        val pending: BroadcastReceiver.PendingResult? = goAsync()
        val work = scope.async {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "Échec de l'actualisation", t)
            }
        }
        scope.launch {
            try {
                if (withTimeoutOrNull(budgetMs) { work.await() } == null) {
                    // Le travail continue, mais Android peut désormais figer le processus : les widgets
                    // ne restent pas sur « … » en l'attendant.
                    Log.w(TAG, "Actualisation plus longue que les $budgetMs ms accordés : elle se poursuit hors du broadcast")
                    WidgetUpdater.settle(appContext)
                }
            } finally {
                try {
                    WidgetUpdater.scheduleTick(appContext)
                } finally {
                    pending?.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "TRWidget"

        /** Une erreur imprévue est consignée : elle ne doit pas faire tomber le processus, et les ticks avec lui. */
        private val logErrors = CoroutineExceptionHandler { _, t -> Log.w(TAG, "Échec inattendu d'un traitement de widget", t) }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + logErrors)
    }
}
