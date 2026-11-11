package fr.gotraceo.live.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import kotlin.concurrent.thread

/** Teste la logique de repli de [PrimClient] contre un faux serveur PRIM local. */
class PrimClientTest {

    private lateinit var server: ServerSocket
    private val requests: MutableList<String> = Collections.synchronizedList(ArrayList())
    @Volatile private var forcedStatus: Int? = null

    private fun siriError(text: String) =
        """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"false","ErrorCondition":{"ErrorInformation":{"ErrorText":"$text"}}}]}}}"""

    private fun visit(line: String, destination: String) =
        """{"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::$line:"},"DestinationName":[{"value":"$destination"}],
           "MonitoredCall":{"ExpectedArrivalTime":"2030-01-01T10:00:00.000Z"}}}"""

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                socket.use { handle(it) }
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Mini serveur HTTP/1.1 (une requête par connexion) simulant l'API PRIM. */
    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val target = requestLine.split(' ').getOrElse(1) { "" }
        val query = URLDecoder.decode(target.substringAfter('?', ""), "UTF-8")
        requests += query

        val (status, body) = when {
            forcedStatus != null -> forcedStatus!! to """{"message":"error"}"""
            headers["apikey"] != "secret" -> 401 to """{"message":"Invalid authentication credentials"}"""
            query.contains("StopArea:SP:71348:") -> 200 to
                """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"true","MonitoredStopVisit":[
                    ${visit("C01371", "La Défense (Grande Arche)")}]}]}}}"""
            query.contains("StopArea:SP:1:") -> 400 to siriError("La requête contient des identifiants qui sont inconnus")
            query.contains("StopPoint:Q:1:") && query.contains("LineRef") ->
                400 to siriError("Le couple MonitoringRef/LineRef n'existe pas")
            query.contains("StopPoint:Q:1:") -> 200 to
                """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"true","MonitoredStopVisit":[
                    ${visit("C01742", "Boissy-Saint-Léger")}, ${visit("C01743", "Robinson")}]}]}}}"""
            query.contains("StopPoint:Q:2:") -> 200 to
                """{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"Status":"true","MonitoredStopVisit":[]}]}}}"""
            else -> 400 to siriError("La requête contient des identifiants qui sont inconnus")
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 $status X\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "X-RateLimit-Remaining-Day: 742\r\nX-RateLimit-Limit-Day: 1000\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
        )
        out.write(bytes)
        out.flush()
    }

    private fun client(key: String = "secret", formats: PrimClient.FormatCache = PrimClient.FormatCache.InMemory()) =
        PrimClient(key, "http://127.0.0.1:${server.localPort}/stop-monitoring", formats)

    @Test
    fun traceoStopAreaIsAlwaysAZone() {
        assertEquals(listOf("STIF:StopArea:SP:71348:"), PrimClient.monitoringRefs("stop_area:IDFM:71348"))
    }

    @Test
    fun otherIdentifiersKeepTheirMonitoringRefs() {
        assertEquals(
            listOf("STIF:StopArea:SP:58879:", "STIF:StopPoint:Q:58879:"),
            PrimClient.monitoringRefs("IDFM:monomodalStopPlace:58879"),
        )
        assertEquals(listOf("STIF:StopPoint:Q:463158:"), PrimClient.monitoringRefs("IDFM:463158"))
        assertEquals(listOf("STIF:StopArea:SP:1:"), PrimClient.monitoringRefs("STIF:StopArea:SP:1:"))
    }

    @Test
    fun widgetCreatedWithoutKeyIsServedByPrimOnceAKeyIsAdded() {
        val snapshot = client().passages("stop_area:IDFM:71348", "C01371")
        assertEquals(listOf("MonitoringRef=STIF:StopArea:SP:71348:&LineRef=STIF:Line::C01371:"), requests)
        assertEquals("stop_area:IDFM:71348", snapshot.departures.single().stopId)
        assertEquals(null, snapshot.dataAt)
        assertEquals(null, snapshot.fresh)
    }

    @Test
    fun remembersAcceptedFormat() {
        val formats = PrimClient.FormatCache.InMemory()
        client(formats = formats).departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals(4, requests.size)

        requests.clear()
        val again = client(formats = formats).departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals(listOf("Boissy-Saint-Léger"), again.map { it.destination })
        assertEquals("Une seule requête grâce au format mémorisé", listOf("MonitoringRef=STIF:StopPoint:Q:1:"), requests)
    }

    @Test
    fun forgetsFormatThatStoppedWorking() {
        val formats = PrimClient.FormatCache.InMemory()
        val key = "IDFM:monomodalStopPlace:1|C01742"
        formats.put(key, "STIF:StopArea:SP:1:|")
        client(formats = formats).departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals("MonitoringRef=STIF:StopArea:SP:1:", requests.first())
        assertEquals("STIF:StopPoint:Q:1:|", formats.get(key))
    }

    @Test
    fun fallsBackToStopPointThenWithoutLineRef() {
        val departures = client().departures("IDFM:monomodalStopPlace:1", "C01742")
        assertEquals(listOf("Boissy-Saint-Léger"), departures.map { it.destination })
        assertEquals("IDFM:monomodalStopPlace:1", departures.single().stopId)
        assertEquals(
            listOf(
                "MonitoringRef=STIF:StopArea:SP:1:&LineRef=STIF:Line::C01742:",
                "MonitoringRef=STIF:StopArea:SP:1:",
                "MonitoringRef=STIF:StopPoint:Q:1:&LineRef=STIF:Line::C01742:",
                "MonitoringRef=STIF:StopPoint:Q:1:",
            ),
            requests,
        )
    }

    @Test
    fun emptyDeliveryMeansNoDeparture() {
        assertTrue(client().departures("IDFM:2", "C01742").isEmpty())
        assertEquals(1, requests.size)
    }

    @Test
    fun rejectedKey() {
        expectKind(PrimException.Kind.AUTH) { client("wrong").departures("IDFM:2", "C01742") }
        expectKind(PrimException.Kind.AUTH) { client("wrong").testKey() }
        client().testKey()
    }

    @Test
    fun quotaAndServerErrors() {
        forcedStatus = 429
        expectKind(PrimException.Kind.QUOTA) { client().departures("IDFM:2", null) }
        forcedStatus = 503
        expectKind(PrimException.Kind.SERVER) { client().departures("IDFM:2", null) }
    }

    @Test
    fun eachResponseReportsWhatIsLeftOfTheDailyQuota() {
        val seen = Collections.synchronizedList(ArrayList<Pair<Int?, Int?>>())
        var exceeded = 0
        val usage = object : PrimClient.UsageListener {
            override fun onResponse(remainingDay: Int?, limitDay: Int?) {
                seen += remainingDay to limitDay
            }

            override fun onQuotaExceeded() {
                exceeded++
            }
        }
        val client = PrimClient("secret", "http://127.0.0.1:${server.localPort}/stop-monitoring", usage = usage)
        client.departures("IDFM:2", null)
        assertEquals(listOf<Pair<Int?, Int?>>(742 to 1000), seen.toList())
        assertEquals(0, exceeded)

        forcedStatus = 429
        expectKind(PrimException.Kind.QUOTA) { client.departures("IDFM:2", null) }
        assertEquals(2, seen.size)
        assertEquals(1, exceeded)
    }

    @Test
    fun missingKey() {
        expectKind(PrimException.Kind.NO_KEY) { client("").departures("IDFM:2", null) }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aKeyPastedWithAnInvisibleCharacterIsRefusedWithoutCrashing() {
        // Un saut de ligne au milieu de la clé n'entre pas dans un en-tête HTTP : la requête ne part pas,
        // et l'erreur doit rester de celles que l'écran de la clé et les widgets savent afficher.
        expectKind(PrimException.Kind.AUTH) { client("sec\nret").departures("IDFM:2", null) }
        assertTrue(requests.isEmpty())
    }

    private fun expectKind(kind: PrimException.Kind, block: () -> Unit) {
        try {
            block()
            fail("PrimException $kind attendue")
        } catch (e: PrimException) {
            assertEquals(kind, e.kind)
        }
    }
}
