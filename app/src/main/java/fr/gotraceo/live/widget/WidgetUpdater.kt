package fr.gotraceo.live.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.PassageRoute
import fr.gotraceo.live.data.PassageSnapshot
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.RefreshSchedule
import fr.gotraceo.live.data.WidgetStates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Récupération des passages et mise à jour des widgets.
 *
 * Un « tick » non réveillant (AlarmManager.RTC) est programmé chaque minute tant qu'il existe
 * des widgets : il recalcule les temps d'attente affichés et relance une requête (Tracéo sans clé,
 * PRIM avec une clé) quand l'intervalle d'actualisation choisi est écoulé. Comme l'alarme ne
 * réveille pas l'appareil, aucune requête n'est faite écran éteint.
 *
 * L'alarme est exacte : depuis Android 12, une alarme simple peut arriver jusqu'à une heure après
 * l'heure demandée, ce qui figerait les temps d'attente affichés.
 *
 * Le tick est une alarme à usage unique, que l'application reprogramme elle-même : s'il se perd
 * (processus tué pendant qu'il travaille, broadcast livré à un processus mourant), plus rien ne le
 * relance et les widgets se figent. Deux précautions : il est reprogrammé avant tout travail, et
 * une alarme répétée, qu'Android reprogramme lui-même, le relance s'il s'est quand même arrêté
 * ([ACTION_WATCHDOG]).
 */
object WidgetUpdater {

    const val ACTION_REFRESH = "fr.gotraceo.live.action.REFRESH"
    const val ACTION_TICK = "fr.gotraceo.live.action.TICK"
    const val ACTION_WATCHDOG = "fr.gotraceo.live.action.WATCHDOG"

    private const val TAG = "TRWidget"
    private const val MAX_PARALLEL_REQUESTS = 4
    private val RENDER_QUALITIES = listOf(1f, 0.7f, 0.5f)

    private val mutex = Mutex()

    /** Widgets dessinés avec l'indicateur d'actualisation (« … ») et pas encore redessinés sans lui. */
    private val showingRefresh: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun widgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetProviders.components(context).flatMap { manager.getAppWidgetIds(it).asList() }.toIntArray()
    }

    /** Redessine un widget, sa notification et la tuile, à partir de l'état en cache (sans réseau). */
    fun render(context: Context, widgetId: Int, refreshing: Boolean = false) {
        if (refreshing) showingRefresh.add(widgetId) else showingRefresh.remove(widgetId)
        draw(context, widgetId, refreshing)
        // Pendant une actualisation, la notification et la tuile gardent les passages précédents.
        if (!refreshing) {
            PassageNotifications.update(context, widgetId)
            QuickTile.update(context, widgetId)
        }
    }

    /**
     * Redessine, sans l'indicateur, les widgets restés sur « … ». À appeler quand l'actualisation en
     * cours n'est plus couverte par un broadcast : Android peut alors figer ou tuer le processus avant
     * qu'elle ne se termine, et le widget ne doit pas rester sur un état de passage.
     */
    fun settle(context: Context) {
        showingRefresh.toList().forEach { render(context, it) }
    }

    private fun draw(context: Context, widgetId: Int, refreshing: Boolean) {
        val prefs = Prefs(context)
        val manager = AppWidgetManager.getInstance(context)
        for (quality in RENDER_QUALITIES) {
            try {
                val views = WidgetRenderer.build(
                    context = context,
                    widgetId = widgetId,
                    config = prefs.config(widgetId),
                    state = prefs.state(widgetId),
                    hasApiKey = prefs.hasApiKey,
                    refreshing = refreshing,
                    options = manager.getAppWidgetOptions(widgetId),
                    fixedFormat = WidgetProviders.formatOf(manager.getAppWidgetInfo(widgetId)?.provider?.className),
                    renderScale = quality,
                    auto = prefs.auto(widgetId),
                    proximity = prefs.proximity(widgetId),
                )
                manager.updateAppWidget(widgetId, views)
                return
            } catch (e: RuntimeException) {
                // Le widget est une image : si elle est trop lourde pour le lanceur, on l'allège.
                Log.w(TAG, "Échec de l'affichage du widget $widgetId (définition $quality)", e)
            }
        }
    }

    fun renderAll(context: Context) {
        widgetIds(context).forEach { render(context, it) }
    }

    /**
     * Actualise les widgets indiqués.
     * @param force true pour interroger la source même si l'intervalle n'est pas écoulé (action utilisateur)
     */
    suspend fun refresh(context: Context, ids: IntArray, force: Boolean) {
        val app = context.applicationContext
        try {
            mutex.withLock { refreshLocked(app, ids, force) }
        } finally {
            // D'où qu'elle vienne (tick, toucher, service de position), une actualisation laisse le
            // tick programmé : c'est aussi ce qui le relance s'il s'était arrêté.
            scheduleTick(app)
        }
    }

    private suspend fun refreshLocked(context: Context, ids: IntArray, force: Boolean) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val hasKey = prefs.hasApiKey
        val timing = Timing.of(prefs)
        // Écran éteint (ex. rafraîchissement périodique déclenché par Android) : aucune requête,
        // sauf action explicite de l'utilisateur.
        val screenOn = isInteractive(context)
        val nearby = ids.filter { WidgetProviders.isProximity(context, it) }.toSet()
        val due = ids.associateWith { timing.dueAt(context, prefs, it) }
        val dueNow = ids.any { id -> due[id]?.let { it <= now } == true }
        // En direct, les widgets presque dus partent avec les autres : ils restent ensuite alignés.
        // Avec Tracéo, inutile : tous attendent le même relevé, annoncé par `nextAt`.
        val alignUntil = if (dueNow && !timing.followsTraceo) now + timing.refreshMinutes * 60_000L / 2 else now
        val targets = ids.filter { id ->
            val config = prefs.config(id)
            // Un widget à reconfigurer ne fait aucun appel, même à la demande : Tracéo répondrait 404.
            config != null && DataSources.route(config.stop.stopIds, hasKey) != PassageRoute.RECONFIGURE &&
                (force || (screenOn && due[id]?.let { it <= alignUntil } == true))
        }.toSet()
        ids.filter { it !in targets }.forEach { render(context, it) }
        if (targets.isEmpty()) return
        Log.i(TAG, "Actualisation de ${targets.size} widget(s) via ${if (timing.followsTraceo) "Tracéo" else "PRIM"}")

        targets.forEach { render(context, it, refreshing = true) }
        // Le format À proximité et les widgets d'une ligne travaillent chacun de leur côté : une
        // réponse lente pour l'un ne retarde pas les autres, et un échec ne les arrête pas tous.
        supervisorScope {
            targets.filter { it in nearby }.forEach { id ->
                launch {
                    settling(context, listOf(id)) {
                        AutoMode.refreshProximity(context, id)
                        render(context, id)
                    }
                }
            }
            val lineTargets = targets.filter { it !in nearby }
            if (lineTargets.isNotEmpty()) {
                launch { settling(context, lineTargets) { refreshLines(context, prefs, lineTargets, hasKey) } }
            }
        }
    }

    /** Si [block] échoue, les widgets retrouvent leur dernier état connu au lieu de rester sur « … ». */
    private suspend fun settling(context: Context, ids: List<Int>, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Échec de l'actualisation des widgets $ids", t)
            ids.filter { it in showingRefresh }.forEach { render(context, it) }
        }
    }

    private suspend fun refreshLines(context: Context, prefs: Prefs, ids: List<Int>, hasKey: Boolean) {
        val source = Sources.passages(context)
        coroutineScope {
            // Requêtes partagées entre widgets portant sur le même point d'arrêt et la même ligne,
            // et jamais plus de quelques-unes à la fois : pas de rafale vers la source.
            val requests = HashMap<Pair<String, String>, Deferred<Result<PassageSnapshot>>>()
            val parallel = Semaphore(MAX_PARALLEL_REQUESTS)
            fun fetch(stopId: String, lineId: String): Deferred<Result<PassageSnapshot>> =
                requests.getOrPut(stopId to lineId) {
                    async(Dispatchers.IO) { parallel.withPermit { runCatching { source.passages(stopId, lineId) } } }
                }

            val perWidget = ids.mapNotNull { id ->
                val config = prefs.config(id) ?: return@mapNotNull null
                val stopIds = config.directions.keys.ifEmpty { config.stop.stopIds.toSet() }
                Triple(id, config, stopIds.map { fetch(it, config.line.id) })
            }
            perWidget.forEach { (id, config, deferred) ->
                val results = deferred.awaitAll()
                results.mapNotNull { it.exceptionOrNull() }.forEach { Log.w(TAG, "Erreur de la source : ${it.message}") }
                val state = WidgetStates.from(
                    config, results, prefs.state(id), System.currentTimeMillis(),
                    removeTerminusArrivals = !hasKey,
                )
                prefs.saveState(id, state)
                render(context, id)
            }
        }
    }

    /** Vrai si l'actualisation automatique de ce widget est due : écran allumé et échéance passée. */
    fun isDue(context: Context, widgetId: Int, now: Long): Boolean {
        if (!isInteractive(context)) return false
        val prefs = Prefs(context)
        return Timing.of(prefs).dueAt(context, prefs, widgetId)?.let { it <= now } == true
    }

    /** Réglages qui fixent les échéances : source, intervalle, relevé annoncé par Tracéo et décalage. */
    private class Timing(
        val followsTraceo: Boolean,
        val refreshMinutes: Int,
        private val traceoNextAt: Long,
        private val offsetMs: Long,
    ) {
        fun dueAt(context: Context, prefs: Prefs, widgetId: Int): Long? {
            val attemptedAt = if (WidgetProviders.isProximity(context, widgetId)) {
                prefs.proximity(widgetId)?.attemptedAt
            } else {
                prefs.state(widgetId)?.attemptedAt
            }
            return RefreshSchedule.dueAt(attemptedAt, refreshMinutes, followsTraceo, traceoNextAt, offsetMs)
        }

        companion object {
            fun of(prefs: Prefs): Timing {
                val usesPrim = prefs.usesPrim
                return Timing(
                    followsTraceo = !usesPrim,
                    refreshMinutes = DataSources.refreshMinutes(prefs.refreshMinutes, usesPrim),
                    traceoNextAt = prefs.traceoNextAt,
                    offsetMs = prefs.refreshOffsetMs,
                )
            }
        }
    }

    /** Modifie la configuration d'un widget sans croiser une actualisation en cours. */
    suspend fun <T> exclusive(block: () -> T): T = mutex.withLock { block() }

    private fun isInteractive(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true


    // --- Planification du tick -------------------------------------------------------------

    private fun alarmIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Programme le prochain tick et repousse le filet de sécurité. Sans effet de bord à répéter :
     * chaque appel remplace les alarmes précédentes. Appelé de partout, y compris en tête de chaque
     * broadcast : un refus du système y est consigné, sans interrompre ce qui suit.
     */
    fun scheduleTick(context: Context) {
        try {
            setAlarms(context)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Programmation du tick impossible", e)
        }
    }

    private fun setAlarms(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val tick = alarmIntent(context, ACTION_TICK)
        val watchdog = alarmIntent(context, ACTION_WATCHDOG)
        val ids = widgetIds(context)
        if (ids.isEmpty()) {
            alarmManager.cancel(tick)
            alarmManager.cancel(watchdog)
            return
        }
        val now = System.currentTimeMillis()
        val prefs = Prefs(context)
        val timing = Timing.of(prefs)
        // Le tick de chaque minute, ou plus tôt si un widget est dû avant (prochain relevé de Tracéo).
        val nextDue = ids.asList().mapNotNull { timing.dueAt(context, prefs, it) }.filter { it > now }.minOrNull()
        val trigger = minOf(RefreshSchedule.nextTickAt(now, prefs.refreshOffsetMs), nextDue ?: Long.MAX_VALUE)
        // RTC (non réveillant) : rien n'est exécuté tant que l'écran est éteint.
        try {
            if (canUseExactAlarms(alarmManager)) {
                alarmManager.setExact(AlarmManager.RTC, trigger, tick)
            } else {
                alarmManager.set(AlarmManager.RTC, trigger, tick)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarme exacte refusée", e)
            alarmManager.set(AlarmManager.RTC, trigger, tick)
        }
        // Filet de sécurité, repoussé à chaque appel : il ne part que si plus rien ne passe ici.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            now + RefreshSchedule.WATCHDOG_INTERVAL_MS,
            RefreshSchedule.WATCHDOG_INTERVAL_MS,
            watchdog,
        )
    }


    private fun canUseExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Vrai si les temps d'attente ne peuvent pas suivre la minute : depuis Android 14, l'alarme
     * exacte doit être autorisée par l'usager (« Alarmes et rappels »).
     */
    fun needsExactAlarmAccess(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return !canUseExactAlarms(alarmManager)
    }

    /** Demande une actualisation immédiate (depuis l'application). */
    fun requestRefresh(context: Context, widgetId: Int? = null) {
        val intent = Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_REFRESH)
        if (widgetId != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        context.sendBroadcast(intent)
    }

    /**
     * Demande un tick tout de suite (depuis l'application) : les widgets dont l'échéance est passée
     * sont actualisés, les autres redessinés, et le tick suivant est programmé.
     */
    fun requestTick(context: Context) {
        context.sendBroadcast(Intent(context, DeparturesWidgetProvider::class.java).setAction(ACTION_TICK))
    }
}
