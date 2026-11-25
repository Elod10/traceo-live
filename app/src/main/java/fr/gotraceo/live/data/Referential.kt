package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.Collator
import java.util.Locale

/**
 * Accès au référentiel open data d'Île-de-France Mobilités (plateforme Opendatasoft,
 * sans clé API) :
 *  - « referentiel-des-lignes » : liste des lignes par mode de transport ;
 *  - « arrets-lignes » : arrêts desservis par chaque ligne.
 *
 * Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.
 */
object Referential {

    const val BASE_URL = "https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets"
    private const val LINES_DATASET = "referentiel-des-lignes"
    private const val STOPS_DATASET = "arrets-lignes"
    private const val PAGE_SIZE = 100

    // --- URLs ----------------------------------------------------------------------------

    fun linesWhere(apiModes: List<String>): String =
        apiModes.joinToString(" OR ") { "`transportmode`=\"$it\"" }

    fun stopsWhere(lineId: String): String = "`id`=\"IDFM:${bareLineId(lineId)}\""

    fun exportUrl(dataset: String, where: String): String =
        "$BASE_URL/$dataset/exports/json?where=${Http.encode(where)}&lang=fr&timezone=Europe%2FParis"

    /** Export CSV de quelques colonnes, pour les jeux trop gros à charger en mémoire. */
    fun csvUrl(dataset: String, select: String): String =
        "$BASE_URL/$dataset/exports/csv?select=${Http.encode(select)}&delimiter=${Http.encode(";")}"

    fun recordsUrl(dataset: String, where: String, offset: Int): String =
        "$BASE_URL/$dataset/records?where=${Http.encode(where)}&limit=$PAGE_SIZE&offset=$offset&lang=fr&timezone=Europe%2FParis"

    // --- Téléchargement ------------------------------------------------------------------

    @Throws(IOException::class)
    fun downloadLines(apiModes: List<String>): List<Line> =
        parseLines(download(LINES_DATASET, linesWhere(apiModes)))

    @Throws(IOException::class)
    fun downloadStops(lineId: String): List<Stop> =
        parseStops(download(STOPS_DATASET, stopsWhere(lineId)))

    /** Export complet ; en cas d'échec, repli sur l'API paginée « records ». */
    @Throws(IOException::class)
    private fun download(dataset: String, where: String): List<JSONObject> {
        val first = try {
            Http.get(exportUrl(dataset, where), readTimeoutMs = 30_000)
        } catch (e: IOException) {
            null
        }
        if (first != null && first.code in 200..299) {
            val records = parseRecords(first.body)
            if (records != null) return records
        }

        val all = ArrayList<JSONObject>()
        var offset = 0
        while (true) {
            val resp = Http.get(recordsUrl(dataset, where, offset), readTimeoutMs = 30_000)
            if (resp.code !in 200..299) {
                throw IOException("Référentiel IDFM indisponible (HTTP ${resp.code})")
            }
            val page = parseRecords(resp.body) ?: throw IOException("Réponse du référentiel IDFM illisible")
            all.addAll(page)
            val total = try {
                JSONObject(resp.body).optInt("total_count", -1)
            } catch (e: Exception) {
                -1
            }
            offset += PAGE_SIZE
            if (page.size < PAGE_SIZE || (total in 0..offset) || offset >= 10_000) break
        }
        return all
    }

    // --- Analyse -------------------------------------------------------------------------

    /**
     * Accepte les différents formats Opendatasoft : tableau d'objets (export v2.1),
     * `{"results": [...]}` (records v2.1) ou objets enveloppés dans `fields` (API v1).
     */
    fun parseRecords(body: String): List<JSONObject>? {
        val trimmed = body.trim()
        val array: JSONArray = try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val o = JSONObject(trimmed)
                    o.optJSONArray("results") ?: o.optJSONArray("records") ?: return null
                }
                else -> return null
            }
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<JSONObject>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out.add(o.optJSONObject("fields") ?: o.optJSONObject("record")?.optJSONObject("fields") ?: o)
        }
        return out
    }

    fun parseLines(records: List<JSONObject>): List<Line> {
        val byId = LinkedHashMap<String, Line>()
        for (o in records) {
            val id = o.str("id_line")?.let(::bareLineId) ?: continue
            val status = o.str("status")
            if (status != null && (status.equals("inactive", true) || status.equals("inactif", true))) continue
            val name = o.str("name_line") ?: o.str("shortname_line") ?: id
            val line = Line(
                id = id,
                name = name,
                shortName = o.str("shortname_line") ?: name,
                mode = o.str("transportmode") ?: "",
                subMode = o.str("transportsubmode"),
                network = o.str("networkname"),
                operator = o.str("operatorname"),
                color = o.str("colourweb_hexa")?.let(::cleanColor),
                textColor = o.str("textcolourweb_hexa")?.let(::cleanColor),
            )
            byId.putIfAbsent(id, line)
        }
        return byId.values.sortedWith(lineComparator)
    }

    fun parseStops(records: List<JSONObject>): List<Stop> {
        data class Acc(val name: String, val town: String?, val ids: LinkedHashSet<String>)

        val groups = LinkedHashMap<String, Acc>()
        for (o in records) {
            val stopId = o.str("stop_id") ?: continue
            val name = o.str("stop_name") ?: continue
            val town = o.str("nom_commune")
            val key = normalizeLabel(name) + "|" + normalizeLabel(town)
            groups.getOrPut(key) { Acc(name.trim(), town?.trim(), LinkedHashSet()) }.ids.add(stopId.trim())
        }
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
        return groups.values
            .map { Stop(it.name, it.town, it.ids.toList()) }
            .sortedWith { a, b ->
                val c = collator.compare(a.name, b.name)
                if (c != 0) c else collator.compare(a.town ?: "", b.town ?: "")
            }
    }

    // --- Utilitaires ---------------------------------------------------------------------

    private val HEX_COLOR = Regex("[0-9A-Fa-f]{6}")
    private val NATURAL_TOKENS = Regex("\\d+|\\D+")

    fun bareLineId(id: String): String = id.trim().trimEnd(':').substringAfterLast(':')

    fun cleanColor(c: String): String? {
        val hex = c.trim().removePrefix("#")
        return if (HEX_COLOR.matches(hex)) hex.uppercase(Locale.ROOT) else null
    }

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = opt(key)
        val s = when (v) {
            is String -> v
            is Number, is Boolean -> v.toString()
            is JSONArray -> if (v.length() > 0) v.opt(0)?.toString() else null
            else -> null
        }
        return s?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Tri « naturel » : 2 < 10, 3 < 3B, puis alphabétique. */
    val naturalComparator: Comparator<String> = Comparator { a, b ->
        val ta = tokenize(a)
        val tb = tokenize(b)
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val xn = x.toBigIntegerOrNull()
            val yn = y.toBigIntegerOrNull()
            val c = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (c != 0) return@Comparator c
        }
        ta.size - tb.size
    }

    val lineComparator: Comparator<Line> = Comparator { a, b ->
        val c = naturalComparator.compare(a.shortName, b.shortName)
        if (c != 0) c else naturalComparator.compare(a.subtitle(), b.subtitle())
    }

    private fun tokenize(s: String): List<String> =
        NATURAL_TOKENS.findAll(s.trim()).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
}
