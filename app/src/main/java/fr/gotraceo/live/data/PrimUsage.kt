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
     */
    fun dueAt(
        attemptedAt: Long?,
        refreshMinutes: Int,
        followsTraceo: Boolean,
        traceoNextAt: Long?,
        offsetMs: Long,
        sourceMinutes: Int = DataSources.TRACEO_MIN_REFRESH_MINUTES,
    ): Long? {
        if (attemptedAt == null) return 0L
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
