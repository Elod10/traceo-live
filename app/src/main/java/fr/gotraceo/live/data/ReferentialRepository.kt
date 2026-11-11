package fr.gotraceo.live.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Référentiel des lignes et arrêts, avec cache disque (7 jours) pour éviter de
 * retélécharger les listes à chaque configuration. En cas d'erreur réseau, le cache
 * périmé est utilisé s'il existe.
 *
 * En Île-de-France, les données viennent de Tracéo sans clé, de l'open data IDFM avec une clé. Pour
 * un autre réseau, toujours de Tracéo, et elles sont redemandées quand le catalogue annonce de
 * nouveaux horaires ([Network.indexAt]).
 */
class ReferentialRepository(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "referential").apply { mkdirs() }
    private val prefs = Prefs(context)
    private val traceo = TraceoClient()

    suspend fun lines(
        mode: TransportMode,
        forceRefresh: Boolean = false,
        network: Network = Networks.ILE_DE_FRANCE,
    ): List<Line> = withContext(Dispatchers.IO) {
        val all = if (network.isIdfm) {
            val hasKey = prefs.hasApiKey
            cached(DataSources.linesCacheName(mode, hasKey), forceRefresh, Line::fromJson, Line::toJson) {
                if (hasKey) Referential.downloadLines(mode.apiModes) else traceo.lines()
            }
        } else {
            // Tracéo sert toutes les lignes du réseau d'un coup : un seul fichier, filtré ensuite par mode.
            cached(
                DataSources.networkLinesCacheName(network.id), forceRefresh, Line::fromJson, Line::toJson,
                notBefore = network.indexAt ?: 0L,
            ) { traceo.lines(network.id) }
        }
        all.filter { mode.includes(it) }
    }

    suspend fun stops(
        line: Line,
        forceRefresh: Boolean = false,
        network: Network = Networks.ILE_DE_FRANCE,
    ): List<Stop> = withContext(Dispatchers.IO) {
        if (Networks.isIdfm(line.networkId)) {
            val hasKey = prefs.hasApiKey
            cached(DataSources.stopsCacheName(line.id, hasKey), forceRefresh, Stop::fromJson, Stop::toJson) {
                if (hasKey) Referential.downloadStops(line.id) else traceo.stops(line.id)
            }
        } else {
            cached(
                DataSources.networkStopsCacheName(line.networkId, line.id), forceRefresh, Stop::fromJson, Stop::toJson,
                notBefore = network.indexAt ?: 0L,
            ) { traceo.stops(line.id, line.networkId) }
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** @param notBefore un fichier enregistré avant cette date est périmé, quel que soit son âge */
    private fun <T> cached(
        name: String,
        forceRefresh: Boolean,
        fromJson: (JSONObject) -> T,
        toJson: (T) -> JSONObject,
        notBefore: Long = 0L,
        download: () -> List<T>,
    ): List<T> {
        val file = File(dir, name)
        val cache = readCache(file, fromJson)
        val recent = cache != null && System.currentTimeMillis() - cache.first < TTL_MS && cache.first >= notBefore
        if (!forceRefresh && cache != null && recent && cache.second.isNotEmpty()) {
            return cache.second
        }
        return try {
            val fresh = download()
            if (fresh.isNotEmpty()) writeCache(file, fresh, toJson)
            fresh
        } catch (e: IOException) {
            if (cache != null && cache.second.isNotEmpty()) cache.second else throw e
        }
    }

    private fun <T> readCache(file: File, fromJson: (JSONObject) -> T): Pair<Long, List<T>>? {
        if (!file.exists()) return null
        return try {
            val o = JSONObject(file.readText())
            val arr = o.getJSONArray("items")
            o.getLong("savedAt") to (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> writeCache(file: File, items: List<T>, toJson: (T) -> JSONObject) {
        try {
            val arr = JSONArray()
            items.forEach { arr.put(toJson(it)) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("savedAt", System.currentTimeMillis()).put("items", arr).toString())
            tmp.renameTo(file)
        } catch (e: Exception) {
            // Le cache est facultatif.
        }
    }

    companion object {
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
