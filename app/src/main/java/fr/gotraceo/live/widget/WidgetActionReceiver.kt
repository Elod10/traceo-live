package fr.gotraceo.live.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.gotraceo.live.data.RefreshSchedule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Actions internes de l'application (alarmes de tick, filet de sécurité, actualisation au toucher,
 * notification balayée). Récepteur non exporté : seules l'application et ses PendingIntents peuvent
 * l'appeler, pas les autres applications installées.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val foreground = intent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val ids = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf(id) else WidgetUpdater.widgetIds(context)
                WidgetBroadcasts.runAsync(this, context, foreground, userAction = true) {
                    WidgetUpdater.refresh(context, ids, force = true)
                }
            }
            WidgetUpdater.ACTION_TICK -> WidgetBroadcasts.runAsync(this, context, foreground) {
                val ids = WidgetUpdater.widgetIds(context)
                val followed = AutoMode.followedNow(context, ids, System.currentTimeMillis())
                // Les autres d'abord : chaque réponse de Tracéo annonce le relevé suivant, et un widget
                // suivi servi avant eux repousserait leur échéance à chaque tick, sans qu'ils ne
                // demandent jamais rien.
                WidgetUpdater.refresh(context, ids.filter { it !in followed }.toIntArray(), force = false)
                // Les widgets qui suivent la position la relisent, puis actualisent leurs passages.
                followed.forEach { AutoMode.follow(context, it) }
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
        }
    }

    private companion object {
        const val TAG = "TRWidget"
    }
}

/** Exécution asynchrone partagée par les récepteurs des widgets, dans le délai accordé au broadcast. */
internal object WidgetBroadcasts {

    private const val TAG = "TRWidget"

    /** Une erreur imprévue est consignée : elle ne doit pas faire tomber le processus, et les ticks avec lui. */
    private val logErrors = CoroutineExceptionHandler { _, t -> Log.w(TAG, "Échec inattendu d'un traitement de widget", t) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + logErrors)

    /**
     * Exécute un traitement asynchrone dans le cadre du broadcast (goAsync), et rend la main à
     * Android avant qu'il ne s'impatiente, même si une requête réseau traîne : passé son délai, il
     * tue le processus sans prévenir, et avec lui tout ce qui était en cours.
     *
     * Le tick suivant est programmé avant le travail : si le processus meurt quand même en route,
     * il est déjà chez Android.
     *
     * @param foreground vrai si le broadcast porte FLAG_RECEIVER_FOREGROUND (budget de 10 s)
     * @param userAction vrai pour une actualisation demandée par l'usager, qu'Android attend plus vite
     */
    fun runAsync(
        receiver: BroadcastReceiver,
        context: Context,
        foreground: Boolean,
        userAction: Boolean = false,
        block: suspend () -> Unit,
    ) {
        val appContext = context.applicationContext
        WidgetUpdater.scheduleTick(appContext)
        val budgetMs = RefreshSchedule.broadcastBudgetMs(userAction, foreground)
        // Null hors d'un broadcast : rien à rendre alors.
        val pending: BroadcastReceiver.PendingResult? = receiver.goAsync()
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
}
