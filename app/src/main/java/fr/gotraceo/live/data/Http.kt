package fr.gotraceo.live.data

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/** Mini client HTTP (GET uniquement) basé sur HttpURLConnection. */
object Http {

    /** @param headers en-têtes de la réponse, noms en minuscules (première valeur seulement) */
    data class Response(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

    private const val USER_AGENT = "TraceoLive-Android/1.0"

    /**
     * Taille maximale d'une réponse lue en mémoire, une fois décompressée. La plus grosse attendue
     * est l'index des arrêts d'un grand réseau, de l'ordre du mégaoctet : au-delà de cette limite,
     * la réponse n'est pas la bonne, et un widget ne doit pas y laisser sa mémoire.
     */
    const val MAX_BODY_BYTES = 8 * 1024 * 1024

    @Throws(IOException::class)
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 12_000,
        maxBytes: Int = MAX_BODY_BYTES,
    ): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                // Certaines versions d'Android lèvent une exception sur un 401 sans en-tête WWW-Authenticate.
                if (e.message?.contains("authentication challenge", ignoreCase = true) == true) {
                    return Response(401, "")
                }
                throw e
            }
            val raw: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            val stream = if (raw != null && "gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(raw)
            } else {
                raw
            }
            val body = stream?.use { readBounded(it, maxBytes).toString(Charsets.UTF_8) } ?: ""
            val responseHeaders = conn.headerFields.orEmpty().entries
                .filter { (name, values) -> name != null && !values.isNullOrEmpty() }
                .associate { (name, values) -> name.lowercase() to values.first() }
            return Response(code, body, responseHeaders)
        } finally {
            conn.disconnect()
        }
    }

    /** Lit le flux en entier, sans dépasser [maxBytes] : une réponse plus longue est refusée, pas tronquée. */
    @Throws(IOException::class)
    internal fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) throw IOException("Réponse trop volumineuse (plus de ${maxBytes / 1024} Ko)")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Lit une réponse volumineuse au fil de l'eau, sans la charger en mémoire. */
    @Throws(IOException::class)
    fun <T> stream(
        url: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 60_000,
        block: (InputStream) -> T,
    ): T {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return stream.use(block)
        } finally {
            conn.disconnect()
        }
    }

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
