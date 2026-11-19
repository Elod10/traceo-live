package fr.gotraceo.live.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageSourceTest {

    private companion object {
        const val STOP = "stop_area:IDFM:71634"
        const val NOW = 1_800_000_000_000L
    }

    private fun dep(destination: String, inMinutes: Int, stopId: String = STOP) = Departure(
        lineRef = "line:IDFM:C01371",
        destination = destination,
        directionName = null,
        directionRef = "Aller",
        expectedArrival = NOW + inMinutes * 60_000L,
        expectedDeparture = null,
        aimedArrival = NOW + inMinutes * 60_000L,
        aimedDeparture = null,
        atStop = false,
        platform = null,
        status = "onTime",
        journeyNote = null,
        stopId = stopId,
    )

    private fun config(stopName: String = "Bastille", directions: Set<String> = emptySet()) = WidgetConfig(
        widgetId = 1,
        mode = TransportMode.METRO,
        line = Line("C01371", "1", "1", "metro"),
        stop = Stop(stopName, null, listOf(STOP)),
        directions = mapOf(STOP to directions),
        directionLabel = "",
    )

    // --- Les quatre cas d'un widget -----------------------------------------------------------

    @Test
    fun stopAreaWithoutKeyGoesThroughTraceo() {
        assertEquals(PassageRoute.TRACEO, DataSources.route(listOf("stop_area:IDFM:71348"), hasKey = false))
    }

    @Test
    fun stopAreaWithKeyGoesThroughPrim() {
        assertEquals(PassageRoute.PRIM, DataSources.route(listOf("stop_area:IDFM:71348"), hasKey = true))
    }

    @Test
    fun referentialStopWithKeyGoesThroughPrim() {
        assertEquals(PassageRoute.PRIM, DataSources.route(listOf("IDFM:463158"), hasKey = true))
        assertEquals(PassageRoute.PRIM, DataSources.route(listOf("IDFM:monomodalStopPlace:58879"), hasKey = true))
    }

    @Test
    fun referentialStopWithoutKeyMustBeReconfigured() {
        assertEquals(PassageRoute.RECONFIGURE, DataSources.route(listOf("IDFM:463158"), hasKey = false))
        assertEquals(PassageRoute.RECONFIGURE, DataSources.route(listOf("IDFM:monomodalStopPlace:58879"), hasKey = false))
        assertEquals(PassageRoute.RECONFIGURE, DataSources.route(listOf("stop_area:IDFM:1", "IDFM:2"), hasKey = false))
        assertEquals(PassageRoute.RECONFIGURE, DataSources.route(emptyList(), hasKey = false))
    }

    // --- Choix de la source -------------------------------------------------------------------

    @Test
    fun sourceDependsOnTheKey() {
        assertTrue(DataSources.passageSource("") is TraceoClient)
        assertTrue(DataSources.passageSource("   ") is TraceoClient)
        // Avec une clé : PRIM, enveloppé pour se replier sur Tracéo quand le quota est atteint.
        assertFalse(DataSources.passageSource("secret") is TraceoClient)
    }

    @Test
    fun oneMinuteMeansTwoWithoutKey() {
        assertEquals(2, DataSources.refreshMinutes(1, hasKey = false))
        assertEquals(1, DataSources.refreshMinutes(1, hasKey = true))
        assertEquals(0, DataSources.refreshMinutes(0, hasKey = false))
        assertEquals(5, DataSources.refreshMinutes(5, hasKey = false))
    }

    @Test
    fun oneMinuteIsNotOfferedWithoutKey() {
        val all = Prefs.REFRESH_CHOICES
        assertEquals(listOf(0, 2, 5, 10), DataSources.refreshChoices(all, hasKey = false))
        assertEquals(listOf(0, 1, 2, 5, 10), DataSources.refreshChoices(all, hasKey = true))
    }

    @Test
    fun theRemovedLongIntervalsBecomeTenMinutes() {
        assertEquals(10, Prefs.offeredRefresh(15))
        assertEquals(10, Prefs.offeredRefresh(30))
        for (minutes in Prefs.REFRESH_CHOICES) assertEquals(minutes, Prefs.offeredRefresh(minutes))
    }

    @Test
    fun cableAndFunicularOnlyWithKey() {
        assertFalse(TransportMode.OTHER in DataSources.modes(hasKey = false))
        assertEquals(TransportMode.IDFM.size - 1, DataSources.modes(hasKey = false).size)
        assertEquals(TransportMode.IDFM, DataSources.modes(hasKey = true))
    }

    @Test
    fun traceoCacheFilesNeverMixWithOpenDataOnes() {
        assertEquals("lines_rail.json", DataSources.linesCacheName(TransportMode.RER, hasKey = true))
        assertEquals("stops_C01371.json", DataSources.stopsCacheName("C01371", hasKey = true))
        assertEquals("traceo_lines.json", DataSources.linesCacheName(TransportMode.RER, hasKey = false))
        assertEquals("traceo_lines.json", DataSources.linesCacheName(TransportMode.BUS, hasKey = false))
        assertEquals("traceo_stops_C01371.json", DataSources.stopsCacheName("C01371", hasKey = false))
    }

    // --- État du widget -----------------------------------------------------------------------

    private fun previous() = WidgetState(
        fetchedAt = NOW - 120_000L,
        attemptedAt = NOW - 120_000L,
        departures = listOf(dep("La Défense (Grande Arche)", 3)),
        error = null,
        dataAt = NOW - 130_000L,
        fresh = true,
    )

    private fun failure(kind: TraceoException.Kind, message: String) =
        listOf(Result.failure<PassageSnapshot>(TraceoException(kind, message)))

    @Test
    fun rateLimitAndUnavailabilityKeepTheLastData() {
        val kinds = listOf(TraceoException.Kind.RATE_LIMITED, TraceoException.Kind.UNAVAILABLE, TraceoException.Kind.NETWORK)
        for (kind in kinds) {
            val state = WidgetStates.from(config(), failure(kind, "Indisponible."), previous(), NOW, removeTerminusArrivals = true)
            assertEquals(previous().departures, state.departures)
            assertEquals(previous().fetchedAt, state.fetchedAt)
            assertEquals(previous().dataAt, state.dataAt)
            assertEquals(previous().fresh, state.fresh)
            assertEquals(NOW, state.attemptedAt)
            assertEquals("Indisponible.", state.error)
        }
    }

    @Test
    fun unknownStopDropsTheLastDataToShowTheMessage() {
        val state = WidgetStates.from(
            config(), failure(TraceoException.Kind.NOT_FOUND, "Reconfigurez."), previous(), NOW, removeTerminusArrivals = true,
        )
        assertTrue(state.departures.isEmpty())
        assertEquals("Reconfigurez.", state.error)
    }

    @Test
    fun snapshotCarriesItsDateAndFreshness() {
        val snapshot = PassageSnapshot(listOf(dep("Château de Vincennes", 2)), dataAt = NOW - 30_000L, fresh = false)
        val state = WidgetStates.from(config(), listOf(Result.success(snapshot)), null, NOW, removeTerminusArrivals = true)
        assertEquals(NOW, state.fetchedAt)
        assertEquals(NOW - 30_000L, state.dataAt)
        assertEquals(false, state.fresh)
        assertTrue(state.isSnapshot)
        assertNull(state.error)
        assertEquals(1, state.departures.size)
    }

    @Test
    fun liveSourceIsNotASnapshot() {
        val state = WidgetStates.from(
            config(), listOf(Result.success(PassageSnapshot(listOf(dep("Château de Vincennes", 2))))), null, NOW,
            removeTerminusArrivals = false,
        )
        assertNull(state.dataAt)
        assertNull(state.fresh)
        assertFalse(state.isSnapshot)
    }

    @Test
    fun terminusArrivalsAreRemovedOnlyOnTraceo() {
        val snapshot = PassageSnapshot(listOf(dep("Château de Vincennes", 2), dep("La Défense (Grande Arche)", 3)))
        val widget = config(stopName = "Château de Vincennes")

        val traceo = WidgetStates.from(widget, listOf(Result.success(snapshot)), null, NOW, removeTerminusArrivals = true)
        assertEquals(listOf("La Défense (Grande Arche)"), traceo.departures.map { it.destination })

        val prim = WidgetStates.from(widget, listOf(Result.success(snapshot)), null, NOW, removeTerminusArrivals = false)
        assertEquals(2, prim.departures.size)
    }

    @Test
    fun directionFilterStillApplies() {
        val snapshot = PassageSnapshot(listOf(dep("Château de Vincennes", 2), dep("La Défense (Grande Arche)", 3)))
        val state = WidgetStates.from(
            config(directions = setOf("La Défense (Grande Arche)")), listOf(Result.success(snapshot)), null, NOW,
            removeTerminusArrivals = true,
        )
        assertEquals(listOf("La Défense (Grande Arche)"), state.departures.map { it.destination })
    }

    // --- États déjà enregistrés ----------------------------------------------------------------

    @Test
    fun stateSavedBeforeTraceoIsStillReadable() {
        val state = WidgetState.fromJson(JSONObject("""{"fetchedAt": 5, "attemptedAt": 6, "departures": [], "error": "Panne"}"""))
        assertEquals(5L, state.fetchedAt)
        assertEquals(6L, state.attemptedAt)
        assertEquals("Panne", state.error)
        assertNull(state.dataAt)
        assertNull(state.fresh)
        assertFalse(state.isSnapshot)
    }

    @Test
    fun snapshotStateSurvivesARoundTrip() {
        assertEquals(previous(), WidgetState.fromJson(previous().toJson()))
        val old = previous().copy(fresh = false)
        assertEquals(old, WidgetState.fromJson(old.toJson()))
        val live = previous().copy(dataAt = null, fresh = null)
        assertEquals(live, WidgetState.fromJson(live.toJson()))
    }
}
