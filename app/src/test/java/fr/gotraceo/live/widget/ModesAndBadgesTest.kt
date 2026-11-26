package fr.gotraceo.live.widget

import fr.gotraceo.live.data.BoundingBox
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.Directions
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.Network
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.data.Stop
import fr.gotraceo.live.data.TransportMode
import fr.gotraceo.live.data.WidgetConfig
import fr.gotraceo.live.data.WidgetState
import fr.gotraceo.live.data.WidgetTheme
import fr.gotraceo.live.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les modes et les pastilles hors Île-de-France : tram, métro, bateau, câble et funiculaire, codes de
 * ligne longs, lignes sans couleur, couleurs de texte illisibles. Les identifiants de réseau sont
 * inventés : l'application n'en écrit aucun.
 */
class ModesAndBadgesTest {

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ELSEWHERE = "ville-a"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF15171C.toInt()

        /** Les couleurs de mode : bus vert, tram bleu-vert, métro bleu, train violet, bateau bleu, autres gris-bleu. */
        val MODE_COLOURS = mapOf(
            "bus" to 0xFF2E7D32.toInt(), "tram" to 0xFF0B7A75.toInt(), "metro" to 0xFF003CA6.toInt(),
            "rail" to 0xFF7A2E8C.toInt(), "boat" to 0xFF00639B.toInt(),
            "cableway" to 0xFF546E7A.toInt(), "funicular" to 0xFF546E7A.toInt(),
        )
    }

    private fun network(vararg modes: String) =
        Network(ELSEWHERE, "Réseau A", "Ville A", BoundingBox(1.0, 2.0, 3.0, 4.0), modes.toList(), refreshSec = 60)

    private fun line(code: String, mode: String = "bus", color: String? = "E20019", textColor: String? = "FFFFFF", networkId: String = ELSEWHERE) =
        Line(id = "id-$code", name = "Parcours de $code", shortName = code, mode = mode, color = color, textColor = textColor, networkId = networkId)

    private fun model(line: Line, theme: WidgetTheme = WidgetTheme.AUTO): WidgetModel {
        val stop = Stop("Gare", null, listOf("zone-1"), line.networkId)
        val config = WidgetConfig(1, TransportMode.BUS, line, stop, Directions.allDirections(stop), Directions.ALL_DIRECTIONS, theme)
        val state = WidgetState(NOW, NOW, emptyList(), null, dataAt = NOW, fresh = true)
        return WidgetModels.build(config, state, hasApiKey = false, refreshing = false, widthDp = 172f, heightDp = 192f, now = NOW)
    }

    // --- Étape du mode -------------------------------------------------------------------------

    @Test
    fun theModesOfANetworkFollowTheOrderOfTheMenu() {
        // L'ordre du catalogue ne compte pas.
        assertEquals(
            listOf(TransportMode.TRAM, TransportMode.BUS, TransportMode.BOAT),
            DataSources.modes(network("boat", "bus", "tram"), hasKey = false),
        )
        assertEquals(
            listOf(TransportMode.METRO, TransportMode.BUS, TransportMode.BOAT),
            DataSources.modes(network("bus", "metro", "boat"), hasKey = true),
        )
        assertEquals(
            listOf(TransportMode.TRAM, TransportMode.BUS, TransportMode.OTHER),
            DataSources.modes(network("bus", "tram", "cableway"), hasKey = false),
        )
        assertFalse(TransportMode.RER in TransportMode.ELSEWHERE)
    }

    /** Sans modes annoncés, tous sont proposés : mieux vaut un mode vide qu'une ligne introuvable. */
    @Test
    fun aNetworkThatDoesNotSayItsModesOffersThemAll() {
        assertEquals(TransportMode.ELSEWHERE, DataSources.modes(network(), hasKey = false))
        assertNull(DataSources.onlyMode(network(), hasKey = false))
    }

    @Test
    fun theLabelsOfTheModesOutsideIleDeFrance() {
        val all = network("metro", "tram", "rail", "bus", "boat", "cableway", "funicular")
        assertEquals(
            listOf("Métro", "Tramway", "Train", "Bus", "Bateau", "Téléphérique / Funiculaire"),
            DataSources.modes(all, hasKey = false).map { it.labelIn(all) },
        )
        // Le libellé ne nomme que ce que le réseau a.
        assertEquals("Téléphérique", TransportMode.OTHER.labelIn(network("bus", "tram", "cableway")))
        assertEquals("Funiculaire", TransportMode.OTHER.labelIn(network("bus", "funicular")))
        // Un réseau qui n'annonce aucun mode se voit proposer les deux : le libellé ne tranche pas.
        assertEquals("Téléphérique / Funiculaire", TransportMode.OTHER.labelIn(network()))
        assertEquals("Funi", UiText.modeChip(TransportMode.OTHER, network("bus", "funicular")))
        assertEquals("Câble", UiText.modeChip(TransportMode.OTHER, network("bus", "cableway")))
        assertEquals("Bateau", UiText.modeChip(TransportMode.BOAT, all))
        // Ces modes se présentent seuls : leurs exemples sont franciliens.
        assertTrue(DataSources.modes(all, hasKey = false).all { UiText.modeSubtitle(it, all.id) == null })
    }

    @Test
    fun ileDeFranceKeepsItsLabels() {
        val idf = Networks.ILE_DE_FRANCE
        assertEquals(
            listOf("Métro", "RER", "Train / Transilien", "Tramway", "Bus / Noctilien", "Câble / Funiculaire"),
            DataSources.modes(idf, hasKey = true).map { it.labelIn(idf) },
        )
        assertEquals("Câble", UiText.modeChip(TransportMode.OTHER, idf))
        assertEquals("Câble", UiText.modeChip(TransportMode.OTHER))
        assertEquals("Câble C1", UiText.lineTitle(Line("C02528", "C1", "C1", "cableway")))
    }

    // --- Titre d'une ligne ---------------------------------------------------------------------

    @Test
    fun aLineIsTitledByItsModeAndItsCode() {
        assertEquals("Tram B", UiText.lineTitle(line("B", "tram")))
        assertEquals("Métro A", UiText.lineTitle(line("A", "metro")))
        assertEquals("Bateau BAT3", UiText.lineTitle(line("BAT3", "boat")))
        assertEquals("Téléphérique C", UiText.lineTitle(line("C", "cableway")))
        assertEquals("Funiculaire F", UiText.lineTitle(line("F", "funicular")))
        // Une ligne ferrée « A » d'un autre réseau n'est pas un RER.
        assertEquals("Train A", UiText.lineTitle(line("A", "rail")))
    }

    @Test
    fun aLongCodeIsItsOwnTitle() {
        assertEquals("Flexo 51", UiText.lineTitle(line("Flexo 51")))
        assertEquals("Navette Gare", UiText.lineTitle(line("Navette Gare")))
        // Le code dit déjà son mode : il n'est pas répété.
        assertEquals("Tram 1", UiText.lineTitle(line("Tram 1", "tram")))
        assertEquals("Bus 104", UiText.lineTitle(line("104")))
    }

    // --- Codes longs dans la pastille ----------------------------------------------------------

    @Test
    fun aCodeTooLongForTheBadgeIsCutVisibly() {
        assertEquals("2B", BadgeLabels.label("2B"))
        assertEquals("Chrono3", BadgeLabels.label(" Chrono3 "))
        assertEquals("Ligne A", BadgeLabels.label("Ligne A"))
        // Sans ses espaces, le code tient : il n'est pas coupé.
        assertEquals("Flexo51", BadgeLabels.label("Flexo 51"))
        assertEquals("Lianes1", BadgeLabels.label("Lianes 1"))
        // Sinon il est coupé au milieu : sa fin, qui distingue les lignes d'une famille, reste.
        assertEquals("Nav…are", BadgeLabels.label("Navette Gare"))
        assertEquals("Tra…350", BadgeLabels.label("TransIsère 7350"))
        assertTrue(listOf("Flexo 51", "Navette Gare", "TransIsère 7350").all { BadgeLabels.label(it).length <= BadgeLabels.MAX_LENGTH })
    }

    /** Les lignes d'une même famille gardent des pastilles différentes. */
    @Test
    fun theLinesOfAFamilyKeepDifferentBadges() {
        for (family in listOf(listOf("Flexo 51", "Flexo 52", "Flexo 53"), listOf("Navette 12", "Navette 14"), (1..9).map { "Lianes $it" })) {
            assertEquals(family.toString(), family.size, family.map(BadgeLabels::label).toSet().size)
        }
    }

    /** La pastille des listes suit la même coupe ; en Île-de-France, elle garde la sienne. */
    @Test
    fun theBadgeOfTheListsUsesTheSameLabel() {
        assertEquals("Flexo51" to 0.46f, BadgeLabels.forList(line("Flexo 51")))
        assertEquals("BAT3" to 0.54f, BadgeLabels.forList(line("BAT3", "boat")))
        assertEquals("2B" to 0.64f, BadgeLabels.forList(line("2B")))
        assertEquals("Navette" to 0.54f, BadgeLabels.forList(Line("C09999", "Navette Gare", "Navette Gare", "bus")))
        assertEquals("N122" to 0.54f, BadgeLabels.forList(Line("C01400", "N122", "N122", "bus")))
    }

    @Test
    fun aLongCodeIsWrittenSmaller() {
        assertEquals(1f, BadgeLabels.textScale("2B"))
        assertEquals(1f, BadgeLabels.textScale("104"))
        assertTrue(BadgeLabels.textScale("BAT3") < 1f)
        assertTrue(BadgeLabels.textScale("Chrono3") < BadgeLabels.textScale("BAT3"))
    }

    @Test
    fun theBadgeOfAWidgetCarriesTheCutCodeAndReadsTheWholeOne() {
        val badge = model(line("Navette Gare")).line!!
        assertEquals("Nav…are", badge.label)
        assertEquals("Navette Gare", badge.code)
        assertEquals(BadgeShape.BUS, badge.shape)
        assertTrue(badge.textScale < 1f)
        assertTrue(WidgetContent.describe(model(line("Navette Gare"))).contains("Ligne Navette Gare"))

        val short = model(line("2B")).line!!
        assertEquals(LineBadge("2B", BadgeShape.BUS), short)
    }

    @Test
    fun ileDeFranceBadgesDoNotChange() {
        val orlyval = model(Line("C01388", "Orlyval", "Orlyval", "metro")).line!!
        assertEquals(LineBadge("Orlyval", BadgeShape.CIRCLE), orlyval)
        val long = model(Line("C09999", "Navette Gare", "Navette Gare", "bus")).line!!
        assertEquals("Navette", long.label)
        assertEquals(1f, long.textScale)
    }

    // --- Ligne sans couleur --------------------------------------------------------------------

    @Test
    fun aLineWithoutColourTakesTheColourOfItsMode() {
        for (mode in listOf("bus", "tram", "metro", "rail", "boat", "cableway", "funicular")) {
            val (background, text) = Palettes.lineColors(line("1", mode, color = null, textColor = null))
            assertEquals(mode, MODE_COLOURS.getValue(mode), background)
            assertEquals("Le blanc se lit sur la couleur du mode $mode", WHITE, text)
            assertTrue(Palettes.contrast(background, text) >= 4.5)
        }
        assertNotEquals(Palettes.modeColor("bus"), Palettes.modeColor("tram"))
        assertNotEquals(Palettes.modeColor("bus"), Palettes.modeColor("boat"))
    }

    /** Avant de servir `null`, Tracéo sert le blanc et le noir par défaut du GTFS : c'est la même absence. */
    @Test
    fun whiteWithBlackTextIsNoColourOutsideIleDeFrance() {
        val served = line("31", "bus", color = "FFFFFF", textColor = "000000")
        assertNull(Palettes.publishedColor(served))
        assertEquals(Palettes.modeColor("bus") to WHITE, Palettes.lineColors(served))
        assertNull(Palettes.publishedColor(line("31", "bus", color = "#ffffff", textColor = null)))
        // Avant comme après, le résultat est le même.
        assertEquals(Palettes.lineColors(line("31", "bus", color = null, textColor = null)), Palettes.lineColors(served))
    }

    @Test
    fun aPublishedColourIsKeptEvenLight() {
        // Un blanc voulu par le réseau porte un texte de couleur : il est gardé.
        val white = line("L", "tram", color = "FFFFFF", textColor = "0055A4")
        assertEquals(WHITE, Palettes.publishedColor(white))
        assertEquals(WHITE to 0xFF0055A4.toInt(), Palettes.lineColors(white))
        // Un jaune clair reste jaune.
        assertEquals(0xFFFFDD00.toInt(), Palettes.lineColors(line("J", color = "FFDD00", textColor = "000000")).first)
        // En Île-de-France, la couleur du référentiel est toujours prise telle quelle.
        val idfWhite = Line("C00001", "X", "X", "bus", color = "FFFFFF", textColor = "000000")
        assertEquals(WHITE, Palettes.publishedColor(idfWhite))
    }

    @Test
    fun theLineThemeOfALineWithoutColourUsesItsMode() {
        val palette = model(line("7", "tram", color = null, textColor = null), WidgetTheme.LINE).palette
        assertTrue(palette.isLine)
        assertEquals(Palettes.modeColor("tram"), palette.background)
        assertTrue(Palettes.contrast(palette.background, palette.text) >= 4.5)
    }

    // --- Texte illisible sur la couleur publiée ------------------------------------------------

    @Test
    fun anUnreadableTextColourIsReplaced() {
        // Blanc sur jaune, bleu nuit sur bleu nuit, et le texte de la couleur du fond.
        val cases = listOf("FFDD00" to "FFFFFF", "0A1F44" to "0A1F55", "E20019" to "E20019", "8BC34A" to "FFFFFF")
        for ((color, textColor) in cases) {
            val (background, text) = Palettes.lineColors(line("9", color = color, textColor = textColor))
            assertEquals(Palettes.parseColor(color), background)
            // Remplacé par le plus lisible du noir et du blanc.
            assertTrue("$textColor sur $color", text == WHITE || text == INK)
            assertTrue("$textColor sur $color", Palettes.contrast(background, text) >= Palettes.contrast(background, Palettes.parseColor(textColor)!!))
            assertTrue("$textColor sur $color", Palettes.contrast(background, text) >= 4.0)
        }
        // Un texte lisible est celui du réseau : il suffit qu'il se lise en gros, dans une pastille.
        assertEquals(0xFF0055A4.toInt(), Palettes.lineColors(line("9", color = "FFFFFF", textColor = "0055A4")).second)
        assertEquals(WHITE, Palettes.lineColors(line("9", color = "FF0000", textColor = "FFFFFF")).second)
        // Une couleur de texte illisible au sens propre (pas une couleur) est ignorée.
        assertEquals(WHITE, Palettes.lineColors(line("9", color = "E20019", textColor = "pas une couleur")).second)
    }

    /**
     * Tracéo sert `null` quand le réseau publie la couleur d'une ligne sans sa couleur de texte
     * (Saint-Étienne, Dijon) : l'application choisit alors le plus lisible du noir et du blanc. Un
     * noir servi est un choix du réseau : il est gardé là où il se lit, comme en Île-de-France.
     */
    @Test
    fun anUnpublishedTextColourIsChosenAndAPublishedBlackIsKept() {
        for (color in listOf("0066CC", "008000", "E2001A", "7B4397")) {
            val unpublished = line("9", color = color, textColor = null)
            assertNull(color, Palettes.publishedTextColor(unpublished))
            assertEquals(color, WHITE, Palettes.lineColors(unpublished).second)
            // Le fond du thème « couleur de la ligne » reste la couleur de la ligne, sans être éclairci.
            assertEquals(color, Palettes.parseColor(color), model(unpublished, WidgetTheme.LINE).palette.background)
        }
        assertEquals(INK, Palettes.lineColors(line("9", color = "FFDD00", textColor = null)).second)
        // Le noir publié sur du jaune, comme à Brest ou à Troyes, est gardé tel quel.
        val black = line("9", color = "FFDD00", textColor = "000000")
        assertEquals(0xFF000000.toInt(), Palettes.publishedTextColor(black))
        assertEquals(0xFF000000.toInt(), Palettes.lineColors(black).second)
        // Illisible, il cède la place, comme toute couleur de texte publiée.
        assertEquals(WHITE, Palettes.lineColors(line("9", color = "1A237E", textColor = "000000")).second)
        // En Île-de-France, rien ne change.
        val idf = Line("C01371", "1", "1", "metro", color = "FFBE00", textColor = "000000")
        assertEquals(0xFF000000.toInt(), Palettes.publishedTextColor(idf))
        assertEquals(0xFF000000.toInt(), Palettes.lineColors(idf).second)
    }

    /** Fond à la couleur de la ligne : le petit texte doit se lire, quelle que soit la couleur publiée. */
    @Test
    fun theLineThemeStaysReadableWhateverTheColour() {
        val colours = listOf(
            "FFFFFF" to "0055A4", "FFDD00" to "FFFFFF", "000000" to "000000", "7F7F7F" to "808080", "E20019" to "FFFFFF",
            // Rouge sur blanc : ni le fond ne peut s'éclaircir, ni le texte se lire en petit. Il passe au noir.
            "FFFFFF" to "FF0000", "FF0000" to "FFFFFF", "00FF00" to "0000FF",
        )
        for ((color, textColor) in colours) {
            val palette = model(line("9", color = color, textColor = textColor), WidgetTheme.LINE).palette
            assertTrue("$textColor sur $color", Palettes.contrast(palette.background, palette.text) >= 4.5)
        }
    }
}
