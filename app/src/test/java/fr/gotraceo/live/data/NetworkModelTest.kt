package fr.gotraceo.live.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le réseau dans le modèle. Tout ce qui a été enregistré avant l'arrivée des autres réseaux est
 * francilien, et doit se relire puis se réécrire sans changer.
 */
class NetworkModelTest {

    private companion object {
        const val TCAT = "tcat"

        /** Widget posé sans clé par la 2.0.x : une zone d'arrêt de Tracéo, tous les sens. */
        const val SAVED_WITHOUT_KEY = """{"widgetId":12,"mode":"METRO",
            "line":{"id":"C01371","name":"1","shortName":"1","mode":"metro","network":"Métro","operator":"RATP",
                    "color":"FFCD00","textColor":"000000"},
            "stop":{"name":"Bastille","stopIds":["stop_area:IDFM:71634"]},
            "directions":{"stop_area:IDFM:71634":[]},
            "directionLabel":"Tous les sens","theme":"LINE","auto":false}"""

        /** Widget posé avec une clé PRIM : deux quais du référentiel IDFM, un sens retenu. */
        const val SAVED_WITH_KEY = """{"widgetId":7,"mode":"BUS",
            "line":{"id":"C01124","name":"96","shortName":"96","mode":"bus","subMode":"localBus","network":"RATP",
                    "operator":"RATP","color":"00643C","textColor":"FFFFFF"},
            "stop":{"name":"Hôtel de Ville","town":"Paris","stopIds":["IDFM:463158","IDFM:22092"]},
            "directions":{"IDFM:463158":["Porte des Lilas"]},
            "directionLabel":"→ Porte des Lilas","theme":"DARK","auto":false}"""

        /** Widget automatique : sa ligne et son arrêt suivent la position. */
        const val SAVED_AUTO = """{"widgetId":3,"mode":"RER",
            "line":{"id":"C01742","name":"A","shortName":"A","mode":"rail","network":"RER","operator":"SNCF",
                    "color":"EB2132","textColor":"FFFFFF"},
            "stop":{"name":"Auber","town":"Paris","stopIds":["stop_area:IDFM:73650"]},
            "directions":{"stop_area:IDFM:73650":[]},
            "directionLabel":"Tous les sens","theme":"AUTO","auto":true}"""
    }

    /** Le contenu d'un enregistrement, sans égard pour l'ordre de ses champs. */
    private fun content(json: String): Any? = plain(JSONObject(json))

    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }

    private val line2B = Line("2B", "Bréviandes - Voves", "2B", "bus", color = "F29400", textColor = "FFFFFF", networkId = TCAT)
    private val acacias = Stop("Acacias", null, listOf("1:ACACI"), TCAT)

    // --- Configurations enregistrées avant les réseaux -----------------------------------------

    @Test
    fun aWidgetSavedBeforeNetworksIsInIleDeFrance() {
        for (saved in listOf(SAVED_WITHOUT_KEY, SAVED_WITH_KEY, SAVED_AUTO)) {
            val config = WidgetConfig.fromJson(JSONObject(saved))
            assertEquals(Networks.IDFM, config.networkId)
            assertEquals(Networks.IDFM, config.line.networkId)
            assertEquals(Networks.IDFM, config.stop.networkId)
        }
    }

    @Test
    fun aWidgetSavedBeforeNetworksIsReadAsBefore() {
        val withoutKey = WidgetConfig.fromJson(JSONObject(SAVED_WITHOUT_KEY))
        assertEquals(
            WidgetConfig(
                widgetId = 12,
                mode = TransportMode.METRO,
                line = Line("C01371", "1", "1", "metro", network = "Métro", operator = "RATP", color = "FFCD00", textColor = "000000"),
                stop = Stop("Bastille", null, listOf("stop_area:IDFM:71634")),
                directions = mapOf("stop_area:IDFM:71634" to emptySet()),
                directionLabel = "Tous les sens",
                theme = WidgetTheme.LINE,
            ),
            withoutKey,
        )

        val withKey = WidgetConfig.fromJson(JSONObject(SAVED_WITH_KEY))
        assertEquals(listOf("IDFM:463158", "IDFM:22092"), withKey.stop.stopIds)
        assertEquals(mapOf("IDFM:463158" to setOf("Porte des Lilas")), withKey.directions)
        assertEquals("localBus", withKey.line.subMode)

        val auto = WidgetConfig.fromJson(JSONObject(SAVED_AUTO))
        assertTrue(auto.auto)
        assertTrue(auto.line.isRer())
    }

    /** Réenregistré, un widget francilien garde exactement la forme qu'il avait : rien n'y parle de réseau. */
    @Test
    fun aWidgetSavedBeforeNetworksIsWrittenBackUnchanged() {
        for (saved in listOf(SAVED_WITHOUT_KEY, SAVED_WITH_KEY, SAVED_AUTO)) {
            val after = WidgetConfig.fromJson(JSONObject(saved)).toJson().toString()
            assertEquals(content(saved), content(after))
            assertFalse(after.contains("networkId"))
        }
    }

    @Test
    fun aProximityStateSavedBeforeNetworksKeepsItsLinesInIleDeFrance() {
        val saved = """{"fetchedAt":5,"attemptedAt":6,"entries":[{
            "line":{"id":"C01124","name":"96","shortName":"96","mode":"bus"},
            "stop":"Hôtel de Ville","town":"Paris","zone":"71217","distance":33,
            "reason":"NEAREST","favorite":false,"departures":[]}]}"""
        val state = ProximityState.fromJson(JSONObject(saved))
        val nearby = state.entries.single().nearby
        assertEquals(Networks.IDFM, nearby.line.networkId)
        assertEquals("stop_area:IDFM:71217", nearby.stopId)
        assertEquals(content(saved), content(state.toJson().toString()))
    }

    // --- Un autre réseau -----------------------------------------------------------------------

    @Test
    fun aWidgetOfAnotherNetworkKeepsItsNetwork() {
        val config = WidgetConfig(
            widgetId = 20,
            mode = TransportMode.BUS,
            line = line2B,
            stop = acacias,
            directions = Directions.allDirections(acacias),
            directionLabel = Directions.ALL_DIRECTIONS,
        )
        val read = WidgetConfig.fromJson(JSONObject(config.toJson().toString()))
        assertEquals(config, read)
        assertEquals(TCAT, read.networkId)
        assertEquals(TCAT, read.line.networkId)
        assertEquals(TCAT, read.stop.networkId)
        assertEquals(listOf("1:ACACI"), read.stop.stopIds)
    }

    @Test
    fun lineAndStopCarryTheirNetworkThroughJson() {
        assertEquals(line2B, Line.fromJson(JSONObject(line2B.toJson().toString())))
        assertEquals(acacias, Stop.fromJson(JSONObject(acacias.toJson().toString())))
    }

    @Test
    fun theSameIdentifierInTwoNetworksIsNotTheSameLine() {
        val paris1 = Line("1", "1", "1", "metro")
        val troyes1 = Line("1", "1", "1", "bus", networkId = TCAT)
        assertNotEquals(paris1, troyes1)
        assertNotEquals(paris1.key, troyes1.key)
        // En Île-de-France, la clé reste l'identifiant seul : les favoris déjà enregistrés tiennent.
        assertEquals("1", paris1.key)
        assertEquals("C01742", Line("C01742", "A", "A", "rail").key)
    }

    @Test
    fun rerExistsOnlyInIleDeFrance() {
        assertTrue(Line("C01742", "A", "A", "rail").isRer())
        assertFalse(Line("A", "A", "A", "rail", networkId = "ailleurs").isRer())
        assertFalse(Line("X", "RER B", "B", "rail", network = "RER", networkId = "ailleurs").isRer())
    }

    // --- Arrêts proches ------------------------------------------------------------------------

    @Test
    fun aNearbyStopIsAStationOfItsNetwork() {
        val paris = NearbyLine(Line("C01124", "96", "96", "bus"), "Hôtel de Ville", "Paris", "71217", 33)
        assertEquals("stop_area:IDFM:71217", paris.stopId)
        assertEquals(Stop("Hôtel de Ville", "Paris", listOf("stop_area:IDFM:71217")), paris.toStop())

        val troyes = NearbyLine(line2B, "Acacias", null, "1:ACACI", 80)
        assertEquals("1:ACACI", troyes.stopId)
        assertEquals(acacias, troyes.toStop())
    }

    // --- Favoris et lieux mémorisés ------------------------------------------------------------

    private val allModes = setOf("metro", "rail", "tram", "bus")

    @Test
    fun aFavoriteSavedBeforeNetworksStillCounts() {
        val rerA = NearbyLine(Line("C01742", "A", "A", "rail"), "Auber", "Paris", "73650", 300)
        val ranked = NearbyRules.rank(listOf(rerA), favorites = setOf("C01742"), pickedLineKey = null, nightService = false, modes = allModes)
        assertEquals(NearbyReason.FAVORITE, ranked.list.single().reason)
    }

    @Test
    fun aFavoriteBelongsToItsNetwork() {
        val troyes1 = NearbyLine(Line("1", "1", "1", "bus", networkId = TCAT), "Halle", null, "1:HALLE", 100)
        // La ligne 1 favorite à Paris ne fait pas un favori de la ligne 1 de Troyes, et inversement.
        val withParisFavorite = NearbyRules.rank(listOf(troyes1), setOf("1"), null, nightService = false, modes = allModes)
        assertFalse(withParisFavorite.list.single().favorite)
        val withTroyesFavorite = NearbyRules.rank(listOf(troyes1), setOf(troyes1.line.key), null, nightService = false, modes = allModes)
        assertTrue(withTroyesFavorite.list.single().favorite)

        val paris1 = NearbyLine(Line("1", "1", "1", "metro"), "Bastille", "Paris", "71634", 100)
        val inParis = NearbyRules.rank(listOf(paris1), setOf(troyes1.line.key), null, nightService = false, modes = allModes)
        assertFalse(inParis.list.single().favorite)
    }

    @Test
    fun aPlaceSavedBeforeNetworksStillNamesItsLine() {
        val saved = """[{"lat":48.8566,"lon":2.3522,"line":"C01124","at":5}]"""
        val choices = PlaceChoices.fromJson(saved)
        val here = GeoPoint(48.8566, 2.3522)
        assertEquals(Line("C01124", "96", "96", "bus").key, choices.lineAt(here))
        assertEquals(plain(JSONArray(saved)), plain(JSONArray(choices.toJson())))
    }

    @Test
    fun aPlaceRemembersTheLineOfItsNetwork() {
        val troyes = GeoPoint(48.2973, 4.0744)
        val choices = PlaceChoices.fromJson(PlaceChoices().remember(troyes, line2B.key, 9L).toJson())
        assertEquals(line2B.key, choices.lineAt(troyes))
        assertNull(choices.lineAt(GeoPoint(48.8566, 2.3522)))

        val picked = NearbyLine(line2B, "Acacias", null, "1:ACACI", 900)
        val other = NearbyLine(Line("1", "1", "1", "bus", networkId = TCAT), "Halle", null, "1:HALLE", 50)
        val ranked = NearbyRules.rank(listOf(other, picked), emptySet(), choices.lineAt(troyes), nightService = false, modes = allModes)
        assertEquals(NearbyReason.PICKED_HERE, ranked.list.first().reason)
        assertEquals(line2B, ranked.list.first().nearby.line)
    }
}
