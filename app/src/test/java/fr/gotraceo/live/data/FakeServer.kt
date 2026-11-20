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
    private val responses = HashMap<String, MutableList<Reply>>()

    /** Une réponse enregistrée : statut, corps, et en-têtes en plus de ceux de toute réponse. */
    private class Reply(val status: Int, val body: String, val headers: Map<String, String>)
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
    fun respond(path: String, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
        synchronized(responses) { responses.getOrPut(path) { ArrayList() }.add(Reply(status, body, headers)) }
    }

    /** Oublie les réponses enregistrées. */
    fun clear() = synchronized(responses) { responses.clear() }

    fun paths(): List<String> = requests.map { it.path }

    private fun nextResponse(path: String): Reply = synchronized(responses) {
        val queue = responses[path] ?: return@synchronized Reply(500, "{}", emptyMap())
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

        val reply = nextResponse(path)
        if (reply.status == DROP) return
        val bytes = reply.body.toByteArray(Charsets.UTF_8)
        val extra = reply.headers.entries.joinToString("") { (name, value) -> "$name: $value\r\n" }
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 ${reply.status} X\r\nContent-Type: application/json; charset=utf-8\r\n" + extra +
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
