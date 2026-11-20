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

/**
 * Ce que la brique MR1 de Tracéo ajoute au contrat du pilote (routes 79 à 83 de son `docs/API.md`) :
 * le code des 404, l'indication du réveil, la date du jeu de données, les couleurs nulles, les
 * stations qui réunissent leurs quais. Les réponses sont celles de la préproduction, le 20 novembre
 * 2026 ; l'identifiant du réseau n'est qu'un exemple, l'application n'en écrit aucun.
 */
class TraceoMr1ContractTest {

    private companion object {
        const val NETWORK = "rouen"
        const val CATALOG_PATH = "/api/reseaux"
        const val LINES_PATH = "/api/reseaux/$NETWORK/lines"
        const val STATIONS_PATH = "/api/reseaux/$NETWORK/stations"
        const val PASSAGES_PATH = "/api/reseaux/$NETWORK/passages"

        const val MINUTE = 60_000L

        const val MENTION = "Contient des informations du jeu « Réseau urbain Astuce » (Métropole Rouen Normandie), " +
            "mises à disposition sous Licence Ouverte 2.0 sur transport.data.gouv.fr, où le jeu est mis à jour par son producteur."

        const val CATALOG = """{"networks": [{"id": "rouen", "name": "Astuce", "city": "Rouen",
            "bbox": {"latMin": 49.2474, "latMax": 49.5541, "lonMin": 0.7135, "lonMax": 1.2741},
            "modes": ["boat", "bus", "metro"], "refreshSec": 120, "status": "ok",
            "attribution": {"source": "Métropole Rouen Normandie",
                            "url": "https://transport.data.gouv.fr/datasets/donnees-statiques-et-temps-reel-du-reseau-astuce-metropole-rouen-normandie",
                            "licence": "Licence Ouverte 2.0",
                            "licenceUrl": "https://www.etalab.gouv.fr/licence-ouverte-open-licence/",
                            "text": "$MENTION",
                            "updatedAt": "2026-11-19T14:24:01Z"},
            "indexAt": "2026-11-20T15:22:55Z"},
            {"id": "troyes", "name": "TCAT", "city": "Troyes",
            "bbox": {"latMin": 48.169, "latMax": 48.4139, "lonMin": 3.8903, "lonMax": 4.2591},
            "modes": ["bus"], "refreshSec": 120, "status": "stale",
            "attribution": {"source": "TCAT", "url": null, "licence": "ODbL", "licenceUrl": null,
                            "text": "Contient des informations du jeu « TCAT ».", "updatedAt": null},
            "indexAt": "2026-11-20T15:22:18Z"}]}"""

        /** Rouen : une couleur et son texte ; une ligne sans couleur. Saint-Étienne : une couleur sans texte. */
        const val LINES = """{"lines": [
            {"id": "TCAR:10", "code": "10", "name": "Portes de l'Ouest <> Lycée Flaubert",
             "color": "#F8AE4C", "textColor": "#1D1D1B", "mode": {"key": "bus", "label": "Bus"}},
            {"id": "TCAR:26", "code": "26", "name": "Sans couleur publiée",
             "color": null, "textColor": null, "mode": {"key": "bus", "label": "Bus"}},
            {"id": "08", "code": "8", "name": "Terrasse <> Saint-Saëns",
             "color": "#EF7A91", "textColor": null, "mode": {"key": "bus", "label": "Bus"}}
        ]}"""

        const val PASSAGES = """{"passages": [
            {"destination": "Lycée Flaubert", "mission": "", "direction": "0",
             "aimed": "2026-11-20T15:50:00Z", "expected": "2026-11-20T15:50:00Z",
             "delayMin": 0, "status": "onTime", "atStop": false},
            {"destination": "Maromme La Maine", "mission": "", "direction": "1",
             "aimed": "2026-11-20T15:53:00Z", "expected": "2026-11-20T15:54:58Z",
             "delayMin": 2, "status": "delayed", "atStop": false}],
            "fetchedAt": "2026-11-20T15:42:05Z", "ageSec": 33, "fresh": true,
            "nextAt": "2026-11-20T15:44:11+00:00", "waking": false}"""

        /** Première requête sur Rouen après le démarrage du serveur. */
        const val WAKING = """{"passages": [], "fetchedAt": null, "ageSec": null, "fresh": false,
            "nextAt": "2026-11-20T15:42:32+00:00", "waking": true}"""

        /** Heure du serveur quand il a servi [WAKING] : le relevé est annoncé trois secondes plus tard. */
        const val WAKING_DATE = "Fri, 20 Nov 2026 15:42:29 GMT"
    }

    private lateinit var server: FakeServer
    private val warmed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Before
    fun setUp() {
        server = FakeServer()
    }

    @After
    fun tearDown() = server.close()

    private fun client(clock: () -> Long = System::currentTimeMillis) = TraceoClient(server.url, warmed, clock)

    private fun time(iso: String): Long = SiriParser.parseTime(iso)!!

    private fun expectError(block: () -> Unit): TraceoException {
        try {
            block()
        } catch (e: TraceoException) {
            return e
        }
        fail("TraceoException attendue")
        throw AssertionError()
    }

    private val line = Line("TCAR:10", "Portes de l'Ouest <> Lycée Flaubert", "10", "bus", networkId = NETWORK)
    private val stop = Stop("Mont-Mairie", null, listOf("TCAR:ST:MMAIR"), NETWORK)
    private val config = WidgetConfig(1, TransportMode.BUS, line, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS)

    private fun stateFrom(snapshots: List<PassageSnapshot>, previous: WidgetState?, at: Long, of: WidgetConfig = config) =
        WidgetStates.from(of, snapshots.map { Result.success(it) }, previous, at, removeTerminusArrivals = false)

    private fun dueAt(state: WidgetState, chosen: Int, now: Long, offsetMs: Long = 0L): Long? = RefreshSchedule.dueAt(
        state.attemptedAt, chosen, true, null, offsetMs, sourceMinutes = 2,
        wakeAt = state.wakeAt.takeIf { RefreshSchedule.awaitsWake(it, now) },
    )

    // --- Catalogue : la date du jeu de données ---------------------------------------------------

    @Test
    fun theCatalogGivesTheDateOfTheDataset() {
        server.respond(CATALOG_PATH, 200, CATALOG)
        val (rouen, troyes) = client().networks()
        assertEquals(time("2026-11-19T14:24:01Z"), rouen.attribution?.updatedAt)
        assertEquals(time("2026-11-20T15:22:55Z"), rouen.indexAt)
        assertEquals(NetworkStatus.STALE, troyes.status)
        assertNull(troyes.attribution?.updatedAt)
        // Elle reste connue du téléphone hors ligne.
        val reread = NetworkCatalog.parse(NetworkCatalog(listOf(rouen, troyes), 1L).toJson().toString())
        assertEquals(rouen.attribution, reread.byId(NETWORK)?.attribution)
        assertNull(reread.byId("troyes")?.attribution?.updatedAt)
    }

    /** La Licence Ouverte demande la date de la dernière mise à jour du jeu : l'écran des sources la donne. */
    @Test
    fun theSourcesScreenGivesTheDateOfTheDataset() {
        server.respond(CATALOG_PATH, 200, CATALOG)
        val rouen = client().networks().first()
        val paragraph = NetworkMentions.paragraph(rouen, "20 novembre 2026", "19 novembre 2026").split("<br>")
        assertEquals("<b>Rouen · Astuce</b>", paragraph.first())
        assertEquals(MENTION, paragraph[1])
        assertEquals("Jeu de données mis à jour le 19 novembre 2026.", paragraph.last())
        assertFalse(paragraph.any { it.startsWith("Horaires compilés") })
        // Sans elle, la date du référentiel compilé par Tracéo.
        assertEquals("Horaires compilés par Tracéo le 20 novembre 2026.", NetworkMentions.paragraph(rouen, "20 novembre 2026").split("<br>").last())
    }

    // --- Lignes : couleurs nulles, chacune de son côté --------------------------------------------

    @Test
    fun colourAndTextColourAreEachEitherPublishedOrNull() {
        server.respond(LINES_PATH, 200, LINES)
        val byCode = client().lines(NETWORK).associateBy { it.shortName }
        assertEquals("TCAR:10", byCode.getValue("10").id)
        assertEquals("F8AE4C", byCode.getValue("10").color)
        assertEquals("1D1D1B", byCode.getValue("10").textColor)
        assertNull(byCode.getValue("26").color)
        assertNull(byCode.getValue("26").textColor)
        assertEquals("EF7A91", byCode.getValue("8").color)
        assertNull(byCode.getValue("8").textColor)
    }

    // --- Les 404 et leur code ------------------------------------------------------------------

    /** Le `code` tranche, même si le message change de mots. */
    @Test
    fun theCodeOfANotFoundSaysWhatIsMissing() {
        val cases = listOf(
            "network" to TraceoException.Missing.NETWORK,
            "line" to TraceoException.Missing.LINE,
            "stop" to TraceoException.Missing.STOP,
        )
        for ((code, missing) in cases) {
            server.clear()
            server.respond(PASSAGES_PATH, 404, """{"error": "Message reformulé.", "code": "$code"}""")
            val error = expectError { client().departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK) }
            assertEquals(code, TraceoException.Kind.NOT_FOUND, error.kind)
            assertEquals(code, missing, error.missing)
            assertTrue(code, error.message.orEmpty().endsWith("reconfigurez ce widget."))
        }
        // Les réponses telles que servies.
        server.clear()
        server.respond(STATIONS_PATH, 404, """{"error": "Ligne inconnue.", "code": "line"}""")
        assertEquals("Ligne inconnue de Tracéo.", expectError { client().stops("TCAR:99", NETWORK) }.message)
    }

    /** `route` : un serveur qui ne sert pas ces routes. Le widget garde ses passages. */
    @Test
    fun theRouteCodeIsNotTheFaultOfTheWidget() {
        server.respond(PASSAGES_PATH, 404, """{"error": "Route inconnue.", "code": "route"}""")
        val error = expectError { client().departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK) }
        assertEquals(TraceoException.Kind.UNAVAILABLE, error.kind)
        assertEquals(TraceoClient.ROUTES_MISSING_MESSAGE, error.message)
    }

    /** Un code que l'application ne connaît pas l'emporte sur un message qu'elle connaît : 404 sans précision. */
    @Test
    fun anUnknownCodeWinsOverAKnownMessage() {
        server.respond(PASSAGES_PATH, 404, """{"error": "Ligne inconnue.", "code": "dataset"}""")
        val error = expectError { client().departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK) }
        assertEquals(TraceoException.Kind.NOT_FOUND, error.kind)
        assertNull(error.missing)
    }

    // --- Le réveil, dit par Tracéo ----------------------------------------------------------------

    @Test
    fun aSnapshotSaysWhetherItsNetworkIsWaking() {
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val served = client().departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK)
        assertEquals(false, served.waking)
        assertEquals(2, served.departures.size)
        assertEquals(time("2026-11-20T15:44:11Z"), served.nextAt)

        server.clear()
        server.respond(PASSAGES_PATH, 200, WAKING)
        val waking = client().departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK)
        assertEquals(true, waking.waking)
        assertNull(waking.dataAt)
        assertEquals(false, waking.fresh)

        // Un ancien serveur ne dit rien : null, et le réveil se déduit comme avant.
        server.clear()
        server.respond(PASSAGES_PATH, 200, """{"passages": [], "fetchedAt": null, "fresh": false, "nextAt": "2026-11-20T15:42:32Z"}""")
        assertNull(client().departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK).waking)
    }

    /** Tracéo dit que le réseau se réveille : le widget revient à l'heure annoncée, quel que soit son intervalle. */
    @Test
    fun aWidgetComesBackWhenTraceoSaysItsNetworkWakes() {
        val asked = time("2026-11-20T15:42:29Z")
        server.respond(PASSAGES_PATH, 200, WAKING, mapOf("Date" to WAKING_DATE))
        val snapshot = client { asked }.departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK)
        val state = stateFrom(listOf(snapshot), null, asked)
        assertEquals(asked + 3_000L, state.wakeAt)
        assertNull(state.error)
        for (chosen in Prefs.REFRESH_CHOICES) {
            assertEquals("Réglage $chosen min", asked + 3_000L + RefreshSchedule.WAKE_MARGIN_MS, dueAt(state, chosen, asked))
        }
    }

    /**
     * Un flux en panne rend `waking` faux, sans relevé récent, et peut annoncer le prochain tout
     * près : ce n'est plus pris pour un réveil, le widget garde son intervalle.
     */
    @Test
    fun aBrokenFeedIsNoLongerTakenForAWake() {
        val now = time("2026-11-20T15:42:29Z")
        val broken = PassageSnapshot(emptyList(), dataAt = now - 20 * MINUTE, fresh = false, nextAt = now + 3_000L, viaTraceo = true, waking = false)
        val state = stateFrom(listOf(broken), null, now)
        assertNull(state.wakeAt)
        assertNull(dueAt(state, 0, now))
        assertEquals(now + 2 * MINUTE - RefreshSchedule.EARLY_MS, dueAt(state, 2, now))
        // Sans l'indication, la même réponse passait pour un réveil.
        assertEquals(now + 3_000L, stateFrom(listOf(broken.copy(waking = null)), null, now).wakeAt)
    }

    /** Relevé en retard : `nextAt` est à 30 secondes, `waking` est faux. Rien à attendre de plus que d'habitude. */
    @Test
    fun aLateReadingIsNotAWake() {
        val now = time("2026-11-20T15:42:29Z")
        val late = PassageSnapshot(emptyList(), dataAt = now - 5 * MINUTE, fresh = false, nextAt = now + 30_000L, viaTraceo = true, waking = false)
        assertNull(stateFrom(listOf(late), null, now).wakeAt)
    }

    /**
     * L'heure annoncée vient de l'horloge du serveur. Pendant un réveil, elle est ramenée à celle du
     * téléphone d'après l'en-tête Date : une horloge en avance ou en retard ne fait plus manquer le
     * retour, ni ne le repousse.
     */
    @Test
    fun theWakeDoesNotDependOnTheClockOfThePhone() {
        val serverNow = time("2026-11-20T15:42:29Z")
        for (skew in listOf(-5 * MINUTE, -15_000L, 0L, 15_000L, 90_000L, 10 * MINUTE)) {
            val phoneNow = serverNow + skew
            server.clear()
            server.respond(PASSAGES_PATH, 200, WAKING, mapOf("Date" to WAKING_DATE))
            val snapshot = client { phoneNow }.departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK)
            assertEquals("Décalage $skew", phoneNow + 3_000L, snapshot.nextAt)
            assertEquals("Décalage $skew", phoneNow + 3_000L, stateFrom(listOf(snapshot), null, phoneNow).wakeAt)
        }
    }

    /** Sans en-tête Date, l'heure du retour reste bornée : jamais avant la réponse, jamais au-delà du délai d'un réveil. */
    @Test
    fun withoutADateTheWakeStaysBounded() {
        val serverNow = time("2026-11-20T15:42:29Z")
        server.respond(PASSAGES_PATH, 200, WAKING)
        val ahead = serverNow + 90_000L
        val early = stateFrom(listOf(client { ahead }.departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK)), null, ahead)
        assertEquals(ahead, early.wakeAt)
        assertEquals(ahead + RefreshSchedule.WAKE_MIN_GAP_MS, dueAt(early, 2, ahead))
        val behind = serverNow - 5 * MINUTE
        val far = stateFrom(listOf(client { behind }.departures("TCAR:ST:MMAIR", "TCAR:10", NETWORK)), null, behind)
        assertEquals(behind + RefreshSchedule.WAKE_WINDOW_MS, far.wakeAt)
        // Un en-tête illisible compte comme absent.
        assertNull(Http.parseDate("hier"))
        assertNull(Http.parseDate(null))
        assertEquals(serverNow, Http.parseDate(WAKING_DATE))
    }

    /** Un réveil qui durerait au-delà de deux retours n'est plus attendu : le widget reprend son intervalle. */
    @Test
    fun theReturnsOfAnAnnouncedWakeAreStillBounded() {
        var at = time("2026-11-20T15:42:29Z")
        var state: WidgetState? = null
        val wakes = ArrayList<Boolean>()
        repeat(3) {
            val waking = PassageSnapshot(emptyList(), null, false, at + 3_000L, viaTraceo = true, waking = true)
            state = stateFrom(listOf(waking), state, at)
            wakes += state!!.wakeAt != null
            at = (state!!.wakeAt ?: at) + RefreshSchedule.WAKE_MARGIN_MS
        }
        assertEquals(listOf(true, true, false), wakes)
    }

    /** Un arrêt interrogé par plusieurs quais : il suffit qu'une réponse dise que le réseau se réveille. */
    @Test
    fun oneWakingResponseIsEnough() {
        val now = time("2026-11-20T15:42:29Z")
        val served = PassageSnapshot(emptyList(), now - MINUTE, true, now + MINUTE, viaTraceo = true, waking = false)
        val waking = PassageSnapshot(emptyList(), null, false, now + 3_000L, viaTraceo = true, waking = true)
        assertEquals(now + 3_000L, stateFrom(listOf(served, waking), null, now).wakeAt)
        assertNull(stateFrom(listOf(served, served), null, now).wakeAt)
    }

    // --- Stations qui réunissent leurs quais ----------------------------------------------------

    /**
     * Tracéo répond pour la station entière, quel que soit le quai demandé : un widget dont l'arrêt
     * réunit deux quais reçoit deux fois la même réponse, et ne montre chaque passage qu'une fois.
     */
    @Test
    fun aStopAskedByTwoOfItsPlatformsShowsEachPassageOnce() {
        val now = time("2026-11-20T15:42:30Z")
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val twoPlatforms = stop.copy(stopIds = listOf("TCAR:ST:MMAIR", "TCAR:MMAIR2"))
        val config = config.copy(stop = twoPlatforms, directions = Directions.allDirections(twoPlatforms))
        val responses = twoPlatforms.stopIds.map { client().departures(it, "TCAR:10", NETWORK) }
        assertEquals(4, responses.sumOf { it.departures.size })
        val state = stateFrom(responses, null, now, of = config)
        assertEquals(listOf("Lycée Flaubert", "Maromme La Maine"), state.departures.map { it.destination })

        // Un sens coché par quai, comme à l'époque où chaque quai était un arrêt : chacun garde le sien.
        val oneEach = config.copy(directions = mapOf("TCAR:ST:MMAIR" to setOf("Lycée Flaubert"), "TCAR:MMAIR2" to setOf("Maromme La Maine")))
        assertEquals(listOf("Lycée Flaubert", "Maromme La Maine"), stateFrom(responses, null, now, of = oneEach).departures.map { it.destination })
        val secondOnly = config.copy(directions = mapOf("TCAR:ST:MMAIR" to setOf("Nulle part"), "TCAR:MMAIR2" to setOf("Maromme La Maine")))
        assertEquals(listOf("Maromme La Maine"), stateFrom(responses, null, now, of = secondOnly).departures.map { it.destination })
    }
}
