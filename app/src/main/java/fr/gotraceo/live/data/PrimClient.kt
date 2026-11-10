package fr.gotraceo.live.data

import java.io.IOException
import java.util.Locale

/** Erreur lors d'un appel à l'API PRIM, avec un message lisible par l'utilisateur. */
class PrimException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NO_KEY, AUTH, QUOTA, NETWORK, UNKNOWN_STOP, SERVER, INVALID_RESPONSE }
}

/**
 * Client de l'API PRIM d'Île-de-France Mobilités : requête unitaire « Prochains passages »
 * (SIRI Lite StopMonitoring).
 *
 * Documentation : https://prim.iledefrance-mobilites.fr (API « Prochains passages (plateforme IDFM) - requête unitaire »).
 */
class PrimClient(
    private val apiKey: String,
    private val stopMonitoringUrl: String = STOP_MONITORING_URL,
    private val formats: FormatCache = FormatCache.InMemory(),
    private val usage: UsageListener = UsageListener.NONE,
) : PassageSource {

    /**
     * Suit la consommation du quota journalier de la clé : chaque réponse de PRIM compte, et PRIM
     * indique dans ses en-têtes ce qu'il reste pour la journée.
     */
    interface UsageListener {
        /** Une requête a reçu une réponse. Les valeurs viennent des en-têtes de PRIM, null si absents. */
        fun onResponse(remainingDay: Int?, limitDay: Int?) = Unit

        /** PRIM refuse la requête : le quota du jour est épuisé. */
        fun onQuotaExceeded() = Unit

        companion object {
            val NONE = object : UsageListener {}
        }
    }

    /**
     * Mémorise, pour chaque couple point d'arrêt / ligne, le format de requête accepté par PRIM
     * (MonitoringRef et présence du filtre LineRef), pour ne pas refaire les essais de repli
     * à chaque actualisation.
     */
    interface FormatCache {
        fun get(key: String): String?
        fun put(key: String, value: String)
        fun remove(key: String)

        class InMemory : FormatCache {
            private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
            override fun get(key: String): String? = map[key]
            override fun put(key: String, value: String) { map[key] = value }
            override fun remove(key: String) { map.remove(key) }
        }
    }


    companion object {
        const val STOP_MONITORING_URL = "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring"

        /** Mention de la source devant l'heure de mise à jour, avec une clé. */
        const val SOURCE_NAME = "PRIM"

        /** En-têtes de PRIM sur le quota journalier, comme les lit Tracéo. */
        const val HEADER_REMAINING_DAY = "x-ratelimit-remaining-day"
        const val HEADER_LIMIT_DAY = "x-ratelimit-limit-day"

        /** Point d'arrêt utilisé pour tester la clé (métro 4, Châtelet). */
        const val TEST_MONITORING_REF = "STIF:StopPoint:Q:22092:"

        /**
         * Convertit un identifiant d'arrêt du référentiel IDFM en MonitoringRef(s) SIRI candidats.
         *  - « IDFM:monomodalStopPlace:58879 » (zone d'arrêt, lignes ferrées) → « STIF:StopArea:SP:58879: »
         *  - « IDFM:463158 » (arrêt de référence / quai) → « STIF:StopPoint:Q:463158: »
         *  - « stop_area:IDFM:71348 » (zone d'arrêt d'un widget créé sans clé) → « STIF:StopArea:SP:71348: »
         */
        fun monitoringRefs(stopId: String): List<String> {
            val id = stopId.trim().trimEnd(':').substringAfterLast(':')
            return when {
                stopId.startsWith("STIF:") -> listOf(stopId)
                // Toujours une zone : le même numéro en StopPoint:Q désignerait un autre arrêt.
                stopId.startsWith("stop_area:", ignoreCase = true) -> listOf("STIF:StopArea:SP:$id:")
                stopId.contains("monomodalStopPlace", ignoreCase = true) ||
                    stopId.contains("StopArea", ignoreCase = true) ->
                    listOf("STIF:StopArea:SP:$id:", "STIF:StopPoint:Q:$id:")
                else -> listOf("STIF:StopPoint:Q:$id:")
            }
        }

        /** Vrai si le LineRef SIRI correspond à l'identifiant de ligne IDFM (ex. « C01742 »). */
        fun lineMatches(lineRef: String?, lineId: String): Boolean {
            if (lineRef.isNullOrBlank()) return true
            val normalized = lineRef.uppercase(Locale.ROOT)
            val id = lineId.uppercase(Locale.ROOT)
            return normalized == id || normalized.contains(":$id:") || normalized.endsWith(":$id")
        }

        private fun isUnknownIdentifier(error: String?): Boolean {
            if (error == null) return false
            val e = normalizeLabel(error)
            return e.contains("inconnu") || e.contains("n existe pas") || e.contains("unknown") ||
                e.contains("not found") || e.contains("invalide")
        }

        /** Erreurs SIRI qui signifient simplement « aucun passage prévu ». */
        private fun isNoData(error: String): Boolean {
            val e = normalizeLabel(error)
            return !isUnknownIdentifier(error) &&
                (e.contains("aucun") || e.contains("no data") || e.contains("pas de donnee") || e.contains("no result"))
        }
    }

    /**
     * Récupère les prochains passages d'une ligne à un point d'arrêt du référentiel.
     * Essaie les différents formats de MonitoringRef, puis sans filtre LineRef si le couple
     * MonitoringRef/LineRef est refusé (le filtrage par ligne est alors fait localement).
     */
    @Throws(PrimException::class)
    fun departures(stopId: String, lineId: String?): List<Departure> {
        if (apiKey.isBlank()) throw PrimException(PrimException.Kind.NO_KEY, "Clé API PRIM non configurée")
        val lineRef = lineId?.let { "STIF:Line::$it:" }
        val cacheKey = "$stopId|${lineId.orEmpty()}"

        // Toutes les combinaisons possibles, dans l'ordre de préférence.
        val attempts = monitoringRefs(stopId).flatMap { ref ->
            if (lineRef != null) listOf(ref to lineRef, ref to null) else listOf(ref to null)
        }
        // Le format qui a fonctionné la dernière fois est essayé en premier.
        val remembered = formats.get(cacheKey)?.let { saved -> attempts.firstOrNull { encode(it) == saved } }
        val ordered = if (remembered != null) listOf(remembered) + (attempts - remembered) else attempts

        var lastError: PrimException? = null
        for (attempt in ordered) {
            try {
                val result = request(attempt.first, attempt.second, stopId)
                if (attempt != remembered) formats.put(cacheKey, encode(attempt))
                return if (lineId != null) result.filter { lineMatches(it.lineRef, lineId) } else result
            } catch (e: PrimException) {
                lastError = e
                if (e.kind != PrimException.Kind.UNKNOWN_STOP) throw e
                if (attempt == remembered) formats.remove(cacheKey)
            }
        }
        throw lastError ?: PrimException(PrimException.Kind.UNKNOWN_STOP, "Arrêt inconnu de l'API PRIM")
    }

    override fun passages(stopId: String, lineId: String): PassageSnapshot =
        PassageSnapshot(departures(stopId, lineId))

    private fun encode(attempt: Pair<String, String?>): String = attempt.first + "|" + (attempt.second ?: "")

    /** Vérifie que la clé est acceptée par PRIM. */
    @Throws(PrimException::class)
    fun testKey() {
        try {
            request(TEST_MONITORING_REF, null, "")
        } catch (e: PrimException) {
            // Une erreur fonctionnelle SIRI signifie que la clé a bien été acceptée.
            if (e.kind == PrimException.Kind.UNKNOWN_STOP || e.kind == PrimException.Kind.SERVER) return
            throw e
        }
    }

    @Throws(PrimException::class)
    private fun request(monitoringRef: String, lineRef: String?, stopId: String): List<Departure> {
        val url = buildString {
            append(stopMonitoringUrl)
            append("?MonitoringRef=").append(Http.encode(monitoringRef))
            if (lineRef != null) append("&LineRef=").append(Http.encode(lineRef))
        }
        val response = try {
            Http.get(url, mapOf("apikey" to apiKey.trim()))
        } catch (e: IOException) {
            throw PrimException(PrimException.Kind.NETWORK, "Erreur réseau : ${e.message ?: e.javaClass.simpleName}", e)
        } catch (e: IllegalArgumentException) {
            // Un caractère invisible collé avec la clé (espace insécable, saut de ligne) n'entre pas
            // dans un en-tête HTTP : la requête n'est même pas envoyée.
            throw PrimException(
                PrimException.Kind.AUTH,
                "Clé API illisible : elle contient un caractère qui ne peut pas être envoyé. Collez-la à nouveau.",
                e,
            )
        }

        usage.onResponse(
            response.headers[HEADER_REMAINING_DAY]?.trim()?.toIntOrNull(),
            response.headers[HEADER_LIMIT_DAY]?.trim()?.toIntOrNull(),
        )
        when (response.code) {
            401, 403 -> throw PrimException(
                PrimException.Kind.AUTH,
                "Clé API refusée par PRIM (HTTP ${response.code}). Vérifiez votre jeton.",
            )
            429 -> {
                usage.onQuotaExceeded()
                throw PrimException(PrimException.Kind.QUOTA, "Quota PRIM du jour atteint (HTTP 429).")
            }
        }

        val parsed = SiriParser.parse(response.body, stopId)
        if (!parsed.recognized) {
            val kind = if (response.code >= 500) PrimException.Kind.SERVER else PrimException.Kind.INVALID_RESPONSE
            throw PrimException(kind, "Réponse inattendue de PRIM (HTTP ${response.code})")
        }
        if (parsed.departures.isEmpty() && parsed.errorText != null) {
            if (isNoData(parsed.errorText)) return emptyList()
            val kind = if (isUnknownIdentifier(parsed.errorText)) PrimException.Kind.UNKNOWN_STOP else PrimException.Kind.SERVER
            throw PrimException(kind, "PRIM : ${parsed.errorText}")
        }
        if (response.code !in 200..299 && parsed.departures.isEmpty()) {
            val kind = if (response.code == 400 || response.code == 404) PrimException.Kind.UNKNOWN_STOP else PrimException.Kind.SERVER
            throw PrimException(kind, "Erreur PRIM (HTTP ${response.code})")
        }
        return parsed.departures
    }
}
