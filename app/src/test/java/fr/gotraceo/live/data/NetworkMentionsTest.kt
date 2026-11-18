package fr.gotraceo.live.data

import fr.gotraceo.live.widget.WidgetModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les mentions d'un réseau hors Île-de-France viennent du catalogue de Tracéo. L'ODbL (section 4.3)
 * demande le nom de la base avec son lien, et la licence avec le lien vers son texte.
 */
class NetworkMentionsTest {

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DATASET = "https://transport.data.gouv.fr/datasets/donnees-tcat-troyes-champagne-metropole-1"
        const val ODBL = "https://opendatacommons.org/licenses/odbl/1-0/"
    }

    private val attribution = Attribution(
        source = "TCAT, Troyes Champagne Métropole",
        url = DATASET,
        licence = "ODbL",
        licenceUrl = ODBL,
        text = "Données TCAT, sous licence ODbL, via Tracéo.",
    )

    private val tcat = Network(
        id = "tcat", name = "TCAT", city = "Troyes",
        bbox = BoundingBox(48.17, 48.41, 3.89, 4.26), modes = listOf("bus"), refreshSec = 60,
        attribution = attribution,
    )

    // --- Mention courte ------------------------------------------------------------------------

    @Test
    fun theShortMentionNamesTheSourceAndItsLicenceWithTheirLinks() {
        assertEquals(
            "Troyes : contient des informations de <a href=\"$DATASET\">TCAT, Troyes Champagne Métropole</a>, " +
                "présentement mises à disposition aux conditions de la licence <a href=\"$ODBL\">ODbL</a>.",
            NetworkMentions.short(tcat),
        )
    }

    @Test
    fun aMentionStaysHonestWhenThePartsAreMissing() {
        val noLicence = tcat.copy(attribution = attribution.copy(licence = null, licenceUrl = null))
        assertEquals(
            "Troyes : contient des informations de <a href=\"$DATASET\">TCAT, Troyes Champagne Métropole</a>, relevées par Tracéo.",
            NetworkMentions.short(noLicence),
        )
        val noLinks = tcat.copy(attribution = attribution.copy(url = null, licenceUrl = null))
        assertFalse(NetworkMentions.short(noLinks).contains("<a"))
        assertTrue(NetworkMentions.short(noLinks).contains("licence ODbL"))

        // Réseau sorti du catalogue : il reste nommé.
        assertEquals("Troyes : données du réseau TCAT, relevées par Tracéo.", NetworkMentions.short(tcat.copy(attribution = null)))
    }

    /** Le catalogue vient d'un serveur : ses textes sont affichés, jamais interprétés. */
    @Test
    fun catalogTextsAreEscapedAndOnlyWebLinksAreKept() {
        val hostile = tcat.copy(
            city = "A <b>",
            attribution = attribution.copy(
                source = "Régie \"R&D\" <script>",
                url = "javascript:alert(1)",
                licenceUrl = "https://exemple.fr/?a=1&b=\"2\"",
            ),
        )
        val mention = NetworkMentions.short(hostile)
        assertTrue(mention.startsWith("A &lt;b&gt; : "))
        assertTrue(mention.contains("Régie &quot;R&amp;D&quot; &lt;script&gt;"))
        assertFalse(mention.contains("javascript:"))
        assertFalse(mention.contains("<script>"))
        assertTrue(mention.contains("<a href=\"https://exemple.fr/?a=1&amp;b=&quot;2&quot;\">ODbL</a>"))
    }

    // --- Écran Sources et licences -------------------------------------------------------------

    @Test
    fun theSourcesScreenShowsOneEntryPerNetworkWithItsDate() {
        val paragraph = NetworkMentions.paragraph(tcat, "11 novembre 2026")
        assertEquals(
            listOf(
                "<b>Troyes · TCAT</b>",
                "Données TCAT, sous licence ODbL, via Tracéo.",
                "Contient des informations de <a href=\"$DATASET\">TCAT, Troyes Champagne Métropole</a>, " +
                    "présentement mises à disposition aux conditions de la licence <a href=\"$ODBL\">ODbL</a>.",
                "Horaires compilés par Tracéo le 11 novembre 2026.",
            ),
            paragraph.split("<br>"),
        )
        // Sans date connue, la ligne des horaires disparaît plutôt que d'en inventer une.
        assertFalse(NetworkMentions.paragraph(tcat, null).contains("Horaires"))
        assertEquals("Troyes", NetworkMentions.title(tcat.copy(name = "Troyes")))
    }

    @Test
    fun theAppSaysItDependsOnNoneOfTheNetworksItShows() {
        assertEquals("TCAT, Troyes Champagne Métropole", NetworkMentions.publisher(tcat))
        assertEquals("TCAT", NetworkMentions.publisher(tcat.copy(attribution = null)))

        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("""<string name="sources_not_affiliated_networks">"""))
        assertTrue(strings.contains("ni à Île-de-France Mobilités, ni aux réseaux dont elle affiche les données : %1\$s"))
        val code = File("src/main/java/fr/gotraceo/live/ui/SourcesActivity.kt").readText()
        assertTrue(code.contains("R.string.sources_not_affiliated_networks"))
        assertTrue(code.contains("R.id.networks_about"))
        assertTrue(code.contains("NetworkMentions.paragraph"))
        val layout = File("src/main/res/layout/activity_sources.xml").readText()
        assertTrue(layout.contains("@+id/networks_about"))
        assertTrue(layout.contains("@+id/not_affiliated"))
    }

    /** Chaque écran qui montre des données pose la mention des réseaux qu'il affiche. */
    @Test
    fun everyScreenShowingDataBindsTheMentionOfItsNetworks() {
        for (activity in listOf("MainActivity", "ConfigureActivity", "NearbyChoiceActivity")) {
            val code = File("src/main/java/fr/gotraceo/live/ui/$activity.kt").readText()
            assertTrue("Mention par réseau absente de $activity", code.contains("Mentions.bind("))
        }
        // L'Île-de-France garde sa mention écrite dans l'application.
        val glue = File("src/main/java/fr/gotraceo/live/ui/Mentions.kt").readText()
        assertTrue(glue.contains("R.string.data_mention"))
        assertTrue(glue.contains("NetworkMentions.short"))
    }

    // --- Source citée sur le widget ------------------------------------------------------------

    private fun stateAt(time: Long) = WidgetState(time, time, emptyList(), null, dataAt = time, fresh = true)

    @Test
    fun aWidgetOfAnotherNetworkNeverCitesPrim() {
        val line = Line("2B", "Bréviandes - Voves", "2B", "bus", networkId = "tcat")
        val stop = Stop("Acacias", null, listOf("1:ACACI"), "tcat")
        val config = WidgetConfig(1, TransportMode.BUS, line, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS)

        val named = WidgetModels.build(config, stateAt(NOW), false, false, 348f, 192f, NOW, networkName = "TCAT")
        assertEquals("TCAT via Tracéo", named.sourceDetail)
        assertEquals("Tracéo", named.sourceLabel)
        assertNull(named.liveSource)

        // Catalogue perdu : la source reste Tracéo, sans prêter les données à PRIM.
        val unnamed = WidgetModels.build(config, stateAt(NOW), true, false, 348f, 192f, NOW)
        assertEquals("Tracéo", unnamed.sourceDetail)

        val entry = ProximityEntry(NearbyLine(line, "Acacias", null, "1:ACACI", 80), NearbyReason.NEAREST, false, emptyList())
        val proximity = WidgetModels.buildProximity(
            config, ProximityState(NOW, NOW, listOf(entry), dataAt = NOW, fresh = true), false, 348f, 192f, NOW,
            night = true, networkName = "TCAT",
        )
        assertEquals("TCAT via Tracéo", proximity.sourceDetail)
    }

    @Test
    fun aWidgetOfIleDeFranceStillCitesPrimThroughTraceo() {
        val stop = Stop("Bastille", null, listOf("stop_area:IDFM:71634"))
        val config = WidgetConfig(1, TransportMode.METRO, Line("C01371", "1", "1", "metro"), stop, Directions.allDirections(stop), "")
        val model = WidgetModels.build(config, stateAt(NOW), false, false, 348f, 192f, NOW, networkName = "ignoré")
        assertEquals(TraceoClient.SOURCE_DETAIL, model.sourceDetail)
        assertEquals("PRIM via Tracéo", model.sourceDetail)
    }
}
