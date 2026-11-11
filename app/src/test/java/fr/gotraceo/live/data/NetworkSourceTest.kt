package fr.gotraceo.live.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le choix de la source par réseau : la clé PRIM, son quota et son repli ne valent que pour
 * l'Île-de-France ; ailleurs, les passages viennent toujours du cache de Tracéo.
 */
class NetworkSourceTest {

    private companion object {
        const val TCAT = "tcat"
        const val STATION = "1:ACACI"
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }

    /** Le cache de Tracéo, réseau par réseau : il retient ce qu'on lui a demandé. */
    private class FakeTraceo {
        val asked = ArrayList<Triple<String, String, String>>()

        fun forNetwork(networkId: String) = PassageSource { stopId, lineId ->
            asked += Triple(networkId, stopId, lineId)
            PassageSnapshot(emptyList(), dataAt = NOW, fresh = true, nextAt = NOW + MINUTE, viaTraceo = true)
        }
    }

    private val line2B = Line("2B", "Bréviandes - Voves", "2B", "bus", networkId = TCAT)

    private fun dep(destination: String, inMinutes: Int) = Departure(
        lineRef = "2B",
        destination = destination,
        directionName = null,
        directionRef = "0",
        expectedArrival = NOW + inMinutes * MINUTE,
        expectedDeparture = null,
        aimedArrival = null,
        aimedDeparture = null,
        atStop = false,
        platform = null,
        status = "onTime",
        journeyNote = null,
        stopId = STATION,
    )

    // --- Source d'un widget --------------------------------------------------------------------

    @Test
    fun anotherNetworkAlwaysGoesThroughTraceoWithOrWithoutKey() {
        for (key in listOf("", "secret")) {
            for (paused in listOf(false, true)) {
                val traceo = FakeTraceo()
                val source = DataSources.networkSource(TCAT, key, primPaused = paused, traceo = traceo::forNetwork)
                val snapshot = source.passages(STATION, "2B")
                assertTrue(snapshot.viaTraceo)
                assertEquals(listOf(Triple(TCAT, STATION, "2B")), traceo.asked)
            }
        }
    }

    /** Une station d'un autre réseau n'a pas la forme d'une zone francilienne : le quota PRIM ne la met pas en attente. */
    @Test
    fun theKeyQuotaDoesNotPauseAnotherNetwork() {
        assertFalse(TraceoClient.isTraceoStop(STATION))
        val traceo = FakeTraceo()
        DataSources.networkSource(TCAT, "secret", primPaused = true, traceo = traceo::forNetwork).passages(STATION, "2B")
        assertEquals(1, traceo.asked.size)
    }

    @Test
    fun ileDeFranceKeepsItsChoiceByTheKey() {
        assertTrue(DataSources.networkSource(Networks.IDFM, "") is TraceoClient)
        // Avec une clé : PRIM, enveloppé pour se replier sur Tracéo quand le quota est atteint.
        assertFalse(DataSources.networkSource(Networks.IDFM, "secret") is TraceoClient)

        val traceo = FakeTraceo()
        val paused = DataSources.networkSource(Networks.IDFM, "secret", primPaused = true, traceo = traceo::forNetwork)
        paused.passages("stop_area:IDFM:71634", "C01371")
        assertEquals(listOf(Triple(Networks.IDFM, "stop_area:IDFM:71634", "C01371")), traceo.asked)
    }

    @Test
    fun routeOfAWidgetDependsOnItsNetwork() {
        // Hors Île-de-France : Tracéo, que l'usager ait une clé PRIM ou non.
        assertEquals(PassageRoute.TRACEO, DataSources.route(TCAT, listOf(STATION), hasKey = false))
        assertEquals(PassageRoute.TRACEO, DataSources.route(TCAT, listOf(STATION), hasKey = true))
        assertEquals(PassageRoute.RECONFIGURE, DataSources.route(TCAT, emptyList(), hasKey = false))

        // En Île-de-France, rien ne change.
        assertEquals(PassageRoute.PRIM, DataSources.route(Networks.IDFM, listOf("IDFM:463158"), hasKey = true))
        assertEquals(PassageRoute.RECONFIGURE, DataSources.route(Networks.IDFM, listOf("IDFM:463158"), hasKey = false))
        assertEquals(PassageRoute.TRACEO, DataSources.route(Networks.IDFM, listOf("stop_area:IDFM:71348"), hasKey = false))

        val stop = Stop("Acacias", null, listOf(STATION), TCAT)
        val config = WidgetConfig(1, TransportMode.BUS, line2B, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS)
        assertEquals(PassageRoute.TRACEO, DataSources.route(config, hasKey = true))
    }

    // --- Source du format À proximité ----------------------------------------------------------

    @Test
    fun nearbyLinesOfAnotherNetworkNeverGoToPrim() {
        val nearby = NearbyLine(line2B, "Acacias", null, STATION, 80)
        for (key in listOf("", "secret")) {
            val traceo = FakeTraceo()
            val source = DataSources.proximitySource(key, traceo = traceo::forNetwork)
            assertTrue(source(nearby).getOrThrow().viaTraceo)
            assertEquals(listOf(Triple(TCAT, STATION, "2B")), traceo.asked)
        }
    }

    // --- Arrivées au terminus ------------------------------------------------------------------

    @Test
    fun terminusArrivalsAreOnlyRemovedInIleDeFrance() {
        assertTrue(DataSources.dropsTerminusArrivals(Networks.IDFM, viaTraceo = true))
        assertFalse(DataSources.dropsTerminusArrivals(Networks.IDFM, viaTraceo = false))
        assertFalse(DataSources.dropsTerminusArrivals(TCAT, viaTraceo = true))
    }

    /** À l'arrêt « Chartreux », un bus « vers Chartreux » peut être une boucle : le serveur a déjà fait le tri. */
    @Test
    fun aDestinationNamedLikeTheStopIsKeptOutsideIleDeFrance() {
        val stop = Stop("Chartreux", null, listOf(STATION), TCAT)
        val config = WidgetConfig(1, TransportMode.BUS, line2B, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS)
        val snapshot = PassageSnapshot(listOf(dep("Chartreux", 2), dep("Voves", 5)), dataAt = NOW, fresh = true, viaTraceo = true)

        val state = WidgetStates.from(config, listOf(Result.success(snapshot)), null, NOW, removeTerminusArrivals = true)
        assertEquals(listOf("Chartreux", "Voves"), state.departures.map { it.destination })

        val ranked = listOf(RankedLine(NearbyLine(line2B, "Chartreux", null, STATION, 50), NearbyReason.NEAREST, 50, false))
        val proximity = runBlocking {
            Proximity.collect(ranked, rows = 1, now = NOW, previous = null, removeTerminusArrivals = true) { batch ->
                batch.map { Result.success(snapshot) }
            }
        }
        assertEquals(listOf("Chartreux", "Voves"), proximity.entries.single().departures.map { it.destination })
    }

    // --- Cadence d'actualisation ---------------------------------------------------------------

    @Test
    fun theIntervalFollowsThePaceOfTheNetwork() {
        // Réseau relevé chaque minute : « chaque minute » s'applique tel quel.
        assertEquals(1, DataSources.refreshMinutes(1, sourceMinutes = 1))
        assertEquals(2, DataSources.refreshMinutes(1, sourceMinutes = 2))
        assertEquals(3, DataSources.refreshMinutes(2, sourceMinutes = 3))
        assertEquals(5, DataSources.refreshMinutes(5, sourceMinutes = 2))
        assertEquals(0, DataSources.refreshMinutes(0, sourceMinutes = 2))
        // Même règle que celle de l'Île-de-France sans clé.
        for (chosen in Prefs.REFRESH_CHOICES) {
            assertEquals(DataSources.refreshMinutes(chosen, hasKey = false), DataSources.refreshMinutes(chosen, sourceMinutes = 2))
            assertEquals(DataSources.refreshMinutes(chosen, hasKey = true), DataSources.refreshMinutes(chosen, sourceMinutes = 1))
        }
    }

    @Test
    fun aWidgetFollowsTheSnapshotsOfItsOwnNetwork() {
        val nextAt = NOW + MINUTE
        // Réseau relevé chaque minute, réglage « chaque minute » : le widget suit les relevés annoncés.
        assertEquals(nextAt + 5_000L, RefreshSchedule.dueAt(NOW, 1, true, nextAt, 5_000L, sourceMinutes = 1))
        // Réglage plus lent que le réseau : l'intervalle choisi est respecté.
        val byInterval = NOW + 2 * MINUTE - RefreshSchedule.EARLY_MS
        assertEquals(byInterval, RefreshSchedule.dueAt(NOW, 2, true, nextAt, 5_000L, sourceMinutes = 1))
        // Sans précision, la cadence est celle de l'Île-de-France : rien ne change pour elle.
        assertEquals(RefreshSchedule.dueAt(NOW, 2, true, nextAt, 5_000L), RefreshSchedule.dueAt(NOW, 2, true, nextAt, 5_000L, sourceMinutes = 2))
    }

    @Test
    fun eachNetworkRemembersItsOwnNextSnapshot() {
        // L'Île-de-France garde la préférence d'avant les réseaux.
        assertEquals("traceo_next_at", DataSources.nextAtPreference(Networks.IDFM))
        assertNotEquals(DataSources.nextAtPreference(Networks.IDFM), DataSources.nextAtPreference(TCAT))
        assertNotEquals(DataSources.nextAtPreference("bordeaux"), DataSources.nextAtPreference(TCAT))
    }

    // --- Fichiers du référentiel ---------------------------------------------------------------

    @Test
    fun cacheFilesOfANetworkNeverMixWithOthers() {
        val names = setOf(
            DataSources.linesCacheName(TransportMode.BUS, hasKey = false),
            DataSources.linesCacheName(TransportMode.BUS, hasKey = true),
            DataSources.networkLinesCacheName(TCAT),
            DataSources.networkLinesCacheName("bordeaux"),
        )
        assertEquals(4, names.size)

        // Des identifiants opaques que le nettoyage du nom confondrait gardent chacun leur fichier.
        val stops = listOf("ZI TAD", "ZI_TAD", "ZI:TAD").map { DataSources.networkStopsCacheName(TCAT, it) }
        assertEquals(3, stops.toSet().size)
        assertTrue(stops.all { it.matches(Regex("[A-Za-z0-9_.-]+")) })
        assertNotEquals(DataSources.networkStopsCacheName(TCAT, "1"), DataSources.networkStopsCacheName("bordeaux", "1"))
        assertNotEquals(DataSources.networkStopsCacheName(TCAT, "1"), DataSources.stopsCacheName("1", hasKey = false))
    }
}
