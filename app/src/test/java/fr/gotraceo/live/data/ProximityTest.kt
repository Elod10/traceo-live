package fr.gotraceo.live.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProximityTest {

    private val now = 1_700_000_000_000L

    private fun line(code: String) = Line("C$code", code, code, "bus")
    private fun near(code: String, zone: String = "1") = NearbyLine(line(code), "Arrêt $code", "Paris", zone, 50)
    private fun ranked(code: String, reason: NearbyReason = NearbyReason.NEAREST, favorite: Boolean = false, zone: String = "1") =
        RankedLine(near(code, zone), reason, 50, favorite)

    private fun dep(inMinutes: Int, destination: String = "Gare", lineRef: String? = null, status: String? = null) = Departure(
        lineRef = lineRef,
        destination = destination,
        directionName = null,
        directionRef = null,
        expectedArrival = now + inMinutes * 60_000L,
        expectedDeparture = null,
        aimedArrival = null,
        aimedDeparture = null,
        atStop = false,
        platform = null,
        status = status,
        journeyNote = null,
    )

    private fun entry(code: String, vararg departures: Departure, favorite: Boolean = false, reason: NearbyReason = NearbyReason.NEAREST) =
        ProximityEntry(near(code), reason, favorite, departures.toList())

    private fun collect(
        lines: List<RankedLine>,
        rows: Int = 2,
        previous: ProximityState? = null,
        removeTerminus: Boolean = false,
        answer: (NearbyLine) -> Result<PassageSnapshot>,
    ): Pair<ProximityState, List<List<String>>> {
        val batches = ArrayList<List<String>>()
        val state = runBlocking {
            Proximity.collect(lines, rows, now, previous, removeTerminus) { batch ->
                batches.add(batch.map { it.line.shortName })
                batch.map(answer)
            }
        }
        return state to batches
    }

    private fun soon(@Suppress("UNUSED_PARAMETER") nearby: NearbyLine) = Result.success(PassageSnapshot(listOf(dep(3))))
    private fun none(@Suppress("UNUSED_PARAMETER") nearby: NearbyLine) = Result.success(PassageSnapshot(emptyList()))

    // --- Lignes affichées --------------------------------------------------------------------------

    @Test
    fun aLineWithAPassageInTheHourIsShown() {
        assertEquals(1, Proximity.visible(listOf(entry("1", dep(59))), now, 3).size)
    }

    @Test
    fun aLineWithoutAPassageInTheHourDisappears() {
        assertTrue(Proximity.visible(listOf(entry("1", dep(61)), entry("2")), now, 3).isEmpty())
    }

    @Test
    fun aCancelledPassageDoesNotCount() {
        assertTrue(Proximity.visible(listOf(entry("1", dep(5, status = "cancelled"))), now, 3).isEmpty())
    }

    @Test
    fun aPassageAlreadyGoneDoesNotCount() {
        assertTrue(Proximity.visible(listOf(entry("1", dep(-5))), now, 3).isEmpty())
    }

    @Test
    fun aFavoriteOrAChoiceStaysShownWithoutPassage() {
        val shown = Proximity.visible(
            listOf(entry("1", favorite = true), entry("2", reason = NearbyReason.PICKED_HERE), entry("3")),
            now,
            3,
        )
        assertEquals(listOf("1", "2"), shown.map { it.nearby.line.shortName })
    }

    @Test
    fun theOrderOfTheRankingIsKeptUpToTheRows() {
        val shown = Proximity.visible(listOf(entry("1", dep(30)), entry("2", dep(2)), entry("3", dep(1))), now, 2)
        assertEquals(listOf("1", "2"), shown.map { it.nearby.line.shortName })
    }

    // --- Requêtes ----------------------------------------------------------------------------------

    @Test
    fun onlyTheLinesToShowAreAsked() {
        val (state, batches) = collect(listOf(ranked("1"), ranked("2"), ranked("3"), ranked("4")), answer = ::soon)
        assertEquals(listOf(listOf("1", "2")), batches)
        assertEquals(listOf("1", "2"), state.entries.map { it.nearby.line.shortName })
    }

    @Test
    fun aLineWithoutPassageLeavesItsPlaceToTheNext() {
        val (state, batches) = collect(listOf(ranked("1"), ranked("2"), ranked("3"), ranked("4"))) {
            if (it.line.shortName == "1") none(it) else soon(it)
        }
        assertEquals(listOf(listOf("1", "2"), listOf("3")), batches)
        assertEquals(listOf("2", "3"), Proximity.visible(state.entries, now, 2).map { it.nearby.line.shortName })
    }

    @Test
    fun theNumberOfLinesAskedIsLimited() {
        val lines = (1..20).map { ranked("$it") }
        val (_, batches) = collect(lines, answer = ::none)
        assertEquals(Proximity.MAX_LINES, batches.flatten().size)
    }

    @Test
    fun terminusArrivalsAreRemovedWhenAsked() {
        val answer = { n: NearbyLine -> Result.success(PassageSnapshot(listOf(dep(2, n.stopName), dep(3, "Autre")))) }
        val kept = collect(listOf(ranked("1")), rows = 1, removeTerminus = true, answer = answer).first
        assertEquals(listOf("Autre"), kept.entries.single().departures.map { it.destination })
        val all = collect(listOf(ranked("1")), rows = 1, removeTerminus = false, answer = answer).first
        assertEquals(2, all.entries.single().departures.size)
    }

    @Test
    fun onlyAFewPassagesAreStoredPerLine() {
        val (state, _) = collect(listOf(ranked("1")), rows = 1) {
            Result.success(PassageSnapshot((1..20).map { minutes -> dep(minutes) }))
        }
        assertEquals(Proximity.MAX_STORED_DEPARTURES, state.entries.single().departures.size)
    }

    @Test
    fun theOldestSnapshotIsAnnounced() {
        val (state, _) = collect(listOf(ranked("1"), ranked("2"))) {
            val old = it.line.shortName == "1"
            Result.success(PassageSnapshot(listOf(dep(3)), dataAt = if (old) 100L else 200L, fresh = !old))
        }
        assertEquals(100L, state.dataAt)
        assertEquals(false, state.fresh)
        assertTrue(state.isSnapshot)
    }

    @Test
    fun aDirectAnswerIsNotASnapshot() {
        val (state, _) = collect(listOf(ranked("1")), rows = 1, answer = ::soon)
        assertNull(state.dataAt)
        assertFalse(state.isSnapshot)
    }

    // --- Erreurs -----------------------------------------------------------------------------------

    private val previous = ProximityState(10L, 10L, listOf(entry("9", dep(3))), dataAt = 5L, fresh = true)

    @Test
    fun whenEverythingFailsThePreviousLinesStay() {
        val (state, _) = collect(listOf(ranked("1")), rows = 1, previous = previous) {
            Result.failure(TraceoException(TraceoException.Kind.UNAVAILABLE, "Indisponible"))
        }
        assertEquals("Indisponible", state.error)
        assertEquals(listOf("9"), state.entries.map { it.nearby.line.shortName })
        assertEquals(10L, state.fetchedAt)
        assertEquals(now, state.attemptedAt)
        assertEquals(true, state.fresh)
    }

    @Test
    fun withoutNetworkTheOtherLinesAreNotAsked() {
        val (_, batches) = collect((1..6).map { ranked("$it") }, rows = 2) { Result.failure(IOException("Réseau")) }
        assertEquals(1, batches.size)
    }

    @Test
    fun aLineUnknownOfTheSourceLeavesItsPlace() {
        val (state, batches) = collect(listOf(ranked("1"), ranked("2")), rows = 1) {
            if (it.line.shortName == "1") {
                Result.failure(TraceoException(TraceoException.Kind.NOT_FOUND, "Inconnue"))
            } else {
                soon(it)
            }
        }
        assertEquals(2, batches.size)
        assertNull(state.error)
        assertEquals(listOf("2"), Proximity.visible(state.entries, now, 1).map { it.nearby.line.shortName })
    }

    @Test
    fun aPartialFailureIsReportedWhenNothingCanBeShown() {
        val (state, _) = collect(listOf(ranked("1"), ranked("2")), rows = 2) {
            if (it.line.shortName == "1") {
                Result.failure(TraceoException(TraceoException.Kind.SERVER, "Panne"))
            } else {
                none(it)
            }
        }
        assertEquals("Panne", state.error)
    }

    // --- Avec une clé ------------------------------------------------------------------------------

    @Test
    fun withAKeyEachZoneIsAskedOnce() {
        val asked = ArrayList<String>()
        val source = PrimZonePassages { stop ->
            asked.add(stop)
            listOf(dep(2, lineRef = "STIF:Line::C1:"), dep(3, lineRef = "STIF:Line::C2:"))
        }
        val first = source.passages(near("1", "71673")).getOrThrow()
        val second = source.passages(near("2", "71673")).getOrThrow()
        source.passages(near("3", "99")).getOrThrow()
        assertEquals(listOf("stop_area:IDFM:71673", "stop_area:IDFM:99"), asked)
        assertEquals(listOf(2), first.departures.map { ((it.displayTime!! - now) / 60_000L).toInt() })
        assertEquals(listOf(3), second.departures.map { ((it.displayTime!! - now) / 60_000L).toInt() })
    }

    @Test
    fun withAKeyAZoneFailureIsSharedByItsLines() {
        var calls = 0
        val source = PrimZonePassages {
            calls++
            throw PrimException(PrimException.Kind.QUOTA, "Quota")
        }
        assertTrue(source.passages(near("1")).isFailure)
        assertTrue(source.passages(near("2")).isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun withAKeyTwoZonesAreAskedAtTheSameTime() {
        // Chaque zone attend que l'autre soit demandée aussi : possible seulement si les deux requêtes
        // sont en cours ensemble, pas si l'une attend la fin de l'autre.
        val bothAsked = java.util.concurrent.CyclicBarrier(2)
        val source = PrimZonePassages { _ ->
            bothAsked.await(5, java.util.concurrent.TimeUnit.SECONDS)
            listOf(dep(2, lineRef = "STIF:Line::C1:"))
        }
        val results = java.util.Collections.synchronizedList(ArrayList<Result<PassageSnapshot>>())
        val threads = listOf("71673", "99").map { zone -> kotlin.concurrent.thread { results += source.passages(near("1", zone)) } }
        threads.forEach { it.join(10_000L) }
        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
    }

    // --- Échecs réseau et enregistrement -------------------------------------------------------------

    @Test
    fun networkFailuresAreRecognized() {
        assertTrue(DataSources.isNetworkFailure(TraceoException(TraceoException.Kind.NETWORK, "x")))
        assertTrue(DataSources.isNetworkFailure(PrimException(PrimException.Kind.NETWORK, "x")))
        assertFalse(DataSources.isNetworkFailure(TraceoException(TraceoException.Kind.NOT_FOUND, "x")))
        assertFalse(DataSources.isNetworkFailure(null))
    }

    @Test
    fun stateSurvivesJson() {
        val state = ProximityState(
            fetchedAt = 1L,
            attemptedAt = 2L,
            entries = listOf(entry("1", dep(3, "Gare"), favorite = true, reason = NearbyReason.FAVORITE)),
            error = "Panne",
            dataAt = 3L,
            fresh = false,
        )
        assertEquals(state, ProximityState.fromJson(JSONObject(state.toJson().toString())))
        val direct = ProximityState(1L, 2L, emptyList())
        assertEquals(direct, ProximityState.fromJson(JSONObject(direct.toJson().toString())))
    }
}
