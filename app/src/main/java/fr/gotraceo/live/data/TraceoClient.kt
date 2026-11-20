package fr.gotraceo.live.data

import fr.gotraceo.live.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Erreur lors d'un appel à Tracéo, avec un message lisible par l'utilisateur.
 *
 * C'est une [IOException] pour que le référentiel se replie sur son cache périmé, comme avec l'open data.
 */
class TraceoException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
    /** Ce que Tracéo dit ne pas connaître, sur les routes d'un réseau ; null s'il ne le précise pas. */
    val missing: Missing? = null,
) : IOException(message, cause) {
    enum class Kind { BAD_REQUEST, NOT_FOUND, RATE_LIMITED, UNAVAILABLE, SERVER, NETWORK, INVALID_RESPONSE }

    /** Ce qu'un 404 des routes d'un réseau désigne : le réseau, la ligne, ou l'arrêt sur cette ligne. */
    enum class Missing { NETWORK, LINE, STOP }
}

/**
 * Client de Tracéo (https://gotraceo.fr), accès par défaut aux données PRIM quand l'usager n'a pas de
 * clé : Tracéo interroge PRIM et sert ses lignes, ses arrêts et son cache de passages SIRI ET.
 * Aucune clé ni en-tête particulier.
 *
 * Tracéo sert aussi d'autres réseaux que l'Île-de-France, par les routes `/api/reseaux` : même forme de
 * réponse, mais des identifiants opaques, propres à chaque réseau, qui partent tels quels.
 *
 * Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.
 *
 * @param warmedLines lignes dont les arrêts ont déjà été redemandés après un 503
 * @param clock horloge du téléphone, pour ramener l'heure d'un réveil à la sienne
 */
class TraceoClient(
    private val baseUrl: String = BASE_URL,
    private val warmedLines: MutableSet<String> = warmedInThisProcess,
    private val clock: () -> Long = System::currentTimeMillis,
) : PassageSource {

    companion object {
        /**
         * Adresse de Tracéo. Un build de release ne connaît que la production ; un build de debug peut
         * en viser une autre pour un essai, lue à la compilation dans un fichier que git ne suit pas
         * (`traceo.debugUrl` de `local.properties`).
         */
        val BASE_URL: String = BuildConfig.TRACEO_URL
        const val SOURCE_NAME = "Tracéo"

        /** Version complète de la mention, quand la place le permet : les données restent celles de PRIM. */
        const val SOURCE_DETAIL = "PRIM via Tracéo"

        private const val LINE_PREFIX = "line:IDFM:"
        private const val STOP_PREFIX = "stop_area:IDFM:"

        /** Délai de lecture des listes : `/api/lines` pèse environ 32 Ko, `/api/stations` peut partir à froid. */
        private const val LIST_READ_TIMEOUT_MS = 30_000
        private const val PASSAGES_READ_TIMEOUT_MS = 12_000
        private const val UNKNOWN_STOP_MESSAGE = "Arrêt ou ligne inconnus de Tracéo : reconfigurez ce widget."
        private const val RECONFIGURE = " : reconfigurez ce widget."

        /** Un 503 des routes d'un réseau : il est coupé, ou ses horaires ne sont pas encore compilés. */
        const val NETWORK_UNAVAILABLE_MESSAGE = "Ce réseau est momentanément indisponible chez Tracéo."

        /** Le serveur ne sert pas les routes des réseaux : le widget n'y est pour rien. */
        const val ROUTES_MISSING_MESSAGE = "Tracéo ne sert pas ce réseau pour le moment."

        private val warmedInThisProcess: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Vrai si l'identifiant est une zone d'arrêt de Tracéo, et non un arrêt du référentiel IDFM. */
        fun isTraceoStop(stopId: String): Boolean = stopId.startsWith(STOP_PREFIX)

        private fun lineUrn(lineId: String): String = LINE_PREFIX + Referential.bareLineId(lineId)
    }

    /** Les réseaux servis en plus de l'Île-de-France, qui ne figure pas à ce catalogue. */
    @Throws(TraceoException::class)
    fun networks(): List<Network> {
        val root = get("$baseUrl/api/reseaux", PASSAGES_READ_TIMEOUT_MS, "Catalogue des réseaux introuvable chez Tracéo.")
        val networks = root.optJSONArray("networks") ?: throw invalidResponse()
        return (0 until networks.length()).mapNotNull { i -> networks.optJSONObject(i)?.let(Network::fromJson) }
    }

    /** Toutes les lignes d'un réseau, triées comme celles du référentiel. */
    @Throws(TraceoException::class)
    fun lines(networkId: String = Networks.IDFM): List<Line> {
        val idfm = Networks.isIdfm(networkId)
        val url = if (idfm) "$baseUrl/api/lines" else networkUrl(networkId) + "/lines"
        val root = get(url, LIST_READ_TIMEOUT_MS, "Liste des lignes introuvable chez Tracéo.", networkRoute(idfm))
        val lines = root.optJSONArray("lines") ?: throw invalidResponse()
        return (0 until lines.length())
            .mapNotNull { i -> lines.optJSONObject(i)?.let { if (idfm) parseLine(it) else parseNetworkLine(it, networkId) } }
            .sortedWith(Referential.lineComparator)
    }

    /**
     * Zones d'arrêt d'une ligne. À n'appeler qu'à la configuration, jamais dans la boucle
     * d'actualisation : à froid, cet appel coûte du quota à Tracéo.
     */
    @Throws(TraceoException::class)
    fun stops(lineId: String, networkId: String = Networks.IDFM): List<Stop> {
        val idfm = Networks.isIdfm(networkId)
        val url = if (idfm) {
            "$baseUrl/api/stations?line=" + Http.encode(lineUrn(lineId))
        } else {
            networkUrl(networkId) + "/stations?line=" + Http.encode(lineId)
        }
        val root = get(url, LIST_READ_TIMEOUT_MS, "Cette ligne est inconnue de Tracéo.", networkRoute(idfm))
        val stations = root.optJSONArray("stations") ?: throw invalidResponse()
        return (0 until stations.length()).mapNotNull { i ->
            stations.optJSONObject(i)?.let { if (idfm) parseStop(it) else parseNetworkStop(it, networkId) }
        }
    }

    /**
     * Index des arrêts d'un réseau hors Île-de-France, pour chercher les arrêts proches sur le
     * téléphone. Demander l'index d'un réseau dit seulement à Tracéo qu'on s'y intéresse : la
     * position n'est pas envoyée.
     */
    @Throws(TraceoException::class)
    fun index(networkId: String): NetworkIndex {
        val root = get(
            networkUrl(networkId) + "/index", LIST_READ_TIMEOUT_MS, "Index des arrêts introuvable chez Tracéo.", NetworkRoute.LIST,
        )
        val linesJson = root.optJSONArray("lines") ?: throw invalidResponse()
        val stopsJson = root.optJSONArray("stops") ?: throw invalidResponse()
        // Les arrêts désignent leurs lignes par leur position : une ligne illisible garde donc sa place.
        val lines = (0 until linesJson.length()).map { i -> linesJson.optJSONObject(i)?.let { parseNetworkLine(it, networkId) } }
        var skipped = 0
        val stops = (0 until stopsJson.length()).mapNotNull { i ->
            val stop = stopsJson.optJSONObject(i)?.let { parseIndexedStop(it, lines) }
            if (stop == null) skipped++
            stop
        }
        return NetworkIndex(SiriParser.parseTime(root.optStringOrNull("builtAt")), lines.filterNotNull(), stops, skipped)
    }

    private fun parseIndexedStop(o: JSONObject, lines: List<Line?>): IndexedStop? {
        val zone = o.optStringOrNull("zone") ?: return null
        val name = o.optStringOrNull("name") ?: return null
        val lat = o.optDouble("lat")
        val lon = o.optDouble("lon")
        if (lat.isNaN() || lon.isNaN()) return null
        val served = o.optJSONArray("lines") ?: return null
        val lineIds = (0 until served.length()).mapNotNull { i -> lines.getOrNull(served.optInt(i, -1))?.id }.distinct()
        if (lineIds.isEmpty()) return null
        return IndexedStop(name = name, town = o.optStringOrNull("town"), lat = lat, lon = lon, zone = zone, lineIds = lineIds)
    }

    /**
     * Prochains passages d'une ligne à une zone d'arrêt, en Île-de-France.
     *
     * Un 503 peut venir d'arrêts absents du cache de Tracéo : les demander une fois par ligne et par
     * processus le remplit, sans insister si la route est coupée.
     */
    @Throws(TraceoException::class)
    fun departures(stopId: String, lineId: String): PassageSnapshot = try {
        requestPassages(stopId, lineId)
    } catch (e: TraceoException) {
        if (e.kind != TraceoException.Kind.UNAVAILABLE || !warmedLines.add(Referential.bareLineId(lineId))) throw e
        try {
            stops(lineId)
        } catch (stationsError: TraceoException) {
            throw e
        }
        requestPassages(stopId, lineId)
    }

    /**
     * Prochains passages d'une ligne à une station d'un réseau. Hors Île-de-France, un 503 ne se
     * rattrape pas en demandant les arrêts : le réseau est coupé, ou son relevé n'est pas prêt.
     */
    @Throws(TraceoException::class)
    fun departures(stopId: String, lineId: String, networkId: String): PassageSnapshot {
        if (Networks.isIdfm(networkId)) return departures(stopId, lineId)
        val url = networkUrl(networkId) + "/passages?line=" + Http.encode(lineId) + "&stop=" + Http.encode(stopId)
        val response = request(url, PASSAGES_READ_TIMEOUT_MS, UNKNOWN_STOP_MESSAGE, NetworkRoute.PASSAGES)
        val receivedAt = clock()
        val snapshot = snapshot(json(response), stopId, lineId)
        // Pendant un réveil, le prochain relevé est à quelques secondes : un téléphone dont l'horloge
        // est décalée d'autant manquerait son retour. L'heure annoncée est donc ramenée à celle du
        // téléphone, d'après l'heure du serveur que donne l'en-tête Date de la même réponse.
        if (snapshot.waking != true || snapshot.nextAt == null) return snapshot
        val serverNow = Http.parseDate(response.headers["date"]) ?: return snapshot
        return snapshot.copy(nextAt = receivedAt + (snapshot.nextAt - serverNow))
    }

    override fun passages(stopId: String, lineId: String): PassageSnapshot = departures(stopId, lineId)

    /** Les passages d'un réseau, vus comme une source : en Île-de-France, ce client lui-même. */
    fun forNetwork(networkId: String): PassageSource =
        if (Networks.isIdfm(networkId)) this else PassageSource { stopId, lineId -> departures(stopId, lineId, networkId) }

    /** Racine des routes d'un réseau. Son identifiant vient du catalogue : il doit pouvoir entrer dans un chemin. */
    private fun networkUrl(networkId: String): String {
        if (!Networks.isValidId(networkId)) {
            throw TraceoException(TraceoException.Kind.BAD_REQUEST, "Réseau inconnu : reconfigurez ce widget.")
        }
        return "$baseUrl/api/reseaux/$networkId"
    }

    private fun requestPassages(stopId: String, lineId: String): PassageSnapshot {
        val line = lineUrn(lineId)
        val url = "$baseUrl/api/widget/passages?line=" + Http.encode(line) + "&stop=" + Http.encode(stopId)
        return snapshot(get(url, PASSAGES_READ_TIMEOUT_MS, UNKNOWN_STOP_MESSAGE), stopId, line)
    }

    /** Les deux routes de passages ont le même corps. */
    private fun snapshot(root: JSONObject, stopId: String, line: String): PassageSnapshot {
        val passages = root.optJSONArray("passages") ?: throw invalidResponse()
        return PassageSnapshot(
            departures = (0 until passages.length()).mapNotNull { i ->
                passages.optJSONObject(i)?.let { parsePassage(it, stopId, line) }
            },
            dataAt = SiriParser.parseTime(root.optStringOrNull("fetchedAt")),
            fresh = root.optBoolean("fresh", false),
            // Absent des anciennes versions de Tracéo, null quand la collecte est suspendue.
            nextAt = SiriParser.parseTime(root.optStringOrNull("nextAt")),
            viaTraceo = true,
            waking = if (root.has("waking") && !root.isNull("waking")) root.optBoolean("waking") else null,
            // Un statut inconnu ne dit rien : contrairement au catalogue, il n'est pas pris pour `ok`.
            status = root.optStringOrNull("status")?.let { key -> NetworkStatus.entries.firstOrNull { it.key == key } },
        )
    }

    /** Les routes d'un réseau hors Île-de-France disent ce qui manque ; seule celle des passages vient d'un widget. */
    private enum class NetworkRoute { LIST, PASSAGES }

    private fun networkRoute(idfm: Boolean): NetworkRoute? = if (idfm) null else NetworkRoute.LIST

    /**
     * @param network route d'un réseau hors Île-de-France, null pour le catalogue et les routes
     *        franciliennes, dont les messages ne changent pas
     */
    private fun get(url: String, readTimeoutMs: Int, notFoundMessage: String, network: NetworkRoute? = null): JSONObject =
        json(request(url, readTimeoutMs, notFoundMessage, network))

    /** La réponse d'une route, si elle a réussi ; ses en-têtes restent lisibles. */
    private fun request(url: String, readTimeoutMs: Int, notFoundMessage: String, network: NetworkRoute? = null): Http.Response {
        val response = try {
            Http.get(url, readTimeoutMs = readTimeoutMs)
        } catch (e: IOException) {
            throw TraceoException(TraceoException.Kind.NETWORK, "Erreur réseau : ${e.message ?: e.javaClass.simpleName}", e)
        }
        when (response.code) {
            in 200..299 -> Unit
            400 -> throw TraceoException(TraceoException.Kind.BAD_REQUEST, "Requête refusée par Tracéo (HTTP 400).")
            404 -> throw notFound(response.body, notFoundMessage, network)
            429 -> throw TraceoException(
                TraceoException.Kind.RATE_LIMITED,
                "Trop de requêtes vers Tracéo : nouvel essai à la prochaine actualisation.",
            )
            503 -> throw TraceoException(
                TraceoException.Kind.UNAVAILABLE,
                if (network != null) NETWORK_UNAVAILABLE_MESSAGE else "Tracéo est momentanément indisponible.",
            )
            else -> throw TraceoException(TraceoException.Kind.SERVER, "Erreur de Tracéo (HTTP ${response.code}).")
        }
        return response
    }

    private fun json(response: Http.Response): JSONObject = try {
        JSONObject(response.body)
    } catch (e: Exception) {
        throw invalidResponse(e)
    }

    /**
     * Un 404 des routes d'un réseau dit ce que Tracéo ne connaît pas, par son `code` : `network`,
     * `line`, `stop`. `route` vient d'un serveur qui ne sert pas ces routes : le widget n'est pas
     * faux, il garde donc ses passages, comme sur un 503. Il en va de même d'un 404 qui n'est pas du
     * JSON : Tracéo répond toujours en JSON, ce 404 vient donc d'un intermédiaire.
     *
     * Sans `code` (serveur d'avant), c'est le message qui le dit : « Réseau inconnu. », « Ligne
     * inconnue. », « Arrêt inconnu sur cette ligne. », « Route inconnue. ». Ce que l'application ne
     * reconnaît pas reste un 404 sans précision.
     */
    private fun notFound(body: String, fallback: String, network: NetworkRoute?): TraceoException {
        if (network == null) return TraceoException(TraceoException.Kind.NOT_FOUND, fallback)
        val error = try {
            JSONObject(body)
        } catch (e: Exception) {
            return TraceoException(TraceoException.Kind.UNAVAILABLE, ROUTES_MISSING_MESSAGE)
        }
        val said = normalizeLabel(error.optStringOrNull("error")).trimEnd('.').trim()
        val code = error.optStringOrNull("code") ?: when {
            said == "route inconnue" -> "route"
            said == "reseau inconnu" -> "network"
            said == "ligne inconnue" -> "line"
            said.startsWith("arret inconnu") -> "stop"
            else -> null
        }
        val (missing, message) = when (code) {
            "route" -> return TraceoException(TraceoException.Kind.UNAVAILABLE, ROUTES_MISSING_MESSAGE)
            "network" -> TraceoException.Missing.NETWORK to "Réseau inconnu de Tracéo"
            "line" -> TraceoException.Missing.LINE to "Ligne inconnue de Tracéo"
            "stop" -> TraceoException.Missing.STOP to "Arrêt inconnu sur cette ligne"
            else -> return TraceoException(TraceoException.Kind.NOT_FOUND, fallback)
        }
        // À la configuration, il n'y a pas de widget à reconfigurer : le message s'arrête au constat.
        val text = if (network == NetworkRoute.PASSAGES) message + RECONFIGURE else "$message."
        return TraceoException(TraceoException.Kind.NOT_FOUND, text, missing = missing)
    }

    private fun invalidResponse(cause: Throwable? = null) =
        TraceoException(TraceoException.Kind.INVALID_RESPONSE, "Réponse inattendue de Tracéo.", cause)

    private fun parseLine(o: JSONObject): Line? {
        val id = o.optStringOrNull("id")?.let(Referential::bareLineId) ?: return null
        val code = o.optStringOrNull("code")
        val name = o.optStringOrNull("name") ?: code ?: id
        val modeKey = o.optJSONObject("mode")?.optStringOrNull("key").orEmpty()
        return Line(
            id = id,
            name = name,
            shortName = code ?: name,
            // Comme dans le référentiel IDFM : Line.isRer() sépare ensuite RER et Transilien.
            mode = if (modeKey == "rer" || modeKey == "transilien") "rail" else modeKey,
            subMode = o.optStringOrNull("submode"),
            network = o.optStringOrNull("network"),
            operator = o.optJSONArray("operators").toStringList().firstOrNull(),
            color = o.optStringOrNull("color")?.let(Referential::cleanColor),
            textColor = o.optStringOrNull("textColor")?.let(Referential::cleanColor),
        )
    }

    /** Ce sont des zones d'arrêt, pas des quais : un seul identifiant par arrêt, et pas de commune. */
    private fun parseStop(o: JSONObject): Stop? {
        val id = o.optStringOrNull("id") ?: return null
        val name = o.optStringOrNull("name") ?: return null
        return Stop(name = name, town = null, stopIds = listOf(id))
    }

    /** L'identifiant d'une ligne d'un autre réseau est opaque : il est gardé entier, deux-points compris. */
    private fun parseNetworkLine(o: JSONObject, networkId: String): Line? {
        val id = o.optStringOrNull("id") ?: return null
        val code = o.optStringOrNull("code")
        val name = o.optStringOrNull("name") ?: code ?: id
        return Line(
            id = id,
            name = name,
            shortName = code ?: name,
            mode = Networks.appMode(o.optJSONObject("mode")?.optStringOrNull("key").orEmpty()),
            color = o.optStringOrNull("color")?.let(Referential::cleanColor),
            textColor = o.optStringOrNull("textColor")?.let(Referential::cleanColor),
            networkId = networkId,
            // Absent, le champ ne dit rien : la ligne est supposée avoir du temps réel.
            realtime = o.optBoolean("realtime", true),
        )
    }

    /** Une station d'un autre réseau : tous les quais de l'arrêt, dans les deux sens. */
    private fun parseNetworkStop(o: JSONObject, networkId: String): Stop? {
        val id = o.optStringOrNull("id") ?: return null
        val name = o.optStringOrNull("name") ?: return null
        return Stop(name = name, town = o.optStringOrNull("town"), stopIds = listOf(id), networkId = networkId)
    }

    private fun parsePassage(o: JSONObject, stopId: String, line: String): Departure? {
        val expected = SiriParser.parseTime(o.optStringOrNull("expected"))
        val aimed = SiriParser.parseTime(o.optStringOrNull("aimed"))
        if (expected == null && aimed == null) return null
        return Departure(
            lineRef = line,
            destination = o.optStringOrNull("destination")?.trim() ?: "?",
            // Pas de nom de direction : les sens se regroupent par destination.
            directionName = null,
            directionRef = o.optStringOrNull("direction"),
            expectedArrival = expected,
            expectedDeparture = null,
            aimedArrival = aimed,
            aimedDeparture = null,
            atStop = o.optBoolean("atStop"),
            platform = null,
            status = o.optStringOrNull("status"),
            journeyNote = o.optStringOrNull("mission"),
            stopId = stopId,
        )
    }
}
