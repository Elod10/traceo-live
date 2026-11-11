package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max

// Réseaux de transport que l'application sait afficher : l'Île-de-France, connue d'office, et ceux
// du catalogue de Tracéo (`/api/reseaux`).
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/** Emprise d'un réseau : le rectangle qui contient ses arrêts, avec une marge. */
data class BoundingBox(val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double) {

    fun toJson(): JSONObject = JSONObject()
        .put("latMin", latMin)
        .put("latMax", latMax)
        .put("lonMin", lonMin)
        .put("lonMax", lonMax)

    companion object {
        fun fromJson(o: JSONObject): BoundingBox? {
            val values = listOf("latMin", "latMax", "lonMin", "lonMax").map { o.optDouble(it) }
            if (values.any { it.isNaN() }) return null
            return BoundingBox(values[0], values[1], values[2], values[3])
        }
    }
}

/** Source et licence des données d'un réseau, telles que le catalogue demande de les citer. */
data class Attribution(
    /** Qui publie les données : « TCAT, Troyes Champagne Métropole ». */
    val source: String,
    /** Page du jeu de données. */
    val url: String?,
    /** Nom court de la licence : « ODbL ». */
    val licence: String?,
    val licenceUrl: String?,
    /** Mention complète, prête à afficher. */
    val text: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("source", source)
        .putOpt("url", url)
        .putOpt("licence", licence)
        .putOpt("licenceUrl", licenceUrl)
        .putOpt("text", text)

    companion object {
        fun fromJson(o: JSONObject): Attribution? {
            val source = o.optStringOrNull("source") ?: return null
            return Attribution(
                source = source,
                url = o.optStringOrNull("url"),
                licence = o.optStringOrNull("licence"),
                licenceUrl = o.optStringOrNull("licenceUrl"),
                text = o.optStringOrNull("text"),
            )
        }
    }
}

/** État d'un réseau d'après le catalogue. */
enum class NetworkStatus(val key: String) {
    OK("ok"),

    /** Le flux du réseau est muet : ses derniers passages vieillissent. */
    STALE("stale"),

    /** Le réseau est coupé : aucun passage n'est servi. */
    OFF("off");

    companion object {
        /** Un état inconnu ne condamne pas le réseau : c'est la route des passages qui tranchera. */
        fun fromKey(key: String?): NetworkStatus = entries.firstOrNull { it.key == key } ?: OK
    }
}

/** Un réseau, dans la forme du catalogue de Tracéo. */
data class Network(
    /** Minuscules, chiffres et tirets : il sert dans les chemins des routes. */
    val id: String,
    /** Nom du réseau : « TCAT ». */
    val name: String,
    /** Ville ou région desservie : « Troyes ». */
    val city: String,
    val bbox: BoundingBox,
    /** Modes du réseau, dans le vocabulaire de l'application (cf. [Networks.appMode]). */
    val modes: List<String>,
    /** Cadence de relevé visée par Tracéo, en secondes. */
    val refreshSec: Int,
    val status: NetworkStatus = NetworkStatus.OK,
    /** Null pour l'Île-de-France, dont les mentions sont écrites dans l'application. */
    val attribution: Attribution? = null,
    /** Date de compilation des horaires : elle change quand lignes, arrêts et index sont à recharger. */
    val indexAt: Long? = null,
) {
    val isIdfm: Boolean get() = Networks.isIdfm(id)

    /** Cadence de relevé en minutes entières : inutile d'actualiser un widget plus souvent. */
    val sourceMinutes: Int get() = max(1, ceil(refreshSec / 60.0).toInt())

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("city", city)
        .put("bbox", bbox.toJson())
        .put("modes", JSONArray(modes))
        .put("refreshSec", refreshSec)
        .put("status", status.key)
        .putOpt("attribution", attribution?.toJson())
        .putOpt("indexAt", indexAt)

    companion object {
        /** Cadence supposée d'un réseau qui ne l'annonce pas : celle du relevé francilien. */
        const val DEFAULT_REFRESH_SEC = 120

        /** Null si l'entrée est inutilisable : sans identifiant sûr pour un chemin, ou sans emprise. */
        fun fromJson(o: JSONObject): Network? {
            val id = o.optStringOrNull("id")?.takeIf { Networks.isValidId(it) } ?: return null
            val bbox = o.optJSONObject("bbox")?.let(BoundingBox::fromJson) ?: return null
            val name = o.optStringOrNull("name") ?: id
            return Network(
                id = id,
                name = name,
                city = o.optStringOrNull("city") ?: name,
                bbox = bbox,
                modes = o.optJSONArray("modes").toStringList().map(Networks::appMode),
                refreshSec = o.optInt("refreshSec", DEFAULT_REFRESH_SEC).takeIf { it > 0 } ?: DEFAULT_REFRESH_SEC,
                status = NetworkStatus.fromKey(o.optStringOrNull("status")),
                attribution = o.optJSONObject("attribution")?.let(Attribution::fromJson),
                // Date ISO dans la réponse de Tracéo, millisecondes dans le fichier du téléphone.
                indexAt = SiriParser.parseTime(o.optStringOrNull("indexAt")) ?: o.optLongOrNull("indexAt"),
            )
        }
    }
}

object Networks {

    /**
     * L'Île-de-France, réseau d'origine de l'application. C'est aussi le réseau de tout ce qui a été
     * enregistré avant l'arrivée des autres : une configuration sans réseau est francilienne.
     */
    const val IDFM = "idfm"

    fun isIdfm(networkId: String): Boolean = networkId == IDFM

    private val VALID_ID = Regex("[a-z0-9][a-z0-9-]*")

    /** Vrai si l'identifiant peut entrer tel quel dans le chemin d'une route. */
    fun isValidId(networkId: String): Boolean = VALID_ID.matches(networkId)

    /**
     * Le catalogue nomme les modes `metro`, `tram`, `rail`, `bus`, `boat`, `cable` et `funicular` ;
     * l'application garde le vocabulaire du référentiel IDFM, où le câble s'appelle `cableway`.
     */
    fun appMode(catalogMode: String): String = if (catalogMode == "cable") "cableway" else catalogMode

    /**
     * L'Île-de-France ne figure pas au catalogue : l'application la connaît d'office. Son emprise est
     * large, car ses lignes sortent de la région (Montargis, Dreux, Château-Thierry, Creil, Gisors).
     */
    val ILE_DE_FRANCE = Network(
        id = IDFM,
        name = "Île-de-France Mobilités",
        city = "Île-de-France",
        bbox = BoundingBox(latMin = 47.9, latMax = 49.5, lonMin = 1.1, lonMax = 3.7),
        modes = listOf("metro", "rail", "tram", "bus", "cableway", "funicular"),
        refreshSec = DataSources.TRACEO_MIN_REFRESH_MINUTES * 60,
    )
}

/** Les réseaux connus du téléphone : l'Île-de-France, puis ceux du dernier catalogue reçu. */
class NetworkCatalog(others: List<Network> = emptyList(), val savedAt: Long = 0L) {

    val networks: List<Network> = listOf(Networks.ILE_DE_FRANCE) + others.filter { !it.isIdfm }.distinctBy { it.id }

    /** Les réseaux du catalogue de Tracéo, sans l'Île-de-France. */
    val others: List<Network> get() = networks.drop(1)

    fun byId(networkId: String): Network? = networks.firstOrNull { it.id == networkId }

    fun toJson(): JSONObject = JSONObject()
        .put("savedAt", savedAt)
        .put("networks", JSONArray().also { array -> others.forEach { array.put(it.toJson()) } })

    companion object {
        /** Lit la réponse de `/api/reseaux`, ou le fichier que le téléphone en a gardé. */
        @Throws(IOException::class)
        fun parse(body: String, savedAt: Long? = null): NetworkCatalog {
            val root = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IOException("Catalogue des réseaux illisible", e)
            }
            val array = root.optJSONArray("networks") ?: throw IOException("Catalogue des réseaux illisible")
            val networks = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(Network::fromJson) }
            return NetworkCatalog(networks, savedAt ?: root.optLong("savedAt"))
        }
    }
}

/**
 * Le catalogue sur le téléphone. Il sert sans réseau : l'emprise des réseaux, pour reconnaître la ville
 * où l'on est, leur cadence et leurs mentions. Sans fichier, seule l'Île-de-France est connue.
 */
class NetworkCatalogStore(private val file: File, private val download: () -> List<Network> = { TraceoClient().networks() }) {

    fun read(): NetworkCatalog {
        val stamp = if (file.exists()) file.lastModified() else 0L
        synchronized(LOCK) { memory[file.path]?.takeIf { it.first == stamp }?.let { return it.second } }
        val catalog = if (stamp == 0L) {
            NetworkCatalog()
        } else {
            try {
                NetworkCatalog.parse(file.readText())
            } catch (e: IOException) {
                NetworkCatalog()
            }
        }
        synchronized(LOCK) { memory[file.path] = stamp to catalog }
        return catalog
    }

    /** Vrai s'il faut redemander le catalogue : jamais reçu, ou depuis plus d'un jour. */
    fun isStale(now: Long): Boolean {
        val savedAt = read().savedAt
        // Une date d'enregistrement dans le futur vient d'une horloge déréglée : elle ne prouve rien.
        return savedAt <= 0L || now - savedAt !in 0 until MAX_AGE_MS
    }

    /** Redemande le catalogue à Tracéo. En cas d'échec, le fichier précédent reste en place. */
    @Throws(IOException::class)
    fun refresh(now: Long = System.currentTimeMillis()): NetworkCatalog {
        val catalog = NetworkCatalog(download(), now)
        synchronized(LOCK) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(catalog.toJson().toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                    throw IOException("Écriture du catalogue impossible")
                }
            }
            memory.remove(file.path)
        }
        return read()
    }

    /**
     * Le catalogue, redemandé s'il est ancien ; à défaut de réponse, celui du téléphone. Après un
     * échec, il n'est pas redemandé avant [RETRY_AFTER_MS] : un widget ne doit pas insister à chaque
     * actualisation auprès d'un serveur qui ne répond pas.
     */
    fun fresh(now: Long = System.currentTimeMillis()): NetworkCatalog {
        if (!isStale(now)) return read()
        val failedAt = synchronized(LOCK) { failures[file.path] }
        if (failedAt != null && now - failedAt in 0 until RETRY_AFTER_MS) return read()
        return try {
            refresh(now).also { synchronized(LOCK) { failures.remove(file.path) } }
        } catch (e: IOException) {
            synchronized(LOCK) { failures[file.path] = now }
            read()
        }
    }

    companion object {
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val RETRY_AFTER_MS = 10L * 60 * 1000
        private val LOCK = Any()
        private val failures = HashMap<String, Long>()

        /** Dernière lecture de chaque fichier : le catalogue est consulté à chaque dessin d'un widget. */
        private val memory = HashMap<String, Pair<Long, NetworkCatalog>>()
    }
}
