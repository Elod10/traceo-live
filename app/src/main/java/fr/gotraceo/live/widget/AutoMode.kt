package fr.gotraceo.live.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import fr.gotraceo.live.data.AnchorDecision
import fr.gotraceo.live.data.AnchorPolicy
import fr.gotraceo.live.data.AutoMessage
import fr.gotraceo.live.data.AutoState
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.Directions
import fr.gotraceo.live.data.GeoPoint
import fr.gotraceo.live.data.NearbyLine
import fr.gotraceo.live.data.NearbyRanking
import fr.gotraceo.live.data.NearbyRules
import fr.gotraceo.live.data.PassageSource
import fr.gotraceo.live.data.PositionFix
import fr.gotraceo.live.data.PositionReader
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.Proximity
import fr.gotraceo.live.data.StopIndexStore
import fr.gotraceo.live.data.TransportMode
import fr.gotraceo.live.data.WidgetConfig
import fr.gotraceo.live.data.WidgetState
import fr.gotraceo.live.data.WidgetStates
import fr.gotraceo.live.data.WidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Mode automatique : un widget ordinaire dont la ligne et l'arrêt sont réécrits quand on le touche.
 * Le dessin et l'actualisation restent ceux des autres widgets, ce qui garde tous les formats compatibles.
 *
 * Un toucher relit la position. Deux touchers rapprochés ouvrent le choix des lignes proches.
 */
object AutoMode {

    private const val TAG = "TRWidget"
    private const val INDEX_FILE = "stop_index.bin"

    /** Widgets dont la position est en cours de lecture : la feuille de choix l'attend. */
    private val locating: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Depuis quand l'usager choisit une ligne : une mise à jour plus ancienne ne doit plus rien changer. */
    private val choosingSince = ConcurrentHashMap<Int, Long>()

    fun store(context: Context): StopIndexStore =
        StopIndexStore(File(context.applicationContext.noBackupFilesDir, INDEX_FILE))

    /** Classement autour d'un point, sans réseau. Null si l'index n'existe pas encore. */
    fun ranking(context: Context, point: GeoPoint): NearbyRanking? {
        val prefs = Prefs(context)
        val area = store(context).around(point.lat, point.lon, NearbyRules.READ_RADIUS_M) ?: return null
        val paris = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
        return NearbyRules.rank(
            candidates = NearbyRules.linesAround(area, point),
            favorites = prefs.favoriteLines,
            pickedLineKey = prefs.placeChoices.lineAt(point),
            nightService = NearbyRules.isNightService(paris.get(Calendar.HOUR_OF_DAY), paris.get(Calendar.MINUTE)),
            modes = DataSources.modes(prefs.hasApiKey).flatMap { it.apiModes }.toSet(),
        )
    }

    fun configFor(widgetId: Int, nearby: NearbyLine, theme: WidgetTheme): WidgetConfig {
        val stop = nearby.toStop()
        return WidgetConfig(
            widgetId = widgetId,
            mode = TransportMode.entries.firstOrNull { it.includes(nearby.line) } ?: TransportMode.BUS,
            line = nearby.line,
            stop = stop,
            directions = Directions.allDirections(stop),
            directionLabel = Directions.ALL_DIRECTIONS,
            theme = theme,
            auto = true,
        )
    }

    /** Enregistre le choix fait pendant la configuration du widget. */
    fun saveConfiguration(
        context: Context,
        widgetId: Int,
        nearby: NearbyLine,
        fix: PositionFix,
        theme: WidgetTheme,
        changedByUser: Boolean,
    ) {
        val prefs = Prefs(context)
        if (changedByUser) {
            prefs.placeChoices = prefs.placeChoices.remember(fix.point, nearby.line.key, System.currentTimeMillis())
        }
        prefs.saveConfig(configFor(widgetId, nearby, theme))
        prefs.saveAuto(widgetId, AutoState(anchor = fix.point, last = fix.point, distanceM = nearby.distanceM))
    }

    /**
     * Premier toucher : le prochain toucher du délai ouvrira le choix. Seule la cible du toucher est
     * changée, sans redessiner l'image, pour que le lanceur la reçoive le plus vite possible.
     */
    fun openDoubleTapWindow(context: Context, widgetId: Int, now: Long) {
        val prefs = Prefs(context)
        prefs.saveAuto(widgetId, prefs.auto(widgetId).copy(choiceUntil = now + AutoState.DOUBLE_TAP_MS))
        AppWidgetManager.getInstance(context)
            .partiallyUpdateAppWidget(widgetId, WidgetRenderer.doubleTapViews(context, widgetId))
    }

    /** Second toucher reçu par le service, avant que le lanceur ait pris en compte la nouvelle cible. */
    fun openChoice(context: Context, widgetId: Int) {
        try {
            context.startActivity(WidgetRenderer.choiceIntent(context, widgetId))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Ouverture du choix refusée", e)
        }
    }

    fun beginLocating(widgetId: Int) {
        locating.add(widgetId)
    }

    fun endLocating(widgetId: Int) {
        locating.remove(widgetId)
    }

    fun isLocating(widgetId: Int): Boolean = widgetId in locating

    /** La feuille de choix vient de s'ouvrir : le toucher précédent ne doit plus changer le widget. */
    fun markChoosing(widgetId: Int) {
        choosingSince[widgetId] = System.currentTimeMillis()
    }

    private fun tookOver(widgetId: Int, tappedAt: Long): Boolean = (choosingSince[widgetId] ?: 0L) >= tappedAt

    /**
     * Widgets qui relisent la position sans qu'on les touche : option activée, permission « Toujours
     * autoriser » accordée, plage horaire en cours et intervalle d'actualisation écoulé. Comme toute
     * actualisation automatique, seulement écran allumé.
     */
    fun followedNow(context: Context, ids: IntArray, now: Long): List<Int> {
        if (!PositionReader(context).hasBackgroundAccess) return emptyList()
        val prefs = Prefs(context)
        return ids.filter { id ->
            prefs.followsPosition(id) && prefs.config(id)?.auto == true && prefs.inTimeWindow(id) &&
                !isLocating(id) && WidgetUpdater.isDue(context, id, now)
        }
    }

    /** Relit la position d'un widget suivi, comme un toucher, mais sans message à l'écran. */
    suspend fun follow(context: Context, widgetId: Int) {
        beginLocating(widgetId)
        try {
            onTap(context, widgetId, System.currentTimeMillis(), quiet = true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Échec du suivi de la position", e)
        } finally {
            endLocating(widgetId)
        }
    }

    /**
     * Toucher du widget : relit la position et met à jour le widget.
     * @param quiet vrai pour un suivi sans toucher : le widget n'affiche ni « +3 lignes » ni message
     */
    suspend fun onTap(context: Context, widgetId: Int, tappedAt: Long, quiet: Boolean = false) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val config = prefs.config(widgetId) ?: return
        if (!config.auto) {
            WidgetUpdater.refresh(app, intArrayOf(widgetId), force = true)
            return
        }
        WidgetUpdater.render(app, widgetId, refreshing = true)

        val started = SystemClock.elapsedRealtime()
        val fix = PositionReader(app).read()
        val before = prefs.auto(widgetId)
        val decision = AnchorPolicy.decide(before.anchor, fix)
        val moved = if (fix != null && before.anchor != null) before.anchor.distanceTo(fix.point) else null
        Log.i(
            TAG,
            "Position en ${SystemClock.elapsedRealtime() - started} ms, précision ${fix?.accuracyM} m, " +
                "à $moved m de l'arrêt choisi : $decision",
        )

        // Une position trop imprécise ne remplace pas la dernière position fiable.
        val point = if (decision == AnchorDecision.IMPRECISE) before.last ?: fix?.point else fix?.point ?: before.last
        prefs.saveAuto(widgetId, before.copy(last = point))
        endLocating(widgetId)
        if (tookOver(widgetId, tappedAt)) return

        val ranking = withContext(Dispatchers.IO) { point?.let { rankingEnsuringIndex(app, it) } }
        var message = when (decision) {
            AnchorDecision.NO_FIX -> AutoMessage.NO_POSITION
            AnchorDecision.IMPRECISE -> AutoMessage.IMPRECISE
            else -> null
        }
        val proximity = WidgetProviders.isProximity(app, widgetId)
        var anchor = before.anchor
        var shownLineId = config.line.id
        var applied = false
        if (decision == AnchorDecision.MOVED && point != null && proximity) {
            // L'actualisation qui suit classe les lignes autour de cette nouvelle position.
            if (ranking?.list.isNullOrEmpty()) {
                message = AutoMessage.NO_STOP
            } else {
                anchor = point
                prefs.saveAuto(widgetId, prefs.auto(widgetId).copy(anchor = point))
            }
        } else if (decision == AnchorDecision.MOVED && point != null) {
            val best = pickBest(prefs, config, ranking)
            if (tookOver(widgetId, tappedAt)) return
            if (best == null) {
                message = AutoMessage.NO_STOP
            } else {
                WidgetUpdater.exclusive {
                    prefs.saveConfig(best.config)
                    prefs.saveState(widgetId, best.state)
                }
                anchor = point
                shownLineId = best.nearby.line.id
                applied = true
            }
        }
        if (!applied) WidgetUpdater.refresh(app, intArrayOf(widgetId), force = true)
        if (tookOver(widgetId, tappedAt)) return

        val lines = ranking?.list.orEmpty()
        val latest = prefs.auto(widgetId)
        val now = System.currentTimeMillis()
        val others = if (proximity) {
            val shown = prefs.proximity(widgetId)?.let { Proximity.visible(it.entries, now, proximityRows(app, widgetId)).size } ?: 0
            maxOf(0, lines.size - shown)
        } else {
            lines.count { it.nearby.line.id != shownLineId }
        }
        prefs.saveAuto(
            widgetId,
            latest.copy(
                anchor = anchor,
                last = point,
                distanceM = lines.firstOrNull { it.nearby.line.id == shownLineId }?.nearby?.distanceM ?: latest.distanceM,
                hintUntil = if (quiet) latest.hintUntil else now + AutoState.HINT_MS,
                others = if (quiet) latest.others else others,
                message = if (quiet) latest.message else message,
            ),
        )
        WidgetUpdater.render(app, widgetId)
    }

    /**
     * Actualise le format À proximité autour de la position où ses lignes ont été choisies. Les
     * favoris et le choix fait ici sont relus à chaque fois, sans appel réseau de plus.
     */
    suspend fun refreshProximity(context: Context, widgetId: Int) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val auto = prefs.auto(widgetId)
        val point = auto.anchor ?: auto.last
        val ranked = withContext(Dispatchers.IO) { point?.let { rankingEnsuringIndex(app, it) } }?.list.orEmpty()
        val source = Sources.proximity(app)
        val state = Proximity.collect(
            ranked = ranked,
            rows = proximityRows(app, widgetId),
            now = System.currentTimeMillis(),
            previous = prefs.proximity(widgetId),
            removeTerminusArrivals = !prefs.usesPrim,
        ) { batch ->
            withContext(Dispatchers.IO) { batch.map { async { source(it) } }.awaitAll() }
        }
        prefs.saveProximity(widgetId, state)
    }

    /** Vrai si un widget À proximité a de la place pour plus de lignes qu'il n'en connaît. */
    fun wantsMoreLines(context: Context, widgetId: Int): Boolean {
        if (!WidgetProviders.isProximity(context, widgetId)) return false
        val state = Prefs(context).proximity(widgetId) ?: return false
        val rows = proximityRows(context, widgetId)
        return Proximity.visible(state.entries, System.currentTimeMillis(), rows).size < rows
    }

    /** Rangées à remplir : le plus grand nombre que le widget peut montrer, en portrait ou en paysage. */
    private fun proximityRows(context: Context, widgetId: Int): Int {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val sizes = WidgetRenderer.widgetSizes(options)
        return listOf(sizes.portrait, sizes.landscape).maxOf { ProximityLayout.rows(it.width, it.height) }
    }

    private fun rankingEnsuringIndex(context: Context, point: GeoPoint): NearbyRanking? {
        val store = store(context)
        if (store.savedAt() == null) {
            try {
                store.download()
            } catch (e: IOException) {
                Log.w(TAG, "Index des arrêts indisponible", e)
                return null
            }
        }
        return ranking(context, point)
    }

    private class Picked(val nearby: NearbyLine, val config: WidgetConfig, val state: WidgetState)

    /** La meilleure ligne qui a un passage bientôt, ou null si aucune ligne n'est proche. */
    private suspend fun pickBest(prefs: Prefs, config: WidgetConfig, ranking: NearbyRanking?): Picked? {
        val ranked = ranking?.list.orEmpty()
        if (ranked.isEmpty()) return null
        val hasKey = prefs.hasApiKey
        // Les lignes classées sont toutes du réseau où l'on se trouve.
        val source = Sources.passages(prefs.appContext, ranked.first().nearby.line.networkId)
        val now = System.currentTimeMillis()
        var offline = false
        val chosen = withContext(Dispatchers.IO) {
            NearbyRules.pickActive(
                ranked,
                probe = { nearby ->
                    val next = configFor(config.widgetId, nearby, config.theme)
                    // Sans réseau, inutile d'attendre le délai d'expiration de chaque ligne.
                    val result = if (offline) {
                        Result.failure(IOException("Réseau indisponible"))
                    } else {
                        fetch(source, nearby)
                    }
                    if (DataSources.isNetworkFailure(result.exceptionOrNull())) offline = true
                    next to WidgetStates.from(next, listOf(result), null, now, removeTerminusArrivals = !hasKey)
                },
                isActive = { (_, state) -> Proximity.hasPassageSoon(state.departures, now) },
            )
        } ?: return null
        val (next, state) = chosen.second
        return Picked(chosen.first.nearby, next, state)
    }

    /** Choix fait dans la feuille : l'affiche et le retient pour ce lieu. */
    suspend fun choose(context: Context, widgetId: Int, nearby: NearbyLine) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val config = prefs.config(widgetId) ?: return
        val now = System.currentTimeMillis()
        prefs.auto(widgetId).last?.let { prefs.placeChoices = prefs.placeChoices.remember(it, nearby.line.key, now) }
        if (WidgetProviders.isProximity(app, widgetId)) {
            // La ligne choisie passe en tête : le classement suffit à la placer.
            val current = prefs.auto(widgetId)
            prefs.saveAuto(
                widgetId,
                current.copy(anchor = current.last ?: current.anchor, choiceUntil = 0L, hintUntil = 0L, others = 0, message = null),
            )
            WidgetUpdater.refresh(app, intArrayOf(widgetId), force = true)
            return
        }
        val next = configFor(widgetId, nearby, config.theme)
        val source = Sources.passages(prefs.appContext, nearby.line.networkId)
        val result = withContext(Dispatchers.IO) { fetch(source, nearby) }
        val state = WidgetStates.from(next, listOf(result), null, now, removeTerminusArrivals = !prefs.hasApiKey)
        WidgetUpdater.exclusive {
            prefs.saveConfig(next)
            prefs.saveState(widgetId, state)
        }
        val auto = prefs.auto(widgetId)
        prefs.saveAuto(
            widgetId,
            auto.copy(
                anchor = auto.last ?: auto.anchor,
                distanceM = nearby.distanceM,
                choiceUntil = 0L,
                hintUntil = 0L,
                others = 0,
                message = null,
            ),
        )
        WidgetUpdater.render(app, widgetId)
    }

    /** Ferme la fenêtre du double toucher : le prochain toucher relira la position. */
    fun closeChoice(context: Context, widgetId: Int) {
        val prefs = Prefs(context)
        prefs.saveAuto(widgetId, prefs.auto(widgetId).copy(choiceUntil = 0L, hintUntil = 0L, message = null))
        WidgetUpdater.render(context, widgetId)
    }

    /** Met l'index à jour s'il est ancien : chaque semaine en Wi-Fi, chaque mois sur le réseau mobile. */
    fun refreshIndexIfStale(context: Context) {
        val app = context.applicationContext
        val store = store(app)
        val metered = app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
        if (store.savedAt() == null || !store.isStale(System.currentTimeMillis(), unmetered = !metered)) return
        try {
            val stats = store.download()
            Log.i(TAG, "Index des arrêts à jour : ${stats.stops} arrêts, ${stats.lines} lignes")
        } catch (e: IOException) {
            Log.w(TAG, "Mise à jour de l'index impossible : l'ancien reste en place", e)
        }
    }

    fun fetch(source: PassageSource, nearby: NearbyLine) =
        runCatching { source.passages(nearby.stopId, nearby.line.id) }
}
