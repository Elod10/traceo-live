package fr.gotraceo.live.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsTest {

    private val now = 1_700_000_000_000L

    private fun dep(stopId: String, destination: String, inMinutes: Int, direction: String? = null) = Departure(
        lineRef = "STIF:Line::C01374:",
        destination = destination,
        directionName = direction,
        directionRef = null,
        expectedArrival = now + inMinutes * 60_000L,
        expectedDeparture = null,
        aimedArrival = null,
        aimedDeparture = null,
        atStop = false,
        platform = null,
        status = null,
        journeyNote = null,
        stopId = stopId,
    )

    @Test
    fun onePointPerDirection() {
        val stop = Stop("Châtelet", "Paris", listOf("IDFM:22092", "IDFM:463158"))
        val results = mapOf(
            "IDFM:22092" to listOf(dep("IDFM:22092", "Porte de Clignancourt", 2), dep("IDFM:22092", "Porte de Clignancourt", 6)),
            "IDFM:463158" to listOf(dep("IDFM:463158", "Bagneux - Lucie Aubrac", 3)),
        )
        val options = Directions.buildOptions(stop, results)
        assertEquals(2, options.size)
        assertEquals("Vers Porte de Clignancourt", options[0].title)
        assertEquals("Vers Bagneux - Lucie Aubrac", options[1].title)

        val selection = Directions.toSelection(options, listOf(options[0]))
        assertEquals(mapOf("IDFM:22092" to emptySet<String>()), selection)
        assertEquals("→ Porte de Clignancourt", Directions.label(options, listOf(options[0]), stop))
        assertEquals(Directions.ALL_DIRECTIONS, Directions.label(options, options, stop))

        val filtered = Directions.filter(results.values.flatten(), selection)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.stopId == "IDFM:22092" })
    }

    @Test
    fun destinationsOfASingleStopArea() {
        val zda = "IDFM:monomodalStopPlace:473921"
        val stop = Stop("Châtelet les Halles", "Paris", listOf(zda))
        val deps = listOf(
            dep(zda, "Boissy-Saint-Léger", 2),
            dep(zda, "Saint-Germain-en-Laye", 3),
            dep(zda, "Marne-la-Vallée Chessy", 5),
            dep(zda, "Boissy-Saint-Léger", 12),
        )
        val options = Directions.buildOptions(stop, mapOf(zda to deps))
        assertEquals(3, options.size)

        val chosen = options.filter { it.key == "Boissy-Saint-Léger" || it.key == "Marne-la-Vallée Chessy" }
        val selection = Directions.toSelection(options, chosen)
        assertEquals(setOf("Boissy-Saint-Léger", "Marne-la-Vallée Chessy"), selection[zda])

        val filtered = Directions.filter(deps, selection)
        assertEquals(3, filtered.size)
        assertTrue(filtered.none { it.destination == "Saint-Germain-en-Laye" })
    }

    @Test
    fun directionNameGroupsShortTurns() {
        val zda = "IDFM:monomodalStopPlace:1"
        val stop = Stop("Gare", null, listOf(zda))
        val deps = listOf(
            dep(zda, "Terminus Nord", 2, direction = "Nord"),
            dep(zda, "Arrêt intermédiaire", 4, direction = "Nord"),
            dep(zda, "Terminus Sud", 5, direction = "Sud"),
        )
        val options = Directions.buildOptions(stop, mapOf(zda to deps))
        assertEquals(listOf("Nord", "Sud"), options.map { it.key })
        assertEquals("Destinations : Terminus Nord, Arrêt intermédiaire", options[0].subtitle)
    }

    @Test
    fun unknownDirectionKeepsAllPassages() {
        val stop = Stop("Mairie", "Montreuil", listOf("IDFM:1", "IDFM:2"))
        val options = Directions.buildOptions(stop, mapOf("IDFM:1" to emptyList(), "IDFM:2" to null))
        assertEquals(2, options.size)
        assertNull(options[0].key)
        assertEquals("Point d'arrêt 1", options[0].title)
        val selection = Directions.toSelection(options, listOf(options[1]))
        assertEquals(mapOf("IDFM:2" to emptySet<String>()), selection)
    }

    @Test
    fun upcomingDropsPastAndSorts() {
        val deps = listOf(
            dep("a", "Later", 9),
            dep("a", "Past", -5),
            dep("a", "Soon", 1),
        )
        val upcoming = Directions.upcoming(deps, now)
        assertEquals(listOf("Soon", "Later"), upcoming.map { it.destination })
    }

    @Test
    fun aVehicleAtTheStopStaysALittleAfterItsTimeButNotForever() {
        fun atStop(destination: String, inMinutes: Int) = dep("a", destination, inMinutes).copy(atStop = true)
        // À quai depuis une minute et demie : encore là, alors qu'un passage ordinaire serait retiré.
        assertEquals(listOf("Quai"), Directions.upcoming(listOf(atStop("Quai", 0).shifted(-90_000L)), now).map { it.destination })
        assertTrue(Directions.upcoming(listOf(dep("a", "Parti", 0).shifted(-90_000L)), now).isEmpty())
        // Sans nouvelle de la source (hors connexion, actualisation manuelle), il a fini par partir :
        // le suivant prend sa place au lieu de rester derrière un « à quai » qui ne bouge plus.
        val stale = listOf(atStop("Parti", -3), dep("a", "Suivant", 4))
        assertEquals(listOf("Suivant"), Directions.upcoming(stale, now).map { it.destination })
    }

    private fun Departure.shifted(byMs: Long) = copy(expectedArrival = expectedArrival?.plus(byMs))

    @Test
    fun filterAcceptsADestinationWhenPrimReturnsAnotherDirectionName() {
        val zone = "stop_area:IDFM:71348"
        // Widget configuré sans clé : la clé de sens est la destination vue par Tracéo.
        val selection = mapOf(zone to setOf("La Défense (Grande Arche)"))
        val deps = listOf(
            dep(zone, "La Défense (Grande Arche)", 2, direction = "La Défense"),
            dep(zone, "Château de Vincennes", 3, direction = "Château de Vincennes"),
        )
        assertEquals(listOf("La Défense (Grande Arche)"), Directions.filter(deps, selection).map { it.destination })
    }

    @Test
    fun filterStillMatchesTheDirectionName() {
        val zone = "IDFM:monomodalStopPlace:1"
        val selection = mapOf(zone to setOf("Nord"))
        val deps = listOf(dep(zone, "Terminus Nord", 2, direction = "Nord"), dep(zone, "Terminus Sud", 3, direction = "Sud"))
        assertEquals(listOf("Terminus Nord"), Directions.filter(deps, selection).map { it.destination })
    }

    @Test
    fun terminusArrivalsAreRemoved() {
        val deps = listOf(dep("s", "Auber", 2), dep("s", "Boissy-Saint-Léger", 3))
        assertEquals(listOf("Boissy-Saint-Léger"), Directions.withoutTerminusArrivals(deps, "Auber").map { it.destination })
    }

    @Test
    fun terminusComparisonIgnoresCaseAccentsAndFinalTown() {
        fun kept(destination: String, stop: String) =
            Directions.withoutTerminusArrivals(listOf(dep("s", destination, 2)), stop).isNotEmpty()

        assertFalse(kept("CHATELET", "Châtelet"))
        assertFalse(kept("Boissy Saint Léger", "Boissy-Saint-Léger"))
        assertFalse(kept("La Défense (Grande Arche)", "La Défense"))
        assertFalse(kept("La Défense", "La Défense (Grande Arche)"))
        assertFalse(kept("Mairie (Montreuil)", "Mairie (Paris)"))
        // Seul un suffixe final compte, et un nom seulement proche reste un vrai sens.
        assertTrue(kept("Esplanade de la Défense", "La Défense"))
        assertTrue(kept("Gare (Nord) Sud", "Gare"))
        assertTrue(kept("La Défense", ""))
    }

    private fun rail(line: String, destination: String, ref: String?) =
        dep("s", destination, 2).copy(lineRef = line, directionRef = ref)

    @Test
    fun aTrainAnnouncedElsewhereButEndingAtTheBranchEndIsAnArrival() {
        // Relevé réel : NANI22, annoncé vers Sucy, passe à Sucy à 17 h 05 puis à Boissy à 17 h 11.
        val deps = listOf(
            rail("C01742", "Sucy - Bonneuil", "Aller"),
            rail("C01742", "Boissy-Saint-Léger", "Aller"),
            rail("C01742", "Cergy le Haut", "Retour"),
        )
        assertEquals(listOf("Cergy le Haut"), Directions.withoutTerminusArrivals(deps, "Boissy-Saint-Léger").map { it.destination })
        assertEquals(
            listOf("Sucy - Bonneuil", "Boissy-Saint-Léger", "Cergy le Haut"),
            Directions.withoutTerminusArrivals(deps, "Nation").map { it.destination },
        )
    }

    @Test
    fun theSameHoldsAtRobinsonOnTheRerB() {
        val deps = listOf(rail("line:IDFM:C01743", "Orsay-Ville", "Aller"), rail("line:IDFM:C01743", "Mitry - Claye", "Retour"))
        assertEquals(listOf("Mitry - Claye"), Directions.withoutTerminusArrivals(deps, "Robinson").map { it.destination })
    }

    @Test
    fun aBranchEndOnlyConcernsItsOwnLineAndAKnownDirection() {
        // Des bus desservent aussi un arrêt « Robinson », et leur sens « Aller » n'y est pas une arrivée.
        assertEquals(1, Directions.withoutTerminusArrivals(listOf(rail("C01000", "Clamart", "Aller")), "Robinson").size)
        assertEquals(1, Directions.withoutTerminusArrivals(listOf(rail("C01742", "Sucy - Bonneuil", null)), "Boissy-Saint-Léger").size)
    }

    @Test
    fun realDirectionOnlyChangesTheRerA() {
        assertEquals("Retour", Directions.realDirection("C01743", "Massy-Palaiseau", "Retour", "Nation"))
        assertNull(Directions.realDirection("C01743", "Massy-Palaiseau", null, "Nation"))
    }

    @Test
    fun theWestBranchesOfTheRerAKeepTheFeedDirection() {
        fun real(destination: String, ref: String?, stop: String) = Directions.realDirection("C01742", destination, ref, stop)
        assertEquals(real("Cergy le Haut", "Retour", "Nation"), real("Saint-Germain-en-Laye", "Retour", "Nation"))
        assertEquals(real("Boissy-Saint-Léger", "Aller", "Nation"), real("Poissy", "Aller", "Nation"))
        assertNotEquals(real("Cergy le Haut", "Retour", "Nation"), real("Marne-la-Vallée Chessy", "Retour", "Nation"))
    }

    @Test
    fun terminusCanEmptyTheDirections() {
        val stop = Stop("Auber", null, listOf("s"))
        val results = mapOf("s" to Directions.withoutTerminusArrivals(listOf(dep("s", "Auber", 2)), stop.name))
        val option = Directions.buildOptions(stop, results).single()
        assertNull(option.key)
    }

    @Test
    fun widgetConfigRoundTrip() {
        val config = WidgetConfig(
            widgetId = 42,
            mode = TransportMode.METRO,
            line = Line("C01374", "4", "4", "metro", network = "Métro", operator = "RATP", color = "BE418D", textColor = "FFFFFF"),
            stop = Stop("Châtelet", "Paris", listOf("IDFM:22092", "IDFM:463158")),
            directions = mapOf("IDFM:22092" to emptySet(), "IDFM:463158" to setOf("Bagneux")),
            directionLabel = "→ Porte de Clignancourt",
        )
        assertEquals(config, WidgetConfig.fromJson(config.toJson()))
    }
}
