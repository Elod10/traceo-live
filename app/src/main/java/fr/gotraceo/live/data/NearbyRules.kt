package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Règles du mode automatique : quelle ligne afficher selon la position.
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/** Un point sur la carte. */
data class GeoPoint(val lat: Double, val lon: Double) {
    fun distanceTo(other: GeoPoint): Int {
        val rad = Math.PI / 180.0
        val h = sin((other.lat - lat) * rad / 2).pow(2) +
            cos(lat * rad) * cos(other.lat * rad) * sin((other.lon - lon) * rad / 2).pow(2)
        return (2 * EARTH_RADIUS_M * asin(sqrt(h))).roundToInt()
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}

/** Une position relevée par le téléphone. [accuracyM] vaut null si Android ne l'indique pas. */
data class PositionFix(val point: GeoPoint, val accuracyM: Float?)

/** Une ligne près de l'usager, à son arrêt le plus proche. */
data class NearbyLine(
    val line: Line,
    val stopName: String,
    val town: String?,
    val zone: String,
    val distanceM: Int,
) {
    /** Zone d'arrêt à interroger : celle de Tracéo en Île-de-France, la station du catalogue ailleurs. */
    val stopId: String get() = if (Networks.isIdfm(line.networkId)) "stop_area:IDFM:$zone" else zone

    fun toStop(): Stop = Stop(stopName, town, listOf(stopId), line.networkId)
}

/** Pourquoi une ligne est à ce rang. */
enum class NearbyReason { PICKED_HERE, FAVORITE, NEAREST }

data class RankedLine(val nearby: NearbyLine, val reason: NearbyReason, val effectiveM: Int, val favorite: Boolean)

class NearbyRanking(val list: List<RankedLine>, val nearestM: Int?, val noStop: Boolean)

object NearbyRules {

    const val FAVORITE_REACH_M = 800
    const val TOLERANCE_M = 400
    const val RAIL_BONUS_M = 150
    const val SEARCH_CAP_M = 2_000

    /** Rayon à lire dans l'index : il couvre la recherche et la portée des favoris. */
    val READ_RADIUS_M: Int = max(SEARCH_CAP_M, FAVORITE_REACH_M)

    /** Une ligne sans passage dans ce délai s'efface devant les suivantes. */
    const val ACTIVE_WITHIN_MINUTES = 60

    /** Nombre de lignes interrogées au plus pour trouver une ligne avec un passage. */
    const val MAX_PROBES = 3

    private val NIGHT_CODE = Regex("^N\\d+$")

    fun isNightLine(line: Line): Boolean =
        NIGHT_CODE.matches(line.shortName.trim()) || line.network?.contains("Noctilien", ignoreCase = true) == true

    /** Vrai pendant le service de nuit, de 0 h 30 à 5 h 30. */
    fun isNightService(hour: Int, minute: Int): Boolean {
        val minutes = hour * 60 + minute
        return minutes in 30 until 5 * 60 + 30
    }

    /** Une ligne n'apparaît qu'une fois, à son arrêt le plus proche. */
    fun linesAround(area: StopIndexArea, position: GeoPoint): List<NearbyLine> {
        val nearest = HashMap<String, NearbyLine>()
        area.stops.forEach { stop ->
            val distance = position.distanceTo(GeoPoint(stop.lat, stop.lon))
            stop.lineIds.forEach { id ->
                val line = area.lines[id] ?: return@forEach
                val current = nearest[id]
                if (current == null || distance < current.distanceM) {
                    nearest[id] = NearbyLine(line, stop.name, stop.town, stop.zone, distance)
                }
            }
        }
        return nearest.values.toList()
    }

    private fun railBonus(line: Line): Int = when (line.mode) {
        "metro", "rail" -> RAIL_BONUS_M
        "tram" -> RAIL_BONUS_M / 2
        else -> 0
    }

    /**
     * Classe les lignes : le choix fait ici, puis les favoris à portée, puis la distance, où le métro,
     * le RER et le train comptent 150 m de moins et le tram 75 m. On garde ce qui reste à moins de
     * 400 m de plus que l'arrêt le plus proche, sans dépasser 2 km.
     *
     * @param favorites clés des lignes favorites (cf. [Line.key])
     * @param pickedLineKey clé de la ligne choisie à cet endroit
     * @param modes modes acceptés par la source de passages
     */
    fun rank(
        candidates: List<NearbyLine>,
        favorites: Set<String>,
        pickedLineKey: String?,
        nightService: Boolean,
        modes: Set<String>,
    ): NearbyRanking {
        val usable = candidates.filter { it.line.mode in modes }
        val nearest = usable.filter { !isNightLine(it.line) }.minOfOrNull { it.distanceM }
        val noStop = nearest == null || nearest > SEARCH_CAP_M

        val ranked = usable.mapNotNull { c ->
            val favorite = c.line.key in favorites
            val effective = c.distanceM - railBonus(c.line)
            val reason = when {
                c.line.key == pickedLineKey -> NearbyReason.PICKED_HERE
                favorite && c.distanceM <= FAVORITE_REACH_M -> NearbyReason.FAVORITE
                isNightLine(c.line) && !nightService -> null
                !noStop && c.distanceM <= SEARCH_CAP_M && effective <= (nearest ?: 0) + TOLERANCE_M -> NearbyReason.NEAREST
                else -> null
            } ?: return@mapNotNull null
            RankedLine(c, reason, effective, favorite)
        }
        val order = compareBy<RankedLine> { it.reason.ordinal }
            .thenBy { if (it.reason == NearbyReason.FAVORITE) it.nearby.distanceM else it.effectiveM }
            .thenComparator { a, b -> Referential.naturalComparator.compare(a.nearby.line.shortName, b.nearby.line.shortName) }
        return NearbyRanking(ranked.sortedWith(order), nearest, noStop && ranked.isEmpty())
    }

    /**
     * Retient la première ligne qui a un passage bientôt, en interrogeant au plus [MAX_PROBES] lignes.
     * Le choix fait ici n'est jamais remplacé. Si aucune n'a de passage, la première reste affichée.
     */
    fun <T> pickActive(ranked: List<RankedLine>, probe: (NearbyLine) -> T, isActive: (T) -> Boolean): Pair<RankedLine, T>? {
        val first = ranked.firstOrNull() ?: return null
        val firstResult = probe(first.nearby)
        if (first.reason == NearbyReason.PICKED_HERE || isActive(firstResult)) return first to firstResult
        for (candidate in ranked.drop(1).take(MAX_PROBES - 1)) {
            val result = probe(candidate.nearby)
            if (isActive(result)) return candidate to result
        }
        return first to firstResult
    }
}

/** Ce qu'il faut faire d'une nouvelle position. */
enum class AnchorDecision {
    /** Pas de position : on actualise seulement les passages. */
    NO_FIX,

    /** Position trop imprécise pour décider : on actualise seulement les passages. */
    IMPRECISE,

    /** Moins de 100 m depuis le choix de l'arrêt : on le garde et on actualise les passages. */
    SAME_PLACE,

    /** L'usager s'est déplacé : on cherche à nouveau la meilleure ligne. */
    MOVED,
}

object AnchorPolicy {

    const val MOVE_THRESHOLD_M = 100
    const val MAX_ACCURACY_M = 200f

    /**
     * On compare à la position où l'arrêt a été choisi, et non à la précédente : sinon, plusieurs
     * touchers espacés de 90 m ne changeraient jamais l'arrêt. Le déplacement doit aussi dépasser la
     * marge d'erreur du GPS, pour que l'arrêt ne saute pas quand on n'a pas bougé.
     */
    fun decide(anchor: GeoPoint?, fix: PositionFix?): AnchorDecision {
        if (fix == null) return AnchorDecision.NO_FIX
        val accuracy = fix.accuracyM ?: 0f
        if (accuracy > MAX_ACCURACY_M) return AnchorDecision.IMPRECISE
        if (anchor == null) return AnchorDecision.MOVED
        return if (anchor.distanceTo(fix.point) <= max(MOVE_THRESHOLD_M.toFloat(), accuracy)) {
            AnchorDecision.SAME_PLACE
        } else {
            AnchorDecision.MOVED
        }
    }
}

/**
 * Ce que l'usager a choisi à chaque endroit : le widget s'en souvient quand il y revient. La ligne
 * est retenue par sa clé ([Line.key]), qui porte son réseau hors Île-de-France.
 */
class PlaceChoices(private val entries: List<Entry> = emptyList()) {

    data class Entry(val point: GeoPoint, val lineKey: String, val at: Long)

    fun lineAt(point: GeoPoint): String? =
        entries.map { it to it.point.distanceTo(point) }
            .filter { it.second <= PLACE_RADIUS_M }
            .minByOrNull { it.second }
            ?.first?.lineKey

    /** Remplace le choix fait à cet endroit, en oubliant les plus anciens au-delà de [MAX_ENTRIES]. */
    fun remember(point: GeoPoint, lineKey: String, at: Long): PlaceChoices {
        val others = entries.filter { it.point.distanceTo(point) > PLACE_RADIUS_M }
        return PlaceChoices((others + Entry(point, lineKey, at)).sortedByDescending { it.at }.take(MAX_ENTRIES))
    }

    fun toJson(): String {
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().put("lat", it.point.lat).put("lon", it.point.lon).put("line", it.lineKey).put("at", it.at))
        }
        return array.toString()
    }

    companion object {
        const val PLACE_RADIUS_M = 200
        const val MAX_ENTRIES = 50

        fun fromJson(raw: String?): PlaceChoices {
            if (raw.isNullOrBlank()) return PlaceChoices()
            return try {
                val array = JSONArray(raw)
                PlaceChoices(
                    (0 until array.length()).mapNotNull { i ->
                        val o = array.optJSONObject(i) ?: return@mapNotNull null
                        val line = o.optStringOrNull("line") ?: return@mapNotNull null
                        Entry(GeoPoint(o.getDouble("lat"), o.getDouble("lon")), line, o.optLong("at"))
                    },
                )
            } catch (e: Exception) {
                PlaceChoices()
            }
        }
    }
}

/** État du mode automatique pour un widget. */
data class AutoState(
    /** Position où l'arrêt affiché a été choisi. */
    val anchor: GeoPoint? = null,
    /** Dernière position relue. */
    val last: GeoPoint? = null,
    /** Distance à l'arrêt affiché. */
    val distanceM: Int? = null,
    /** Fin de la fenêtre du double toucher : avant cette date, un toucher ouvre le choix des lignes. */
    val choiceUntil: Long = 0L,
    /** Fin du message qui remplace brièvement l'heure sur le widget. */
    val hintUntil: Long = 0L,
    /** Autres lignes proches, proposées par le double toucher. */
    val others: Int = 0,
    /** Motif à signaler sur le widget pendant ce message. */
    val message: AutoMessage? = null,
) {
    fun isChoiceOpen(now: Long): Boolean = now < choiceUntil

    fun hint(now: Long): AutoHint? = when {
        now >= hintUntil -> null
        message != null -> AutoHint(message.label, message.short)
        others > 0 -> AutoHint(if (others == 1) "+1 ligne" else "+$others lignes", "+$others")
        else -> null
    }

    fun toJson(): JSONObject = JSONObject()
        .putOpt("anchorLat", anchor?.lat)
        .putOpt("anchorLon", anchor?.lon)
        .putOpt("lastLat", last?.lat)
        .putOpt("lastLon", last?.lon)
        .putOpt("distance", distanceM)
        .put("choiceUntil", choiceUntil)
        .put("hintUntil", hintUntil)
        .put("others", others)
        .putOpt("message", message?.name)

    companion object {
        /** Deux touchers plus rapprochés que ce délai ouvrent le choix des lignes. */
        const val DOUBLE_TAP_MS = 1_500L

        /** Durée du message qui remplace l'heure après une mise à jour. */
        const val HINT_MS = 4_000L

        fun fromJson(o: JSONObject): AutoState = AutoState(
            anchor = point(o, "anchorLat", "anchorLon"),
            last = point(o, "lastLat", "lastLon"),
            distanceM = if (o.has("distance")) o.optInt("distance") else null,
            choiceUntil = o.optLong("choiceUntil"),
            hintUntil = o.optLong("hintUntil"),
            others = o.optInt("others"),
            message = AutoMessage.entries.firstOrNull { it.name == o.optStringOrNull("message") },
        )

        private fun point(o: JSONObject, lat: String, lon: String): GeoPoint? =
            if (o.has(lat) && o.has(lon)) GeoPoint(o.getDouble(lat), o.getDouble(lon)) else null
    }
}

/** Ce que le widget affiche brièvement à la place de l'heure. */
data class AutoHint(val label: String, val short: String)

enum class AutoMessage(val label: String, val short: String) {
    NO_POSITION("GPS indisponible", "GPS ?"),
    IMPRECISE("GPS imprécis", "GPS ?"),
    NO_STOP("Aucun arrêt", "Aucun"),
    NEEDS_ACCESS("Position à autoriser", "GPS ?"),
}

/** Distance lisible : « 33 m », « 1,1 km ». */
fun formatDistance(meters: Int): String =
    if (meters < 1000) {
        "$meters m"
    } else {
        val tenths = min(999, (meters + 50) / 100)
        "${tenths / 10},${tenths % 10} km"
    }
