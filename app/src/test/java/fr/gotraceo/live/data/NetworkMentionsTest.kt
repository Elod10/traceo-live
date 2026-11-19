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
        const val OPEN_LICENCE = "https://www.etalab.gouv.fr/licence-ouverte-open-licence/"
        const val OTHER_DATASET = "https://transport.data.gouv.fr/datasets/exemple"
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
        assertTrue(glue.contains("NetworkMentions.others"))
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

    // --- Plusieurs réseaux, plusieurs licences -------------------------------------------------

    /** Un réseau sous Licence Ouverte, tel que le catalogue le décrit ; son identifiant est inventé. */
    private fun open(index: Int, city: String, name: String, licence: String? = "Licence Ouverte 2.0") = Network(
        id = "r$index", name = name, city = city,
        bbox = BoundingBox(40.0, 41.0, 1.0, 2.0), modes = listOf("bus"), refreshSec = 60,
        attribution = Attribution(
            source = "$name, métropole de $city", url = OTHER_DATASET, licence = licence,
            licenceUrl = OPEN_LICENCE.takeIf { licence != null },
            text = "Contient des informations du jeu « Réseau $name » ($name, métropole de $city)" +
                (licence?.let { ", mises à disposition sous $it." } ?: "."),
        ),
    )

    /** « Licence Ouverte » porte déjà le mot : la mention ne dit pas « la licence Licence Ouverte ». */
    @Test
    fun theOpenLicenceIsNamedWithoutRepeatingTheWord() {
        val mention = NetworkMentions.short(open(1, "Bordeaux", "TBM"))
        assertTrue(mention.endsWith("aux conditions de la <a href=\"$OPEN_LICENCE\">Licence Ouverte 2.0</a>."))
        assertFalse(mention.contains("licence Licence", ignoreCase = true))
        assertTrue(NetworkMentions.short(open(1, "Bordeaux", "TBM", licence = "licence ouverte")).contains("de la <a"))
        // L'ODbL garde sa formule, celle que demande sa section 4.3.
        assertTrue(NetworkMentions.short(tcat).contains("aux conditions de la licence <a href=\"$ODBL\">ODbL</a>."))
    }

    /** Quand Tracéo sert la mention complète, l'écran des sources ne la redit pas : il y ajoute les liens. */
    @Test
    fun theFullMentionOfTheCatalogIsNotSaidTwice() {
        val bordeaux = open(1, "Bordeaux", "TBM")
        assertEquals(
            listOf(
                "<b>Bordeaux · TBM</b>",
                "Contient des informations du jeu « Réseau TBM » (TBM, métropole de Bordeaux), mises à disposition sous Licence Ouverte 2.0.",
                "Source : <a href=\"$OTHER_DATASET\">TBM, métropole de Bordeaux</a>. Licence : <a href=\"$OPEN_LICENCE\">Licence Ouverte 2.0</a>.",
                "Horaires compilés par Tracéo le 18 novembre 2026.",
            ),
            NetworkMentions.paragraph(bordeaux, "18 novembre 2026").split("<br>"),
        )
        // Sans licence annoncée, la source reste citée avec son lien.
        val unlicensed = open(2, "Nîmes", "Tango", licence = null)
        assertTrue(NetworkMentions.paragraph(unlicensed, null).endsWith("Source : <a href=\"$OTHER_DATASET\">Tango, métropole de Nîmes</a>."))
    }

    /** Des widgets dans plusieurs villes : une phrase pour tous, au lieu d'une mention entière par réseau. */
    @Test
    fun severalNetworksShareOneSentence() {
        val two = NetworkMentions.compact(listOf(open(1, "Bordeaux", "TBM"), tcat))
        assertEquals(
            "Autres réseaux : contient des informations des réseaux <a href=\"$OTHER_DATASET\">TBM</a> " +
                "(<a href=\"$OPEN_LICENCE\">Licence Ouverte 2.0</a>) et <a href=\"$DATASET\">TCAT</a> (<a href=\"$ODBL\">ODbL</a>), " +
                "relevées par Tracéo. Détail dans Sources et licences.",
            two,
        )
        val three = NetworkMentions.compact(listOf(open(1, "Bordeaux", "TBM"), open(2, "Nice", "Lignes d'Azur"), tcat))
        assertTrue(three.contains("</a>), <a"))
        assertTrue(three.contains(") et <a href=\"$DATASET\">TCAT</a>"))
        assertEquals("", NetworkMentions.compact(emptyList()))
        // Sans widget en Île-de-France, rien ne précède : la phrase ne parle pas d'« autres » réseaux.
        assertTrue(NetworkMentions.compact(listOf(open(1, "Bordeaux", "TBM"), tcat), afterIdf = false).startsWith("Contient des informations des réseaux <a"))
        // Aucun « de » devant un nom : « Ametis », « Irigo » ou « Le Met' » s'y lisent tels quels.
        val vowels = NetworkMentions.compact(listOf(open(1, "Amiens", "Ametis"), open(2, "Angers", "Irigo"), open(3, "Metz", "Le Met'")))
        assertFalse(Regex("\\bde <a").containsMatchIn(vowels))
    }

    /** La mention entière d'un réseau s'accorde avec le nom de sa source et de sa licence. */
    @Test
    fun theSentenceOfANetworkIsWrittenInProperFrench() {
        assertTrue(NetworkMentions.short(open(1, "Amiens", "Ametis")).contains("des informations d'<a href=\"$OTHER_DATASET\">Ametis, métropole de Amiens</a>"))
        assertTrue(NetworkMentions.short(open(2, "Angers", "Irigo")).contains("des informations d'<a"))
        assertTrue(NetworkMentions.short(open(3, "Bordeaux", "TBM")).contains("des informations de <a"))
        // Le mot « licence » où qu'il soit dans le nom, en français ou en anglais, n'est pas répété.
        assertTrue(NetworkMentions.short(open(4, "X", "Y", licence = "Open Licence")).contains("aux conditions de l'<a"))
        assertTrue(NetworkMentions.short(open(4, "X", "Y", licence = "Open Database License")).contains("aux conditions de l'<a"))
        assertTrue(NetworkMentions.short(open(4, "X", "Y", licence = "CC BY 4.0")).contains("aux conditions de la licence <a"))
    }

    /** Ce que chaque écran pose en bas : rien, la mention entière d'un réseau, ou la phrase commune, par ville. */
    @Test
    fun theMentionOfTheOtherNetworksDependsOnHowManyThereAre() {
        assertNull(NetworkMentions.others(emptyList(), afterIdf = true))
        assertNull("L'Île-de-France a sa propre mention", NetworkMentions.others(listOf(Networks.ILE_DE_FRANCE), afterIdf = true))
        // Un seul : sa mention entière, comme au pilote, qu'un widget francilien existe ou non.
        assertEquals(NetworkMentions.short(tcat), NetworkMentions.others(listOf(tcat), afterIdf = true))
        assertEquals(NetworkMentions.short(tcat), NetworkMentions.others(listOf(tcat, tcat, Networks.ILE_DE_FRANCE), afterIdf = false))
        // Plusieurs : la phrase commune, par ville quel que soit l'ordre des widgets.
        val bordeaux = open(1, "Bordeaux", "TBM")
        val several = NetworkMentions.others(listOf(tcat, bordeaux), afterIdf = true)!!
        assertEquals(NetworkMentions.compact(listOf(bordeaux, tcat)), several)
        assertTrue(several.indexOf("TBM") < several.indexOf("TCAT"))
        assertTrue(NetworkMentions.others(listOf(tcat, bordeaux), afterIdf = false)!!.startsWith("Contient"))
    }

    @Test
    fun beyondAFewNetworksTheSentencePointsToTheSourcesScreen() {
        val many = (1..7).map { open(it, "Ville $it", "Réseau $it") }
        val mention = NetworkMentions.compact(many)
        assertEquals(NetworkMentions.MAX_NAMED, Regex("Réseau \\d").findAll(mention).count())
        assertTrue(mention.contains(" et 3 autres, relevées par Tracéo."))
        assertTrue(mention.endsWith("Détail dans Sources et licences."))
        // Un seul réseau de plus que la limite est nommé : le compter ne gagnerait pas de place.
        val five = NetworkMentions.compact(many.take(5))
        assertEquals(5, Regex("Réseau \\d").findAll(five).count())
        assertFalse(five.contains("autres,"))
        // Sept réseaux en une phrase prennent moins de place à l'écran que deux mentions entières.
        fun shown(html: String) = html.replace(Regex("<[^>]+>"), "").length
        assertTrue(shown(mention) < shown(NetworkMentions.short(many[0])) + shown(NetworkMentions.short(many[1])))
    }

    /** Un réseau sorti du catalogue, ou sans licence, reste nommé ; ses textes ne sont jamais interprétés. */
    @Test
    fun theSentenceStaysHonestAndSafe() {
        val gone = tcat.copy(attribution = null)
        val hostile = open(3, "X", "Régie <b>\"R&D\"</b>").let { it.copy(attribution = it.attribution!!.copy(url = "javascript:alert(1)")) }
        val mention = NetworkMentions.compact(listOf(gone, hostile, open(4, "Y", "Sans licence", licence = null)))
        assertTrue(mention.startsWith("Autres réseaux : contient des informations des réseaux TCAT, Régie &lt;b&gt;&quot;R&amp;D&quot;&lt;/b&gt; ("))
        assertFalse(mention.contains("javascript:"))
        assertTrue(mention.contains(" et <a href=\"$OTHER_DATASET\">Sans licence</a>, relevées par Tracéo."))
    }

    /** L'écran d'accueil pose la mention condensée dès que les widgets couvrent plusieurs autres réseaux. */
    @Test
    fun theScreensUseTheSharedSentence() {
        val code = File("src/main/java/fr/gotraceo/live/ui/Mentions.kt").readText()
        assertTrue(code.contains("NetworkMentions.others(others, afterIdf = idfm)"))
        assertTrue(File("src/main/java/fr/gotraceo/live/ui/SourcesActivity.kt").readText().contains("sortedWith(NetworkChoices.byCity)"))
        // Un widget À proximité compte pour le réseau dont il montre les lignes.
        assertTrue(File("src/main/java/fr/gotraceo/live/widget/Sources.kt").readText().contains("prefs.proximity(id)?.entries"))
    }
}
