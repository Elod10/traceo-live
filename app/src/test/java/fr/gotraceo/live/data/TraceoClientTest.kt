package fr.gotraceo.live.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** Teste [TraceoClient] contre un faux serveur local, avec les réponses du contrat de Tracéo. */
class TraceoClientTest {

    private companion object {
        const val STOP = "stop_area:IDFM:71673"
        const val PASSAGES_PATH = "/api/widget/passages"
        const val STATIONS_PATH = "/api/stations"
        const val LINES_PATH = "/api/lines"

        const val DROP = FakeServer.DROP

        const val LINES = """{"lines": [
            {"id": "line:IDFM:C01742", "code": "A", "name": "A", "color": "#EB2132", "textColor": "#FFFFFF",
             "mode": {"key": "rer", "label": "RER", "icon": "🚆", "order": 2},
             "network": "RER", "operators": ["SNCF", "RATP"]},
            {"id": "line:IDFM:C01739", "code": "H", "name": "H", "color": "#7D5A3C", "textColor": "#FFFFFF",
             "mode": {"key": "transilien", "label": "Transilien", "icon": "🚆", "order": 3},
             "network": "Transilien", "operators": ["SNCF"]},
            {"id": "line:IDFM:C01371", "code": "1", "name": "1", "color": "#FFCD00", "textColor": "#000000",
             "mode": {"key": "metro", "label": "Métro", "icon": "🚇", "order": 1},
             "network": "Métro", "operators": ["RATP"]},
            {"id": "line:IDFM:C01389", "code": "T3a", "name": "T3a", "color": "#FF7E2E", "textColor": "#000000",
             "mode": {"key": "tram", "label": "Tram", "icon": "🚊", "order": 4},
             "network": "Tram", "operators": ["RATP"]},
            {"id": "line:IDFM:C02000", "code": "A", "name": "Remplacement RER A", "color": "#000000", "textColor": "#FFFFFF",
             "mode": {"key": "bus", "label": "Bus", "icon": "🚌", "order": 5},
             "network": "Bus", "operators": ["Keolis"], "submode": "railReplacementBus"}
        ], "upcoming": []}"""

        const val STATIONS = """{"stations": [{"id": "stop_area:IDFM:71348", "name": "Argentine", "wheelchair": false}]}"""

        const val PASSAGES = """{"passages": [{"destination": "La Défense (Grande Arche)", "mission": "", "direction": "Aller",
            "aimed": "2026-11-04T22:35:55.344Z", "expected": "2026-11-04T22:37:55.344Z",
            "delayMin": 2, "status": "delayed", "atStop": false},
            {"destination": "Château de Vincennes", "mission": "ZEUS", "direction": "",
            "aimed": "2026-11-04T22:36:00.000Z", "expected": "2026-11-04T22:36:00.000Z",
            "delayMin": 0, "status": "onTime", "atStop": true}],
            "fetchedAt": "2026-11-04T22:34:01.006Z", "ageSec": 122, "fresh": true}"""
    }

    private lateinit var server: FakeServer
    private val requests: MutableList<FakeServer.Request> get() = server.requests
    private val responses: FakeServer get() = server

    @Before
    fun setUp() {
        server = FakeServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(path: String, status: Int, body: String) = server.respond(path, status, body)

    private val warmed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun client() = TraceoClient(server.url, warmed)

    private fun paths() = server.paths()

    private fun expectKind(kind: TraceoException.Kind, block: () -> Unit): TraceoException {
        try {
            block()
        } catch (e: TraceoException) {
            assertEquals(kind, e.kind)
            return e
        }
        fail("TraceoException $kind attendue")
        throw AssertionError()
    }

    // --- Lignes ------------------------------------------------------------------------------

    @Test
    fun linesAreMappedToTheReferentialModel() {
        respond(LINES_PATH, 200, LINES)
        val lines = client().lines().associateBy { it.id }

        val rerA = lines.getValue("C01742")
        assertEquals("A", rerA.shortName)
        assertEquals("A", rerA.name)
        assertEquals("rail", rerA.mode)
        assertEquals("RER", rerA.network)
        assertEquals("SNCF", rerA.operator)
        assertEquals("EB2132", rerA.color)
        assertEquals("FFFFFF", rerA.textColor)
        assertNull(rerA.subMode)
        assertEquals("STIF:Line::C01742:", rerA.lineRef)

        assertEquals("rail", lines.getValue("C01739").mode)
        assertEquals("metro", lines.getValue("C01371").mode)
        assertEquals("tram", lines.getValue("C01389").mode)
        assertEquals("railReplacementBus", lines.getValue("C02000").subMode)
    }

    @Test
    fun railLinesSplitBetweenRerAndTrainThroughTheNetwork() {
        respond(LINES_PATH, 200, LINES)
        val lines = client().lines()
        fun idsOf(mode: TransportMode) = lines.filter { mode.includes(it) }.map { it.id }.toSet()

        assertEquals(setOf("C01742"), idsOf(TransportMode.RER))
        assertEquals(setOf("C01739"), idsOf(TransportMode.TRAIN))
        assertEquals(setOf("C01371"), idsOf(TransportMode.METRO))
        assertEquals(setOf("C01389"), idsOf(TransportMode.TRAM))
        assertEquals(setOf("C02000"), idsOf(TransportMode.BUS))
        assertTrue(idsOf(TransportMode.OTHER).isEmpty())
    }

    @Test
    fun linesSharingACodeStayDistinct() {
        respond(LINES_PATH, 200, LINES)
        val sameCode = client().lines().filter { it.shortName == "A" }
        assertEquals(setOf("C01742", "C02000"), sameCode.map { it.id }.toSet())
    }

    // --- Arrêts ------------------------------------------------------------------------------

    @Test
    fun stationsAreStopAreasWithoutTown() {
        respond(STATIONS_PATH, 200, STATIONS)
        assertEquals(
            listOf(Stop("Argentine", null, listOf("stop_area:IDFM:71348"))),
            client().stops("C01371"),
        )
        assertEquals("/api/stations?line=line%3AIDFM%3AC01371", requests.single().target)
    }

    // --- Passages ----------------------------------------------------------------------------

    @Test
    fun passagesAreMappedToDepartures() {
        respond(PASSAGES_PATH, 200, PASSAGES)
        val snapshot = client().departures(STOP, "C01371")

        assertEquals(SiriParser.parseTime("2026-11-04T22:34:01.006Z"), snapshot.dataAt)
        assertEquals(true, snapshot.fresh)
        assertEquals(2, snapshot.departures.size)

        val first = snapshot.departures[0]
        assertEquals("La Défense (Grande Arche)", first.destination)
        assertEquals("La Défense (Grande Arche)", first.directionKey)
        assertNull(first.directionName)
        assertEquals("Aller", first.directionRef)
        assertEquals(SiriParser.parseTime("2026-11-04T22:37:55.344Z"), first.expectedArrival)
        assertEquals(SiriParser.parseTime("2026-11-04T22:35:55.344Z"), first.aimedArrival)
        assertNull(first.expectedDeparture)
        assertNull(first.aimedDeparture)
        assertFalse(first.atStop)
        assertEquals("delayed", first.status)
        assertEquals(2, first.delayMinutes)
        assertNull("Une mission vide n'est pas une mission", first.journeyNote)
        assertNull(first.platform)
        assertEquals(STOP, first.stopId)

        val second = snapshot.departures[1]
        assertNull("Une direction vide reste vide", second.directionRef)
        assertTrue(second.atStop)
        assertEquals("onTime", second.status)
        assertEquals("ZEUS", second.journeyNote)
    }

    @Test
    fun nextSnapshotDateIsKeptForTheScheduling() {
        respond(
            PASSAGES_PATH, 200,
            """{"passages": [], "fetchedAt": "2026-11-04T22:34:01.006Z", "ageSec": 5, "fresh": true,
                "nextAt": "2026-11-04T22:36:04+00:00"}""",
        )
        val snapshot = client().departures(STOP, "C01371")
        assertEquals(SiriParser.parseTime("2026-11-04T22:36:04Z"), snapshot.nextAt)
        assertTrue(snapshot.viaTraceo)
    }

    @Test
    fun noNextSnapshotWhenTheCollectionIsSuspendedOrTraceoIsOlder() {
        respond(PASSAGES_PATH, 200, """{"passages": [], "fetchedAt": null, "fresh": false, "nextAt": null}""")
        assertNull(client().departures(STOP, "C01371").nextAt)
        respond(PASSAGES_PATH, 200, PASSAGES)
        assertNull(client().departures(STOP, "C01371").nextAt)
    }

    @Test
    fun noSnapshotAndNoPassage() {
        respond(PASSAGES_PATH, 200, """{"passages": [], "fetchedAt": null, "ageSec": null, "fresh": false}""")
        val snapshot = client().departures(STOP, "C01371")
        assertTrue(snapshot.departures.isEmpty())
        assertNull(snapshot.dataAt)
        assertEquals(false, snapshot.fresh)
    }

    @Test
    fun oldSnapshotIsNotFresh() {
        respond(
            PASSAGES_PATH, 200,
            """{"passages": [], "fetchedAt": "2026-11-04T02:30:00.000Z", "ageSec": 960, "fresh": false}""",
        )
        val snapshot = client().departures(STOP, "C01371")
        assertEquals(SiriParser.parseTime("2026-11-04T02:30:00.000Z"), snapshot.dataAt)
        assertEquals(false, snapshot.fresh)
    }

    @Test
    fun callPrefixesTheLineEncodesParametersAndSendsNoKey() {
        respond(PASSAGES_PATH, 200, PASSAGES)
        client().departures(STOP, "C01371")

        val request = requests.single()
        assertEquals("/api/widget/passages?line=line%3AIDFM%3AC01371&stop=stop_area%3AIDFM%3A71673", request.target)
        assertFalse("La clé PRIM ne part jamais vers Tracéo", request.headers.containsKey("apikey"))
    }

    @Test
    fun noneOfTheRoutesSendsAKey() {
        respond(LINES_PATH, 200, LINES)
        respond(STATIONS_PATH, 200, STATIONS)
        respond(PASSAGES_PATH, 200, PASSAGES)
        val traceo = client()
        traceo.lines()
        traceo.stops("C01371")
        traceo.passages(STOP, "C01371")
        assertEquals(3, requests.size)
        assertTrue(requests.none { it.headers.containsKey("apikey") })
    }

    // --- Erreurs -----------------------------------------------------------------------------

    @Test
    fun httpErrorsHaveTheirKindAndAFrenchMessageWithoutPrim() {
        val cases = listOf(
            400 to TraceoException.Kind.BAD_REQUEST,
            404 to TraceoException.Kind.NOT_FOUND,
            429 to TraceoException.Kind.RATE_LIMITED,
        )
        for ((status, kind) in cases) {
            responses.clear()
            requests.clear()
            respond(PASSAGES_PATH, status, """{"error": "Message du serveur PRIM"}""")
            val error = expectKind(kind) { client().departures(STOP, "C01371") }
            assertFalse(error.message.orEmpty(), error.message.orEmpty().contains("PRIM"))
            assertEquals("HTTP $status : aucun nouvel essai immédiat", listOf(PASSAGES_PATH), paths())
        }
    }

    @Test
    fun unknownStopAsksToReconfigure() {
        respond(PASSAGES_PATH, 404, """{"error": "Arrêt inconnu."}""")
        val error = expectKind(TraceoException.Kind.NOT_FOUND) { client().departures(STOP, "C01371") }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("reconfigurez"))
    }

    @Test
    fun otherServerErrorsAndInvalidBodies() {
        respond(PASSAGES_PATH, 500, "oups")
        expectKind(TraceoException.Kind.SERVER) { client().departures(STOP, "C01371") }

        responses.clear()
        respond(PASSAGES_PATH, 200, "pas du JSON")
        expectKind(TraceoException.Kind.INVALID_RESPONSE) { client().departures(STOP, "C01371") }

        responses.clear()
        respond(LINES_PATH, 200, """{"autre": []}""")
        expectKind(TraceoException.Kind.INVALID_RESPONSE) { client().lines() }
    }

    @Test
    fun networkFailureIsReportedWithoutPrim() {
        // Une connexion coupée sans réponse, plutôt qu'un port fermé : sous Linux, une connexion vers un
        // port éphémère libre peut se connecter à elle-même et lire sa propre requête comme une réponse.
        respond(PASSAGES_PATH, DROP, "")
        val error = expectKind(TraceoException.Kind.NETWORK) { client().departures(STOP, "C01371") }
        assertFalse(error.message.orEmpty().contains("PRIM"))
    }

    @Test
    fun errorsAreIoExceptionsForTheStaleCacheFallback() {
        respond(STATIONS_PATH, 429, """{"error": "Trop de requêtes."}""")
        try {
            client().stops("C01371")
            fail("IOException attendue")
        } catch (e: java.io.IOException) {
            assertTrue(e is TraceoException)
        }
    }

    // --- Règle du 503 ------------------------------------------------------------------------

    @Test
    fun unavailableWarmsTheLineOnceThenRetriesOnce() {
        respond(PASSAGES_PATH, 503, """{"error": "Arrêts absents du cache."}""")
        respond(PASSAGES_PATH, 200, PASSAGES)
        respond(STATIONS_PATH, 200, STATIONS)

        val snapshot = client().departures(STOP, "C01371")

        assertEquals(2, snapshot.departures.size)
        assertEquals(listOf(PASSAGES_PATH, STATIONS_PATH, PASSAGES_PATH), paths())
        assertEquals("/api/stations?line=line%3AIDFM%3AC01371", requests[1].target)
    }

    @Test
    fun unavailableWarmsAtMostOncePerLineAndPerProcess() {
        respond(PASSAGES_PATH, 503, """{"error": "Route coupée."}""")
        respond(STATIONS_PATH, 200, STATIONS)

        // Première fois : les arrêts sont demandés, puis un seul nouvel essai.
        expectKind(TraceoException.Kind.UNAVAILABLE) { client().departures(STOP, "C01371") }
        assertEquals(listOf(PASSAGES_PATH, STATIONS_PATH, PASSAGES_PATH), paths())

        // Même ligne, même avec un autre client du même processus : plus rien d'autre que la requête.
        requests.clear()
        expectKind(TraceoException.Kind.UNAVAILABLE) { client().departures("stop_area:IDFM:71348", "line:IDFM:C01371") }
        assertEquals(listOf(PASSAGES_PATH), paths())

        // Autre ligne : elle a droit à son propre essai.
        requests.clear()
        expectKind(TraceoException.Kind.UNAVAILABLE) { client().departures(STOP, "C01742") }
        assertEquals(listOf(PASSAGES_PATH, STATIONS_PATH, PASSAGES_PATH), paths())
    }

    @Test
    fun unavailableStationsMeanNoRetry() {
        respond(PASSAGES_PATH, 503, """{"error": "Route coupée."}""")
        respond(STATIONS_PATH, 503, """{"error": "Route coupée."}""")

        expectKind(TraceoException.Kind.UNAVAILABLE) { client().departures(STOP, "C01371") }
        assertEquals(listOf(PASSAGES_PATH, STATIONS_PATH), paths())

        requests.clear()
        expectKind(TraceoException.Kind.UNAVAILABLE) { client().departures(STOP, "C01371") }
        assertEquals(listOf(PASSAGES_PATH), paths())
    }

    @Test
    fun rateLimitDoesNotWarmTheLine() {
        respond(PASSAGES_PATH, 429, """{"error": "Trop de requêtes."}""")
        expectKind(TraceoException.Kind.RATE_LIMITED) { client().departures(STOP, "C01371") }
        assertEquals(listOf(PASSAGES_PATH), paths())
        assertTrue(warmed.isEmpty())
    }
}
