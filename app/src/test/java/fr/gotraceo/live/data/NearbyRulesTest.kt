package fr.gotraceo.live.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyRulesTest {

    private val modes = StopIndexStore.ALL_MODES.toSet()

    private fun line(id: String, code: String, mode: String, network: String? = null) = Line(id, code, code, mode, network = network)
    private fun near(line: Line, stop: String, distance: Int, zone: String = "1") = NearbyLine(line, stop, "Paris", zone, distance)

    private val m11 = line("C01381", "11", "metro")
    private val m1 = line("C01371", "1", "metro")
    private val m7 = line("C01377", "7", "metro")
    private val rerB = line("C01743", "B", "rail", "RER")
    private val rerC = line("C01727", "C", "rail", "RER")
    private val t3 = line("C01389", "T3a", "tram")
    private val b69 = line("C01069", "69", "bus")
    private val b38 = line("C01038", "38", "bus")
    private val b96 = line("C01127", "96", "bus")
    private val n11 = line("C01611", "N11", "bus", "Noctilien")

    private val paris = listOf(
        near(m11, "Hôtel de Ville", 33),
        near(b69, "Hôtel de Ville", 33),
        near(b38, "Coutellerie", 63),
        near(b96, "Coutellerie", 63),
        near(n11, "Hôtel de Ville", 77),
        near(m1, "Hôtel de Ville", 149),
        near(m7, "Châtelet", 211),
        near(rerB, "Châtelet - Les Halles", 556),
        near(rerC, "Saint-Michel Notre-Dame", 666),
    )

    private fun rank(
        candidates: List<NearbyLine> = paris,
        favorites: Set<String> = emptySet(),
        picked: String? = null,
        night: Boolean = false,
        allowed: Set<String> = modes,
    ) = NearbyRules.rank(candidates, favorites, picked, night, allowed)

    private fun NearbyRanking.codes() = list.map { it.nearby.line.shortName }

    @Test
    fun railLinesCountAsCloserThanTheBusNextToThem() {
        assertEquals(listOf("11", "1", "69", "7", "38", "96", "B"), rank().codes())
        assertEquals(NearbyReason.NEAREST, rank().list.first().reason)
    }

    @Test
    fun aLineWithoutRealtimeIsNeverRanked() {
        // Brest : le téléphérique n'est jamais dans le flux ; son arrêt, tout près, ne compte pas.
        val cable = Line("C", "Téléphérique", "C", "cableway", networkId = "brest", realtime = false)
        val tram = Line("A", "Tram A", "A", "tram", networkId = "brest")
        val ranking = rank(
            listOf(near(cable, "Capucins", 20), near(tram, "Château", 600)),
            favorites = setOf(cable.key),
            picked = cable.key,
            allowed = setOf("cableway", "tram"),
        )
        assertEquals(listOf("A"), ranking.codes())
        assertEquals(600, ranking.nearestM)
        assertFalse(ranking.noStop)
    }

    @Test
    fun aTramCountsHalfOfTheRailBonus() {
        val ranking = rank(listOf(near(b69, "A", 50), near(t3, "B", 100)))
        assertEquals(listOf("T3a", "69"), ranking.codes())
        assertEquals(25, ranking.list.first().effectiveM)
    }

    @Test
    fun linesFartherThanTheToleranceAreLeftOut() {
        assertFalse(rank().codes().contains("C"))
        // 556 - 150 = 406 reste à moins de 400 m de plus que le plus proche, à 33 m.
        assertTrue(rank().codes().contains("B"))
    }

    @Test
    fun aFarawayStopIsEnoughWhenItIsTheFirstOne() {
        val line = line("C00979", "4410", "bus")
        val other = line("C00980", "4459", "bus")
        val ranking = rank(listOf(near(line, "Beaumont", 1135), near(other, "Moulineux", 1955)))
        assertEquals(listOf("4410"), ranking.codes())
    }

    @Test
    fun nothingWithinTwoKilometersMeansNoStop() {
        val ranking = rank(listOf(near(b69, "Loin", 2100)))
        assertTrue(ranking.noStop)
        assertTrue(ranking.list.isEmpty())
    }

    @Test
    fun noctiliensOnlyCountAtNight() {
        assertFalse(rank().codes().contains("N11"))
        assertTrue(rank(night = true).codes().contains("N11"))
        assertTrue(NearbyRules.isNightLine(line("X", "N122", "bus")))
        assertFalse(NearbyRules.isNightLine(line("X", "96", "bus")))
    }

    @Test
    fun nightServiceRunsFromHalfPastMidnight() {
        assertFalse(NearbyRules.isNightService(0, 29))
        assertTrue(NearbyRules.isNightService(0, 30))
        assertTrue(NearbyRules.isNightService(5, 29))
        assertFalse(NearbyRules.isNightService(5, 30))
        assertFalse(NearbyRules.isNightService(12, 0))
    }

    @Test
    fun aFavoriteWithinReachComesFirst() {
        val ranking = rank(favorites = setOf(rerC.id))
        assertEquals("C", ranking.codes().first())
        assertEquals(NearbyReason.FAVORITE, ranking.list.first().reason)
    }

    @Test
    fun severalFavoritesAreSortedByDistance() {
        assertEquals(listOf("96", "B"), rank(favorites = setOf(rerB.id, b96.id)).codes().take(2))
    }

    @Test
    fun aFavoriteBeyondItsReachIsNotPromoted() {
        val far = near(rerC, "Loin", 900)
        val ranking = rank(paris.filter { it.line != rerC } + far, favorites = setOf(rerC.id))
        assertFalse(ranking.codes().contains("C"))
        assertEquals("11", ranking.codes().first())
    }

    @Test
    fun aFavoriteNoctilienStaysAvailableByDay() {
        assertEquals("N11", rank(favorites = setOf(n11.id)).codes().first())
    }

    @Test
    fun theChoiceMadeHereComesBeforeEverything() {
        val ranking = rank(favorites = setOf(rerB.id), picked = b96.id)
        assertEquals("96", ranking.codes().first())
        assertEquals(NearbyReason.PICKED_HERE, ranking.list.first().reason)
    }

    @Test
    fun modesTheSourceCannotServeAreLeftOut() {
        val cable = line("C00001", "Câble", "cableway")
        val candidates = paris + near(cable, "Cable", 10)
        assertTrue(rank(candidates).codes().contains("Câble"))
        assertFalse(rank(candidates, allowed = modes - "cableway").codes().contains("Câble"))
    }

    @Test
    fun aLineAppearsOnceAtItsNearestStop() {
        val area = StopIndexArea(
            mapOf(b96.id to b96, m1.id to m1),
            listOf(
                IndexedStop("Loin", "Paris", 48.8600, 2.3510, "2", listOf(b96.id)),
                IndexedStop("Près", "Paris", 48.8576, 2.3510, "1", listOf(b96.id, m1.id)),
            ),
        )
        val lines = NearbyRules.linesAround(area, GeoPoint(48.8575, 2.3510))
        assertEquals(2, lines.size)
        val b = lines.first { it.line == b96 }
        assertEquals("Près", b.stopName)
        assertEquals("stop_area:IDFM:1", b.stopId)
        assertEquals(listOf("stop_area:IDFM:1"), b.toStop().stopIds)
        assertEquals(11, b.distanceM)
    }

    // --- Lignes avec un passage -----------------------------------------------------------------

    @Test
    fun pickActiveSkipsLinesWithoutAPassage() {
        val asked = ArrayList<String>()
        val picked = NearbyRules.pickActive(
            rank().list,
            probe = { asked.add(it.line.shortName); it.line.shortName },
            isActive = { it == "1" },
        )
        assertEquals("1", picked?.first?.nearby?.line?.shortName)
        assertEquals(listOf("11", "1"), asked)
    }

    @Test
    fun pickActiveStopsAfterThreeLinesAndKeepsTheFirst() {
        val asked = ArrayList<String>()
        val picked = NearbyRules.pickActive(rank().list, { asked.add(it.line.shortName); false }, { it })
        assertEquals(listOf("11", "1", "69"), asked)
        assertEquals("11", picked?.first?.nearby?.line?.shortName)
    }

    @Test
    fun pickActiveKeepsTheChoiceMadeHereEvenWithoutAPassage() {
        val asked = ArrayList<String>()
        val picked = NearbyRules.pickActive(rank(picked = b96.id).list, { asked.add(it.line.shortName); false }, { it })
        assertEquals("96", picked?.first?.nearby?.line?.shortName)
        assertEquals(listOf("96"), asked)
    }

    @Test
    fun pickActiveWithNothingNearby() {
        assertNull(NearbyRules.pickActive(emptyList(), { true }, { it }))
    }

    // --- Règle des 100 m -----------------------------------------------------------------------

    private val anchor = GeoPoint(48.8575, 2.3510)

    /** Point situé à environ [meters] au nord de l'ancre. */
    private fun north(meters: Int) = GeoPoint(anchor.lat + meters / 111_195.0, anchor.lon)

    @Test
    fun withoutPositionOnlyThePassagesAreRefreshed() {
        assertEquals(AnchorDecision.NO_FIX, AnchorPolicy.decide(anchor, null))
    }

    @Test
    fun aVeryImprecisePositionDecidesNothing() {
        assertEquals(AnchorDecision.IMPRECISE, AnchorPolicy.decide(anchor, PositionFix(north(800), 500f)))
        assertEquals(AnchorDecision.IMPRECISE, AnchorPolicy.decide(null, PositionFix(north(800), 500f)))
    }

    @Test
    fun theFirstPositionAlwaysChoosesAStop() {
        assertEquals(AnchorDecision.MOVED, AnchorPolicy.decide(null, PositionFix(anchor, 20f)))
    }

    @Test
    fun underAHundredMetersTheStopStays() {
        assertEquals(AnchorDecision.SAME_PLACE, AnchorPolicy.decide(anchor, PositionFix(north(90), 15f)))
        assertEquals(AnchorDecision.MOVED, AnchorPolicy.decide(anchor, PositionFix(north(110), 15f)))
    }

    @Test
    fun aMoveInsideTheMarginOfErrorDoesNotCount() {
        assertEquals(AnchorDecision.SAME_PLACE, AnchorPolicy.decide(anchor, PositionFix(north(140), 150f)))
        assertEquals(AnchorDecision.MOVED, AnchorPolicy.decide(anchor, PositionFix(north(160), 150f)))
    }

    @Test
    fun aWalkInSmallStepsEndsUpChangingTheStop() {
        // L'ancre ne bouge pas tant que l'arrêt est gardé : trois pas de 60 m finissent par compter.
        assertEquals(AnchorDecision.SAME_PLACE, AnchorPolicy.decide(anchor, PositionFix(north(60), 10f)))
        assertEquals(AnchorDecision.MOVED, AnchorPolicy.decide(anchor, PositionFix(north(180), 10f)))
    }

    // --- Choix par lieu et état ------------------------------------------------------------------

    @Test
    fun aChoiceIsFoundAgainNearTheSamePlace() {
        val choices = PlaceChoices().remember(anchor, b96.id, 1L)
        assertEquals(b96.id, choices.lineAt(north(150)))
        assertNull(choices.lineAt(north(250)))
    }

    @Test
    fun aNewChoiceReplacesTheOneMadeHere() {
        val choices = PlaceChoices().remember(anchor, b96.id, 1L).remember(north(100), m1.id, 2L)
        assertEquals(m1.id, choices.lineAt(anchor))
    }

    @Test
    fun theOldestChoicesAreForgotten() {
        var choices = PlaceChoices()
        for (i in 0..PlaceChoices.MAX_ENTRIES) {
            choices = choices.remember(GeoPoint(48.0 + i * 0.01, 2.3), "L$i", i.toLong())
        }
        assertNull(choices.lineAt(GeoPoint(48.0, 2.3)))
        assertEquals("L1", choices.lineAt(GeoPoint(48.01, 2.3)))
    }

    @Test
    fun choicesSurviveJson() {
        val choices = PlaceChoices().remember(anchor, b96.id, 5L)
        assertEquals(b96.id, PlaceChoices.fromJson(choices.toJson()).lineAt(anchor))
        assertNull(PlaceChoices.fromJson("illisible").lineAt(anchor))
        assertNull(PlaceChoices.fromJson(null).lineAt(anchor))
    }

    @Test
    fun onlyATouchInsideTheWindowOpensTheChoice() {
        val state = AutoState(choiceUntil = 1_000L)
        assertTrue(state.isChoiceOpen(999L))
        assertFalse(state.isChoiceOpen(1_000L))
        assertFalse(AutoState().isChoiceOpen(0L))
    }

    @Test
    fun aDoubleTapTakesLessThanTwoSeconds() {
        assertTrue(AutoState.DOUBLE_TAP_MS < 2_000L)
    }

    @Test
    fun theHintDoesNotKeepTheChoiceOpen() {
        val state = AutoState(hintUntil = 4_000L, others = 3)
        assertEquals(AutoHint("+3 lignes", "+3"), state.hint(3_999L))
        assertFalse(state.isChoiceOpen(0L))
    }

    @Test
    fun theHintNamesTheOtherLinesThenDisappears() {
        assertEquals(AutoHint("+1 ligne", "+1"), AutoState(hintUntil = 1_000L, others = 1).hint(0L))
        assertNull(AutoState(hintUntil = 1_000L, others = 3).hint(1_000L))
        assertNull(AutoState(hintUntil = 1_000L, others = 0).hint(0L))
    }

    @Test
    fun aProblemIsShownBeforeTheOtherLines() {
        val state = AutoState(hintUntil = 1_000L, others = 3, message = AutoMessage.NO_POSITION)
        assertEquals("GPS indisponible", state.hint(0L)?.label)
    }

    @Test
    fun stateSurvivesJson() {
        val state = AutoState(anchor, north(50), 33, 99L, 88L, 4, AutoMessage.IMPRECISE)
        assertEquals(state, AutoState.fromJson(JSONObject(state.toJson().toString())))
        assertEquals(AutoState(), AutoState.fromJson(JSONObject(AutoState().toJson().toString())))
    }

    @Test
    fun distancesAreReadable() {
        assertEquals("33 m", formatDistance(33))
        assertEquals("999 m", formatDistance(999))
        assertEquals("1,0 km", formatDistance(1000))
        assertEquals("1,1 km", formatDistance(1135))
        assertEquals("2,0 km", formatDistance(1990))
    }

    @Test
    fun anAutomaticConfigSurvivesJsonAndOlderOnesAreNotAutomatic() {
        val stop = near(m1, "Nation", 16).toStop()
        val config = WidgetConfig(1, TransportMode.METRO, m1, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS, auto = true)
        assertTrue(WidgetConfig.fromJson(JSONObject(config.toJson().toString())).auto)
        val older = JSONObject(config.toJson().toString()).apply { remove("auto") }
        assertFalse(WidgetConfig.fromJson(older).auto)
    }
}
