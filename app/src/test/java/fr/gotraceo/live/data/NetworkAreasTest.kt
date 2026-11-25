package fr.gotraceo.live.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * À proximité et le mode automatique avec une vingtaine de réseaux : des emprises voisines ou qui se
 * recouvrent, les index des réseaux quittés, et la limite de taille du client HTTP. Les réseaux et
 * leurs identifiants sont inventés : l'application n'en écrit aucun.
 */
class NetworkAreasTest {

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 1_800_000_000_000L

        /** Un degré de latitude fait environ 111 km. */
        fun north(base: GeoPoint, meters: Int) = GeoPoint(base.lat + meters / 111_320.0, base.lon)
    }

    private fun network(id: String, bbox: BoundingBox, status: NetworkStatus = NetworkStatus.OK) =
        Network(id, id.uppercase(), "Ville de $id", bbox, listOf("bus"), refreshSec = 60, status = status)

    // Deux réseaux voisins, dont les emprises se touchent presque sans se recouvrir ; un réseau
    // étendu, dont l'emprise contient celle du premier ; l'Île-de-France est loin de tous.
    private val west = network("ouest", BoundingBox(45.00, 45.20, 1.00, 1.30))
    private val east = network("est", BoundingBox(45.00, 45.20, 1.32, 1.60))
    private val wide = network("large", BoundingBox(44.80, 45.60, 0.60, 1.318))
    private val catalog = NetworkCatalog(listOf(west, east, wide))

    private val inWest = GeoPoint(45.10, 1.15)
    private val inEast = GeoPoint(45.10, 1.45)
    private val inTheGap = GeoPoint(45.10, 1.315)

    private fun line(networkId: String) = Line("L1", "Ligne 1", "1", "bus", networkId = networkId)

    /** L'index d'un réseau réduit à un arrêt, à [meters] au nord du point. */
    private fun area(networkId: String, from: GeoPoint, meters: Int): StopIndexArea {
        val stop = north(from, meters)
        return StopIndexArea(mapOf("L1" to line(networkId)), listOf(IndexedStop("Arrêt de $networkId", null, stop.lat, stop.lon, "z-$networkId", listOf("L1"))))
    }

    private val read = ArrayList<String>()
    private val readOnPhone = ArrayList<String>()

    @After
    fun tearDown() {
        read.clear()
        readOnPhone.clear()
    }

    /**
     * @param areas l'index de chaque réseau autour du point ; absent, il n'est pas sur le téléphone
     * @param downloadable réseaux dont l'index peut être téléchargé au besoin
     */
    private fun lookup(point: GeoPoint, areas: Map<String, StopIndexArea>, downloadable: Map<String, StopIndexArea> = emptyMap()) =
        NearbyNetworks.lookup(
            catalog, point, emptySet(), null, nightService = false, idfmModes = setOf("bus"),
            wider = { network ->
                readOnPhone += network.id
                areas[network.id]
            },
        ) { network ->
            read += network.id
            areas[network.id] ?: downloadable[network.id]
        }

    // --- Emprises voisines ---------------------------------------------------------------------

    @Test
    fun neighbouringNetworksEachCoverTheirSide() {
        assertEquals(listOf("ouest", "large"), catalog.covering(inWest).map { it.id })
        assertEquals(listOf("est"), catalog.covering(inEast).map { it.id })
        // Entre les deux, à quelques centaines de mètres de chacun : le réseau étendu seul couvre le point.
        assertEquals(listOf("large"), catalog.covering(inTheGap).map { it.id })
        // À l'étape du réseau, une position approximative au bord propose le plus local des deux.
        assertEquals("ouest", catalog.nearest(inWest)?.id)
        assertEquals("est", catalog.nearest(inEast)?.id)
    }

    @Test
    fun aPointBetweenTwoNetworksFallsBackOnTheOneThatCoversIt() {
        val found = lookup(inTheGap, mapOf("large" to area("large", inTheGap, 300)))
        assertEquals("large", (found as NearbyLookup.Found).network.id)
        // Sans réseau étendu, personne ne couvre l'entre-deux : le widget le dit.
        val two = NetworkCatalog(listOf(west, east))
        assertTrue(two.covering(inTheGap).isEmpty())
        val none = NearbyNetworks.lookup(two, inTheGap, emptySet(), null, false, setOf("bus")) { fail("aucun index à lire"); null }
        assertTrue(none is NearbyLookup.NoNetwork)
    }

    @Test
    fun theClosestNetworkIsNamedWhereNoneCovers() {
        val far = GeoPoint(46.50, 1.15)
        assertTrue(catalog.covering(far).isEmpty())
        val (closest, meters) = catalog.closest(far)!!
        assertEquals("large", closest.id)
        assertTrue("à une centaine de kilomètres", meters in 95_000..105_000)
        // Un réseau coupé n'est pas cité.
        val cut = NetworkCatalog(listOf(west, wide.copy(status = NetworkStatus.OFF)))
        assertEquals("ouest", cut.closest(far)!!.first.id)
        // Sans catalogue, il reste l'Île-de-France.
        assertEquals(Networks.IDFM, NetworkCatalog().closest(far)!!.first.id)
    }

    @Test
    fun aDistanceToAnotherCityIsReadInKilometres() {
        assertEquals("33 m", formatDistance(33))
        assertEquals("1,1 km", formatDistance(1_080))
        assertEquals("35,0 km", formatDistance(35_010))
        assertEquals("99,9 km", formatDistance(99_949))
        assertEquals("100 km", formatDistance(99_950))
        assertEquals("140 km", formatDistance(140_300))
        assertEquals("387 km", formatDistance(386_600))
    }

    // --- Emprises qui se recouvrent ------------------------------------------------------------

    @Test
    fun theLocalNetworkWinsWhenItHasAStopNearby() {
        val found = lookup(inWest, mapOf("ouest" to area("ouest", inWest, 150), "large" to area("large", inWest, 20)))
        assertEquals("ouest", (found as NearbyLookup.Found).network.id)
        assertEquals("Le réseau étendu n'est même pas lu", listOf("ouest"), read + readOnPhone)
    }

    /** L'emprise du réseau local contient un coin où il n'a rien : on ne montre pas ses lignes à 1,9 km à côté d'un arrêt d'un autre. */
    @Test
    fun aWiderNetworkWinsWhenItsStopIsClearlyCloser() {
        val found = lookup(inWest, mapOf("ouest" to area("ouest", inWest, 1_900), "large" to area("large", inWest, 100)))
        assertEquals("large", (found as NearbyLookup.Found).network.id)
        assertEquals(100, found.ranking.list.first().nearby.distanceM)
    }

    @Test
    fun aSlightlyCloserWiderNetworkDoesNotTakeOver() {
        val found = lookup(inWest, mapOf("ouest" to area("ouest", inWest, 600), "large" to area("large", inWest, 550)))
        assertEquals("ouest", (found as NearbyLookup.Found).network.id)
        // De part et d'autre de la tolérance : dix mètres en deçà, le réseau local reste ; dix au-delà,
        // l'autre passe devant. Les distances sont relues sur les arrêts, pas supposées.
        val limit = NearbyRules.TOLERANCE_M
        fun nearest(area: StopIndexArea) = area.stops.minOf { inWest.distanceTo(GeoPoint(it.lat, it.lon)) }
        val local = area("ouest", inWest, 800)
        val under = area("large", inWest, 800 - limit + 10)
        val over = area("large", inWest, 800 - limit - 10)
        assertTrue(nearest(local) - nearest(under) in 1 until limit)
        assertTrue(nearest(local) - nearest(over) > limit)
        assertEquals("ouest", (lookup(inWest, mapOf("ouest" to local, "large" to under)) as NearbyLookup.Found).network.id)
        assertEquals("large", (lookup(inWest, mapOf("ouest" to local, "large" to over)) as NearbyLookup.Found).network.id)
    }

    /**
     * Une fois des lignes trouvées, un réseau plus étendu est lu par un autre chemin que le premier :
     * c'est là que l'appelant décide de ce qu'il télécharge sur ce doute.
     */
    @Test
    fun aWiderNetworkIsReadItsOwnWayOnceLinesAreFound() {
        val found = lookup(
            inWest,
            areas = mapOf("ouest" to area("ouest", inWest, 1_900)),
            downloadable = mapOf("large" to area("large", inWest, 100)),
        )
        assertEquals("ouest", (found as NearbyLookup.Found).network.id)
        assertEquals(listOf("ouest"), read)
        assertEquals(listOf("large"), readOnPhone)
    }

    /** Tant que rien n'est trouvé, le réseau suivant est essayé pour de bon, téléchargement compris. */
    @Test
    fun theNextNetworkIsTriedInFullWhenTheLocalOneHasNothing() {
        val empty = StopIndexArea(emptyMap(), emptyList())
        val found = lookup(inWest, areas = mapOf("ouest" to empty), downloadable = mapOf("large" to area("large", inWest, 400)))
        assertEquals("large", (found as NearbyLookup.Found).network.id)
        assertEquals(listOf("ouest", "large"), read)

        val nothing = lookup(inWest, areas = mapOf("ouest" to empty))
        assertEquals("Un index manquant passe devant un réseau sans arrêt", "large", (nothing as NearbyLookup.NoIndex).network.id)
    }

    // --- Index des réseaux quittés -------------------------------------------------------------

    @Test
    fun theIndexOfANetworkStaysWhileItIsOfUse() {
        // Un widget posé dans le réseau : gardé, même sans recherche récente.
        assertTrue(IndexRetention.keeps(inCatalog = true, hasWidget = true, usedAt = 0L, now = NOW))
        // Une recherche récente autour de l'usager : gardé.
        assertTrue(IndexRetention.keeps(inCatalog = true, hasWidget = false, usedAt = NOW - 29 * DAY, now = NOW))
        // Une horloge reculée ne fait pas tout supprimer.
        assertTrue(IndexRetention.keeps(inCatalog = true, hasWidget = false, usedAt = NOW + DAY, now = NOW))
    }

    @Test
    fun theIndexOfANetworkLeftBehindIsRemoved() {
        assertFalse(IndexRetention.keeps(inCatalog = true, hasWidget = false, usedAt = NOW - 31 * DAY, now = NOW))
        // Téléchargé pour une configuration abandonnée, il n'a jamais servi autour de l'usager.
        assertFalse(IndexRetention.keeps(inCatalog = true, hasWidget = false, usedAt = 0L, now = NOW))
        // Réseau sorti du catalogue : son index ne sera plus renouvelé, il ne sert plus.
        assertFalse(IndexRetention.keeps(inCatalog = false, hasWidget = false, usedAt = NOW, now = NOW))
        // Sauf si un widget posé s'y trouve : un catalogue vide par accident n'emporte pas son index.
        assertTrue(IndexRetention.keeps(inCatalog = false, hasWidget = true, usedAt = 0L, now = NOW))
        assertEquals(30 * DAY, IndexRetention.KEEP_UNUSED_MS)
    }

    @Test
    fun indexFilesAreNamedAfterTheirNetwork() {
        assertEquals("stop_index.bin", StopIndexFiles.nameOf(Networks.IDFM))
        assertEquals("ville-a", StopIndexFiles.networkOf(StopIndexFiles.nameOf("ville-a")))
        // L'index de l'Île-de-France, les fichiers en cours d'écriture et le reste ne sont pas concernés.
        assertNull(StopIndexFiles.networkOf("stop_index.bin"))
        assertNull(StopIndexFiles.networkOf("stop_index_ville-a.bin.tmp"))
        assertNull(StopIndexFiles.networkOf("stop_index_idfm.bin"))
        assertNull(StopIndexFiles.networkOf("networks.json"))
        assertNull(StopIndexFiles.networkOf("stop_index_../autre.bin"))
    }

    // --- Limite du client HTTP -----------------------------------------------------------------

    @Test
    fun aResponseBeyondTheLimitIsRefusedNotTruncated() {
        val body = ByteArray(100_000) { 'a'.code.toByte() }
        assertEquals(100_000, Http.readBounded(ByteArrayInputStream(body), 100_000).size)
        try {
            Http.readBounded(ByteArrayInputStream(body), 99_999)
            fail("IOException attendue")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("volumineuse"))
        }
        assertEquals(0, Http.readBounded(ByteArrayInputStream(ByteArray(0)), 10).size)
    }

    /** Une réponse démesurée est un échec de réseau pour le client : le téléphone garde ce qu'il avait. */
    @Test
    fun anOversizedAnswerIsAFailureForTheClient() {
        val server = FakeServer()
        try {
            server.respond("/gros", 200, "x".repeat(5_000))
            assertEquals(5_000, Http.get(server.url + "/gros").body.length)
            try {
                Http.get(server.url + "/gros", maxBytes = 4_999)
                fail("IOException attendue")
            } catch (e: IOException) {
                assertTrue(e.message.orEmpty().contains("volumineuse"))
            }
        } finally {
            server.close()
        }
        assertTrue(Http.MAX_BODY_BYTES >= 4 * 1024 * 1024)
    }
}
