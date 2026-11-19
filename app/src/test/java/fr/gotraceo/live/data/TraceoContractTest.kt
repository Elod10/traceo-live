package fr.gotraceo.live.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Le client face au contrat tel que Tracéo le sert, là où il s'écarte du contrat v0 du pilote
 * (chantiers/multi-reseaux/reseaux-ouverts.md, chapitre 5). Les réponses sont celles de sa
 * documentation ; l'identifiant du réseau n'est qu'un exemple, l'application n'en écrit aucun.
 */
class TraceoContractTest {

    private companion object {
        /** Un identifiant d'exemple : il vient toujours du catalogue. */
        const val NETWORK = "tcat"
        const val CATALOG_PATH = "/api/reseaux"
        const val LINES_PATH = "/api/reseaux/$NETWORK/lines"
        const val STATIONS_PATH = "/api/reseaux/$NETWORK/stations"
        const val PASSAGES_PATH = "/api/reseaux/$NETWORK/passages"
        const val INDEX_PATH = "/api/reseaux/$NETWORK/index"

        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L

        /** La mention complète, telle que le serveur l'écrit. */
        const val FULL_MENTION = "Contient des informations du jeu « Réseau urbain TCAT » (TCAT, Troyes Champagne " +
            "Métropole), mises à disposition sous licence ODbL."

        const val CATALOG = """{"version": 1, "networks": [{
            "id": "tcat", "name": "TCAT", "city": "Troyes",
            "bbox": {"latMin": 48.169, "latMax": 48.4139, "lonMin": 3.8903, "lonMax": 4.2591},
            "modes": ["bus"], "refreshSec": 60, "status": "ok",
            "attribution": {"source": "TCAT, Troyes Champagne Métropole",
                            "url": "https://transport.data.gouv.fr/datasets/donnees-tcat-troyes-champagne-metropole-1",
                            "licence": "ODbL", "licenceUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
                            "text": "$FULL_MENTION"},
            "indexAt": "2026-11-11T19:13:41Z"}]}"""

        /** L'identifiant d'une ligne est le `route_id` du réseau : « 204 » pour la ligne « 2B ». */
        const val LINES = """{"lines": [
            {"id": "204", "code": "2B", "name": "Saint Thibault Vers Bucheres",
             "color": "#E20019", "textColor": "#FFFFFF", "mode": {"key": "bus", "label": "Bus"}},
            {"id": "7", "code": "T1", "name": "Sans couleur publiée",
             "color": null, "textColor": null, "mode": {"key": "tram", "label": "Tramway"}},
            {"id": "31", "code": "31", "name": "Blanc servi faute de couleur",
             "color": "#FFFFFF", "textColor": "#000000", "mode": {"key": "bus", "label": "Bus"}},
            {"id": "90", "code": "TB", "name": "Mode que l'application ne connaît pas",
             "color": "#123456", "textColor": "#FFFFFF", "mode": {"key": "trolleybus", "label": "Trolleybus"},
             "agency": "champ en plus", "accessible": true},
            {"id": "91", "code": "SM", "name": "Sans mode"}
        ]}"""

        /** Exemple réel du 11 novembre 2026 : un bus en avance, un à l'heure. */
        const val PASSAGES = """{"passages": [
            {"destination": "1er Ram", "mission": "", "direction": "0",
             "aimed": "2026-11-11T19:18:20Z", "expected": "2026-11-11T19:16:18Z",
             "delayMin": -2, "status": "onTime", "atStop": false},
            {"destination": "Gare Jardins", "mission": "", "direction": "1",
             "aimed": "2026-11-11T19:19:19Z", "expected": "2026-11-11T19:19:09Z",
             "delayMin": 0, "status": "onTime", "atStop": false},
            {"destination": "Gare Jardins", "mission": "", "direction": "1",
             "aimed": "2026-11-11T19:31:00Z", "expected": "2026-11-11T19:31:00Z",
             "delayMin": 0, "status": "cancelled", "atStop": false, "vehicle": "champ en plus"}],
            "fetchedAt": "2026-11-11T19:13:57Z", "ageSec": 31, "fresh": true,
            "nextAt": "2026-11-11T19:15:03+00:00", "network": "champ en plus"}"""

        /** Un réseau que personne n'a encore demandé : pas de relevé, et l'heure de son réveil. */
        const val NEVER_FETCHED = """{"passages": [], "fetchedAt": null, "ageSec": null, "fresh": false,
            "nextAt": "2026-11-11T19:14:05+00:00"}"""
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

    private fun expectError(block: () -> Unit): TraceoException {
        try {
            block()
        } catch (e: TraceoException) {
            return e
        }
        fail("TraceoException attendue")
        throw AssertionError()
    }

    private fun time(iso: String): Long = SiriParser.parseTime(iso)!!

    private val line = Line("204", "Saint Thibault Vers Bucheres", "2B", "bus", networkId = NETWORK)
    private val stop = Stop("Patton", null, listOf("1:PATTO"), NETWORK)
    private val config = WidgetConfig(1, TransportMode.BUS, line, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS)

    // --- Catalogue -----------------------------------------------------------------------------

    @Test
    fun theCatalogCarriesTheFullMention() {
        server.respond(CATALOG_PATH, 200, CATALOG)
        val network = client().networks().single()
        assertEquals(FULL_MENTION, network.attribution?.text)
        assertEquals(BoundingBox(48.169, 48.4139, 3.8903, 4.2591), network.bbox)
        assertEquals(time("2026-11-11T19:13:41Z"), network.indexAt)
    }

    @Test
    fun aCatalogWithUnknownValuesStillListsItsNetworks() {
        server.respond(
            CATALOG_PATH, 200,
            """{"version": 2, "generatedAt": "2026-11-18T10:00:00Z", "networks": [
                {"id": "ville-a", "name": "Réseau A", "city": "Ville A", "region": "champ en plus",
                 "bbox": {"latMin": 44.7, "latMax": 45.0, "lonMin": -0.8, "lonMax": -0.4, "marge": 0.015},
                 "modes": ["bus", "tram", "boat", "trolleybus", "coach"], "refreshSec": 30, "status": "ok"},
                {"id": "ville-b", "name": "Réseau B", "city": "Ville B",
                 "bbox": {"latMin": 43.6, "latMax": 43.8, "lonMin": 7.1, "lonMax": 7.4},
                 "modes": ["cable", "funicular", "metro"], "status": "off"}]}""",
        )
        val (a, b) = client().networks()
        // Deux clés inconnues se rangent avec les bus, une seule fois.
        assertEquals(listOf("bus", "tram", "boat"), a.modes)
        assertEquals(1, a.sourceMinutes)
        assertEquals(listOf("cableway", "funicular", "metro"), b.modes)
        assertEquals(NetworkStatus.OFF, b.status)
    }

    /** Le fichier du téléphone garde les modes dans le vocabulaire de l'application : le relire ne les change pas. */
    @Test
    fun theModesOfANetworkSurviveTheFileOfThePhone() {
        val network = Network.fromJson(
            org.json.JSONObject(
                """{"id": "ville-b", "name": "B", "city": "B",
                    "bbox": {"latMin": 1, "latMax": 2, "lonMin": 3, "lonMax": 4},
                    "modes": ["cable", "funicular", "boat", "rail"]}""",
            ),
        )!!
        assertEquals(listOf("cableway", "funicular", "boat", "rail"), network.modes)
        assertEquals(network.modes, Network.fromJson(network.toJson())!!.modes)
    }

    // --- Lignes --------------------------------------------------------------------------------

    @Test
    fun theIdentifierOfALineIsNotItsCode() {
        server.respond(LINES_PATH, 200, LINES)
        val byCode = client().lines(NETWORK).associateBy { it.shortName }

        val line2B = byCode.getValue("2B")
        assertEquals("204", line2B.id)
        assertEquals("Saint Thibault Vers Bucheres", line2B.name)
        assertEquals("E20019", line2B.color)
        assertEquals("$NETWORK:204", line2B.key)
        // `realtime` n'est pas servi : une ligne est supposée avoir du temps réel.
        assertTrue(byCode.values.all { it.realtime })
    }

    @Test
    fun aLineWithoutPublishedColourHasNone() {
        server.respond(LINES_PATH, 200, LINES)
        val byCode = client().lines(NETWORK).associateBy { it.shortName }
        assertNull(byCode.getValue("T1").color)
        assertNull(byCode.getValue("T1").textColor)
        assertEquals("tram", byCode.getValue("T1").mode)
        // Tant que le serveur sert du blanc et du noir faute de couleur, le client les rend tels quels :
        // c'est à l'affichage de reconnaître ce couple (cf. Palettes).
        assertEquals("FFFFFF", byCode.getValue("31").color)
        assertEquals("000000", byCode.getValue("31").textColor)
    }

    @Test
    fun aLineOfAnUnknownModeStaysReachableWithTheBuses() {
        server.respond(LINES_PATH, 200, LINES)
        val byCode = client().lines(NETWORK).associateBy { it.shortName }
        assertEquals("bus", byCode.getValue("TB").mode)
        assertEquals("123456", byCode.getValue("TB").color)
        assertEquals("bus", byCode.getValue("SM").mode)
        assertTrue(TransportMode.BUS.includes(byCode.getValue("TB")))
        assertEquals(5, byCode.size)
    }

    @Test
    fun theIdentifierOfTheLineGoesToTheOtherRoutesNotItsCode() {
        server.respond(STATIONS_PATH, 200, """{"stations": [{"id": "1:CHART", "name": "Chartreux", "town": null}]}""")
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val traceo = client()
        assertEquals(listOf(Stop("Chartreux", null, listOf("1:CHART"), NETWORK)), traceo.stops(line.id, NETWORK))
        traceo.departures("1:CHART", line.id, NETWORK)
        assertEquals(
            listOf("$STATIONS_PATH?line=204", "$PASSAGES_PATH?line=204&stop=1%3ACHART"),
            server.requests.map { it.target },
        )
    }

    // --- Passages ------------------------------------------------------------------------------

    @Test
    fun aBusAheadOfTimeHasNoDelay() {
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val snapshot = client().departures("1:PATTO", "204", NETWORK)
        assertEquals(3, snapshot.departures.size)

        val early = snapshot.departures[0]
        assertEquals(time("2026-11-11T19:16:18Z"), early.expectedArrival)
        assertEquals(time("2026-11-11T19:18:20Z"), early.aimedArrival)
        assertEquals("Un retard négatif n'est pas un retard", 0, early.delayMinutes)
        assertEquals("0", early.directionRef)
        assertNull("Une mission vide n'est pas une mission", early.journeyNote)

        assertEquals(time("2026-11-11T19:13:57Z"), snapshot.dataAt)
        assertEquals(true, snapshot.fresh)
        assertEquals(time("2026-11-11T19:15:03Z"), snapshot.nextAt)
    }

    /** Un passage supprimé porte l'heure théorique dans `expected` : il garde une heure, et son état. */
    @Test
    fun aCancelledPassageKeepsItsScheduledTime() {
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val cancelled = client().departures("1:PATTO", "204", NETWORK).departures[2]
        assertTrue(cancelled.isCancelled)
        assertEquals(time("2026-11-11T19:31:00Z"), cancelled.displayTime)
        assertEquals(0, cancelled.delayMinutes)
    }

    @Test
    fun twentyPassagesOfBothDirectionsAreAllRead() {
        val passages = (0 until 20).joinToString(",") { i ->
            """{"destination": "Terminus ${i % 2}", "mission": "", "direction": "${i % 2}",
                "aimed": null, "expected": "2026-11-11T19:${20 + i}:00Z", "delayMin": 0, "status": "onTime", "atStop": false}"""
        }
        server.respond(PASSAGES_PATH, 200, """{"passages": [$passages], "fetchedAt": "2026-11-11T19:13:57Z", "ageSec": 5, "fresh": true, "nextAt": null}""")
        val snapshot = client().departures("1:PATTO", "204", NETWORK)
        assertEquals(20, snapshot.departures.size)
        assertEquals(setOf("0", "1"), snapshot.departures.map { it.directionRef }.toSet())
    }

    // --- Réseau jamais relevé : il se réveille -------------------------------------------------

    @Test
    fun aNetworkNeverFetchedSaysWhenItWakes() {
        server.respond(PASSAGES_PATH, 200, NEVER_FETCHED)
        val snapshot = client().departures("1:PATTO", "204", NETWORK)
        assertTrue(snapshot.departures.isEmpty())
        assertNull(snapshot.dataAt)
        assertEquals(false, snapshot.fresh)
        assertEquals(time("2026-11-11T19:14:05Z"), snapshot.nextAt)
        assertTrue(snapshot.viaTraceo)
    }

    /** L'échéance d'un widget, calculée comme le fait l'actualisation : son réveil n'y compte que s'il est encore attendu. */
    private fun dueAt(state: WidgetState, chosen: Int, networkNextAt: Long?, now: Long, offsetMs: Long = 40_000L): Long? =
        RefreshSchedule.dueAt(
            state.attemptedAt, chosen, true, networkNextAt, offsetMs, sourceMinutes = 1,
            wakeAt = state.wakeAt.takeIf { RefreshSchedule.awaitsWake(it, now) },
        )

    /** Une réponse de réseau endormi, reçue à [at] : pas de relevé récent, le prochain dans [inMs]. */
    private fun waking(at: Long, inMs: Long = 4_000L) = PassageSnapshot(emptyList(), dataAt = null, fresh = false, nextAt = at + inMs, viaTraceo = true)

    private fun stateFrom(snapshot: PassageSnapshot, previous: WidgetState?, at: Long, of: WidgetConfig = config) =
        WidgetStates.from(of, listOf(Result.success(snapshot)), previous, at, removeTerminusArrivals = false)

    /**
     * Le widget revient à l'heure du réveil, quel que soit l'intervalle choisi, au lieu de rester
     * une cadence entière sans passage.
     */
    @Test
    fun aWidgetComesBackWhenItsNetworkWakes() {
        server.respond(PASSAGES_PATH, 200, NEVER_FETCHED)
        val snapshot = client().departures("1:PATTO", "204", NETWORK)
        val nextAt = snapshot.nextAt!!
        val asked = nextAt - 4_000L
        val state = stateFrom(snapshot, null, asked)
        assertEquals(false, state.fresh)
        assertNull(state.error)
        assertEquals(nextAt, state.wakeAt)

        for (chosen in Prefs.REFRESH_CHOICES) {
            assertEquals("Réglage $chosen min", nextAt + RefreshSchedule.WAKE_MARGIN_MS, dueAt(state, chosen, nextAt, now = asked))
        }
        // Les installations s'étalent sur quelques secondes, au lieu de revenir toutes au même instant.
        val spread = (0L..40_000L step 500L).map { dueAt(state, 2, nextAt, asked, offsetMs = it)!! - nextAt }.toSet()
        assertTrue(spread.size > 4)
        assertTrue(spread.all { it in RefreshSchedule.WAKE_MARGIN_MS until RefreshSchedule.WAKE_MARGIN_MS + RefreshSchedule.WAKE_SPREAD_MS })
        // Sans le réveil, le même widget attendrait son intervalle, ou le prochain toucher.
        val plain = state.copy(wakeAt = null)
        assertNull(dueAt(plain, 0, nextAt, asked))
        assertEquals(asked + 10 * MINUTE - RefreshSchedule.EARLY_MS, dueAt(plain, 10, nextAt, asked))
        // Jamais à moins de quelques secondes de la demande, même si le réveil est annoncé tout de suite.
        val soon = stateFrom(waking(asked, inMs = 500L), null, asked)
        assertEquals(asked + RefreshSchedule.WAKE_MIN_GAP_MS, dueAt(soon, 2, asked + 500L, asked))
    }

    /**
     * Une réponse sans relevé récent qui annonce le prochain tout près n'est pas toujours un réveil :
     * un flux en panne en donne aussi, tant que dure la panne. Le widget n'y revient que deux fois
     * de suite, puis reprend son intervalle.
     */
    @Test
    fun theReturnsOfAWakeAreBounded() {
        var at = NOW
        var state = stateFrom(waking(at), null, at)
        val wakes = ArrayList<Long?>()
        repeat(5) {
            wakes += state.wakeAt
            at = (state.wakeAt ?: (at + 2 * MINUTE)) + RefreshSchedule.WAKE_MARGIN_MS
            state = stateFrom(waking(at), state, at)
        }
        // La première réponse et celle du premier retour posent chacune un retour : deux en tout. La
        // réponse du second n'en pose plus, et l'actualisation ordinaire suivante repart de zéro.
        assertEquals(listOf(true, true, false, true, true), wakes.map { it != null })
        assertEquals(2, RefreshSchedule.WAKE_MAX_RETURNS)

        // Au troisième retour refusé, l'échéance est celle de l'intervalle, ou rien en manuel.
        val exhausted = generateSequence(stateFrom(waking(NOW), null, NOW)) { previous ->
            previous.wakeAt?.let { wakeAt -> stateFrom(waking(wakeAt + 2_000L), previous, wakeAt + 2_000L) }
        }.last()
        assertNull(exhausted.wakeAt)
        assertNull(dueAt(exhausted, 0, exhausted.attemptedAt + 4_000L, exhausted.attemptedAt))
        assertEquals(
            exhausted.attemptedAt + 5 * MINUTE - RefreshSchedule.EARLY_MS,
            dueAt(exhausted, 5, exhausted.attemptedAt + 4_000L, exhausted.attemptedAt),
        )
    }

    @Test
    fun whatIsNotAWake() {
        // Relevé frais, ou source en direct : rien à attendre.
        assertNull(RefreshSchedule.wakeAt(NETWORK, true, NOW + 3_000L, NOW, 0))
        assertNull(RefreshSchedule.wakeAt(NETWORK, null, NOW + 3_000L, NOW, 0))
        // Flux muet : le prochain relevé est annoncé à une cadence de là.
        assertNull(RefreshSchedule.wakeAt(NETWORK, false, NOW + MINUTE, NOW, 0))
        // Annonce déjà passée (horloge du téléphone en avance) : l'intervalle s'applique.
        assertNull(RefreshSchedule.wakeAt(NETWORK, false, NOW - 3_000L, NOW, 0))
        // Réseau coupé : pas d'annonce.
        assertNull(RefreshSchedule.wakeAt(NETWORK, false, null, NOW, 0))
        assertNull(RefreshSchedule.wakeAt(NETWORK, false, 0L, NOW, 0))
        // L'Île-de-France est relevée en continu : elle ne se réveille pas, quoi que dise la réponse.
        assertNull(RefreshSchedule.wakeAt(Networks.IDFM, false, NOW + 3_000L, NOW, 0))
        assertEquals(NOW + 3_000L, RefreshSchedule.wakeAt(NETWORK, false, NOW + 3_000L, NOW, 0))

        val paris = Stop("Bastille", null, listOf("stop_area:IDFM:71634"))
        val idf = WidgetConfig(1, TransportMode.METRO, Line("C01371", "1", "1", "metro"), paris, Directions.allDirections(paris), "")
        val idfState = stateFrom(waking(NOW), null, NOW, of = idf)
        assertNull(idfState.wakeAt)
        assertEquals(NOW + 2 * MINUTE - RefreshSchedule.EARLY_MS, dueAt(idfState, 2, null, NOW))
    }

    /** Écran resté éteint, retour en échec : le réveil n'est plus attendu, et ne bloque pas le suivant. */
    @Test
    fun aMissedWakeGivesWayToTheInterval() {
        val state = stateFrom(waking(NOW), null, NOW)
        val late = state.wakeAt!! + RefreshSchedule.WAKE_GRACE_MS + 1L
        assertFalse(RefreshSchedule.awaitsWake(state.wakeAt, late))
        assertNull(dueAt(state, 0, state.wakeAt, late))
        assertEquals(NOW + 2 * MINUTE - RefreshSchedule.EARLY_MS, dueAt(state, 2, state.wakeAt, late))

        // Le retour échoue : l'état garde ses passages, n'attend plus de réveil.
        val failed = WidgetStates.from(
            config, listOf(Result.failure(TraceoException(TraceoException.Kind.NETWORK, "Erreur réseau"))), state,
            state.wakeAt!! + 2_000L, removeTerminusArrivals = false,
        )
        assertNull(failed.wakeAt)
        // Une heure après, le réseau s'est rendormi : c'est un nouveau réveil, avec tous ses retours.
        val later = NOW + 60 * MINUTE
        val again = stateFrom(waking(later), state, later)
        assertEquals(later + 4_000L, again.wakeAt)
        assertEquals(0, again.wakeReturns)
    }

    /**
     * Le réveil se décide sur les réponses du widget lui-même : un autre widget du même réseau,
     * servi après le relevé, a déjà reçu une annonce lointaine, qui ne doit pas annuler ce retour.
     */
    @Test
    fun anotherWidgetOfTheSameNetworkDoesNotCancelTheReturn() {
        val state = stateFrom(waking(NOW), null, NOW)
        val announcedToTheOther = NOW + 62_000L
        assertEquals(state.wakeAt!! + RefreshSchedule.WAKE_MARGIN_MS, dueAt(state, 0, announcedToTheOther, NOW + 3_000L))
        assertEquals(state.wakeAt!! + RefreshSchedule.WAKE_MARGIN_MS, dueAt(state, 5, announcedToTheOther, NOW + 3_000L))
    }

    /** Une fois le premier relevé reçu, le widget reprend le rythme annoncé par le réseau. */
    @Test
    fun afterTheWakeTheWidgetFollowsTheNetwork() {
        server.respond(PASSAGES_PATH, 200, PASSAGES)
        val snapshot = client().departures("1:PATTO", "204", NETWORK)
        val asked = snapshot.dataAt!! + 20_000L
        val woken = stateFrom(waking(asked - 6_000L), null, asked - 6_000L)
        val state = stateFrom(snapshot, woken, asked)
        assertEquals(true, state.fresh)
        assertNull(state.wakeAt)
        assertEquals(0, state.wakeReturns)
        assertEquals(snapshot.nextAt!! + 5_000L, dueAt(state, 1, snapshot.nextAt, asked, offsetMs = 5_000L))
    }

    @Test
    fun theWakeSurvivesTheFileOfThePhone() {
        val state = stateFrom(waking(NOW + 4_000L), stateFrom(waking(NOW), null, NOW), NOW + 6_000L)
        assertEquals(1, state.wakeReturns)
        val reread = WidgetState.fromJson(state.toJson())
        assertEquals(state.wakeAt, reread.wakeAt)
        assertEquals(1, reread.wakeReturns)
        // Un état enregistré avant ce champ n'attend aucun réveil.
        val old = WidgetState.fromJson(org.json.JSONObject("""{"fetchedAt": 1, "attemptedAt": 1, "departures": [], "fresh": false}"""))
        assertNull(old.wakeAt)
        assertEquals(0, old.wakeReturns)
    }

    // --- Réveil et format À proximité ----------------------------------------------------------

    private fun ranked(code: String, distance: Int) =
        RankedLine(NearbyLine(Line("id-$code", "Parcours $code", code, "bus", networkId = NETWORK), "Patton", null, "1:PATTO", distance), NearbyReason.NEAREST, distance, false)

    /**
     * Le format À proximité interroge ses lignes par lots. Sur un réseau qui se réveille, le premier
     * lot revient sans passage : il ne sert à rien d'essayer les lignes suivantes, qui prendraient
     * la place des meilleures. Le widget revient d'un bloc.
     */
    @Test
    fun theNearbyFormatStopsAtTheFirstBatchOfAWakingNetwork() {
        val lines = listOf(ranked("1", 50), ranked("2", 120), ranked("3", 300), ranked("4", 400))
        val batches = ArrayList<List<String>>()
        val state = runBlocking {
            Proximity.collect(lines, rows = 2, now = NOW, previous = null, removeTerminusArrivals = false) { batch ->
                batches += batch.map { it.line.shortName }
                batch.map { Result.success(waking(NOW)) }
            }
        }
        assertEquals(listOf(listOf("1", "2")), batches)
        assertEquals(NOW + 4_000L, state.wakeAt)
        assertEquals(false, state.fresh)

        // Au retour, le relevé est là : les lignes se remplissent, et plus rien n'est attendu.
        val fresh = PassageSnapshot(listOf(departure(inMinutes = 5)), dataAt = NOW + 5_000L, fresh = true, nextAt = NOW + 65_000L, viaTraceo = true)
        val after = runBlocking {
            Proximity.collect(lines, rows = 2, now = NOW + 6_000L, previous = state, removeTerminusArrivals = false) { batch ->
                batch.map { Result.success(fresh) }
            }
        }
        assertNull(after.wakeAt)
        assertEquals(2, after.entries.size)
        assertEquals(state.wakeAt, ProximityState.fromJson(state.toJson()).wakeAt)
    }

    /** Hors réveil, rien ne change : une ligne sans passage laisse sa place à la suivante. */
    @Test
    fun theNearbyFormatStillTriesTheNextLinesOtherwise() {
        val lines = listOf(ranked("1", 50), ranked("2", 120), ranked("3", 300))
        val empty = PassageSnapshot(emptyList(), dataAt = NOW - 10_000L, fresh = true, nextAt = NOW + 50_000L, viaTraceo = true)
        var calls = 0
        val state = runBlocking {
            Proximity.collect(lines, rows = 1, now = NOW, previous = null, removeTerminusArrivals = false) { batch ->
                calls++
                batch.map { Result.success(empty) }
            }
        }
        assertEquals(3, calls)
        assertNull(state.wakeAt)
    }

    // --- Réseau coupé, horaires pas encore compilés --------------------------------------------

    @Test
    fun aNetworkThatIsCutAnswersUnavailableOnEveryRoute() {
        val body = """{"error": "Service momentanément indisponible."}"""
        for (path in listOf(LINES_PATH, STATIONS_PATH, PASSAGES_PATH, INDEX_PATH)) server.respond(path, 503, body)
        val traceo = client()
        val errors = listOf(
            expectError { traceo.lines(NETWORK) },
            expectError { traceo.stops("204", NETWORK) },
            expectError { traceo.departures("1:PATTO", "204", NETWORK) },
            expectError { traceo.index(NETWORK) },
        )
        assertTrue(errors.all { it.kind == TraceoException.Kind.UNAVAILABLE })
        assertTrue(errors.all { it.message == TraceoClient.NETWORK_UNAVAILABLE_MESSAGE })
        assertEquals("Un seul appel par route, sans réchauffage", 4, server.requests.size)
    }

    @Test
    fun timetablesNotCompiledYetAreAPassingFailure() {
        server.respond(
            PASSAGES_PATH, 503,
            """{"error": "Horaires de ce réseau pas encore chargés : réessayez dans une minute."}""",
        )
        val error = expectError { client().departures("1:PATTO", "204", NETWORK) }
        assertEquals(TraceoException.Kind.UNAVAILABLE, error.kind)
        assertEquals(listOf(PASSAGES_PATH), server.paths())

        // Le widget garde ses passages et dit ce qui se passe ; il réessaiera à son intervalle.
        val previous = WidgetState(
            fetchedAt = 1_000L, attemptedAt = 1_000L,
            departures = listOf(departure(inMinutes = 6)), error = null, dataAt = 900L, fresh = true,
        )
        val state = WidgetStates.from(config, listOf(Result.failure(error)), previous, now = NOW, removeTerminusArrivals = false)
        assertEquals(previous.departures, state.departures)
        assertEquals(TraceoClient.NETWORK_UNAVAILABLE_MESSAGE, state.error)
        assertEquals(1_000L, state.fetchedAt)
        assertEquals(NOW, state.attemptedAt)
    }

    /** Un réseau coupé n'annonce plus de relevé : le widget s'en tient à son intervalle. */
    @Test
    fun withoutAnAnnouncedReadingTheWidgetKeepsItsInterval() {
        server.respond(PASSAGES_PATH, 200, """{"passages": [], "fetchedAt": "2026-11-11T19:13:57Z", "ageSec": 900, "fresh": false, "nextAt": null}""")
        val snapshot = client().departures("1:PATTO", "204", NETWORK)
        assertNull(snapshot.nextAt)
        assertEquals(false, snapshot.fresh)
        val state = stateFrom(snapshot, null, NOW)
        assertNull(state.wakeAt)
        assertEquals(NOW + 2 * MINUTE - RefreshSchedule.EARLY_MS, dueAt(state, 2, snapshot.nextAt, NOW))
    }

    // --- Les 404 -------------------------------------------------------------------------------

    @Test
    fun eachNotFoundSaysWhatIsMissing() {
        val cases = listOf(
            "Réseau inconnu." to TraceoException.Missing.NETWORK,
            "Ligne inconnue." to TraceoException.Missing.LINE,
            "Arrêt inconnu sur cette ligne." to TraceoException.Missing.STOP,
        )
        for ((said, missing) in cases) {
            server.clear()
            server.respond(PASSAGES_PATH, 404, """{"error": "$said"}""")
            val error = expectError { client().departures("1:PARTI", "204", NETWORK) }
            assertEquals(said, TraceoException.Kind.NOT_FOUND, error.kind)
            assertEquals(said, missing, error.missing)
            assertTrue(said, error.message.orEmpty().endsWith("reconfigurez ce widget."))
        }
    }

    /** À la configuration, il n'y a pas de widget à reconfigurer : le message s'arrête au constat. */
    @Test
    fun theListsSayWhatIsMissingWithoutAskingToReconfigure() {
        server.respond(LINES_PATH, 404, """{"error": "Réseau inconnu."}""")
        server.respond(STATIONS_PATH, 404, """{"error": "Ligne inconnue."}""")
        val traceo = client()
        val network = expectError { traceo.lines(NETWORK) }
        assertEquals(TraceoException.Missing.NETWORK, network.missing)
        assertEquals("Réseau inconnu de Tracéo.", network.message)
        val line = expectError { traceo.stops("204", NETWORK) }
        assertEquals(TraceoException.Missing.LINE, line.missing)
        assertEquals("Ligne inconnue de Tracéo.", line.message)
    }

    /**
     * « Route inconnue. » vient d'un serveur qui ne sert pas, ou plus, les routes des réseaux : le
     * widget n'est pas faux. Il garde ses passages, au lieu de se vider et de demander à être refait.
     */
    @Test
    fun aServerWithoutTheNetworkRoutesDoesNotEmptyTheWidgets() {
        server.respond(PASSAGES_PATH, 404, """{"error": "Route inconnue."}""")
        val error = expectError { client().departures("1:PATTO", "204", NETWORK) }
        assertEquals(TraceoException.Kind.UNAVAILABLE, error.kind)
        assertNull(error.missing)
        assertEquals(TraceoClient.ROUTES_MISSING_MESSAGE, error.message)

        val previous = WidgetState(1_000L, 1_000L, listOf(departure(inMinutes = 6)), null, dataAt = 900L, fresh = true)
        val kept = WidgetStates.from(config, listOf(Result.failure(error)), previous, NOW, removeTerminusArrivals = false)
        assertEquals(previous.departures, kept.departures)

        // Un arrêt que le réseau ne connaît plus, lui, vide le widget tout de suite.
        server.clear()
        server.respond(PASSAGES_PATH, 404, """{"error": "Arrêt inconnu sur cette ligne."}""")
        val gone = expectError { client().departures("1:PATTO", "204", NETWORK) }
        val emptied = WidgetStates.from(config, listOf(Result.failure(gone)), previous, NOW, removeTerminusArrivals = false)
        assertTrue(emptied.departures.isEmpty())
        assertEquals("Arrêt inconnu sur cette ligne : reconfigurez ce widget.", emptied.error)
    }

    /** Un message reformulé ou absent reste un 404 : le widget demande à être reconfiguré. */
    @Test
    fun anUnrecognisedNotFoundStaysANotFound() {
        for (body in listOf("""{"error": "Introuvable."}""", "{}", """{"error": null}""", """{"error": 404}""")) {
            server.clear()
            server.respond(PASSAGES_PATH, 404, body)
            val error = expectError { client().departures("1:PATTO", "204", NETWORK) }
            assertEquals(body, TraceoException.Kind.NOT_FOUND, error.kind)
            assertNull(body, error.missing)
            assertTrue(body, error.message.orEmpty().contains("reconfigurez"))
        }
    }

    /** Tracéo répond toujours en JSON : un 404 qui n'en est pas vient d'un intermédiaire, et ne vide pas le widget. */
    @Test
    fun aNotFoundThatIsNotFromTraceoKeepsThePassages() {
        for (body in listOf("<html><body>404 Not Found</body></html>", "", "Not Found")) {
            server.clear()
            server.respond(PASSAGES_PATH, 404, body)
            val error = expectError { client().departures("1:PATTO", "204", NETWORK) }
            assertEquals(body, TraceoException.Kind.UNAVAILABLE, error.kind)
            assertEquals(body, TraceoClient.ROUTES_MISSING_MESSAGE, error.message)
        }
    }

    /** Les listes de la configuration lisent « Route inconnue. » de la même façon. */
    @Test
    fun aServerWithoutTheNetworkRoutesSaysSoInTheConfigurationToo() {
        val body = """{"error": "Route inconnue."}"""
        for (path in listOf(LINES_PATH, STATIONS_PATH, INDEX_PATH)) server.respond(path, 404, body)
        val traceo = client()
        val errors = listOf(
            expectError { traceo.lines(NETWORK) },
            expectError { traceo.stops("204", NETWORK) },
            expectError { traceo.index(NETWORK) },
        )
        assertTrue(errors.all { it.kind == TraceoException.Kind.UNAVAILABLE && it.message == TraceoClient.ROUTES_MISSING_MESSAGE })
    }

    /** Le catalogue garde sa lecture : un serveur sans la route n'a pas d'autre réseau, ce n'est pas une panne. */
    @Test
    fun aMissingCatalogRouteStillMeansNoOtherNetwork() {
        server.respond(CATALOG_PATH, 404, """{"error": "Route inconnue."}""")
        assertEquals(TraceoException.Kind.NOT_FOUND, expectError { client().networks() }.kind)
    }

    /** L'Île-de-France garde ses messages : ses routes ne disent pas ce qui manque. */
    @Test
    fun ileDeFranceKeepsItsOwnMessages() {
        server.respond("/api/widget/passages", 404, """{"error": "Ligne inconnue."}""")
        val error = expectError { client().departures("stop_area:IDFM:71673", "C01371") }
        assertEquals(TraceoException.Kind.NOT_FOUND, error.kind)
        assertNull(error.missing)
        assertEquals("Arrêt ou ligne inconnus de Tracéo : reconfigurez ce widget.", error.message)

        // « Route inconnue. » et un 404 d'un intermédiaire y restent des 404, et son 503 garde son message.
        for (body in listOf("""{"error": "Route inconnue."}""", "<html>404</html>")) {
            server.clear()
            server.respond("/api/widget/passages", 404, body)
            assertEquals(body, TraceoException.Kind.NOT_FOUND, expectError { client().departures("stop_area:IDFM:71673", "C01371") }.kind)
        }
        server.clear()
        server.respond("/api/lines", 503, """{"error": "Service momentanément indisponible."}""")
        val unavailable = expectError { client().lines() }
        assertEquals(TraceoException.Kind.UNAVAILABLE, unavailable.kind)
        assertEquals("Tracéo est momentanément indisponible.", unavailable.message)
    }

    // --- Autres erreurs ------------------------------------------------------------------------

    @Test
    fun theOtherErrorsOfThePassageRoute() {
        val cases = listOf(
            400 to TraceoException.Kind.BAD_REQUEST,
            429 to TraceoException.Kind.RATE_LIMITED,
            500 to TraceoException.Kind.SERVER,
        )
        for ((status, kind) in cases) {
            server.clear()
            server.requests.clear()
            server.respond(PASSAGES_PATH, status, """{"error": "Paramètres 'line' et 'stop' attendus une fois chacun, en minuscules."}""")
            assertEquals(kind, expectError { client().departures("1:PATTO", "204", NETWORK) }.kind)
            assertEquals("HTTP $status : aucun nouvel essai", 1, server.requests.size)
        }
    }

    // --- Index ---------------------------------------------------------------------------------

    /** L'index reprend les lignes de la route des lignes : identifiant opaque, couleur absente, champ en plus. */
    @Test
    fun theIndexRefersToLinesByTheirIdentifier() {
        server.respond(
            INDEX_PATH, 200,
            """{"version": 1, "builtAt": "2026-11-11T19:13:41Z", "stopCount": 2,
                "lines": [{"id": "204", "code": "2B", "name": "…", "color": null, "textColor": null,
                           "mode": {"key": "bus", "label": "Bus"}},
                          {"id": "1", "code": "1", "name": "…", "color": "#009DE0", "textColor": "#FFFFFF",
                           "mode": {"key": "bus", "label": "Bus"}}],
                "stops": [{"zone": "1:11NOV", "name": "11 Novembre", "town": null,
                           "lat": 48.29441, "lon": 4.05995, "lines": [0, 1], "wheelchair": true}]}""",
        )
        val file = File.createTempFile("index", ".bin").also { files += it }
        val store = StopIndexStore(file, NETWORK) { client().index(it) }
        store.download()
        assertEquals(time("2026-11-11T19:13:41Z"), store.savedAt())

        val area = store.around(48.2944, 4.0599, 300)!!
        assertEquals(listOf("204", "1"), area.stops.single().lineIds)
        val line2B = area.lines.getValue("204")
        assertEquals("2B", line2B.shortName)
        assertNull(line2B.color)
        assertEquals(NETWORK, line2B.networkId)
    }

    private fun departure(inMinutes: Int) = Departure(
        lineRef = "204",
        destination = "Gare Jardins",
        directionName = null,
        directionRef = "1",
        expectedArrival = NOW + inMinutes * MINUTE,
        expectedDeparture = null,
        aimedArrival = null,
        aimedDeparture = null,
        atStop = false,
        platform = null,
        status = "onTime",
        journeyNote = null,
        stopId = "1:PATTO",
    )
}
