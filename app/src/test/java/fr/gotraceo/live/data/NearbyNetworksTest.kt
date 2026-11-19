package fr.gotraceo.live.data

import fr.gotraceo.live.widget.AutoMode
import fr.gotraceo.live.widget.NoticeKind
import fr.gotraceo.live.widget.WidgetModels
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le mode automatique et le format À proximité déduisent le réseau de la position, sur le téléphone :
 * un widget qui la suit change de réseau avec la ville, et dit quand aucun réseau ne couvre l'endroit.
 */
class NearbyNetworksTest {

    private companion object {
        const val TCAT = "tcat"
        const val NOW = 1_800_000_000_000L
        val PARIS = GeoPoint(48.8575, 2.3510)
        val TROYES = GeoPoint(48.2973, 4.0741)
        val LYON = GeoPoint(45.7640, 4.8357)

        /** Dans l'emprise de Troyes, mais loin de tout arrêt. */
        val TROYES_COUNTRYSIDE = GeoPoint(48.19, 3.95)

        /** En Île-de-France, loin de tout arrêt de l'index de test. */
        val FONTAINEBLEAU_FOREST = GeoPoint(48.40, 2.65)
    }

    private val tcat = Network(TCAT, "TCAT", "Troyes", BoundingBox(48.17, 48.41, 3.89, 4.26), listOf("bus"), refreshSec = 60)
    private val catalog = NetworkCatalog(listOf(tcat))

    private val bus96 = Line("C01124", "96", "96", "bus")
    private val noctilien = Line("C01400", "N11", "N11", "bus", network = "Noctilien")
    private val cable = Line("C02528", "C1", "C1", "cableway")
    private val line2B = Line("2B", "Bréviandes - Voves", "2B", "bus", networkId = TCAT)
    private val lineN1 = Line("N1", "Navette 1", "N1", "bus", networkId = TCAT)

    private val parisArea = StopIndexArea(
        lines = listOf(bus96, noctilien, cable).associateBy { it.id },
        stops = listOf(IndexedStop("Hôtel de Ville", "Paris", 48.8575, 2.3510, "71217", listOf("C01124", "C01400", "C02528"))),
    )
    private val troyesArea = StopIndexArea(
        lines = listOf(line2B, lineN1).associateBy { it.id },
        stops = listOf(IndexedStop("Acacias", null, 48.2973, 4.0741, "1:ACACI", listOf("2B", "N1"))),
    )
    private val empty = StopIndexArea(emptyMap(), emptyList())

    /** Les index du téléphone : chaque réseau a le sien, vide loin de ses arrêts. */
    private fun areas(point: GeoPoint, missing: Set<String> = emptySet()): (Network) -> StopIndexArea? = { network ->
        when {
            network.id in missing -> null
            network.isIdfm -> if (point == PARIS) parisArea else empty
            network.id == TCAT -> if (point == TROYES) troyesArea else empty
            else -> empty
        }
    }

    private fun lookup(
        point: GeoPoint,
        catalog: NetworkCatalog = this.catalog,
        missing: Set<String> = emptySet(),
        night: Boolean = false,
        idfmModes: Set<String> = setOf("metro", "rail", "tram", "bus"),
    ) = NearbyNetworks.lookup(catalog, point, emptySet(), null, night, idfmModes, areas(point, missing))

    // --- Le réseau de la position --------------------------------------------------------------

    @Test
    fun theNetworksCoveringAPointComeFromTheirAreas() {
        assertEquals(listOf(Networks.IDFM), catalog.covering(PARIS).map { it.id })
        assertEquals(listOf(TCAT), catalog.covering(TROYES).map { it.id })
        assertTrue(catalog.covering(LYON).isEmpty())
        // Sans catalogue, Troyes n'est couverte par rien : l'Île-de-France ne s'étend pas jusque-là.
        assertTrue(NetworkCatalog().covering(TROYES).isEmpty())
    }

    @Test
    fun inParisTheLinesAreThoseOfIleDeFrance() {
        val found = lookup(PARIS) as NearbyLookup.Found
        assertEquals(Networks.IDFM, found.network.id)
        val nearby = found.ranking.list.single().nearby
        assertEquals(bus96, nearby.line)
        assertEquals("stop_area:IDFM:71217", nearby.stopId)
    }

    @Test
    fun inTroyesTheLinesAreThoseOfItsNetwork() {
        val found = lookup(TROYES) as NearbyLookup.Found
        assertEquals(TCAT, found.network.id)
        assertEquals(setOf("2B", "N1"), found.ranking.list.map { it.nearby.line.id }.toSet())
        assertTrue(found.ranking.list.all { it.nearby.line.networkId == TCAT })
        // La station part telle quelle vers Tracéo, sans le préfixe des zones franciliennes.
        assertEquals("1:ACACI", found.ranking.list.first().nearby.stopId)
    }

    // --- Changement de ville -------------------------------------------------------------------

    /** Un widget automatique posé à Paris, emmené à Troyes : il change de réseau sans passer par les réglages. */
    @Test
    fun aWidgetThatFollowsThePositionChangesNetworkWithTheCity() {
        val inParis = (lookup(PARIS) as NearbyLookup.Found).ranking.list.first().nearby
        val before = AutoMode.configFor(7, inParis, WidgetTheme.AUTO)
        assertEquals(Networks.IDFM, before.networkId)

        val inTroyes = (lookup(TROYES) as NearbyLookup.Found).ranking.list.first { it.nearby.line.id == "2B" }.nearby
        val after = AutoMode.configFor(7, inTroyes, before.theme)
        assertEquals(TCAT, after.networkId)
        assertTrue(after.auto)
        assertEquals(line2B, after.line)
        assertEquals(Stop("Acacias", null, listOf("1:ACACI"), TCAT), after.stop)
        assertEquals(TransportMode.BUS, after.mode)

        // Sa source change avec lui : Tracéo pour Troyes, même avec une clé PRIM enregistrée.
        assertEquals(PassageRoute.TRACEO, DataSources.route(after, hasKey = true))
        val asked = ArrayList<Triple<String, String, String>>()
        val traceo = { networkId: String ->
            PassageSource { stopId, lineId ->
                asked += Triple(networkId, stopId, lineId)
                PassageSnapshot(emptyList(), viaTraceo = true)
            }
        }
        DataSources.networkSource(after.networkId, "secret", traceo = traceo).passages(after.stop.stopIds.single(), after.line.id)
        assertEquals(listOf(Triple(TCAT, "1:ACACI", "2B")), asked)

        // Enregistrée puis relue, la configuration garde son nouveau réseau.
        assertEquals(after, WidgetConfig.fromJson(JSONObject(after.toJson().toString())))
    }

    @Test
    fun backInParisTheWidgetReturnsToIleDeFrance() {
        val inTroyes = (lookup(TROYES) as NearbyLookup.Found).ranking.list.first().nearby
        assertEquals(TCAT, AutoMode.configFor(7, inTroyes, WidgetTheme.AUTO).networkId)
        val inParis = (lookup(PARIS) as NearbyLookup.Found).ranking.list.first().nearby
        val back = AutoMode.configFor(7, inParis, WidgetTheme.AUTO)
        assertEquals(Networks.IDFM, back.networkId)
        assertEquals(PassageRoute.PRIM, DataSources.route(back, hasKey = true))
        assertFalse(back.toJson().toString().contains("networkId"))
    }

    // --- Aucun réseau, aucun arrêt, aucun index ------------------------------------------------

    @Test
    fun whereNoNetworkCoversThePlaceTheWidgetSaysSo() {
        val result = lookup(LYON)
        assertSame(NearbyLookup.NoNetwork, result)
        assertNull(result.rankingOrNull)
        assertTrue(result.leavesWidgetWithoutNetwork(Networks.IDFM))
        assertTrue(result.leavesWidgetWithoutNetwork(TCAT))
    }

    /** Dans une autre ville sans arrêt à portée, le widget ne reste pas sur les passages de la sienne. */
    @Test
    fun anotherNetworkWithoutStopInReachAlsoLeavesTheWidgetWithoutNetwork() {
        val result = lookup(TROYES_COUNTRYSIDE)
        assertEquals(TCAT, (result as NearbyLookup.NoStop).network.id)
        assertTrue(result.leavesWidgetWithoutNetwork(Networks.IDFM))
        // Dans son propre réseau, il garde son arrêt, comme avant.
        assertFalse(result.leavesWidgetWithoutNetwork(TCAT))
    }

    @Test
    fun inIleDeFranceAWidgetWithoutStopInReachKeepsItsStopAsBefore() {
        val result = lookup(FONTAINEBLEAU_FOREST)
        assertEquals(Networks.IDFM, (result as NearbyLookup.NoStop).network.id)
        assertFalse(result.leavesWidgetWithoutNetwork(Networks.IDFM))
    }

    /** Arrivé à Troyes sans réseau mobile : l'index manque, il sera téléchargé au prochain toucher. */
    @Test
    fun aMissingIndexIsNotAPlaceWithoutNetwork() {
        val result = lookup(TROYES, missing = setOf(TCAT))
        assertEquals(TCAT, (result as NearbyLookup.NoIndex).network.id)
        assertFalse(result.leavesWidgetWithoutNetwork(Networks.IDFM))
    }

    @Test
    fun theMostLocalNetworkIsSearchedFirstThenTheWiderOne() {
        val local = Network("local", "Local", "Centre", BoundingBox(48.80, 48.90, 2.30, 2.40), listOf("bus"), refreshSec = 60)
        val both = NetworkCatalog(listOf(tcat, local))
        assertEquals(listOf("local", Networks.IDFM), both.covering(PARIS).map { it.id })

        // Le réseau local n'a pas d'arrêt à portée : les lignes viennent du réseau plus étendu.
        val found = lookup(PARIS, catalog = both) as NearbyLookup.Found
        assertEquals(Networks.IDFM, found.network.id)

        // Sans l'index du réseau local, on se replie de même sur celui qu'on a.
        assertEquals(Networks.IDFM, (lookup(PARIS, catalog = both, missing = setOf("local")) as NearbyLookup.Found).network.id)

        // L'index n'est lu, donc téléchargé au besoin, que pour les réseaux qui couvrent le point, dans l'ordre.
        val read = ArrayList<String>()
        NearbyNetworks.lookup(both, PARIS, emptySet(), null, false, setOf("bus")) { network ->
            read += network.id
            areas(PARIS)(network)
        }
        assertEquals(listOf("local", Networks.IDFM), read)
    }

    // --- Règles propres à l'Île-de-France ------------------------------------------------------

    @Test
    fun theNightRuleOnlyAppliesInIleDeFrance() {
        assertTrue(NearbyRules.isNightLine(noctilien))
        assertFalse(NearbyRules.isNightLine(lineN1))

        // De jour : le Noctilien s'efface à Paris, la ligne « N1 » de Troyes reste proposée.
        val paris = (lookup(PARIS, night = false) as NearbyLookup.Found).ranking.list.map { it.nearby.line.shortName }
        assertEquals(listOf("96"), paris)
        val troyes = (lookup(TROYES, night = false) as NearbyLookup.Found).ranking.list.map { it.nearby.line.shortName }
        assertEquals(setOf("2B", "N1"), troyes.toSet())

        assertTrue("N11" in (lookup(PARIS, night = true) as NearbyLookup.Found).ranking.list.map { it.nearby.line.shortName })
    }

    /** Sans clé PRIM, le câble n'a pas de passages en Île-de-France ; un autre réseau sert tous ses modes. */
    @Test
    fun theModesWithoutKeyOnlyRestrictIleDeFrance() {
        val withKey = lookup(PARIS, idfmModes = setOf("bus", "cableway")) as NearbyLookup.Found
        assertTrue("C1" in withKey.ranking.list.map { it.nearby.line.shortName })
        val withoutKey = lookup(PARIS, idfmModes = setOf("bus")) as NearbyLookup.Found
        assertFalse("C1" in withoutKey.ranking.list.map { it.nearby.line.shortName })

        val troyesWithCable = StopIndexArea(
            lines = mapOf("TC" to Line("TC", "Téléphérique", "TC", "cableway", networkId = TCAT)),
            stops = listOf(IndexedStop("Gare", null, TROYES.lat, TROYES.lon, "1:GARE", listOf("TC"))),
        )
        val found = NearbyNetworks.lookup(catalog, TROYES, emptySet(), null, false, setOf("bus")) { troyesWithCable }
        assertEquals("TC", (found as NearbyLookup.Found).ranking.list.single().nearby.line.id)
    }

    // --- Ce que le widget garde et montre ------------------------------------------------------

    @Test
    fun theWidgetRemembersItIsOutOfAnyNetwork() {
        val away = AutoState(anchor = PARIS, last = LYON, uncovered = true)
        assertEquals(away, AutoState.fromJson(JSONObject(away.toJson().toString())))

        // Enregistré avant les réseaux, ou de retour dans un réseau : rien n'en parle.
        val saved = """{"anchorLat":48.8575,"anchorLon":2.351,"lastLat":48.8575,"lastLon":2.351,"distance":33,
            "choiceUntil":0,"hintUntil":0,"others":0}"""
        assertFalse(AutoState.fromJson(JSONObject(saved)).uncovered)
        assertFalse(AutoState(anchor = PARIS, last = PARIS).toJson().toString().contains("uncovered"))

        assertEquals("Aucun réseau ici", AutoMessage.NO_NETWORK.label)
    }

    private fun departure(inMinutes: Int) = Departure(
        lineRef = "C01124", destination = "Porte des Lilas", directionName = null, directionRef = null,
        expectedArrival = NOW + inMinutes * 60_000L, expectedDeparture = null, aimedArrival = null, aimedDeparture = null,
        atStop = false, platform = null, status = "onTime", journeyNote = null, stopId = "stop_area:IDFM:71217",
    )

    /** Les passages gardés sont ceux d'une autre ville : le widget dit qu'il est hors réseau, au lieu de les montrer. */
    @Test
    fun outOfAnyNetworkTheWidgetShowsNoDepartureFromElsewhere() {
        val nearby = NearbyLine(bus96, "Hôtel de Ville", "Paris", "71217", 33)
        val config = AutoMode.configFor(7, nearby, WidgetTheme.AUTO)
        val state = WidgetState(NOW, NOW, listOf(departure(4)), null, dataAt = NOW, fresh = true)

        val covered = WidgetModels.build(config, state, false, false, 172f, 192f, NOW)
        assertNull(covered.notice)
        val away = WidgetModels.build(config, state, false, false, 172f, 192f, NOW, uncovered = true)
        assertEquals(NoticeKind.NO_NETWORK, away.notice?.kind)

        val entries = listOf(ProximityEntry(nearby, NearbyReason.NEAREST, false, listOf(departure(4))))
        val proximity = ProximityState(NOW, NOW, entries, dataAt = NOW, fresh = true)
        assertNull(WidgetModels.buildProximity(config, proximity, false, 348f, 192f, NOW, night = true).notice)
        assertEquals(
            NoticeKind.NO_NETWORK,
            WidgetModels.buildProximity(config, proximity, false, 348f, 192f, NOW, night = true, uncovered = true).notice?.kind,
        )
    }
}
