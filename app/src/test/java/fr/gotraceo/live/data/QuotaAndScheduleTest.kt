package fr.gotraceo.live.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Quota journalier de la clé PRIM, repli sur Tracéo et échéances des actualisations. */
class QuotaAndScheduleTest {

    private companion object {
        const val TODAY = "2026-11-07"
        const val TOMORROW = "2026-11-08"
        const val MINUTE = 60_000L
        const val T0 = 1_800_000_000_000L
        const val TRACEO_STOP = "stop_area:IDFM:71634"
        const val IDFM_STOP = "IDFM:463158"
    }

    // --- Quota ----------------------------------------------------------------------------------

    @Test
    fun countsItsOwnRequestsUntilPrimSaysWhatIsLeft() {
        var usage = PrimUsage()
        repeat(3) { usage = usage.recorded(TODAY, null, null) }
        assertEquals(3, usage.used)
        assertEquals(PrimUsage.DEFAULT_DAILY_QUOTA, usage.quota)

        // PRIM compte aussi les autres usages de la clé : sa valeur l'emporte.
        usage = usage.recorded(TODAY, 600, 1_000)
        assertEquals(400, usage.used)
        assertEquals(1_000, usage.quota)
    }

    @Test
    fun aNewDayStartsFromZeroButKeepsTheQuotaLearnedFromPrim() {
        val yesterday = PrimUsage().recorded(TODAY, 10, 2_000).exceeded(TODAY)
        assertTrue(yesterday.isExhausted(TODAY))

        val today = yesterday.on(TOMORROW)
        assertEquals(0, today.used)
        assertEquals(2_000, today.quota)
        assertFalse(today.isExhausted(TOMORROW))
    }

    @Test
    fun warnsOnceAt80PercentThenFallsBackWhenTheQuotaIsUsed() {
        var usage = PrimUsage().recorded(TODAY, 201, 1_000)
        assertFalse(usage.needsWarning(TODAY))
        usage = usage.recorded(TODAY, 200, 1_000)
        assertTrue(usage.needsWarning(TODAY))
        usage = usage.copy(warnedDay = TODAY)
        assertFalse("Une seule alerte par jour", usage.needsWarning(TODAY))

        assertFalse(usage.isExhausted(TODAY))
        assertTrue(usage.recorded(TODAY, 0, 1_000).isExhausted(TODAY))
        assertTrue("Un refus de PRIM suffit", PrimUsage().exceeded(TODAY).isExhausted(TODAY))
    }

    @Test
    fun usageSurvivesStorage() {
        val usage = PrimUsage().recorded(TODAY, 120, 1_000).exceeded(TODAY).copy(warnedDay = TODAY)
        assertEquals(usage, PrimUsage.fromJson(usage.toJson()))
        assertEquals(PrimUsage(), PrimUsage.fromJson("pas du json"))
    }

    // --- Repli sur Tracéo -----------------------------------------------------------------------

    private val traceoSnapshot = PassageSnapshot(emptyList(), dataAt = T0, fresh = true, nextAt = T0 + 2 * MINUTE, viaTraceo = true)

    private class FakeTraceo(private val answer: PassageSnapshot) : PassageSource {
        val asked = mutableListOf<String>()
        override fun passages(stopId: String, lineId: String): PassageSnapshot {
            asked += stopId
            return answer
        }
    }

    @Test
    fun withoutQuotaWidgetsKnownToTraceoGoThroughItsCache() {
        val traceo = FakeTraceo(traceoSnapshot)
        val source = DataSources.passageSource("secret", primPaused = true, traceo = traceo)
        assertEquals(traceoSnapshot, source.passages(TRACEO_STOP, "C01371"))
        assertEquals(listOf(TRACEO_STOP), traceo.asked)
    }

    @Test
    fun withoutQuotaWidgetsCreatedWithTheKeyWaitForTomorrow() {
        val traceo = FakeTraceo(traceoSnapshot)
        val source = DataSources.passageSource("secret", primPaused = true, traceo = traceo)
        try {
            source.passages(IDFM_STOP, "C01371")
            fail("Quota attendu")
        } catch (e: PrimException) {
            assertEquals(PrimException.Kind.QUOTA, e.kind)
            assertEquals(DataSources.QUOTA_WAIT_MESSAGE, e.message)
        }
        assertTrue("Tracéo ne connaît pas cet arrêt : aucun appel", traceo.asked.isEmpty())
    }

    @Test
    fun withoutKeyTraceoIsUsedDirectly() {
        val traceo = FakeTraceo(traceoSnapshot)
        assertEquals(traceoSnapshot, DataSources.passageSource("", traceo = traceo).passages(TRACEO_STOP, "C01371"))
    }

    @Test
    fun traceoAnswersDropTerminusArrivalsEvenWithAKey() {
        val config = WidgetConfig(
            widgetId = 1,
            mode = TransportMode.METRO,
            line = Line("C01371", "1", "1", "metro"),
            stop = Stop("Château de Vincennes", null, listOf(TRACEO_STOP)),
            directions = mapOf(TRACEO_STOP to emptySet()),
            directionLabel = "",
        )
        val arrival = Departure(
            lineRef = "line:IDFM:C01371", destination = "Château de Vincennes", directionName = null,
            directionRef = null, expectedArrival = T0 + MINUTE, expectedDeparture = null, aimedArrival = T0 + MINUTE,
            aimedDeparture = null, atStop = false, platform = null, status = null, journeyNote = null, stopId = TRACEO_STOP,
        )
        val snapshot = traceoSnapshot.copy(departures = listOf(arrival))
        val state = WidgetStates.from(config, listOf(Result.success(snapshot)), null, T0, removeTerminusArrivals = false)
        assertTrue(state.departures.isEmpty())
    }

    // --- Échéances ------------------------------------------------------------------------------

    @Test
    fun aWidgetThatNeverAskedIsDueAtOnceAndAManualOneNever() {
        assertEquals(0L, RefreshSchedule.dueAt(null, 2, true, null, 0L))
        assertNull(RefreshSchedule.dueAt(T0, 0, true, T0 + MINUTE, 5_000L))
    }

    @Test
    fun withAKeyTheIntervalAloneCounts() {
        val due = RefreshSchedule.dueAt(T0, 1, followsTraceo = false, traceoNextAt = T0 + 10 * MINUTE, offsetMs = 5_000L)
        assertEquals(T0 + MINUTE - RefreshSchedule.EARLY_MS, due)
    }

    @Test
    fun withTraceoTheWidgetWaitsForTheNextSnapshotPlusItsOwnOffset() {
        val nextAt = T0 + 90_000L
        assertEquals(nextAt + 12_000L, RefreshSchedule.dueAt(T0, 2, true, nextAt, 12_000L))
        // Un relevé annoncé très tôt n'autorise pas une rafale : 30 s au moins entre deux demandes.
        assertEquals(T0 + RefreshSchedule.TRACEO_MIN_GAP_MS, RefreshSchedule.dueAt(T0, 2, true, T0 + 1_000L, 2_000L))
    }

    @Test
    fun aSlowerIntervalIsStillRespectedWithTraceo() {
        val due = RefreshSchedule.dueAt(T0, 10, true, T0 + 2 * MINUTE, 5_000L)
        assertEquals(T0 + 10 * MINUTE - RefreshSchedule.EARLY_MS, due)
    }

    @Test
    fun aSnapshotAlreadyFetchedOrUnknownFallsBackToTheInterval() {
        val byInterval = T0 + 2 * MINUTE - RefreshSchedule.EARLY_MS
        assertEquals(byInterval, RefreshSchedule.dueAt(T0, 2, true, T0 - MINUTE, 5_000L))
        assertEquals(byInterval, RefreshSchedule.dueAt(T0, 2, true, null, 5_000L))
        assertEquals(byInterval, RefreshSchedule.dueAt(T0, 2, true, 0L, 5_000L))
    }

    // --- Tick et broadcasts ---------------------------------------------------------------------

    @Test
    fun aBroadcastIsHandedBackBeforeAndroidKillsTheApp() {
        // Android accorde 10 s au récepteur d'un broadcast prioritaire, 60 s aux autres, puis tue
        // l'application sans prévenir. Un toucher de widget arrive en broadcast prioritaire.
        assertTrue(RefreshSchedule.broadcastBudgetMs(userAction = true, foreground = false) < 10_000L)
        assertTrue(RefreshSchedule.broadcastBudgetMs(userAction = false, foreground = true) < 10_000L)
        assertTrue(RefreshSchedule.broadcastBudgetMs(userAction = true, foreground = true) < 10_000L)
        assertTrue(RefreshSchedule.broadcastBudgetMs(userAction = false, foreground = false) < 60_000L)
    }

    @Test
    fun aTickKeepsEnoughTimeForASlowRequest() {
        // Connexion (8 s) puis lecture (12 s) : une requête lente doit pouvoir finir dans le broadcast du tick.
        assertTrue(RefreshSchedule.broadcastBudgetMs(userAction = false, foreground = false) >= 20_000L)
    }

    @Test
    fun theSafetyNetStaysBehindTheTick() {
        // Chaque tick repousse le filet d'une période. Elle dépasse nettement la minute qui sépare deux
        // ticks : le filet ne part donc que si les ticks se sont arrêtés.
        assertTrue(RefreshSchedule.WATCHDOG_INTERVAL_MS >= 2 * MINUTE)
        val tick = RefreshSchedule.nextTickAt(T0, 17_000L)
        assertTrue(tick - T0 <= MINUTE)
        assertTrue(T0 + RefreshSchedule.WATCHDOG_INTERVAL_MS > tick + MINUTE)
    }
}
