package fr.gotraceo.live.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Faux serveur HTTP local pour tester les clients sans réseau : il sert des réponses enregistrées
 * par chemin et garde les requêtes reçues.
 */
class FakeServer {

    class Request(val path: String, val target: String, val headers: Map<String, String>)

    val requests: MutableList<Request> = Collections.synchronizedList(ArrayList())
    private val responses = HashMap<String, MutableList<Pair<Int, String>>>()
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    /** Adresse à donner au client testé. */
    val url: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                client.use { handle(it) }
            }
        }
    }

    fun close() = socket.close()

    /** Ajoute une réponse pour ce chemin : elles sont servies dans l'ordre, la dernière se répète. */
    fun respond(path: String, status: Int, body: String) {
        synchronized(responses) { responses.getOrPut(path) { ArrayList() }.add(status to body) }
    }

    /** Oublie les réponses enregistrées. */
    fun clear() = synchronized(responses) { responses.clear() }

    fun paths(): List<String> = requests.map { it.path }

    private fun nextResponse(path: String): Pair<Int, String> = synchronized(responses) {
        val queue = responses[path] ?: return@synchronized 500 to "{}"
        if (queue.size > 1) queue.removeAt(0) else queue.first()
    }

    private fun handle(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val target = requestLine.split(' ').getOrElse(1) { "" }
        val path = target.substringBefore('?')
        requests += Request(path, target, headers)

        val (status, body) = nextResponse(path)
        if (status == DROP) return
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 $status X\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
        )
        out.write(bytes)
        out.flush()
    }

    companion object {
        /** Statut de [respond] qui coupe la connexion sans rien répondre. */
        const val DROP = -1
    }
}
