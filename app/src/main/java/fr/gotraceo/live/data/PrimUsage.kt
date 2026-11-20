package fr.gotraceo.live.data

import org.json.JSONObject
import kotlin.math.max

// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/**
 * Consommation du quota journalier de la clé PRIM, pour la journée [day] (« 2026-11-07 », heure de Paris).
 *
 * L'application compte ses propres requêtes ; quand PRIM indique dans ses en-têtes ce qu'il reste pour
 * la journée, c'est cette valeur qui fait foi, car elle compte aussi les autres usages de la même clé.
 */
data class PrimUsage(
    val day: String = "",
    /** Requêtes envoyées par l'application aujourd'hui. */
    val counted: Int = 0,
    /** Reste du jour d'après PRIM, à sa dernière réponse. */
    val remaining: Int? = null,
    /** Quota du jour d'après PRIM. */
    val limit: Int? = null,
    /** Jour où PRIM a refusé une requête pour quota épuisé. */
    val exhaustedDay: String? = null,
    /** Jour où l'usager a été prévenu qu'il approchait du quota. */
    val warnedDay: String? = null,
    /** Jour où l'usager a été prévenu du passage sur Tracéo. */
    val fallbackNoticeDay: String? = null,
) {

    companion object {
        /** Quota d'une clé PRIM standard, quand PRIM ne l'a pas encore indiqué. */
        const val DEFAULT_DAILY_QUOTA = 1_000

        /** Part du quota à partir de laquelle l'usager est prévenu. */
        const val WARNING_SHARE = 0.8

        fun fromJson(raw: String?): PrimUsage {
            if (raw.isNullOrBlank()) return PrimUsage()
            return try {
                val o = JSONObject(raw)
                PrimUsage(
                    day = o.optString("day"),
                    counted = o.optInt("counted"),
                    remaining = if (o.has("remaining")) o.optInt("remaining") else null,
                    limit = if (o.has("limit")) o.optInt("limit") else null,
                    exhaustedDay = o.optString("exhaustedDay").ifEmpty { null },
                    warnedDay = o.optString("warnedDay").ifEmpty { null },
                    fallbackNoticeDay = o.optString("fallbackNoticeDay").ifEmpty { null },
                )
            } catch (e: Exception) {
                PrimUsage()
            }
        }
    }

    /** L'état de [today] : un nouveau jour repart de zéro, en gardant le quota appris de PRIM. */
    fun on(today: String): PrimUsage = if (day == today) this else PrimUsage(day = today, limit = limit)

    val quota: Int get() = limit?.takeIf { it > 0 } ?: DEFAULT_DAILY_QUOTA

    /** Requêtes consommées aujourd'hui : d'après PRIM si possible, sinon d'après l'application. */
    val used: Int get() = remaining?.let { max(counted, quota - it) } ?: counted

    /** Une réponse de PRIM vient d'arriver. */
    fun recorded(today: String, remainingDay: Int?, limitDay: Int?): PrimUsage {
        val current = on(today)
        return current.copy(
            counted = current.counted + 1,
            remaining = remainingDay ?: current.remaining,
            limit = limitDay?.takeIf { it > 0 } ?: current.limit,
        )
    }

    fun exceeded(today: String): PrimUsage = on(today).copy(exhaustedDay = today)

    /** Vrai si PRIM a refusé une requête aujourd'hui, ou si le quota est consommé. */
    fun isExhausted(today: String): Boolean {
        val current = on(today)
        return current.exhaustedDay == today || current.used >= current.quota
    }

    /** Vrai s'il faut prévenir l'usager qu'il approche du quota (une fois par jour). */
    fun needsWarning(today: String): Boolean {
        val current = on(today)
        return current.warnedDay != today && !current.isExhausted(today) && current.used >= current.quota * WARNING_SHARE
    }

    fun toJson(): String = JSONObject().apply {
        put("day", day)
        put("counted", counted)
        remaining?.let { put("remaining", it) }
        limit?.let { put("limit", it) }
        exhaustedDay?.let { put("exhaustedDay", it) }
        warnedDay?.let { put("warnedDay", it) }
        fallbackNoticeDay?.let { put("fallbackNoticeDay", it) }
    }.toString()
}

/** Jour du quota PRIM, à l'heure de Paris. */
object PrimDay {
    private val zone = java.time.ZoneId.of("Europe/Paris")

    fun today(now: Long = System.currentTimeMillis()): String =
        java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
}

/**
 * Échéances d'actualisation des widgets.
 *
 * Sans clé, Tracéo indique quand il servira son prochain relevé (`nextAt`) : interroger avant ne
 * rapporte que le relevé déjà affiché. Chaque installation ajoute son propre décalage, tiré au hasard,
 * pour que les téléphones ne l'interrogent pas tous à la même seconde.
 */
object RefreshSchedule {

    /** Écart minimal entre deux actualisations automatiques d'un widget qui suit Tracéo. */
    const val TRACEO_MIN_GAP_MS = 30_000L

    /** Marge, car une alarme peut arriver un peu tôt. */
    const val EARLY_MS = 15_000L

    /**
     * Un réseau hors Île-de-France n'est relevé que quand on le demande. Quand il dormait, sa réponse
     * n'a pas de relevé récent et annonce le prochain pour dans quelques secondes : c'est l'heure de
     * son réveil. Au-delà de ce délai, l'annonce est celle de sa cadence ordinaire. Quand Tracéo dit
     * lui-même que le réseau se réveille, ce délai borne seulement l'heure du retour.
     */
    const val WAKE_WINDOW_MS = 20_000L

    /** Marge après l'heure annoncée du réveil : le premier relevé peut tarder un peu. */
    const val WAKE_MARGIN_MS = 2_000L

    /** Étalement des retours entre les installations, en plus de la marge. */
    const val WAKE_SPREAD_MS = 4_000L

    /** Écart minimal avant de redemander un réseau qui se réveille. */
    const val WAKE_MIN_GAP_MS = 5_000L

    /** Passé ce délai après l'heure annoncée, le réveil n'est plus attendu : l'intervalle reprend. */
    const val WAKE_GRACE_MS = 30_000L

    /**
     * Retours accordés à la suite pour un même réveil. Sans l'indication de Tracéo, une réponse sans
     * relevé récent qui annonce le prochain tout près n'est pas toujours un réveil : un flux en panne
     * en donne aussi. Avec elle, un réveil qui dure au-delà de deux retours n'est plus normal. Sans
     * cette borne, le widget redemanderait toutes les quelques secondes tant que cela dure.
     */
    const val WAKE_MAX_RETURNS = 2

    /**
     * Heure à laquelle revenir après une réponse, ou null si elle n'est pas celle d'un réseau qui se
     * réveille. Le widget y revient au lieu d'attendre un intervalle entier sans passage.
     * L'Île-de-France, relevée en continu, ne se réveille pas.
     *
     * Tracéo dit lui-même si le réseau se réveille (`waking`) : c'est alors sa réponse qui tranche,
     * et un flux en panne n'est plus pris pour un réveil. Sans cette indication (ancien serveur), le
     * réveil se déduit d'une réponse sans relevé récent qui annonce le prochain dans quelques
     * secondes. Dans les deux cas, les retours sont bornés.
     *
     * Chaque widget en décide d'après ses propres réponses : celle d'un autre widget du même
     * réseau, arrivée après le relevé, ne doit pas annuler son retour.
     *
     * @param fresh fraîcheur du relevé reçu, faux aussi quand le réseau n'a jamais été relevé
     * @param nextAt `nextAt` de cette réponse, null si le réseau n'annonce rien
     * @param now heure de la réponse
     * @param returnsMade retours déjà faits à la suite pour ce réveil (cf. [wakeReturnsMade])
     * @param waking ce que Tracéo dit du réveil, null s'il ne dit rien
     */
    fun wakeAt(networkId: String, fresh: Boolean?, nextAt: Long?, now: Long, returnsMade: Int, waking: Boolean? = null): Long? {
        if (Networks.isIdfm(networkId) || nextAt == null || nextAt <= 0L || returnsMade >= WAKE_MAX_RETURNS) return null
        if (waking != null) {
            // Une heure annoncée hors de propos (horloge du téléphone décalée, en-tête Date absent)
            // ne doit ni faire manquer le retour, ni le repousser au-delà du délai d'un réveil.
            return if (waking) nextAt.coerceIn(now, now + WAKE_WINDOW_MS) else null
        }
        if (fresh != false || nextAt - now !in 0..WAKE_WINDOW_MS) return null
        return nextAt
    }

    /**
     * Heure du retour d'après toutes les réponses d'une actualisation (un arrêt qui réunit plusieurs
     * quais, un lot de lignes À proximité) : il suffit que l'une dise que le réseau se réveille.
     */
    fun wakeAt(networkId: String, responses: List<PassageSnapshot>, now: Long, returnsMade: Int): Long? {
        if (responses.isEmpty()) return null
        val said = responses.mapNotNull { it.waking }
        if (said.isNotEmpty()) {
            val nextAt = responses.filter { it.waking == true }.mapNotNull { it.nextAt }.minOrNull()
            return wakeAt(networkId, false, nextAt, now, returnsMade, waking = said.any { it })
        }
        val fresh = responses.mapNotNull { it.fresh }.takeIf { it.isNotEmpty() }?.all { it }
        return wakeAt(networkId, fresh, responses.mapNotNull { it.nextAt }.minOrNull(), now, returnsMade)
    }

    /** Vrai tant que le retour prévu à [wakeAt] est attendu : écran resté éteint, il ne l'est plus. */
    fun awaitsWake(wakeAt: Long?, now: Long): Boolean = wakeAt != null && now <= wakeAt + WAKE_GRACE_MS

    /**
     * Retours déjà faits quand une réponse arrive : un de plus si elle répond à un retour encore
     * attendu, zéro sinon, car c'est alors une actualisation ordinaire.
     */
    fun wakeReturnsMade(previousWakeAt: Long?, previousReturns: Int, now: Long): Int =
        if (awaitsWake(previousWakeAt, now)) previousReturns + 1 else 0

    /** Bornes du décalage propre à chaque installation. */
    const val OFFSET_MIN_MS = 2_000L
    const val OFFSET_MAX_MS = 40_000L

    private const val MINUTE_MS = 60_000L

    /**
     * Date à partir de laquelle un widget doit être actualisé, ou null s'il ne s'actualise qu'au toucher.
     *
     * @param attemptedAt dernière tentative, null si le widget n'a encore rien demandé
     * @param refreshMinutes intervalle appliqué (0 = manuel)
     * @param followsTraceo vrai si le widget passe par Tracéo
     * @param traceoNextAt dernier `nextAt` reçu de Tracéo, null ou 0 si inconnu
     * @param offsetMs décalage de cette installation
     * @param sourceMinutes cadence de relevé du réseau chez Tracéo : 2 minutes en Île-de-France
     * @param wakeAt heure du réveil que le widget attend encore (cf. [wakeAt], [awaitsWake]) : il y
     *        revient quel que soit l'intervalle, même manuel
     */
    fun dueAt(
        attemptedAt: Long?,
        refreshMinutes: Int,
        followsTraceo: Boolean,
        traceoNextAt: Long?,
        offsetMs: Long,
        sourceMinutes: Int = DataSources.TRACEO_MIN_REFRESH_MINUTES,
        wakeAt: Long? = null,
    ): Long? {
        if (attemptedAt == null) return 0L
        if (wakeAt != null && followsTraceo) {
            return max(attemptedAt + WAKE_MIN_GAP_MS, wakeAt + WAKE_MARGIN_MS + Math.floorMod(offsetMs, WAKE_SPREAD_MS))
        }
        if (refreshMinutes <= 0) return null
        val byInterval = attemptedAt + refreshMinutes * MINUTE_MS - EARLY_MS
        if (!followsTraceo || traceoNextAt == null || traceoNextAt <= 0L) return byInterval
        val nextSnapshot = traceoNextAt + offsetMs
        // Relevé déjà demandé (ou annonce périmée, après un échec) : on revient à l'intervalle.
        if (nextSnapshot <= attemptedAt) return byInterval
        // Au rythme de Tracéo, on suit ses relevés ; plus lentement, on attend aussi l'intervalle choisi.
        val earliest = if (refreshMinutes <= sourceMinutes) {
            attemptedAt + TRACEO_MIN_GAP_MS
        } else {
            byInterval
        }
        return max(earliest, nextSnapshot)
    }

    /**
     * Prochain tick, qui redessine les temps d'attente chaque minute : à la seconde [offsetMs] de la
     * minute plutôt qu'à la minute pile, pour étaler aussi les requêtes faites à ce moment.
     * Reprogrammer pendant la même minute garde la même échéance.
     */
    fun nextTickAt(now: Long, offsetMs: Long): Long {
        val offset = Math.floorMod(offsetMs, MINUTE_MS)
        val candidate = Math.floorDiv(now - offset, MINUTE_MS) * MINUTE_MS + offset + MINUTE_MS
        return candidate
    }

    /**
     * Période du filet de sécurité : une alarme répétée, qu'Android reprogramme lui-même. Chaque tick
     * la repousse, si bien qu'elle ne se déclenche que si les ticks se sont arrêtés (processus tué
     * pendant un tick, broadcast perdu) ; elle les relance alors, sans attendre la mise à jour
     * périodique des widgets, qui ne passe que toutes les 30 minutes.
     *
     * Elle dépasse nettement la minute du tick, pour ne jamais partir avec lui.
     */
    const val WATCHDOG_INTERVAL_MS = 3 * MINUTE_MS

    /**
     * Temps laissé à un traitement lancé par un broadcast avant de rendre la main à Android.
     *
     * Android tue sans prévenir une application en arrière-plan dont le récepteur dépasse 10 s pour un
     * broadcast prioritaire, et 60 s pour les autres. Or le toucher d'un widget arrive en broadcast
     * prioritaire : le lanceur l'envoie avec FLAG_ACTIVITY_NEW_TASK, qui a la même valeur que
     * FLAG_RECEIVER_FOREGROUND.
     *
     * @param userAction vrai pour une actualisation demandée par l'usager (toucher, bouton)
     * @param foreground vrai si le broadcast porte FLAG_RECEIVER_FOREGROUND
     */
    fun broadcastBudgetMs(userAction: Boolean, foreground: Boolean): Long =
        if (userAction || foreground) FOREGROUND_BUDGET_MS else BACKGROUND_BUDGET_MS

    const val FOREGROUND_BUDGET_MS = 7_000L
    const val BACKGROUND_BUDGET_MS = 40_000L
}
