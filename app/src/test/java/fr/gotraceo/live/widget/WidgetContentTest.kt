package fr.gotraceo.live.widget

import fr.gotraceo.live.data.RefreshSchedule
import fr.gotraceo.live.data.AutoHint
import fr.gotraceo.live.data.Departure
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.NearbyLine
import fr.gotraceo.live.data.NearbyReason
import fr.gotraceo.live.data.ProximityEntry
import fr.gotraceo.live.data.ProximityState
import fr.gotraceo.live.data.Stop
import fr.gotraceo.live.data.TransportMode
import fr.gotraceo.live.data.WidgetConfig
import fr.gotraceo.live.data.WidgetState
import fr.gotraceo.live.data.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {

    private val now = 1_700_000_000_000L
    private val metro = Line("C01371", "1", "1", "metro", color = "FFBE00", textColor = "000000")
    private val rer = Line("C01742", "RER A", "A", "rail", network = "RER", color = "EB2132", textColor = "FFFFFF")
    private val tram = Line("C01389", "T3a", "T3a", "tram", color = "FF7E2E", textColor = "000000")
    private val transilien = Line("C01730", "P", "P", "rail", network = "Transilien", color = "F28E42", textColor = "000000")

    private fun dep(
        destination: String,
        inMinutes: Int,
        direction: String? = null,
        atStop: Boolean = false,
        status: String? = null,
        delay: Int = 0,
        platform: String? = null,
        mission: String? = null,
        stopId: String = "IDFM:1",
    ) = Departure(
        lineRef = null,
        destination = destination,
        directionName = direction,
        directionRef = null,
        expectedArrival = now + inMinutes * 60_000L,
        expectedDeparture = null,
        aimedArrival = now + (inMinutes - delay) * 60_000L,
        aimedDeparture = null,
        atStop = atStop,
        platform = platform,
        status = status,
        journeyNote = mission,
        stopId = stopId,
    )

    private fun config(line: Line, directions: Map<String, Set<String>>, theme: WidgetTheme = WidgetTheme.AUTO) = WidgetConfig(
        widgetId = 1,
        mode = TransportMode.METRO,
        line = line,
        stop = Stop("Bastille", "Paris", listOf("IDFM:1", "IDFM:2")),
        directions = directions,
        directionLabel = "",
        theme = theme,
    )

    private val allDirections = mapOf("IDFM:1" to emptySet<String>(), "IDFM:2" to emptySet<String>())
    private val oneDirection = mapOf("IDFM:1" to setOf("Château de Vincennes"))

    // --- Actualisation ----------------------------------------------------------------------

    @Test
    fun tickFallsOnTheNextMinuteWhateverTheMomentItIsScheduled() {
        val minute = 60_000L
        val start = 28_000_000L * minute
        // Reprogrammer pendant la même minute ne repousse pas l'échéance.
        assertEquals(start + minute, RefreshSchedule.nextTickAt(start, 0L))
        assertEquals(start + minute, RefreshSchedule.nextTickAt(start + 1L, 0L))
        assertEquals(start + minute, RefreshSchedule.nextTickAt(start + minute - 1L, 0L))
        // Une fois déclenché, le tick suivant tombe sur la minute d'après.
        assertEquals(start + 2L * minute, RefreshSchedule.nextTickAt(start + minute, 0L))
    }

    @Test
    fun tickKeepsTheOffsetOfThisPhoneWithinTheMinute() {
        val minute = 60_000L
        val start = 28_000_000L * minute
        val offset = 17_000L
        assertEquals(start + offset, RefreshSchedule.nextTickAt(start, offset))
        assertEquals(start + offset, RefreshSchedule.nextTickAt(start + offset - 1L, offset))
        assertEquals(start + minute + offset, RefreshSchedule.nextTickAt(start + offset, offset))
    }

    // --- Choix du format ---------------------------------------------------------------------

    @Test
    fun formatFollowsSizeLineAndDirection() {
        fun select(w: Float, h: Float, rail: Boolean, single: Boolean) = WidgetFormat.select(w, h, rail, single)

        assertEquals(WidgetFormat.PASTILLE, select(84f, 92f, rail = false, single = true))
        assertEquals(WidgetFormat.PASTILLE, select(84f, 192f, rail = true, single = false))
        assertEquals(WidgetFormat.UN_SENS, select(172f, 92f, rail = true, single = true))
        assertEquals(WidgetFormat.UN_SENS, select(172f, 92f, rail = false, single = false))
        assertEquals(WidgetFormat.GRAND_CHIFFRE, select(172f, 192f, rail = false, single = true))
        assertEquals(WidgetFormat.TRAIN, select(172f, 192f, rail = true, single = true))
        assertEquals(WidgetFormat.DEUX_SENS, select(172f, 192f, rail = false, single = false))
        assertEquals(WidgetFormat.DEUX_SENS, select(172f, 192f, rail = true, single = false))
        assertEquals(WidgetFormat.FRISE, select(348f, 92f, rail = false, single = true))
        assertEquals(WidgetFormat.COMPACT, select(348f, 92f, rail = false, single = false))
        assertEquals(WidgetFormat.DEUX_SENS_LARGE, select(348f, 192f, rail = false, single = false))
        assertEquals(WidgetFormat.LISTE, select(348f, 192f, rail = true, single = false))
        assertEquals(WidgetFormat.LISTE, select(348f, 192f, rail = false, single = true))
        assertEquals(WidgetFormat.LISTE, select(348f, 292f, rail = false, single = false))
    }

    @Test
    fun eachWidgetEntryImposesItsFormat() {
        assertEquals(WidgetFormat.PASTILLE, WidgetProviders.formatOf(PastilleWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.UN_SENS, WidgetProviders.formatOf(UnSensWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.GRAND_CHIFFRE, WidgetProviders.formatOf(GrandChiffreWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.FRISE, WidgetProviders.formatOf(FriseWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.COMPACT, WidgetProviders.formatOf(CompactWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.DEUX_SENS, WidgetProviders.formatOf(DeuxSensWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.DEUX_SENS_LARGE, WidgetProviders.formatOf(DeuxSensLargeWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.TRAIN, WidgetProviders.formatOf(TrainWidgetProvider::class.java.name))
        assertEquals(WidgetFormat.LISTE, WidgetProviders.formatOf(ListeWidgetProvider::class.java.name))
        // L'entrée automatique, et les widgets déjà posés avec elle, n'imposent aucun format.
        assertNull(WidgetProviders.formatOf(DeparturesWidgetProvider::class.java.name))
        assertNull(WidgetProviders.formatOf(null))
    }

    @Test
    fun catalogOffersEveryFormatOnceAndTheAutomaticEntry() {
        val catalog = WidgetProviders.catalog
        assertEquals(WidgetFormat.entries.toSet(), catalog.mapNotNull { it.format }.toSet())
        assertEquals(WidgetFormat.entries.size + 1, catalog.size)
        assertEquals(catalog.size, catalog.map { it.provider }.toSet().size)
        assertEquals(DeparturesWidgetProvider::class.java, catalog.single { it.format == null }.provider)
    }

    @Test
    fun chosenFormatWinsOverSizeAndDirection() {
        val chosen = WidgetModels.build(
            config(metro, oneDirection), state(dep("A", 5)), hasApiKey = true, refreshing = false,
            widthDp = 348f, heightDp = 192f, now = now, fixedFormat = WidgetFormat.PASTILLE,
        )
        assertEquals(WidgetFormat.PASTILLE, chosen.format)

        val automatic = WidgetModels.build(
            config(metro, oneDirection), state(dep("A", 5)), hasApiKey = true, refreshing = false,
            widthDp = 84f, heightDp = 92f, now = now,
        )
        assertEquals(WidgetFormat.PASTILLE, automatic.format)
    }

    @Test
    fun aTallFormatTooShortGivesWayToItsOneRowEquivalent() {
        fun fitting(format: WidgetFormat, w: Float, h: Float, single: Boolean = false) = format.fitting(w, h, single)

        // À sa taille, ou un peu rétréci, chaque format garde son dessin.
        WidgetFormat.entries.forEach { assertEquals(it, fitting(it, it.baseWidth, it.baseHeight)) }
        assertEquals(WidgetFormat.DEUX_SENS_LARGE, fitting(WidgetFormat.DEUX_SENS_LARGE, 348f, 150f))
        // Écran d'accueil en paysage : deux rangées y sont moins hautes qu'une seule en portrait.
        assertEquals(WidgetFormat.COMPACT, fitting(WidgetFormat.DEUX_SENS_LARGE, 650f, 120f))
        assertEquals(WidgetFormat.COMPACT, fitting(WidgetFormat.DEUX_SENS, 320f, 120f))
        assertEquals(WidgetFormat.FRISE, fitting(WidgetFormat.GRAND_CHIFFRE, 320f, 120f, single = true))
        // Widget rétréci à une rangée en portrait : trop étroit pour deux sens, comme l'entrée automatique.
        assertEquals(WidgetFormat.COMPACT, fitting(WidgetFormat.DEUX_SENS_LARGE, 348f, 110f))
        assertEquals(WidgetFormat.UN_SENS, fitting(WidgetFormat.DEUX_SENS, 172f, 110f))
        assertEquals(WidgetFormat.UN_SENS, fitting(WidgetFormat.GRAND_CHIFFRE, 172f, 110f, single = true))
    }

    @Test
    fun theTrackAndTheListShrinkFurtherBeforeGivingWay() {
        // Ni la voie ni la liste des départs n'ont d'équivalent sur une rangée.
        assertEquals(WidgetFormat.TRAIN, WidgetFormat.TRAIN.fitting(320f, 120f, singleDirection = true))
        assertEquals(WidgetFormat.TRAIN, WidgetFormat.TRAIN.fitting(172f, 103f, singleDirection = true))
        assertEquals(WidgetFormat.UN_SENS, WidgetFormat.TRAIN.fitting(172f, 100f, singleDirection = true))
        assertEquals(WidgetFormat.LISTE, WidgetFormat.LISTE.fitting(650f, 120f, singleDirection = false))
        assertEquals(WidgetFormat.LISTE, WidgetFormat.LISTE.fitting(348f, 92f, singleDirection = false))
        assertEquals(WidgetFormat.COMPACT, WidgetFormat.LISTE.fitting(348f, 85f, singleDirection = false))
        assertEquals(WidgetFormat.FRISE, WidgetFormat.LISTE.fitting(348f, 85f, singleDirection = true))
        assertEquals(WidgetFormat.UN_SENS, WidgetFormat.LISTE.fitting(180f, 85f, singleDirection = false))
    }

    @Test
    fun formatsOnOneRowAndTheNearbyFormatNeverGiveWay() {
        listOf(WidgetFormat.PASTILLE, WidgetFormat.UN_SENS, WidgetFormat.FRISE, WidgetFormat.COMPACT, WidgetFormat.PROXIMITE)
            .forEach { assertEquals(it, it.fitting(650f, 40f, singleDirection = false)) }
    }

    @Test
    fun theDrawingShrinksRatherThanOverflowWhenTheHeightIsShort() {
        // Dans ses limites habituelles, l'échelle ne change pas.
        assertEquals(1f, WidgetFormat.DEUX_SENS_LARGE.sceneScale(348f, 192f), 0.001f)
        assertEquals(0.75f, WidgetFormat.DEUX_SENS_LARGE.sceneScale(250f, 192f), 0.001f)
        assertEquals(1.6f, WidgetFormat.PASTILLE.sceneScale(400f, 400f), 0.001f)
        // La liste tient encore à 110 dp de haut : elle garde son échelle et montre moins de départs.
        assertEquals(0.75f, WidgetFormat.LISTE.sceneScale(348f, 110f), 0.001f)
        // Une frise sur une rangée d'un écran en paysage (55 dp) : tout le dessin (92 dp) tient.
        val scale = WidgetFormat.FRISE.sceneScale(650f, 55f)
        assertEquals(92f, 55f / scale, 0.5f)
        // Plus bas encore, le dessin ne rétrécit plus : il deviendrait illisible.
        assertEquals(0.5f, WidgetFormat.FRISE.sceneScale(650f, 30f), 0.001f)
    }

    @Test
    fun theChosenFormatIsDrawnAsItsEquivalentWhenItCannotFit() {
        val model = WidgetModels.build(
            config(metro, allDirections), state(dep("A", 5), dep("B", 7, stopId = "IDFM:2")), hasApiKey = true,
            refreshing = false, widthDp = 650f, heightDp = 120f, now = now, fixedFormat = WidgetFormat.DEUX_SENS_LARGE,
        )
        assertEquals(WidgetFormat.COMPACT, model.format)
    }

    // --- Tailles données par le lanceur ---------------------------------------------------------

    @Test
    fun theLauncherGivesOneSizePerOrientation() {
        // Un widget 4×2 sur un téléphone : 328×190 dp en portrait, 658×94 dp en paysage.
        val sizes = WidgetSizes.of(minWidth = 328, maxWidth = 658, minHeight = 94, maxHeight = 190)
        assertEquals(WidgetSize(328f, 190f), sizes.portrait)
        assertEquals(WidgetSize(658f, 94f), sizes.landscape)
        assertEquals(sizes.landscape, sizes.otherLandscape)
    }

    @Test
    fun aWidgetWithASingleSizeIsDrawnOnce() {
        assertNull(WidgetSizes.of(300, 300, 150, 150).otherLandscape)
        // Un lanceur qui ne dit rien : la taille de la maquette.
        val unknown = WidgetSizes.of(0, 0, 0, 0)
        assertEquals(WidgetSize(348f, 192f), unknown.portrait)
        assertNull(unknown.otherLandscape)
        // Une seule orientation renseignée sert pour les deux.
        val portraitOnly = WidgetSizes.of(minWidth = 328, maxWidth = 0, minHeight = 0, maxHeight = 190)
        assertEquals(WidgetSize(328f, 190f), portraitOnly.landscape)
        assertNull(portraitOnly.otherLandscape)
    }

    @Test
    fun twoDirectionFormatsAreIdentified() {
        val both = WidgetFormat.entries.filter { it.showsBothDirections }
        assertEquals(
            listOf(WidgetFormat.COMPACT, WidgetFormat.DEUX_SENS, WidgetFormat.DEUX_SENS_LARGE, WidgetFormat.PROXIMITE),
            both,
        )
    }

    @Test
    fun oneDirectionFormatsAreIdentified() {
        val one = WidgetFormat.entries.filter { it.followsOneDirection }
        assertEquals(
            listOf(WidgetFormat.PASTILLE, WidgetFormat.UN_SENS, WidgetFormat.GRAND_CHIFFRE, WidgetFormat.FRISE, WidgetFormat.TRAIN),
            one,
        )
        // Seule la liste accepte les deux usages : elle indique la destination de chaque passage.
        val neither = WidgetFormat.entries.filter { !it.followsOneDirection && !it.showsBothDirections }
        assertEquals(listOf(WidgetFormat.LISTE), neither)
    }

    @Test
    fun singleDirectionDetection() {
        assertTrue(config(metro, oneDirection).isSingleDirection)
        assertFalse(config(metro, allDirections).isSingleDirection)
        // Un seul quai retenu parmi deux : un seul sens, même sans filtre de destination.
        assertTrue(config(metro, mapOf("IDFM:1" to emptySet<String>())).isSingleDirection)
        // Plusieurs destinations cochées (branches du RER).
        assertFalse(config(rer, mapOf("IDFM:1" to setOf("Boissy-Saint-Léger", "Marne-la-Vallée"))).isSingleDirection)
    }

    // --- Passages ----------------------------------------------------------------------------

    @Test
    fun waitKinds() {
        fun kind(d: Departure, mode: String = "metro") = WidgetContent.passage(d, now, mode).kind

        assertEquals(WaitKind.MINUTES, kind(dep("A", 5)))
        assertEquals(WaitKind.CLOCK, kind(dep("A", 75)))
        assertEquals(WaitKind.IMMINENT, kind(dep("A", 0)))
        assertEquals(WaitKind.AT_STOP, kind(dep("A", 1, atStop = true)))
        assertEquals(WaitKind.CANCELLED, kind(dep("A", 1, atStop = true, status = "cancelled")))
    }

    @Test
    fun passageDetailsDependOnMode() {
        val train = WidgetContent.passage(dep("Boissy", 2, platform = "1", mission = "NOTE"), now, "rail")
        assertEquals("Voie", train.platformLabel)
        assertEquals("NOTE", train.mission)
        assertEquals("À quai", train.atStopLabel)

        val bus = WidgetContent.passage(dep("Vincennes", 2, platform = "B", mission = "XXXX"), now, "bus")
        assertEquals("Quai", bus.platformLabel)
        assertNull(bus.mission)
        assertEquals("À l'arrêt", bus.atStopLabel)
    }

    @Test
    fun delayIsFlaggedFromTwoMinutes() {
        assertFalse(WidgetContent.passage(dep("A", 5, delay = 1), now, "rail").isDelayed)
        assertTrue(WidgetContent.passage(dep("A", 5, delay = 3), now, "rail").isDelayed)
        assertFalse(WidgetContent.passage(dep("A", 5, delay = 3, status = "cancelled"), now, "rail").isDelayed)
    }

    @Test
    fun thenLabels() {
        val five = WidgetContent.passage(dep("A", 5), now, "metro")
        assertEquals("puis 5", five.thenLabel())
        assertEquals("puis 5 min", five.thenLabel(withUnit = true))
        assertEquals("puis <1", WidgetContent.passage(dep("A", 0), now, "metro").thenLabel())
    }

    @Test
    fun thenLabelKeepsOnlyTheMinutesWhenSpaceIsShort() {
        val next = WidgetContent.passage(dep("A", 36), now, "metro")
        assertEquals("puis 36", next.thenLabelFitting { it.length <= 7 })
        assertEquals("36", next.thenLabelFitting { it.length <= 2 })
        assertNull(next.thenLabelFitting { it.length <= 1 })
    }

    // --- Sens --------------------------------------------------------------------------------

    private fun passages(vararg departures: Departure) = departures.map { WidgetContent.passage(it, now, "metro") }

    @Test
    fun groupsKeepTwoDirectionsInStableOrder() {
        val soonVincennes = passages(
            dep("La Défense (Grande Arche)", 2),
            dep("Château de Vincennes", 1),
            dep("Château de Vincennes", 5),
        )
        val soonDefense = passages(
            dep("Château de Vincennes", 2),
            dep("La Défense (Grande Arche)", 1),
        )
        val labels = listOf("Château de Vincennes", "La Défense")
        assertEquals(labels, WidgetContent.directionGroups(soonVincennes).map { it.label })
        assertEquals(labels, WidgetContent.directionGroups(soonDefense).map { it.label })
    }

    @Test
    fun severalDestinationsOnTwoPlatformsMakeTwoDirections() {
        val groups = WidgetContent.directionGroups(
            passages(
                dep("Boissy-Saint-Léger", 2, stopId = "E"),
                dep("Nanterre Préfecture", 2, stopId = "W"),
                dep("Poissy", 16, stopId = "W"),
                dep("Marne-la-Vallée", 3, stopId = "E"),
            ),
        )
        assertEquals(2, groups.size)
        assertEquals(setOf("E", "W"), groups.map { it.first.stopId }.toSet())
    }

    @Test
    fun nextPassageStaysInDirectionAndSkipsCancelled() {
        val list = passages(
            dep("Vincennes", 1),
            dep("La Défense", 2),
            dep("Vincennes", 4, status = "cancelled"),
            dep("Vincennes", 6),
        )
        assertEquals(6, WidgetContent.nextPassage(list, list.first())?.minutes)
    }

    @Test
    fun railKeepsFourStopsWithinTheHour() {
        val list = passages(
            dep("A", 1), dep("A", 5), dep("A", 8), dep("A", 11), dep("A", 14),
        )
        assertEquals(listOf(1, 5, 8, 11), WidgetContent.railPassages(list, list.first()).map { it.minutes })

        val far = passages(dep("A", 40), dep("A", 90))
        assertEquals(listOf(40), WidgetContent.railPassages(far, far.first()).map { it.minutes })
    }

    @Test
    fun railCentersMatchTheDesign() {
        assertEquals(
            listOf(74f, 170f, 242f, 314f),
            WidgetContent.railCenters(listOf(1, 5, 8, 11), 74f, 314f, 10, 28f),
        )
    }

    @Test
    fun railCentersKeepLabelsApartAndDropWhatDoesNotFit() {
        // Passages très rapprochés : les points sont espacés, ceux qui débordent disparaissent.
        val centers = WidgetContent.railCenters(listOf(0, 1, 2, 30), 0f, 60f, 30, 28f)
        assertEquals(listOf(0f, 28f, 56f), centers)
        // Une seule valeur : pas de division par zéro.
        assertEquals(listOf(10f), WidgetContent.railCenters(listOf(5), 10f, 100f, 0, 28f))
        // Le deuxième point ne touche pas le grand chiffre.
        assertEquals(listOf(74f, 120f), WidgetContent.railCenters(listOf(1, 2), 74f, 314f, 100, 28f, 120f))
    }

    // --- Noms --------------------------------------------------------------------------------

    @Test
    fun shortNameRemovesParenthesis() {
        assertEquals("La Défense", WidgetContent.shortName("La Défense (Grande Arche)"))
        assertEquals("Poissy", WidgetContent.shortName("Poissy"))
    }

    @Test
    fun compactNamePicksFirstVersionThatFits() {
        fun compact(name: String, max: Int) = WidgetContent.compactName(name) { it.length <= max }

        assertEquals("Poissy", compact("Poissy", 10))
        assertEquals("La Défense", compact("La Défense (Grande Arche)", 10))
        assertEquals("Vincennes", compact("Château de Vincennes", 10))
        assertEquals("Boissy", compact("Boissy-Saint-Léger", 10))
        assertEquals("Nanterre", compact("Nanterre Préfecture", 10))
        assertEquals("Clignancourt", compact("Porte de Clignancourt", 12))
    }

    @Test
    fun ellipsizeAndWrap() {
        val width: (String) -> Float = { it.length.toFloat() }
        assertEquals("Poissy", WidgetContent.ellipsize("Poissy", 10f, width))
        assertEquals("Boissy…", WidgetContent.ellipsize("Boissy-Saint-Léger", 7f, width))
        assertEquals(
            listOf("Aucun passage", "prévu pour le", "moment"),
            WidgetContent.wrapLines("Aucun passage prévu pour le moment", 13f, 3, width),
        )
        assertEquals(
            listOf("Aucun passage", "prévu pour l…"),
            WidgetContent.wrapLines("Aucun passage prévu pour le moment", 13f, 2, width),
        )
    }

    // --- Thèmes ------------------------------------------------------------------------------

    @Test
    fun onlyTheLineThemeUsesTheLineColor() {
        fun isLine(format: WidgetFormat, theme: WidgetTheme) = Palettes.resolve(theme, format, metro).isLine

        WidgetFormat.entries.forEach { assertFalse("$it", isLine(it, WidgetTheme.AUTO)) }
        assertFalse(isLine(WidgetFormat.PASTILLE, WidgetTheme.DARK))
        assertTrue(isLine(WidgetFormat.FRISE, WidgetTheme.LINE))
        assertFalse("La liste n'a pas de thème couleur de ligne", isLine(WidgetFormat.LISTE, WidgetTheme.LINE))
    }

    @Test
    fun autoThemeFollowsTheSystemTheme() {
        val dark = Palettes.resolve(WidgetTheme.DARK, WidgetFormat.PASTILLE, metro)
        val light = Palettes.resolve(WidgetTheme.LIGHT, WidgetFormat.PASTILLE, metro)
        assertEquals(dark.background, Palettes.resolve(WidgetTheme.AUTO, WidgetFormat.PASTILLE, metro, night = true).background)
        assertEquals(light.background, Palettes.resolve(WidgetTheme.AUTO, WidgetFormat.PASTILLE, metro, night = false).background)
    }

    @Test
    fun lineColorIsDarkenedWhenSmallTextWouldBeUnreadable() {
        val palette = Palettes.resolve(WidgetTheme.LINE, WidgetFormat.PASTILLE, rer)
        assertTrue(Palettes.contrast(palette.background, palette.text) >= 4.5)
    }

    @Test
    fun unreadableTextColorFromReferentialIsReplaced() {
        val yellow = Palettes.parseColor("FFBE00")!!
        val on = Palettes.onColor(yellow, Palettes.parseColor("FFFFFF"))
        assertTrue(Palettes.contrast(yellow, on) >= 4.5)
    }

    @Test
    fun parseColorAcceptsReferentialFormats() {
        assertEquals(0xFFEB2132.toInt(), Palettes.parseColor("EB2132"))
        assertEquals(0xFFEB2132.toInt(), Palettes.parseColor("#EB2132"))
        assertNull(Palettes.parseColor("rouge"))
        assertNull(Palettes.parseColor(null))
    }

    // --- Modèle ------------------------------------------------------------------------------

    private fun state(vararg departures: Departure, error: String? = null) =
        WidgetState(fetchedAt = now - 60_000L, attemptedAt = now, departures = departures.toList(), error = error)

    private fun model(
        config: WidgetConfig?,
        state: WidgetState?,
        hasApiKey: Boolean = true,
        width: Float = 348f,
        height: Float = 192f,
    ) = WidgetModels.build(config, state, hasApiKey, refreshing = false, widthDp = width, heightDp = height, now = now)

    @Test
    fun noticesWhenThereIsNothingToShow() {
        assertEquals(NoticeKind.NOT_CONFIGURED, model(null, null).notice?.kind)
        assertEquals(NoticeKind.RECONFIGURE, model(config(metro, allDirections), state(dep("A", 2)), hasApiKey = false).notice?.kind)
        assertEquals(NoticeKind.LOADING, model(config(metro, allDirections), null).notice?.kind)
        assertEquals(NoticeKind.NO_DEPARTURE, model(config(metro, allDirections), state()).notice?.kind)
        assertEquals(NoticeKind.ERROR, model(config(metro, allDirections), state(error = "Panne")).notice?.kind)
    }

    // --- Source des données -------------------------------------------------------------------

    private val traceoStop = "stop_area:IDFM:71673"

    private fun traceoConfig() = WidgetConfig(
        widgetId = 1,
        mode = TransportMode.METRO,
        line = metro,
        stop = Stop("Bastille", null, listOf(traceoStop)),
        directions = mapOf(traceoStop to emptySet()),
        directionLabel = "",
    )

    private fun snapshotState(
        dataAt: Long? = now - 5 * 60_000L,
        fresh: Boolean = true,
        error: String? = null,
    ) = WidgetState(
        fetchedAt = now - 60_000L,
        attemptedAt = now,
        departures = listOf(dep("La Défense (Grande Arche)", 5, stopId = traceoStop)),
        error = error,
        dataAt = dataAt,
        fresh = fresh,
    )

    @Test
    fun fourWidgetCases() {
        // Zone d'arrêt, sans clé puis avec clé : les passages s'affichent.
        assertNull(model(traceoConfig(), snapshotState(), hasApiKey = false).notice)
        assertNull(model(traceoConfig(), state(dep("A", 2, stopId = traceoStop)), hasApiKey = true).notice)
        // Arrêt du référentiel, avec clé : comme avant.
        assertNull(model(config(metro, allDirections), state(dep("A", 2)), hasApiKey = true).notice)
        // Arrêt du référentiel, sans clé : à reconfigurer, même avec des passages en mémoire.
        assertEquals(
            NoticeKind.RECONFIGURE,
            model(config(metro, allDirections), state(dep("A", 2)), hasApiKey = false).notice?.kind,
        )
    }

    @Test
    fun snapshotShowsTheTimeOfTheReadingAndItsSource() {
        val state = snapshotState()
        val result = model(traceoConfig(), state, hasApiKey = false)
        assertEquals(DepartureFormat.clock(state.dataAt!!), result.timeLabel)
        assertEquals("Tracéo", result.sourceLabel)
        assertEquals("PRIM via Tracéo", result.sourceDetail)
        assertFalse(result.oldSnapshot)
        assertFalse(result.stale)
    }

    @Test
    fun oldSnapshotIsFlaggedWithoutBeingAFailure() {
        val result = model(traceoConfig(), snapshotState(fresh = false), hasApiKey = false)
        assertTrue(result.oldSnapshot)
        assertFalse(result.stale)
        assertNull(result.notice)
    }

    @Test
    fun failedRefreshOnSnapshotKeepsItsDateAndFlagsIt() {
        val state = snapshotState(error = "Erreur réseau")
        val result = model(traceoConfig(), state, hasApiKey = false)
        assertTrue(result.stale)
        assertEquals(DepartureFormat.clock(state.dataAt!!), result.timeLabel)
        assertEquals("Tracéo", result.sourceLabel)
    }

    @Test
    fun snapshotWithoutDateShowsNoTime() {
        val result = model(traceoConfig(), snapshotState(dataAt = null), hasApiKey = false)
        assertEquals("", result.timeLabel)
        assertEquals("Tracéo", result.sourceLabel)
    }

    @Test
    fun liveDataShowsThePhoneTimeWithoutSource() {
        val stateOfPrim = state(dep("A", 5))
        val result = model(config(metro, allDirections), stateOfPrim)
        assertEquals(DepartureFormat.clock(stateOfPrim.fetchedAt), result.timeLabel)
        assertNull(result.sourceLabel)
        assertNull(result.sourceDetail)
        assertFalse(result.oldSnapshot)
    }

    @Test
    fun refreshingHidesTheSourceUntilTheNewReading() {
        val result = WidgetModels.build(
            traceoConfig(), snapshotState(fresh = false), hasApiKey = false, refreshing = true,
            widthDp = 348f, heightDp = 192f, now = now,
        )
        assertEquals("…", result.timeLabel)
        assertNull(result.sourceLabel)
        assertNull(result.sourceDetail)
        assertFalse(result.oldSnapshot)
    }

    @Test
    fun screenReadersHearTheReading() {
        val state = snapshotState()
        val time = DepartureFormat.clock(state.dataAt!!)
        assertEquals(
            "Ligne 1, Bastille. Vers La Défense : 5 minutes. Données PRIM via Tracéo, relevé de $time.",
            WidgetContent.describe(model(traceoConfig(), state, hasApiKey = false)),
        )
        assertEquals(
            "Ligne 1, Bastille. Vers La Défense : 5 minutes. Données PRIM via Tracéo, relevé de $time, ancien.",
            WidgetContent.describe(model(traceoConfig(), snapshotState(fresh = false), hasApiKey = false)),
        )
    }

    @Test
    fun failedRefreshKeepsOldPassagesAndFlagsThem() {
        val result = model(config(metro, allDirections), state(dep("A", 5), error = "Panne"))
        assertNull(result.notice)
        assertTrue(result.stale)
        assertEquals(1, result.passages.size)
    }

    @Test
    fun refreshingReplacesTheTime() {
        val result = WidgetModels.build(
            config(metro, allDirections), state(dep("A", 5)), hasApiKey = true, refreshing = true,
            widthDp = 348f, heightDp = 192f, now = now,
        )
        assertEquals("…", result.timeLabel)
    }

    @Test
    fun badgeShapesFollowTheMode() {
        fun badge(line: Line) = model(config(line, oneDirection), state()).line

        assertEquals(LineBadge("1", BadgeShape.CIRCLE), badge(metro))
        assertEquals(LineBadge("A", BadgeShape.CIRCLE), badge(rer))
        assertEquals(LineBadge("T3a", BadgeShape.TRAM), badge(tram))
        assertEquals(LineBadge("P", BadgeShape.SQUARE), badge(transilien))
        assertEquals(
            LineBadge("124", BadgeShape.BUS),
            badge(Line("C1", "124", "124", "bus")),
        )
    }

    @Test
    fun descriptionForScreenReaders() {
        val result = model(
            config(metro, allDirections),
            state(dep("Château de Vincennes", 1), dep("La Défense (Grande Arche)", 2), dep("Château de Vincennes", 5)),
        )
        assertEquals(
            "Ligne 1, Bastille. Vers Château de Vincennes : 1 minute, puis 5 minutes. Vers La Défense : 2 minutes. " +
                "Données PRIM, mises à jour à ${DepartureFormat.clock(now - 60_000L)}.",
            WidgetContent.describe(result),
        )
    }

    @Test
    fun aNewWidgetStartsInTheLineColorExceptTheList() {
        assertEquals(WidgetTheme.LINE, WidgetFormat.defaultTheme(null))
        WidgetFormat.entries.filter { it != WidgetFormat.LISTE }.forEach {
            assertEquals(WidgetTheme.LINE, WidgetFormat.defaultTheme(it))
        }
        assertEquals(WidgetTheme.DARK, WidgetFormat.defaultTheme(WidgetFormat.LISTE))
    }

    @Test
    fun aWidgetSavedWithoutThemeKeepsItsColors() {
        assertEquals(WidgetTheme.AUTO, WidgetTheme.fromName(null))
    }

    // --- Format À proximité ----------------------------------------------------------------------

    private fun nearby(line: Line, distance: Int = 33) = NearbyLine(line, "Nation", "Paris", "71673", distance)

    private fun depWithRef(destination: String, inMinutes: Int, ref: String?, status: String? = null) =
        dep(destination, inMinutes, status = status).copy(directionRef = ref)

    private fun proximityModel(
        vararg entries: ProximityEntry,
        width: Float = 348f,
        height: Float = 192f,
        night: Boolean = true,
        refreshing: Boolean = false,
        hint: AutoHint? = null,
        error: String? = null,
        snapshot: Boolean = false,
    ): WidgetModel {
        val state = ProximityState(
            fetchedAt = now - 60_000L,
            attemptedAt = now,
            entries = entries.toList(),
            error = error,
            dataAt = if (snapshot) now - 30_000L else null,
            fresh = if (snapshot) true else null,
        )
        return WidgetModels.buildProximity(config(metro, allDirections), state, refreshing, width, height, now, night, hint)
    }

    private fun proximityEntry(line: Line, vararg departures: Departure, favorite: Boolean = false) =
        ProximityEntry(nearby(line), NearbyReason.NEAREST, favorite, departures.toList())

    @Test
    fun rowsFollowTheHeightOfTheWidget() {
        assertEquals(2, ProximityLayout.rows(348f, 192f))
        assertEquals(3, ProximityLayout.rows(348f, 292f))
        // Tailles réelles d'un lanceur, plus petites ou plus grandes que la maquette.
        assertEquals(2, ProximityLayout.rows(320f, 150f))
        assertEquals(3, ProximityLayout.rows(320f, 230f))
        assertEquals(2, ProximityLayout.rows(400f, 210f))
    }

    @Test
    fun rowsStayWithinLimits() {
        assertEquals(1, ProximityLayout.rows(348f, 80f))
        assertEquals(5, ProximityLayout.rows(348f, 900f))
    }

    @Test
    fun directionsAreGroupedByTheSourceDirection() {
        val passages = listOf(
            depWithRef("Saint-Germain-en-Laye", 2, "Retour"),
            depWithRef("Sucy - Bonneuil", 3, "Aller"),
            depWithRef("Cergy le Haut", 5, "Retour"),
            depWithRef("Boissy-Saint-Léger", 7, "Aller"),
        ).map { WidgetContent.passage(it, now, "rail") }
        val directions = WidgetContent.proximityDirections(passages)
        assertEquals(listOf("Sucy - Bonneuil", "Saint-Germain-en-Laye"), directions.map { it.label })
        assertEquals(listOf(7, 5), directions.map { it.next?.minutes })
    }

    private fun rerARow(vararg departures: Departure): ProximityRow =
        proximityModel(proximityEntry(rer, *departures)).proximity.single()

    @Test
    fun eastBranchesOfTheRerAStayWithTheEastEvenWhenTheFeedSaysOtherwise() {
        // Relevé réel à Nation : Chessy annoncé « Retour », comme les trains vers l'ouest.
        val row = rerARow(
            depWithRef("Marne-la-Vallée Chessy", 1, "Retour"),
            depWithRef("Saint-Germain-en-Laye", 2, "Retour"),
            depWithRef("Boissy-Saint-Léger", 4, "Aller"),
            depWithRef("Cergy le Haut", 5, "Retour"),
        )
        assertEquals(listOf("Marne-la-Vallée Chessy", "Saint-Germain-en-Laye"), row.directions.map { it.label })
        assertEquals(listOf(4, 5), row.directions.map { it.next?.minutes })
    }

    /** Les sens affichés à une gare du RER A, chaque sens avec ses destinations dans l'ordre. */
    private fun rerAGroups(stop: String, vararg departures: Departure): List<List<String>> {
        val passages = departures.map { WidgetContent.passage(it, now, "rail") }
        return WidgetContent.proximityDirections(passages, rer.id, stop).map { direction ->
            listOfNotNull(direction.first.destination, direction.next?.destination)
        }
    }

    @Test
    fun anUnusualTerminusAnnouncedAllerStaysWithTheEastInsteadOfMakingAThirdDirection() {
        val groups = rerAGroups(
            "Nation",
            depWithRef("La Varenne - Chennevières", 1, "Aller"),
            depWithRef("Boissy-Saint-Léger", 3, "Aller"),
            depWithRef("Cergy le Haut", 5, "Retour"),
        )
        assertEquals(listOf(listOf("La Varenne - Chennevières", "Boissy-Saint-Léger"), listOf("Cergy le Haut")), groups)
    }

    @Test
    fun anUnusualTerminusAnnouncedRetourStillGoesEastFromParis() {
        // Le flux annonce « Retour » pour les trains Saint-Germain - Chessy, dans les deux sens.
        val groups = rerAGroups(
            "Nation",
            depWithRef("Noisy - Champs", 1, "Retour"),
            depWithRef("Cergy le Haut", 5, "Retour"),
        )
        assertEquals(listOf(listOf("Noisy - Champs"), listOf("Cergy le Haut")), groups)
    }

    @Test
    fun aTerminusBehindTheStationIsTowardsTheWestWhateverTheFeedSays() {
        val aller = rerAGroups(
            "Sucy - Bonneuil",
            depWithRef("La Varenne - Chennevières", 1, "Aller"),
            depWithRef("Cergy le Haut", 5, "Retour"),
        )
        assertEquals(listOf(listOf("La Varenne - Chennevières", "Cergy le Haut")), aller)

        val retour = rerAGroups(
            "Bussy-Saint-Georges",
            depWithRef("Torcy", 1, "Retour"),
            depWithRef("Saint-Germain-en-Laye", 5, "Retour"),
            depWithRef("Marne-la-Vallée Chessy", 7, "Retour"),
        )
        assertEquals(listOf(listOf("Marne-la-Vallée Chessy"), listOf("Torcy", "Saint-Germain-en-Laye")), retour)
    }

    @Test
    fun theStopNameMayBeWrittenDifferentlyFromTheDestination() {
        // « Marne-la-Vallée - Chessy » dans l'open data, « Marne-la-Vallée Chessy » dans le flux.
        val groups = rerAGroups(
            "Marne-la-Vallée - Chessy",
            depWithRef("Torcy", 1, "Retour"),
            depWithRef("Saint-Germain-en-Laye", 5, "Retour"),
        )
        assertEquals(listOf(listOf("Torcy", "Saint-Germain-en-Laye")), groups)
    }

    @Test
    fun aTerminusInParisDependsOnTheSideOfTheStation() {
        fun directionCount(stop: String) = rerAGroups(
            stop,
            depWithRef("Nation", 1, "Retour"),
            depWithRef("Cergy le Haut", 5, "Retour"),
        ).size
        // Depuis l'ouest, un train vers Nation part à l'opposé de Cergy ; depuis l'est, dans le même sens.
        assertEquals(2, directionCount("La Défense"))
        assertEquals(2, directionCount("Saint-Germain-en-Laye"))
        assertEquals(1, directionCount("Vincennes"))
        assertEquals(1, directionCount("Lognes"))
    }

    @Test
    fun vincennesIsReachedTowardsTheWestFromBothEastBranches() {
        fun directionCount(stop: String) = rerAGroups(
            stop,
            depWithRef("Vincennes", 1, "Retour"),
            depWithRef("Cergy le Haut", 5, "Retour"),
        ).size
        assertEquals(1, directionCount("Fontenay-sous-Bois"))
        assertEquals(1, directionCount("Neuilly-Plaisance"))
        assertEquals(2, directionCount("Auber"))
    }

    @Test
    fun theFeedDirectionIsKeptForOtherLines() {
        val rerB = Line("C01743", "RER B", "B", "rail", network = "RER", color = "5291CE", textColor = "FFFFFF")
        val row = proximityModel(
            proximityEntry(rerB, depWithRef("Torcy", 1, "Retour"), depWithRef("Mitry - Claye", 2, "Retour")),
        ).proximity.single()
        assertEquals(1, row.directions.size)
    }

    @Test
    fun theNextTrainOfADirectionComesFirstWhateverItsDestination() {
        // Le train à quai, puis 3 min vers une autre branche et 7 min vers la même destination.
        val row = rerARow(
            depWithRef("Saint-Germain-en-Laye", 0, "Retour").copy(atStop = true),
            depWithRef("Cergy le Haut", 3, "Retour"),
            depWithRef("Saint-Germain-en-Laye", 7, "Retour"),
        )
        val direction = row.directions.single()
        assertEquals(WaitKind.AT_STOP, direction.first.kind)
        assertEquals(3, direction.next?.minutes)
    }

    @Test
    fun aTrainAtThePlatformGivesWayOnceItHasLeft() {
        val row = rerARow(
            depWithRef("Saint-Germain-en-Laye", -3, "Retour").copy(atStop = true),
            depWithRef("Cergy le Haut", 3, "Retour"),
            depWithRef("Saint-Germain-en-Laye", 7, "Retour"),
        )
        val direction = row.directions.single()
        assertEquals("Cergy le Haut", direction.label)
        assertEquals(3, direction.first.minutes)
        assertEquals(7, direction.next?.minutes)
    }

    @Test
    fun aTrainJustAtThePlatformStaysShown() {
        val row = rerARow(
            depWithRef("Saint-Germain-en-Laye", -1, "Retour").copy(atStop = true),
            depWithRef("Cergy le Haut", 3, "Retour"),
        )
        assertEquals("Saint-Germain-en-Laye", row.directions.single().label)
    }

    @Test
    fun withoutSourceDirectionTheDestinationsAreUsed() {
        val passages = listOf(dep("Vincennes", 4), dep("La Défense (Grande Arche)", 2), dep("Vincennes", 9))
            .map { WidgetContent.passage(it, now, "metro") }
        val directions = WidgetContent.proximityDirections(passages)
        assertEquals(listOf("La Défense", "Vincennes"), directions.map { it.label })
        assertEquals(9, directions[1].next?.minutes)
        assertNull(directions[0].next)
    }

    @Test
    fun directionsDoNotSwapWhenTheNextPassageChanges() {
        fun order(first: Int, second: Int) = WidgetContent.proximityDirections(
            listOf(depWithRef("A", first, "Aller"), depWithRef("B", second, "Retour"))
                .map { WidgetContent.passage(it, now, "metro") },
        ).map { it.label }
        assertEquals(order(2, 9), order(9, 2))
    }

    @Test
    fun onlyTheTwoSoonestDirectionsAreKept() {
        val passages = listOf(dep("C", 9), dep("B", 2), dep("A", 4)).map { WidgetContent.passage(it, now, "bus") }
        assertEquals(listOf("A", "B"), WidgetContent.proximityDirections(passages).map { it.label })
    }

    @Test
    fun aCancelledPassageIsShownButNotAsTheNextOne() {
        val passages = listOf(dep("A", 2), dep("A", 5, status = "cancelled"), dep("A", 9))
            .map { WidgetContent.passage(it, now, "bus") }
        assertEquals(9, WidgetContent.proximityDirections(passages).single().next?.minutes)
    }

    @Test
    fun noPassageMeansNoDirection() {
        assertTrue(WidgetContent.proximityDirections(emptyList()).isEmpty())
    }

    @Test
    fun aRowDescribesItsLineStopAndDistance() {
        val row = proximityModel(proximityEntry(metro, dep("La Défense", 2))).proximity.single()
        assertEquals("Nation · 33 m", row.place)
        assertEquals("1", row.badge.label)
        assertEquals(BadgeShape.CIRCLE, row.badge.shape)
        assertEquals(Palettes.lineColors(metro).first, row.badgeBg)
    }

    @Test
    fun theWidgetKeepsAsManyLinesAsItsHeightAllows() {
        val entries = listOf(metro, rer, tram, transilien).map { proximityEntry(it, dep("A", 3)) }.toTypedArray()
        assertEquals(2, proximityModel(*entries).proximity.size)
        assertEquals(3, proximityModel(*entries, height = 292f).proximity.size)
    }

    @Test
    fun aFavoriteWithoutPassageIsShownWithoutDirection() {
        val rows = proximityModel(proximityEntry(metro, favorite = true), proximityEntry(rer)).proximity
        assertEquals(1, rows.size)
        assertTrue(rows.single().directions.isEmpty())
    }

    @Test
    fun theColorFollowsTheSystemTheme() {
        val entry = proximityEntry(metro, dep("A", 3))
        val dark = proximityModel(entry, night = true).palette
        val light = proximityModel(entry, night = false).palette
        assertFalse(dark.isLine)
        assertFalse(light.isLine)
        assertTrue(dark.background != light.background)
    }

    @Test
    fun proximityNotices() {
        fun build(state: ProximityState?, config: WidgetConfig? = config(metro, allDirections)) =
            WidgetModels.buildProximity(config, state, false, 348f, 192f, now, true)
        assertEquals(NoticeKind.NOT_CONFIGURED, build(null, null).notice?.kind)
        assertEquals(NoticeKind.LOADING, build(null).notice?.kind)
        assertEquals(NoticeKind.NO_DEPARTURE, build(ProximityState(now, now, emptyList())).notice?.kind)
        assertEquals(NoticeKind.ERROR, build(ProximityState(now, now, emptyList(), error = "Panne")).notice?.kind)
    }

    @Test
    fun anErrorWithLinesIsStaleNotANotice() {
        val result = proximityModel(proximityEntry(metro, dep("A", 3)), error = "Panne")
        assertNull(result.notice)
        assertTrue(result.stale)
    }

    @Test
    fun theHintAndTheSourceShareTheHeader() {
        val entry = proximityEntry(metro, dep("A", 3))
        assertEquals("Tracéo", proximityModel(entry, snapshot = true).sourceLabel)
        val hinted = proximityModel(entry, snapshot = true, hint = AutoHint("+3 lignes", "+3"))
        assertEquals("+3 lignes", hinted.timeLabel)
        assertNull(hinted.sourceLabel)
        assertEquals("…", proximityModel(entry, refreshing = true).timeLabel)
    }

    @Test
    fun screenReadersHearEveryLine() {
        val result = proximityModel(
            proximityEntry(metro, depWithRef("La Défense", 2, "Aller"), depWithRef("La Défense", 6, "Aller")),
            proximityEntry(rer, favorite = true),
        )
        assertEquals(
            "À proximité. Ligne 1, Nation · 33 m. Vers La Défense : 2 minutes, puis 6 minutes. " +
                "Ligne A, Nation · 33 m. Aucun passage. Données PRIM, mises à jour à ${DepartureFormat.clock(now - 60_000L)}.",
            WidgetContent.describe(result),
        )
    }

    // --- Mode automatique ------------------------------------------------------------------------

    private fun autoModel(
        hint: AutoHint? = null,
        distance: Int? = null,
        refreshing: Boolean = false,
        fixedFormat: WidgetFormat? = WidgetFormat.DEUX_SENS_LARGE,
        snapshot: Boolean = false,
    ): WidgetModel {
        val base = state(dep("La Défense", 2))
        return WidgetModels.build(
            config(metro, allDirections),
            if (snapshot) base.copy(dataAt = now - 30_000L, fresh = false) else base,
            hasApiKey = true,
            refreshing = refreshing,
            widthDp = 348f,
            heightDp = 192f,
            now = now,
            fixedFormat = fixedFormat,
            autoHint = hint,
            autoDistanceM = distance,
        )
    }

    @Test
    fun theHintTakesThePlaceOfTheTimeAndTheSource() {
        val result = autoModel(AutoHint("+3 lignes", "+3"), snapshot = true)
        assertEquals("+3 lignes", result.timeLabel)
        assertEquals("+3 lignes", result.autoHint)
        assertEquals("+3", result.autoHintShort)
        assertNull(result.sourceLabel)
        assertNull(result.sourceDetail)
        assertFalse(result.oldSnapshot)
    }

    @Test
    fun theTimeAndTheSourceComeBackWithoutHint() {
        val result = autoModel(snapshot = true)
        assertNull(result.autoHint)
        assertEquals("Tracéo", result.sourceLabel)
        assertTrue(result.oldSnapshot)
    }

    @Test
    fun aRefreshHidesTheHint() {
        val result = autoModel(AutoHint("+3 lignes", "+3"), refreshing = true)
        assertEquals("…", result.timeLabel)
        assertNull(result.autoHint)
    }

    @Test
    fun theDistanceFollowsTheStopOnWideHeadersOnly() {
        assertEquals("Bastille · 33 m", autoModel(distance = 33).stopName)
        assertEquals("Bastille · 33 m", autoModel(distance = 33, fixedFormat = WidgetFormat.LISTE).stopName)
        assertEquals("Bastille", autoModel(distance = 33, fixedFormat = WidgetFormat.DEUX_SENS).stopName)
        assertEquals("Bastille", autoModel(distance = 33, fixedFormat = WidgetFormat.PASTILLE).stopName)
        assertEquals("Bastille", autoModel().stopName)
    }

    @Test
    fun screenReadersHearTheHint() {
        assertTrue(WidgetContent.describe(autoModel(AutoHint("+3 lignes", "+3"))).endsWith("+3 lignes."))
    }

    @Test
    fun everyFormatTakesAnAutomaticWidget() {
        WidgetFormat.entries.forEach { format ->
            val result = autoModel(AutoHint("+3 lignes", "+3"), distance = 33, fixedFormat = format)
            assertEquals(format, result.format)
            assertNull(result.notice)
            assertEquals("La Défense", result.passages.single().destination)
        }
    }
}
