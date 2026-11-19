package fr.gotraceo.live.data

import fr.gotraceo.live.ui.RowItem
import fr.gotraceo.live.ui.RowSearch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les arrêts d'une ligne hors Île-de-France : aucun réseau n'y donne la commune. La liste se cherche
 * et s'affiche sans elle, et deux arrêts de même nom restent distinguables. L'identifiant de réseau
 * est inventé : l'application n'en écrit aucun.
 */
class SameNameStopsTest {

    private companion object {
        const val NETWORK = "ville-a"
        const val LINE = "204"

        /** Un degré de latitude fait environ 111 km : 0,0003° fait une trentaine de mètres. */
        const val LAT = 48.2900
        const val LON = 4.0700
    }

    private val files = ArrayList<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun stop(name: String, id: String) = Stop(name, null, listOf(id), NETWORK)

    private fun at(northM: Int): GeoPoint = GeoPoint(LAT + northM / 111_320.0, LON)

    // --- Sans homonyme -------------------------------------------------------------------------

    @Test
    fun aLineWithoutSameNamesIsLeftAsItIs() {
        val stops = listOf(stop("Acacias", "a"), stop("Gare", "g"), stop("Halle", "h"))
        assertFalse(SameNameStops.hasSameNames(stops))
        assertEquals(stops.map { StopChoice(it) }, SameNameStops.choices(stops))
    }

    @Test
    fun namesAreComparedWithoutCaseOrAccents() {
        assertTrue(SameNameStops.hasSameNames(listOf(stop("Hôtel de Ville", "1"), stop("HOTEL DE VILLE", "2"))))
        assertTrue(SameNameStops.hasSameNames(listOf(stop("Saint-Pierre", "1"), stop("Saint Pierre", "2"))))
        assertFalse(SameNameStops.hasSameNames(listOf(stop("Gare", "1"), stop("Gare Nord", "2"))))
    }

    // --- Les deux sens d'un même arrêt ---------------------------------------------------------

    /** Le réseau ne rattache pas ses quais : chaque sens est un arrêt. Proches, ils n'en font qu'un. */
    @Test
    fun twoSameNamedStopsCloseToEachOtherAreOneStop() {
        val stops = listOf(stop("Acacias", "a"), stop("Gare", "g1"), stop("Gare", "g2"), stop("Halle", "h"))
        val places = mapOf("a" to at(-800), "g1" to at(0), "g2" to at(30), "h" to at(900))
        val choices = SameNameStops.choices(stops, places)

        assertEquals(listOf("Acacias", "Gare", "Halle"), choices.map { it.stop.name })
        val gare = choices[1]
        assertEquals(listOf("g1", "g2"), gare.stop.stopIds)
        assertNull("Un seul arrêt de ce nom : rien à préciser", gare.hint)
        assertEquals(NETWORK, gare.stop.networkId)
        // Le widget interroge les deux, et le sens se choisit à l'étape suivante.
        assertEquals(setOf("g1", "g2"), Directions.allDirections(gare.stop).keys)
        assertEquals(PassageRoute.TRACEO, DataSources.route(NETWORK, gare.stop.stopIds, hasKey = false))
    }

    @Test
    fun theLimitBetweenOneStopAndTwo() {
        val stops = listOf(stop("Gare", "g1"), stop("Gare", "g2"), stop("Parc", "p"))
        val near = SameNameStops.choices(stops, mapOf("g1" to at(0), "g2" to at(SameNameStops.SAME_PLACE_M - 10), "p" to at(5_000)))
        assertEquals(2, near.size)
        val far = SameNameStops.choices(stops, mapOf("g1" to at(0), "g2" to at(SameNameStops.SAME_PLACE_M + 10), "p" to at(5_000)))
        assertEquals(3, far.size)
    }

    /** Trois quais en file : le premier et le dernier sont loin l'un de l'autre, mais celui du milieu les relie. */
    @Test
    fun stopsAreGatheredStepByStep() {
        // Dans cet ordre, les deux bouts forment d'abord deux groupes, que celui du milieu réunit.
        val stops = listOf(stop("Pôle", "p1"), stop("Pôle", "p3"), stop("Pôle", "p2"))
        val choices = SameNameStops.choices(stops, mapOf("p1" to at(0), "p3" to at(300), "p2" to at(150)))
        assertEquals(listOf(listOf("p1", "p3", "p2")), choices.map { it.stop.stopIds })
        // Sans celui du milieu, ils restent deux.
        assertEquals(2, SameNameStops.choices(stops.take(2) + stop("Parc", "x"), mapOf("p1" to at(0), "p3" to at(300), "x" to at(9_000))).count { it.stop.name == "Pôle" })
    }

    /**
     * L'arrêt cité doit situer le bon homonyme. Une « Mairie » de hameau, à 3 km de tout, ne se dit
     * pas « Près de Stade » quand le stade est à 500 m de l'autre « Mairie » : mieux vaut numéroter.
     */
    @Test
    fun aFarNeighbourIsNotANeighbour() {
        val stops = listOf(stop("Gare", "g"), stop("Mairie", "a"), stop("Mairie", "b"), stop("Stade", "s"))
        val places = mapOf("a" to at(0), "s" to at(3_000), "b" to at(3_500), "g" to at(3_800))
        val mairies = SameNameStops.choices(stops, places).filter { it.stop.name == "Mairie" }
        assertEquals(listOf("Arrêt 1 sur 2 de ce nom", "Arrêt 2 sur 2 de ce nom"), mairies.map { it.hint })
        assertTrue(mairies.all { it.near == null })
    }

    /** Un arrêt qui a lui-même un homonyme sur la ligne ne situe rien : « Près de Église » ne dit pas laquelle. */
    @Test
    fun aNeighbourMustHaveAUniqueName() {
        val stops = listOf(
            stop("Église", "e1"), stop("Église", "e2"), stop("Lavoir", "l"), stop("Mairie", "m1"), stop("Mairie", "m2"), stop("Pont", "p"),
        )
        val places = mapOf(
            "m1" to at(0), "e1" to at(100), "l" to at(400),
            "m2" to at(6_000), "e2" to at(6_100), "p" to at(6_500),
        )
        val choices = SameNameStops.choices(stops, places)
        assertEquals(listOf("Près de Lavoir", "Près de Pont"), choices.filter { it.stop.name == "Mairie" }.map { it.hint })
        assertEquals(listOf("Près de Lavoir", "Près de Pont"), choices.filter { it.stop.name == "Église" }.map { it.hint })
        assertEquals(listOf("Lavoir", "Pont"), choices.filter { it.stop.name == "Mairie" }.map { it.near })
    }

    /** Un arrêt réuni dont le réseau ne connaît plus qu'un identifiant le dit, même s'il lui reste des passages. */
    @Test
    fun aMergedStopSaysWhenOneOfItsIdentifiersIsGone() {
        val line = Line(LINE, "Parcours", "2B", "bus", networkId = NETWORK)
        val gare = Stop("Gare", null, listOf("g1", "g2"), NETWORK)
        val config = WidgetConfig(1, TransportMode.BUS, line, gare, Directions.allDirections(gare), Directions.ALL_DIRECTIONS)
        val now = 1_800_000_000_000L
        val passage = Departure(
            lineRef = LINE, destination = "Terminus", directionName = null, directionRef = "0",
            expectedArrival = now + 240_000L, expectedDeparture = null, aimedArrival = null, aimedDeparture = null,
            atStop = false, platform = null, status = "onTime", journeyNote = null, stopId = "g1",
        )
        val served = Result.success(PassageSnapshot(listOf(passage), dataAt = now, fresh = true, viaTraceo = true))
        val gone = Result.failure<PassageSnapshot>(
            TraceoException(TraceoException.Kind.NOT_FOUND, "Arrêt inconnu sur cette ligne : reconfigurez ce widget.", missing = TraceoException.Missing.STOP),
        )
        val state = WidgetStates.from(config, listOf(served, gone), null, now, removeTerminusArrivals = false)
        assertEquals(1, state.departures.size)
        assertEquals("Arrêt inconnu sur cette ligne : reconfigurez ce widget.", state.error)
        // Une panne passagère d'un des deux, elle, ne se dit pas tant qu'il reste des passages.
        val busy = Result.failure<PassageSnapshot>(TraceoException(TraceoException.Kind.RATE_LIMITED, "Trop de requêtes"))
        assertNull(WidgetStates.from(config, listOf(served, busy), null, now, removeTerminusArrivals = false).error)
    }

    // --- Deux endroits différents --------------------------------------------------------------

    @Test
    fun twoSameNamedStopsFarApartAreToldApartByTheirNeighbour() {
        val stops = listOf(
            stop("Église", "e"), stop("Jean Jaurès", "j"), stop("Mairie", "m1"), stop("Mairie", "m2"), stop("Stade", "s"),
        )
        val places = mapOf("j" to at(200), "m1" to at(0), "e" to at(3_900), "m2" to at(4_000), "s" to at(8_000))
        val choices = SameNameStops.choices(stops, places)

        assertEquals(listOf("Église", "Jean Jaurès", "Mairie", "Mairie", "Stade"), choices.map { it.stop.name })
        assertEquals(listOf(null, null, "Près de Jean Jaurès", "Près de Église", null), choices.map { it.hint })
        assertEquals(listOf("m1"), choices[2].stop.stopIds)
        assertEquals(listOf("m2"), choices[3].stop.stopIds)
    }

    /** Deux « Mairie », chacune avec ses deux sens : deux arrêts, de deux identifiants chacun. */
    @Test
    fun eachPlaceKeepsItsTwoDirections() {
        val stops = listOf(
            stop("Lavoir", "l"), stop("Mairie", "m1a"), stop("Mairie", "m1b"), stop("Mairie", "m2a"), stop("Mairie", "m2b"), stop("Pont", "p"),
        )
        val places = mapOf("l" to at(300), "m1a" to at(0), "m1b" to at(25), "m2a" to at(5_000), "m2b" to at(5_020), "p" to at(5_400))
        val mairies = SameNameStops.choices(stops, places).filter { it.stop.name == "Mairie" }
        assertEquals(listOf(listOf("m1a", "m1b"), listOf("m2a", "m2b")), mairies.map { it.stop.stopIds })
        assertEquals(listOf("Près de Lavoir", "Près de Pont"), mairies.map { it.hint })
    }

    // --- Quand la position ne suffit pas -------------------------------------------------------

    @Test
    fun withoutPositionsSameNamedStopsStayApartAndAreNumbered() {
        val stops = listOf(stop("Gare", "g1"), stop("Gare", "g2"), stop("Halle", "h"))
        val expected = listOf("Arrêt 1 sur 2 de ce nom", "Arrêt 2 sur 2 de ce nom", null)
        assertEquals(expected, SameNameStops.choices(stops).map { it.hint })
        // Un seul des deux est situé : on ne les réunit pas à l'aveugle.
        assertEquals(expected, SameNameStops.choices(stops, mapOf("g1" to at(0), "h" to at(500))).map { it.hint })
        assertEquals(listOf(listOf("g1"), listOf("g2"), listOf("h")), SameNameStops.choices(stops).map { it.stop.stopIds })
    }

    /** Le même voisin pour les deux, ou pas de voisin du tout : le voisin ne distingue rien. */
    @Test
    fun aNeighbourThatDoesNotTellThemApartIsNotUsed() {
        val stops = listOf(stop("Mairie", "m1"), stop("Mairie", "m2"), stop("Rond-Point", "r"))
        val places = mapOf("m1" to at(0), "m2" to at(1_000), "r" to at(500))
        assertEquals(
            listOf("Arrêt 1 sur 2 de ce nom", "Arrêt 2 sur 2 de ce nom", null),
            SameNameStops.choices(stops, places).map { it.hint },
        )
        val alone = listOf(stop("Mairie", "m1"), stop("Mairie", "m2"))
        assertEquals(
            listOf("Arrêt 1 sur 2 de ce nom", "Arrêt 2 sur 2 de ce nom"),
            SameNameStops.choices(alone, mapOf("m1" to at(0), "m2" to at(1_000))).map { it.hint },
        )
    }

    // --- Position lue dans l'index du réseau ---------------------------------------------------

    private fun quay(zone: String, name: String, northM: Int, vararg lines: String) =
        IndexedStop(name, null, LAT + northM / 111_320.0, LON, zone, lines.toList())

    @Test
    fun thePlaceOfAStopIsTheMiddleOfItsQuaysOnTheLine() {
        val line = Line(LINE, "Parcours", "2B", "bus", networkId = NETWORK)
        val other = Line("9", "Autre", "9", "bus", networkId = NETWORK)
        val area = StopIndexArea(
            mapOf(line.id to line, other.id to other),
            listOf(
                quay("g", "Gare", 0, LINE),
                quay("g", "Gare", 40, LINE, "9"),
                // Un quai de la même zone que la ligne ne dessert pas : il ne déplace pas l'arrêt.
                quay("g", "Gare", 2_000, "9"),
                quay("x", "Ailleurs", 500, "9"),
            ),
        )
        val places = SameNameStops.places(area, LINE)
        assertEquals(setOf("g"), places.keys)
        assertTrue(places.getValue("g").distanceTo(at(20)) <= 2)
    }

    /** L'index entier se relit depuis le fichier du téléphone, sans réseau. */
    @Test
    fun theWholeIndexOfANetworkIsReadFromThePhone() {
        val line = Line(LINE, "Parcours", "2B", "bus", networkId = NETWORK)
        val quays = listOf(
            quay("m1", "Mairie", 0, LINE), quay("j", "Jean Jaurès", 200, LINE),
            quay("m2", "Mairie", 40_000, LINE), quay("e", "Église", 39_900, LINE),
        )
        val file = File.createTempFile("index", ".bin").also { files += it }
        val store = StopIndexStore(file, NETWORK) { NetworkIndex(builtAt = 1_000L, lines = listOf(line), stops = quays, skipped = 0) }
        assertNull(StopIndexStore(File(file.path + ".absent"), NETWORK).all())
        store.download()

        val all = store.all()!!
        assertEquals(4, all.stops.size)
        assertEquals(NETWORK, all.lines.getValue(LINE).networkId)
        // Chercher autour d'un point ne voit que le voisinage : c'est bien une autre lecture.
        assertEquals(2, store.around(LAT, LON, 2_000)!!.stops.size)

        val stops = listOf(stop("Église", "e"), stop("Jean Jaurès", "j"), stop("Mairie", "m1"), stop("Mairie", "m2"))
        val hints = SameNameStops.choices(stops, SameNameStops.places(all, LINE)).map { it.hint }
        assertEquals(listOf(null, null, "Près de Jean Jaurès", "Près de Église"), hints)
    }

    // --- Recherche et affichage sans commune ---------------------------------------------------

    /** La ligne de liste d'un arrêt : son nom, et à la place de la commune ce qui le distingue, s'il y a lieu. */
    private fun rows(choices: List<StopChoice>): List<RowItem> = choices.map {
        RowItem(title = it.stop.name, subtitle = it.hint ?: it.stop.town, chevron = true, payload = it.stop, searchOnly = it.searchText)
    }

    /** Les mots de l'indice ne se cherchent pas : « de » ou « nom » ne ramènent pas tous les homonymes. */
    @Test
    fun theWordsOfTheHintAreNotSearched() {
        val stops = listOf(stop("Gare", "g1"), stop("Gare", "g2"), stop("Halle", "h"), stop("Rue de Paris", "r"))
        val items = rows(SameNameStops.choices(stops))
        assertEquals(listOf("Rue de Paris"), RowSearch.filter(items, "de").map { it.title })
        assertTrue(RowSearch.filter(items, "nom").isEmpty())
        assertEquals(2, RowSearch.filter(items, "gare").size)
        // La commune d'un arrêt francilien se cherche toujours.
        val idf = rows(listOf(StopChoice(Stop("Mairie", "Montreuil", listOf("IDFM:1")))))
        assertEquals(1, RowSearch.filter(idf, "montreuil").size)
    }

    @Test
    fun aStopWithoutTownIsShownAndSearchedByItsName() {
        val stops = listOf(stop("Hôtel de Ville", "h"), stop("Gare SNCF", "g"), stop("Saint-Pierre", "s"))
        val items = rows(SameNameStops.choices(stops))
        assertTrue("Pas de sous-titre vide ni de « null » à l'écran", items.all { it.subtitle == null })
        assertTrue(items.none { it.searchText.contains("null") })
        assertEquals(listOf("Hôtel de Ville"), RowSearch.filter(items, "hotel ville").map { it.title })
        assertEquals(listOf("Saint-Pierre"), RowSearch.filter(items, "saint pierre").map { it.title })
        assertEquals(listOf("Gare SNCF"), RowSearch.filter(items, "GARE").map { it.title })
    }

    /** Ce qui distingue deux homonymes se cherche aussi : « mairie jaurès » trouve la bonne. */
    @Test
    fun theHintOfASameNamedStopIsSearchedToo() {
        val stops = listOf(stop("Église", "e"), stop("Jean Jaurès", "j"), stop("Mairie", "m1"), stop("Mairie", "m2"))
        val places = mapOf("j" to at(200), "m1" to at(0), "e" to at(3_900), "m2" to at(4_000))
        val items = rows(SameNameStops.choices(stops, places))
        assertEquals(2, RowSearch.filter(items, "mairie").size)
        val found = RowSearch.filter(items, "mairie jaures").single()
        assertEquals(listOf("m1"), (found.payload as Stop).stopIds)
    }
}
