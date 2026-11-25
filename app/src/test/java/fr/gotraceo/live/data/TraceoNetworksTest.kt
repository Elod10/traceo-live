package fr.gotraceo.live.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Teste les routes des réseaux hors Île-de-France (`/api/reseaux`) contre un faux serveur local. Les
 * réponses sont celles du contrat v0 du pilote de Troyes (chantiers/multi-reseaux/pilote-troyes.md).
 */
class TraceoNetworksTest {

    private companion object {
        const val TCAT = "tcat"
        const val CATALOG_PATH = "/api/reseaux"
        const val LINES_PATH = "/api/reseaux/tcat/lines"
        const val STATIONS_PATH = "/api/reseaux/tcat/stations"
        const val PASSAGES_PATH = "/api/reseaux/tcat/passages"

        const val CATALOG = """{"version": 1, "networks": [{
            "id": "tcat", "name": "TCAT", "city": "Troyes",
            "bbox": {"latMin": 48.17, "latMax": 48.41, "lonMin": 3.89, "lonMax": 4.26},
            "modes": ["bus"],
            "refreshSec": 60,
            "status": "ok",
            "attribution": {"source": "TCAT, Troyes Champagne Métropole",
                            "url": "https://transport.data.gouv.fr/datasets/donnees-tcat-troyes-champagne-metropole-1",
                            "licence": "ODbL", "licenceUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
                            "text": "Données TCAT, sous licence ODbL, via Tracéo."},
            "indexAt": "2026-11-11T03:10:00Z"}]}"""

        const val LINES = """{"lines": [
            {"id": "10", "code": "10", "name": "Sainte-Savine - Saint-Julien", "color": "#0072BC",
             "textColor": "#FFFFFF", "mode": {"key": "bus", "label": "Bus"}},
            {"id": "2B", "code": "2B", "name": "Bréviandes - Voves", "color": "#F29400",
             "textColor": "#FFFFFF", "mode": {"key": "bus", "label": "Bus"}, "realtime": true},
            {"id": "ZI TAD", "code": "TAD", "name": "Zone industrielle à la demande", "color": "#777777",
             "textColor": "#FFFFFF", "mode": {"key": "bus", "label": "Bus"}, "realtime": false},
            {"id": "1:C01742", "code": "1", "name": "Chartreux - Pont-Sainte-Marie", "color": "e2001a",
             "textColor": "pas une couleur", "mode": {"key": "bus", "label": "Bus"}}
        ]}"""

        const val STATIONS = """{"stations": [
            {"id": "1:ACACI", "name": "Acacias", "town": null},
            {"id": "1:HALLE", "name": "Halle", "town": "Troyes"}]}"""

        const val PASSAGES = """{"passages": [
            {"destination": "Chartreux", "mission": "", "direction": "0",
             "aimed": "2026-11-11T18:40:00Z", "expected": "2026-11-11T18:42:10Z",
             "delayMin": 2, "status": "delayed", "atStop": false},
            {"destination": "Voves", "mission": "", "direction": "1",
             "aimed": null, "expected": "2026-11-11T18:50:00Z",
             "delayMin": 0, "status": "onTime", "atStop": false}],
            "fetchedAt": "2026-11-11T18:35:01Z", "ageSec": 20, "fresh": true,
            "nextAt": "2026-11-11T18:36:05+00:00"}"""
    }

    private lateinit var server: FakeServer
    private val warmed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val files = ArrayList<File>()

    @Before
    fun setUp() {
        server = FakeServer()
    }

    @After
    fun tearDown() {
        server.close()
        files.forEach { it.delete() }
    }

    private fun client() = TraceoClient(server.url, warmed)

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

    // --- Catalogue -----------------------------------------------------------------------------

    @Test
    fun theCatalogDescribesEachNetwork() {
        server.respond(CATALOG_PATH, 200, CATALOG)
        val tcat = client().networks().single()

        assertEquals(TCAT, tcat.id)
        assertEquals("TCAT", tcat.name)
        assertEquals("Troyes", tcat.city)
        assertEquals(BoundingBox(48.17, 48.41, 3.89, 4.26), tcat.bbox)
        assertEquals(listOf("bus"), tcat.modes)
        assertEquals(60, tcat.refreshSec)
        assertEquals(1, tcat.sourceMinutes)
        assertEquals(NetworkStatus.OK, tcat.status)
        assertEquals(SiriParser.parseTime("2026-11-11T03:10:00Z"), tcat.indexAt)
        assertEquals(
            Attribution(
                source = "TCAT, Troyes Champagne Métropole",
                url = "https://transport.data.gouv.fr/datasets/donnees-tcat-troyes-champagne-metropole-1",
                licence = "ODbL",
                licenceUrl = "https://opendatacommons.org/licenses/odbl/1-0/",
                text = "Données TCAT, sous licence ODbL, via Tracéo.",
            ),
            tcat.attribution,
        )
        assertEquals("/api/reseaux", server.requests.single().target)
    }

    @Test
    fun unusableCatalogEntriesAreSkippedAndUnknownValuesTolerated() {
        server.respond(
            CATALOG_PATH, 200,
            """{"version": 1, "networks": [
                {"id": "Pas Un Chemin", "name": "X", "city": "X",
                 "bbox": {"latMin": 1, "latMax": 2, "lonMin": 3, "lonMax": 4}},
                {"id": "sans-emprise", "name": "Y", "city": "Y"},
                {"id": "toulouse", "name": "Tisséo", "city": "Toulouse",
                 "bbox": {"latMin": 43.4, "latMax": 43.8, "lonMin": 1.2, "lonMax": 1.7},
                 "modes": ["metro", "tram", "bus", "cable", "boat"], "status": "inconnu"},
                {"id": "ferme", "name": "Z", "city": "Z",
                 "bbox": {"latMin": 1, "latMax": 2, "lonMin": 3, "lonMax": 4}, "status": "off", "refreshSec": 90}
            ]}""",
        )
        val networks = client().networks()
        assertEquals(listOf("toulouse", "ferme"), networks.map { it.id })

        val toulouse = networks[0]
        // Le câble porte dans l'application le nom du référentiel IDFM.
        assertEquals(listOf("metro", "tram", "bus", "cableway", "boat"), toulouse.modes)
        assertEquals(NetworkStatus.OK, toulouse.status)
        assertEquals(Network.DEFAULT_REFRESH_SEC, toulouse.refreshSec)
        assertNull(toulouse.attribution)
        assertNull(toulouse.indexAt)

        assertEquals(NetworkStatus.OFF, networks[1].status)
        assertEquals(2, networks[1].sourceMinutes)
    }

    @Test
    fun ileDeFranceIsKnownWithoutTheCatalog() {
        val catalog = NetworkCatalog()
        assertEquals(listOf(Networks.IDFM), catalog.networks.map { it.id })
        assertTrue(catalog.byId(Networks.IDFM)!!.isIdfm)
        assertNull(catalog.byId(TCAT))

        // Une entrée « idfm » du catalogue ne remplace pas l'Île-de-France de l'application.
        val intruder = Networks.ILE_DE_FRANCE.copy(name = "Autre")
        assertSame(Networks.ILE_DE_FRANCE, NetworkCatalog(listOf(intruder)).byId(Networks.IDFM))
    }

    // --- Catalogue gardé sur le téléphone ------------------------------------------------------

    private fun storeFile(): File = File.createTempFile("networks", ".json").also {
        it.delete()
        files += it
    }

    @Test
    fun theCatalogIsKeptOnThePhone() {
        server.respond(CATALOG_PATH, 200, CATALOG)
        val file = storeFile()
        val store = NetworkCatalogStore(file) { client().networks() }
        assertEquals(listOf(Networks.IDFM), store.read().networks.map { it.id })
        assertTrue(store.isStale(now = 1_000L))

        val fetched = store.refresh(now = 5_000_000L)
        assertEquals(listOf(Networks.IDFM, TCAT), fetched.networks.map { it.id })

        // Un autre lecteur du même fichier retrouve tout, sans réseau.
        val reread = NetworkCatalogStore(file) { throw IOException("hors ligne") }.read()
        assertEquals(fetched.byId(TCAT), reread.byId(TCAT))
        assertEquals(5_000_000L, reread.savedAt)
        assertFalse(store.isStale(now = 5_000_000L + NetworkCatalogStore.MAX_AGE_MS - 1))
        assertTrue(store.isStale(now = 5_000_000L + NetworkCatalogStore.MAX_AGE_MS))
    }

    @Test
    fun aFailedRefreshKeepsThePreviousCatalogAndDoesNotInsist() {
        var calls = 0
        var online = true
        val tcat = NetworkCatalog.parse(CATALOG).byId(TCAT)!!
        val store = NetworkCatalogStore(storeFile()) {
            calls++
            if (online) listOf(tcat) else throw IOException("hors ligne")
        }
        val day = NetworkCatalogStore.MAX_AGE_MS
        assertEquals(tcat, store.fresh(now = day).byId(TCAT))
        assertEquals(1, calls)

        // Récent : pas de nouvelle demande.
        store.fresh(now = day + 1_000L)
        assertEquals(1, calls)

        // Ancien et hors ligne : l'ancien catalogue reste servi, et l'échec n'est pas retenté tout de suite.
        online = false
        assertEquals(tcat, store.fresh(now = 2 * day).byId(TCAT))
        assertEquals(2, calls)
        store.fresh(now = 2 * day + 60_000L)
        assertEquals(2, calls)
        store.fresh(now = 2 * day + NetworkCatalogStore.RETRY_AFTER_MS)
        assertEquals(3, calls)
    }

    // --- Lignes --------------------------------------------------------------------------------

    @Test
    fun linesKeepTheirOpaqueIdentifierAndTheirNetwork() {
        server.respond(LINES_PATH, 200, LINES)
        val lines = client().lines(TCAT)

        // Tri naturel par code, comme le référentiel.
        assertEquals(listOf("1", "2B", "10", "TAD"), lines.map { it.shortName })
        assertTrue(lines.all { it.networkId == TCAT })
        assertTrue(lines.all { it.mode == "bus" })

        val byId = lines.associateBy { it.id }
        val line2B = byId.getValue("2B")
        assertEquals("Bréviandes - Voves", line2B.name)
        assertEquals("F29400", line2B.color)
        assertEquals("FFFFFF", line2B.textColor)
        assertTrue(line2B.realtime)
        assertFalse(line2B.isRer())

        // Un identifiant n'est pas réduit à sa fin : « 1:C01742 » n'est pas le RER A.
        val line1 = byId.getValue("1:C01742")
        assertEquals("E2001A", line1.color)
        assertNull("Une couleur illisible est ignorée", line1.textColor)
        assertTrue("Sans indication, une ligne a du temps réel", line1.realtime)

        assertFalse(byId.getValue("ZI TAD").realtime)
        assertEquals("/api/reseaux/tcat/lines", server.requests.single().target)
    }

    // --- Arrêts --------------------------------------------------------------------------------

    @Test
    fun stationsAreStopAreasOfTheirNetwork() {
        server.respond(STATIONS_PATH, 200, STATIONS)
        assertEquals(
            listOf(
                Stop("Acacias", null, listOf("1:ACACI"), TCAT),
                Stop("Halle", "Troyes", listOf("1:HALLE"), TCAT),
            ),
            client().stops("ZI TAD", TCAT),
        )
        // L'identifiant de la ligne part tel quel, encodé : ni préfixe, ni troncature.
        assertEquals("/api/reseaux/tcat/stations?line=ZI+TAD", server.requests.single().target)
    }

    // --- Passages ------------------------------------------------------------------------------

    @Test
    fun passagesHaveTheBodyOfTheWidgetRoute() {
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val snapshot = client().departures("1:ACACI", "2B", TCAT)

        assertEquals("/api/reseaux/tcat/passages?line=2B&stop=1%3AACACI", server.requests.single().target)
        assertEquals(SiriParser.parseTime("2026-11-11T18:35:01Z"), snapshot.dataAt)
        assertEquals(true, snapshot.fresh)
        assertEquals(SiriParser.parseTime("2026-11-11T18:36:05Z"), snapshot.nextAt)
        assertTrue(snapshot.viaTraceo)

        val first = snapshot.departures[0]
        assertEquals("Chartreux", first.destination)
        assertEquals("0", first.directionRef)
        assertEquals("2B", first.lineRef)
        assertEquals("1:ACACI", first.stopId)
        assertEquals(SiriParser.parseTime("2026-11-11T18:42:10Z"), first.expectedArrival)
        assertEquals(2, first.delayMinutes)
        assertEquals("delayed", first.status)
        assertFalse(first.atStop)
        assertNull(first.platform)
        assertNull(first.journeyNote)

        // Horaire théorique inconnu : le passage est servi, sans retard.
        val second = snapshot.departures[1]
        assertEquals("1", second.directionRef)
        assertNull(second.aimedArrival)
        assertEquals(0, second.delayMinutes)
        assertEquals(SiriParser.parseTime("2026-11-11T18:50:00Z"), second.displayTime)
    }

    @Test
    fun aNetworkSourceAsksItsOwnRoutes() {
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val traceo = client()
        assertEquals(2, traceo.forNetwork(TCAT).passages("1:ACACI", "2B").departures.size)
        assertEquals(listOf(PASSAGES_PATH), server.paths())
        // En Île-de-France, la source reste le client lui-même, avec ses routes d'origine.
        assertSame(traceo, traceo.forNetwork(Networks.IDFM))
    }

    @Test
    fun ileDeFranceKeepsItsRoutes() {
        server.respond("/api/widget/passages", 200, """{"passages": [], "fetchedAt": null, "fresh": false}""")
        client().departures("stop_area:IDFM:71673", "C01371", Networks.IDFM)
        assertEquals("/api/widget/passages?line=line%3AIDFM%3AC01371&stop=stop_area%3AIDFM%3A71673", server.requests.single().target)
    }

    // --- Erreurs -------------------------------------------------------------------------------

    @Test
    fun errorsHaveTheKindsOfTheWidgetRoute() {
        val cases = listOf(
            400 to TraceoException.Kind.BAD_REQUEST,
            404 to TraceoException.Kind.NOT_FOUND,
            429 to TraceoException.Kind.RATE_LIMITED,
            503 to TraceoException.Kind.UNAVAILABLE,
            500 to TraceoException.Kind.SERVER,
        )
        for ((status, kind) in cases) {
            server.clear()
            server.requests.clear()
            server.respond(PASSAGES_PATH, status, """{"error": "Message du serveur"}""")
            expectKind(kind) { client().departures("1:ACACI", "2B", TCAT) }
            assertEquals("HTTP $status : aucun nouvel essai", listOf(PASSAGES_PATH), server.paths())
        }
    }

    /** En Île-de-France, un 503 fait redemander les arrêts de la ligne ; ailleurs, cela ne réparerait rien. */
    @Test
    fun unavailableDoesNotWarmTheLineOutsideIleDeFrance() {
        server.respond(PASSAGES_PATH, 503, """{"error": "Réseau coupé."}""")
        server.respond(STATIONS_PATH, 200, STATIONS)
        expectKind(TraceoException.Kind.UNAVAILABLE) { client().departures("1:ACACI", "2B", TCAT) }
        assertEquals(listOf(PASSAGES_PATH), server.paths())
        assertTrue(warmed.isEmpty())
    }

    @Test
    fun anUnknownStopAsksToReconfigure() {
        server.respond(PASSAGES_PATH, 404, """{"error": "Arrêt inconnu."}""")
        val error = expectKind(TraceoException.Kind.NOT_FOUND) { client().departures("1:PARTI", "2B", TCAT) }
        assertTrue(error.message.orEmpty().contains("reconfigurez"))
    }

    @Test
    fun aNetworkIdentifierThatCannotBeAPathSendsNothing() {
        for (bad in listOf("", "../admin", "tcat/lines", "TCAT", "a b")) {
            expectKind(TraceoException.Kind.BAD_REQUEST) { client().lines(bad) }
            expectKind(TraceoException.Kind.BAD_REQUEST) { client().stops("2B", bad) }
            expectKind(TraceoException.Kind.BAD_REQUEST) { client().departures("1:ACACI", "2B", bad) }
        }
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun noneOfTheNetworkRoutesSendsAKey() {
        server.respond(CATALOG_PATH, 200, CATALOG)
        server.respond(LINES_PATH, 200, LINES)
        server.respond(STATIONS_PATH, 200, STATIONS)
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val traceo = client()
        traceo.networks()
        traceo.lines(TCAT)
        traceo.stops("2B", TCAT)
        traceo.departures("1:ACACI", "2B", TCAT)
        assertEquals(4, server.requests.size)
        assertTrue(server.requests.none { it.headers.containsKey("apikey") })
    }

    @Test
    fun aMissingCatalogIsAnIoErrorSoThePhoneKeepsItsOwn() {
        server.respond(CATALOG_PATH, 404, """{"error": "Route inconnue."}""")
        val store = NetworkCatalogStore(storeFile()) { client().networks() }
        assertEquals(listOf(Networks.IDFM), store.fresh(now = NetworkCatalogStore.MAX_AGE_MS).networks.map { it.id })
    }
}
