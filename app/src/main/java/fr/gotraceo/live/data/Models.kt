package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Modes de transport proposés dans le menu de configuration.
 * [apiModes] correspond au champ `transportmode` du référentiel des lignes IDFM.
 */
enum class TransportMode(val label: String, val apiModes: List<String>) {
    METRO("Métro", listOf("metro")),
    RER("RER", listOf("rail")),
    TRAIN("Train / Transilien", listOf("rail")),
    TRAM("Tramway", listOf("tram")),
    BUS("Bus / Noctilien", listOf("bus")),
    OTHER("Câble / Funiculaire", listOf("cableway", "funicular"));

    /** Pour les lignes ferrées, on sépare le RER du reste (Transilien, TER...). */
    fun accepts(line: Line): Boolean = when (this) {
        RER -> line.isRer()
        TRAIN -> !line.isRer()
        else -> true
    }

    /** Vrai si la ligne relève de ce mode, pour trier une liste qui mélange tous les modes. */
    fun includes(line: Line): Boolean = line.mode in apiModes && accepts(line)

    companion object {
        fun fromName(name: String?): TransportMode? = entries.firstOrNull { it.name == name }
    }
}

/** Une ligne du référentiel IDFM (jeu de données « referentiel-des-lignes »). */
data class Line(
    /** Identifiant IDFM de la ligne, ex. « C01742 ». */
    val id: String,
    val name: String,
    val shortName: String,
    val mode: String,
    val subMode: String? = null,
    val network: String? = null,
    val operator: String? = null,
    /** Couleur hexadécimale sans « # », ex. « EB2132 ». */
    val color: String? = null,
    val textColor: String? = null,
) {
    val lineRef: String get() = "STIF:Line::$id:"

    fun isRer(): Boolean {
        if (mode != "rail") return false
        if (network?.contains("RER", ignoreCase = true) == true) return true
        if (subMode?.contains("RER", ignoreCase = true) == true) return true
        val n = shortName.uppercase(Locale.ROOT).removePrefix("RER").trim()
        return n in setOf("A", "B", "C", "D", "E")
    }

    /** Libellé affiché sous le nom : réseau / exploitant. */
    fun subtitle(): String = listOfNotNull(network, operator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .joinToString(" · ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("shortName", shortName)
        .put("mode", mode)
        .putOpt("subMode", subMode)
        .putOpt("network", network)
        .putOpt("operator", operator)
        .putOpt("color", color)
        .putOpt("textColor", textColor)

    companion object {
        fun fromJson(o: JSONObject): Line = Line(
            id = o.getString("id"),
            name = o.optString("name"),
            shortName = o.optString("shortName"),
            mode = o.optString("mode"),
            subMode = o.optStringOrNull("subMode"),
            network = o.optStringOrNull("network"),
            operator = o.optStringOrNull("operator"),
            color = o.optStringOrNull("color"),
            textColor = o.optStringOrNull("textColor"),
        )
    }
}

/**
 * Un arrêt d'une ligne. Les arrêts portant le même nom dans la même commune
 * (typiquement un quai par sens) sont regroupés : [stopIds] contient alors plusieurs
 * identifiants IDFM (« IDFM:463158 » ou « IDFM:monomodalStopPlace:58879 »).
 */
data class Stop(
    val name: String,
    val town: String?,
    val stopIds: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .putOpt("town", town)
        .put("stopIds", JSONArray(stopIds))

    companion object {
        fun fromJson(o: JSONObject): Stop = Stop(
            name = o.optString("name"),
            town = o.optStringOrNull("town"),
            stopIds = o.optJSONArray("stopIds").toStringList(),
        )
    }
}

/** Un passage à l'arrêt, extrait d'une réponse SIRI StopMonitoring. */
data class Departure(
    val lineRef: String?,
    val destination: String,
    val directionName: String?,
    val directionRef: String?,
    val expectedArrival: Long?,
    val expectedDeparture: Long?,
    val aimedArrival: Long?,
    val aimedDeparture: Long?,
    val atStop: Boolean,
    val platform: String?,
    val status: String?,
    val journeyNote: String?,
    /** Identifiant IDFM du point d'arrêt interrogé (cf. [Stop.stopIds]). */
    val stopId: String = "",
) {
    /** Heure de passage à afficher (arrivée prévue, sinon départ). */
    val displayTime: Long? get() = expectedArrival ?: expectedDeparture ?: aimedArrival ?: aimedDeparture

    /** Heure à laquelle le véhicule quitte l'arrêt (pour écarter les passages terminés). */
    val leaveTime: Long? get() = expectedDeparture ?: expectedArrival ?: aimedDeparture ?: aimedArrival

    /** Retard en minutes par rapport à l'horaire théorique (0 si inconnu ou en avance). */
    val delayMinutes: Int
        get() {
            val expected = expectedArrival ?: expectedDeparture ?: return 0
            val aimed = aimedArrival ?: aimedDeparture ?: return 0
            val diff = (expected - aimed) / 60_000L
            return if (diff > 0) diff.toInt() else 0
        }

    val isCancelled: Boolean get() = status.equals("cancelled", ignoreCase = true)

    /** Clé utilisée pour regrouper les passages par sens. */
    val directionKey: String get() = directionName?.takeIf { it.isNotBlank() } ?: destination

    fun toJson(): JSONObject = JSONObject()
        .putOpt("lineRef", lineRef)
        .put("destination", destination)
        .putOpt("directionName", directionName)
        .putOpt("directionRef", directionRef)
        .putOpt("expectedArrival", expectedArrival)
        .putOpt("expectedDeparture", expectedDeparture)
        .putOpt("aimedArrival", aimedArrival)
        .putOpt("aimedDeparture", aimedDeparture)
        .put("atStop", atStop)
        .putOpt("platform", platform)
        .putOpt("status", status)
        .putOpt("journeyNote", journeyNote)
        .put("stopId", stopId)

    companion object {
        fun fromJson(o: JSONObject): Departure = Departure(
            lineRef = o.optStringOrNull("lineRef"),
            destination = o.optString("destination"),
            directionName = o.optStringOrNull("directionName"),
            directionRef = o.optStringOrNull("directionRef"),
            expectedArrival = o.optLongOrNull("expectedArrival"),
            expectedDeparture = o.optLongOrNull("expectedDeparture"),
            aimedArrival = o.optLongOrNull("aimedArrival"),
            aimedDeparture = o.optLongOrNull("aimedDeparture"),
            atStop = o.optBoolean("atStop"),
            platform = o.optStringOrNull("platform"),
            status = o.optStringOrNull("status"),
            journeyNote = o.optStringOrNull("journeyNote"),
            stopId = o.optString("stopId"),
        )
    }
}

/** Apparence choisie pour un widget. */
enum class WidgetTheme {
    /** Sombre ou clair, selon le thème du système. */
    AUTO,
    DARK,
    LIGHT,
    LINE;

    companion object {
        fun fromName(name: String?): WidgetTheme = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Configuration d'un widget : ligne, arrêt et sens.
 *
 * [directions] associe chaque point d'arrêt interrogé à l'ensemble des sens retenus
 * (clés [Departure.directionKey]). Un ensemble vide signifie « tous les passages de ce point d'arrêt ».
 */
data class WidgetConfig(
    val widgetId: Int,
    val mode: TransportMode,
    val line: Line,
    val stop: Stop,
    val directions: Map<String, Set<String>>,
    val directionLabel: String,
    val theme: WidgetTheme = WidgetTheme.AUTO,
    /** Vrai si la ligne et l'arrêt suivent la position : ils sont réécrits quand on touche le widget. */
    val auto: Boolean = false,
) {
    /** Vrai si le widget ne suit qu'un seul sens : un sens coché, ou un seul quai parmi plusieurs. */
    val isSingleDirection: Boolean
        get() {
            if (directions.values.any { it.isEmpty() }) {
                return directions.size == 1 && stop.stopIds.size > 1
            }
            return directions.values.flatten().map(::normalizeLabel).toSet().size == 1
        }

    fun toJson(): JSONObject {
        val dirs = JSONObject()
        directions.forEach { (stopId, keys) -> dirs.put(stopId, JSONArray(keys.toList())) }
        return JSONObject()
            .put("widgetId", widgetId)
            .put("mode", mode.name)
            .put("line", line.toJson())
            .put("stop", stop.toJson())
            .put("directions", dirs)
            .put("directionLabel", directionLabel)
            .put("theme", theme.name)
            .put("auto", auto)
    }

    companion object {
        fun fromJson(o: JSONObject): WidgetConfig {
            val dirsJson = o.optJSONObject("directions") ?: JSONObject()
            val dirs = LinkedHashMap<String, Set<String>>()
            dirsJson.keys().forEach { key -> dirs[key] = dirsJson.optJSONArray(key).toStringList().toSet() }
            return WidgetConfig(
                widgetId = o.getInt("widgetId"),
                mode = TransportMode.fromName(o.optString("mode")) ?: TransportMode.BUS,
                line = Line.fromJson(o.getJSONObject("line")),
                stop = Stop.fromJson(o.getJSONObject("stop")),
                directions = dirs,
                directionLabel = o.optString("directionLabel"),
                theme = WidgetTheme.fromName(o.optStringOrNull("theme")),
                auto = o.optBoolean("auto", false),
            )
        }
    }
}

/** Dernier état connu d'un widget (passages récupérés, erreur éventuelle). */
data class WidgetState(
    /** Date de la dernière récupération réussie (0 si aucune). */
    val fetchedAt: Long,
    /** Date de la dernière tentative (réussie ou non). */
    val attemptedAt: Long,
    val departures: List<Departure>,
    val error: String?,
    /** Date du relevé de la source, si elle ne répond pas en direct (cf. [PassageSnapshot]). */
    val dataAt: Long? = null,
    /** Faux si le relevé est ancien ; null si la source répond en direct. */
    val fresh: Boolean? = null,
) {
    /** Vrai si les passages viennent d'un relevé de la source, et non d'une réponse en direct. */
    val isSnapshot: Boolean get() = fresh != null

    fun toJson(): JSONObject = JSONObject()
        .put("fetchedAt", fetchedAt)
        .put("attemptedAt", attemptedAt)
        .put("departures", JSONArray().also { arr -> departures.forEach { arr.put(it.toJson()) } })
        .putOpt("error", error)
        .putOpt("dataAt", dataAt)
        .putOpt("fresh", fresh)

    companion object {
        fun fromJson(o: JSONObject): WidgetState {
            val arr = o.optJSONArray("departures") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Departure::fromJson) }
            val fetchedAt = o.optLong("fetchedAt")
            return WidgetState(
                fetchedAt = fetchedAt,
                attemptedAt = o.optLong("attemptedAt", fetchedAt),
                departures = list,
                error = o.optStringOrNull("error"),
                // Absents des états enregistrés avant l'arrivée de Tracéo.
                dataAt = o.optLongOrNull("dataAt"),
                fresh = if (o.has("fresh") && !o.isNull("fresh")) o.optBoolean("fresh") else null,
            )
        }
    }
}

// --- Utilitaires JSON / texte -------------------------------------------------------------

fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val v = optLong(key, Long.MIN_VALUE)
    return if (v == Long.MIN_VALUE) null else v
}

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
}

private val COMBINING_MARKS = Regex("\\p{M}+")
private val LABEL_SEPARATORS = Regex("[\\s\\-'’]+")

/** Normalise un libellé pour les comparaisons (minuscules, sans accents ni espaces superflus). */
fun normalizeLabel(s: String?): String {
    if (s == null) return ""
    val decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
    return decomposed.replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(LABEL_SEPARATORS, " ")
        .trim()
}
