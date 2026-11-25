package fr.gotraceo.live.data

import fr.gotraceo.live.ui.UiText
import fr.gotraceo.live.widget.BadgeShape
import fr.gotraceo.live.widget.NoticeKind
import fr.gotraceo.live.widget.WidgetModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que la configuration propose selon le réseau : le réseau de l'endroit, ses modes, ses libellés. */
class NetworkChoiceTest {

    private companion object {
        const val NOW = 1_800_000_000_000L
        val PARIS = GeoPoint(48.8566, 2.3522)
        val TROYES = GeoPoint(48.2973, 4.0744)
        val LYON = GeoPoint(45.7640, 4.8357)
    }

    private fun network(id: String, city: String, bbox: BoundingBox, vararg modes: String, status: NetworkStatus = NetworkStatus.OK) =
        Network(id, id.uppercase(), city, bbox, modes.toList(), refreshSec = 60, status = status)

    private val tcat = network("tcat", "Troyes", BoundingBox(48.17, 48.41, 3.89, 4.26), "bus")
    private val catalog = NetworkCatalog(listOf(tcat))

    // --- Le réseau de l'endroit ----------------------------------------------------------------

    @Test
    fun theNetworkWhoseAreaHoldsThePositionIsProposed() {
        assertEquals(Networks.IDFM, catalog.nearest(PARIS)?.id)
        assertEquals("tcat", catalog.nearest(TROYES)?.id)
    }

    /** Une position approximative peut tomber à quelques kilomètres de l'emprise : le réseau reste le bon. */
    @Test
    fun aPositionJustOutsideTheAreaStillFindsItsNetwork() {
        val fiveKmEast = GeoPoint(48.30, 4.26 + 0.068)
        assertFalse(fiveKmEast in tcat.bbox)
        assertEquals("tcat", catalog.nearest(fiveKmEast)?.id)
    }

    @Test
    fun noNetworkIsProposedFarFromAll() {
        assertNull(catalog.nearest(LYON))
        // Sans catalogue, seule l'Île-de-France est connue : Troyes n'est pas francilienne pour autant.
        assertNull(NetworkCatalog().nearest(TROYES))
    }

    @Test
    fun aNetworkThatIsOffIsNotProposed() {
        val off = NetworkCatalog(listOf(tcat.copy(status = NetworkStatus.OFF)))
        assertNull(off.nearest(TROYES))
        // Un flux muet n'empêche pas de configurer : le réseau reste proposé.
        val stale = NetworkCatalog(listOf(tcat.copy(status = NetworkStatus.STALE)))
        assertEquals("tcat", stale.nearest(TROYES)?.id)
    }

    /** Chartres est dans l'emprise large de l'Île-de-France : son propre réseau, plus local, passerait devant. */
    @Test
    fun theMostLocalNetworkWinsWhenAreasOverlap() {
        val chartres = network("chartres", "Chartres", BoundingBox(48.38, 48.50, 1.40, 1.58), "bus")
        val both = NetworkCatalog(listOf(chartres))
        val inChartres = GeoPoint(48.4469, 1.4892)
        assertTrue(inChartres in Networks.ILE_DE_FRANCE.bbox)
        assertEquals("chartres", both.nearest(inChartres)?.id)
        assertEquals(Networks.IDFM, both.nearest(PARIS)?.id)
    }

    @Test
    fun aNetworkMissingFromTheCatalogIsStillUsableByItsWidget() {
        val gone = Networks.placeholder("ancien", "bus")
        assertEquals("ancien", gone.id)
        assertEquals(listOf(TransportMode.BUS), DataSources.modes(gone, hasKey = false))
        // Sans emprise, il n'est proposé nulle part.
        assertNull(NetworkCatalog(listOf(gone)).nearest(LYON))
    }

    // --- Modes ---------------------------------------------------------------------------------

    @Test
    fun aNetworkOffersItsOwnModes() {
        assertEquals(listOf(TransportMode.BUS), DataSources.modes(tcat, hasKey = false))
        assertEquals(listOf(TransportMode.BUS), DataSources.modes(tcat, hasKey = true))

        val big = network("grand", "Ailleurs", BoundingBox(1.0, 2.0, 3.0, 4.0), "metro", "tram", "rail", "bus", "boat", "cableway")
        assertEquals(
            listOf(TransportMode.METRO, TransportMode.TRAIN, TransportMode.TRAM, TransportMode.BUS, TransportMode.OTHER, TransportMode.BOAT),
            DataSources.modes(big, hasKey = false),
        )
    }

    @Test
    fun rerAndTransilienStayInIleDeFrance() {
        val rail = network("fer", "Ailleurs", BoundingBox(1.0, 2.0, 3.0, 4.0), "rail")
        assertEquals(listOf(TransportMode.TRAIN), DataSources.modes(rail, hasKey = true))
        assertEquals("Train", TransportMode.TRAIN.labelIn("fer"))
        assertEquals("Bus", TransportMode.BUS.labelIn("tcat"))
        assertEquals("Train / Transilien", TransportMode.TRAIN.labelIn(Networks.IDFM))
        assertEquals("Bus / Noctilien", TransportMode.BUS.labelIn(Networks.IDFM))

        // Une ligne ferrée « A » d'un autre réseau est un train, pas un RER.
        val a = Line("A", "A", "A", "rail", networkId = "fer")
        assertTrue(TransportMode.TRAIN.includes(a))
        assertFalse(TransportMode.RER.includes(a))
    }

    @Test
    fun ileDeFranceKeepsItsModesAndItsIndexQuery() {
        assertEquals(DataSources.modes(hasKey = false), DataSources.modes(Networks.ILE_DE_FRANCE, hasKey = false))
        assertEquals(DataSources.modes(hasKey = true), DataSources.modes(Networks.ILE_DE_FRANCE, hasKey = true))
        assertFalse(TransportMode.BOAT in DataSources.modes(hasKey = true))
        // Le bateau n'entre pas dans la requête au référentiel IDFM.
        assertEquals(listOf("metro", "rail", "tram", "bus", "cableway", "funicular"), StopIndexStore.ALL_MODES)
    }

    @Test
    fun oneMinuteIsOfferedWhenAWidgetNetworkIsThatFast() {
        val all = Prefs.REFRESH_CHOICES
        assertEquals(listOf(0, 1, 2, 5, 10), DataSources.refreshChoices(all, sourceMinutes = 1))
        assertEquals(DataSources.refreshChoices(all, hasKey = false), DataSources.refreshChoices(all, sourceMinutes = 2))
        assertEquals(DataSources.refreshChoices(all, hasKey = true), DataSources.refreshChoices(all, sourceMinutes = 1))
    }

    // --- Libellés et pastilles -----------------------------------------------------------------

    @Test
    fun aLineOfAnotherNetworkIsTitledByItsCode() {
        val line2B = Line("2B", "Bréviandes - Voves", "2B", "bus", networkId = "tcat")
        assertEquals("Bus 2B", UiText.lineTitle(line2B))
        assertEquals("Tram B", UiText.lineTitle(Line("B", "Les Aubiers - Pessac", "B", "tram", networkId = "bordeaux")))
        assertEquals("Bateau BAT3", UiText.lineTitle(Line("59", "Lormont - Stalingrad", "BAT3", "boat", networkId = "bordeaux")))
        // En Île-de-France, rien ne change : le nom d'une ligne est son numéro.
        assertEquals("Métro 4", UiText.lineTitle(Line("C01374", "4", "4", "metro")))
        assertEquals("RER B", UiText.lineTitle(Line("C01743", "B", "B", "rail", network = "RER")))
        assertEquals("Bus 38", UiText.lineTitle(Line("C01075", "38", "38", "bus")))
    }

    private fun model(line: Line): fr.gotraceo.live.widget.WidgetModel {
        val stop = Stop("Halle", null, listOf("1:HALLE"), line.networkId)
        val config = WidgetConfig(1, TransportMode.BUS, line, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS)
        val state = WidgetState(NOW, NOW, emptyList(), null, dataAt = NOW, fresh = true)
        return WidgetModels.build(config, state, hasApiKey = false, refreshing = false, widthDp = 172f, heightDp = 192f, now = NOW)
    }

    /** Le rond du métro et les filets du tram sont le code de formes francilien. */
    @Test
    fun theBadgeIsGenericOutsideIleDeFrance() {
        for (mode in listOf("metro", "tram", "rail", "bus", "boat")) {
            assertEquals(BadgeShape.BUS, model(Line("A", "A", "A", mode, networkId = "ailleurs")).line?.shape)
        }
        val paris = Stop("Bastille", null, listOf("stop_area:IDFM:71634"))
        val metro = WidgetConfig(1, TransportMode.METRO, Line("C01371", "1", "1", "metro"), paris, Directions.allDirections(paris), "")
        val state = WidgetState(NOW, NOW, emptyList(), null, dataAt = NOW, fresh = true)
        assertEquals(BadgeShape.CIRCLE, WidgetModels.build(metro, state, false, false, 172f, 192f, NOW).line?.shape)
    }

    @Test
    fun aLineWithoutRealTimeSaysSoInsteadOfNoDeparture() {
        val silent = model(Line("ZI TAD", "Zone industrielle à la demande", "TAD", "bus", networkId = "tcat", realtime = false))
        assertEquals(NoticeKind.NO_DEPARTURE, silent.notice?.kind)
        assertEquals(WidgetModels.NO_REALTIME, silent.notice?.detail)

        val live = model(Line("2B", "Bréviandes - Voves", "2B", "bus", networkId = "tcat"))
        assertEquals(NoticeKind.NO_DEPARTURE, live.notice?.kind)
        assertNull(live.notice?.detail)
    }
}
