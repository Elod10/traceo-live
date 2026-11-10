package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

// Règles du format À proximité : plusieurs lignes, avec leurs passages dans les deux sens.
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/** Une ligne du format et ses passages à venir. */
data class ProximityEntry(
    val nearby: NearbyLine,
    val reason: NearbyReason,
    val favorite: Boolean,
    val departures: List<Departure>,
) {
    /** Une ligne choisie ici ou favorite reste affichée, même sans passage dans l'heure. */
    val pinned: Boolean get() = favorite || reason == NearbyReason.PICKED_HERE

    fun toJson(): JSONObject = JSONObject()
        .put("line", nearby.line.toJson())
        .put("stop", nearby.stopName)
        .putOpt("town", nearby.town)
        .put("zone", nearby.zone)
        .put("distance", nearby.distanceM)
        .put("reason", reason.name)
        .put("favorite", favorite)
        .put("departures", JSONArray().also { array -> departures.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): ProximityEntry {
            val array = o.optJSONArray("departures") ?: JSONArray()
            return ProximityEntry(
                nearby = NearbyLine(
                    line = Line.fromJson(o.getJSONObject("line")),
                    stopName = o.optString("stop"),
                    town = o.optStringOrNull("town"),
                    zone = o.optString("zone"),
                    distanceM = o.optInt("distance"),
                ),
                reason = NearbyReason.entries.firstOrNull { it.name == o.optString("reason") } ?: NearbyReason.NEAREST,
                favorite = o.optBoolean("favorite"),
                departures = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(Departure::fromJson) },
            )
        }
    }
}

/** Dernier état connu du format À proximité. */
data class ProximityState(
    val fetchedAt: Long,
    val attemptedAt: Long,
    val entries: List<ProximityEntry>,
    val error: String? = null,
    val dataAt: Long? = null,
    val fresh: Boolean? = null,
) {
    val isSnapshot: Boolean get() = fresh != null

    fun toJson(): JSONObject = JSONObject()
        .put("fetchedAt", fetchedAt)
        .put("attemptedAt", attemptedAt)
        .put("entries", JSONArray().also { array -> entries.forEach { array.put(it.toJson()) } })
        .putOpt("error", error)
        .putOpt("dataAt", dataAt)
        .putOpt("fresh", fresh)

    companion object {
        fun fromJson(o: JSONObject): ProximityState {
            val array = o.optJSONArray("entries") ?: JSONArray()
            return ProximityState(
                fetchedAt = o.optLong("fetchedAt"),
                attemptedAt = o.optLong("attemptedAt"),
                entries = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(ProximityEntry::fromJson) },
                error = o.optStringOrNull("error"),
                dataAt = o.optLongOrNull("dataAt"),
                fresh = if (o.has("fresh") && !o.isNull("fresh")) o.optBoolean("fresh") else null,
            )
        }
    }
}

object Proximity {

    /** Nombre de lignes interrogées au plus pour remplir le widget. */
    const val MAX_LINES = 8

    /** Passages conservés par ligne : de quoi recalculer les attentes entre deux actualisations. */
    const val MAX_STORED_DEPARTURES = 6

    fun upcoming(departures: List<Departure>, now: Long): List<Departure> = Directions.upcoming(departures, now)

    fun hasPassageSoon(departures: List<Departure>, now: Long): Boolean {
        val limit = now + NearbyRules.ACTIVE_WITHIN_MINUTES * 60_000L
        return departures.any { !it.isCancelled && (it.displayTime ?: Long.MAX_VALUE) <= limit }
    }

    /** Les lignes à montrer, dans l'ordre du classement. */
    fun visible(entries: List<ProximityEntry>, now: Long, rows: Int): List<ProximityEntry> =
        entries.filter { it.pinned || hasPassageSoon(upcoming(it.departures, now), now) }.take(rows)

    /**
     * Interroge les lignes dans l'ordre du classement, par lots, jusqu'à remplir le widget. Il suffit donc
     * le plus souvent d'une requête par rangée, et une ligne sans passage laisse sa place à la suivante.
     *
     * @param rows rangées à remplir
     * @param removeTerminusArrivals vrai pour Tracéo, qui inclut les arrivées au terminus
     * @param fetch passages d'un lot de lignes, dans le même ordre
     */
    suspend fun collect(
        ranked: List<RankedLine>,
        rows: Int,
        now: Long,
        previous: ProximityState?,
        removeTerminusArrivals: Boolean,
        fetch: suspend (List<NearbyLine>) -> List<Result<PassageSnapshot>>,
    ): ProximityState {
        val limit = min(ranked.size, MAX_LINES)
        val entries = ArrayList<ProximityEntry>()
        val snapshots = ArrayList<PassageSnapshot>()
        val errors = ArrayList<Throwable>()
        var index = 0
        while (index < limit) {
            val missing = rows - visible(entries, now, rows).size
            if (missing <= 0) break
            val batch = ranked.subList(index, min(index + missing, limit))
            index += batch.size
            val results = fetch(batch.map { it.nearby })
            batch.zip(results).forEach { (row, result) ->
                val snapshot = result.getOrNull()
                if (snapshot != null) snapshots.add(snapshot)
                result.exceptionOrNull()?.let(errors::add)
                entries.add(entry(row, snapshot, now, removeTerminusArrivals))
            }
            // Sans réseau, inutile d'essayer les lignes suivantes.
            if (results.all { DataSources.isNetworkFailure(it.exceptionOrNull()) }) break
        }

        if (snapshots.isEmpty() && errors.isNotEmpty()) {
            // Un 429, un 503 ou une panne de réseau ne durent pas : on garde les dernières données.
            return ProximityState(
                fetchedAt = previous?.fetchedAt ?: 0L,
                attemptedAt = now,
                entries = previous?.entries.orEmpty(),
                error = WidgetStates.errorMessage(errors.first()),
                dataAt = previous?.dataAt,
                fresh = previous?.fresh,
            )
        }
        val nothingToShow = visible(entries, now, rows).isEmpty()
        return ProximityState(
            fetchedAt = now,
            attemptedAt = now,
            entries = entries,
            error = if (errors.isNotEmpty() && nothingToShow) WidgetStates.errorMessage(errors.first()) else null,
            // Plusieurs relevés : on annonce le plus ancien, et « récent » seulement si tous le sont.
            dataAt = snapshots.mapNotNull { it.dataAt }.minOrNull(),
            fresh = snapshots.mapNotNull { it.fresh }.takeIf { it.isNotEmpty() }?.all { it },
        )
    }

    private fun entry(row: RankedLine, snapshot: PassageSnapshot?, now: Long, removeTerminusArrivals: Boolean): ProximityEntry {
        val received = snapshot?.departures.orEmpty()
        val shown = if (removeTerminusArrivals) {
            Directions.withoutTerminusArrivals(received, row.nearby.stopName)
        } else {
            received
        }
        return ProximityEntry(
            nearby = row.nearby,
            reason = row.reason,
            favorite = row.favorite,
            departures = Directions.upcoming(shown, now).take(MAX_STORED_DEPARTURES),
        )
    }
}

/**
 * Avec une clé, PRIM renvoie toutes les lignes d'une zone d'un coup : une seule requête par zone,
 * dont les passages sont ensuite répartis entre les lignes. À recréer à chaque actualisation.
 */
class PrimZonePassages(private val zoneDepartures: (String) -> List<Departure>) {

    private val zones = HashMap<String, Result<List<Departure>>>()

    fun passages(nearby: NearbyLine): Result<PassageSnapshot> {
        val zone = synchronized(zones) {
            zones.getOrPut(nearby.stopId) { runCatching { zoneDepartures(nearby.stopId) } }
        }
        return zone.map { departures ->
            PassageSnapshot(departures.filter { PrimClient.lineMatches(it.lineRef, nearby.line.id) })
        }
    }
}
