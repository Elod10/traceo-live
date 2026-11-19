package fr.gotraceo.live.data

import fr.gotraceo.live.ui.RowItem
import fr.gotraceo.live.ui.RowSearch
import fr.gotraceo.live.ui.RowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'étape « Réseau » avec une vingtaine de réseaux : l'ordre de la liste, ses cartes et sa recherche.
 * Les villes sont celles du plan (chantiers/multi-reseaux/reseaux-ouverts.md) ; les identifiants sont
 * inventés, l'application n'en écrit aucun.
 */
class NetworkChoicesTest {

    private companion object {
        /** Ville et nom du réseau, dans le désordre où un serveur pourrait les donner. */
        val CITIES = listOf(
            "Toulon" to "Réseau Mistral", "Bordeaux" to "TBM", "Nîmes" to "Tango", "Saint-Étienne" to "STAS",
            "Le Havre" to "LiA", "Amiens" to "Ametis", "Orléans" to "TAO", "Metz" to "Le Met'",
            "Clermont-Ferrand" to "T2C", "Nice" to "Lignes d'Azur", "Angers" to "Irigo", "Tours" to "Fil Bleu",
            "Reims" to "Grand Reims Mobilités", "Le Mans" to "SETRAM", "Dijon" to "Divia", "Brest" to "Bibus",
            "Montpellier" to "TaM", "Rouen" to "Astuce", "Nancy" to "Stan", "Mulhouse" to "Soléa",
            "Lens" to "Tadao", "Troyes" to "TCAT",
        )

        const val FIND_MY_NETWORK = "Trouver mon réseau"
    }

    private fun network(index: Int, city: String, name: String, status: NetworkStatus = NetworkStatus.OK) = Network(
        id = "r$index",
        name = name,
        city = city,
        bbox = BoundingBox(40.0 + index, 40.2 + index, 1.0, 1.2),
        modes = listOf("bus"),
        refreshSec = 60,
        status = status,
    )

    private val networks = CITIES.mapIndexed { index, (city, name) -> network(index, city, name) }
    private val catalog = NetworkCatalog(networks)

    private fun id(city: String): String = networks.first { it.city == city }.id

    private fun cities(list: List<Network>): List<String> = list.map { it.city }

    // --- Ordre ---------------------------------------------------------------------------------

    @Test
    fun citiesFollowFrenchAlphabeticalOrder() {
        val choices = NetworkChoices.of(catalog, widgetNetwork = null, nearest = null, last = null)
        // Sans raison d'en proposer un autre, l'Île-de-France reste en vue.
        assertEquals(listOf("Île-de-France"), cities(choices.leading))
        assertEquals(
            listOf(
                "Amiens", "Angers", "Bordeaux", "Brest", "Clermont-Ferrand", "Dijon", "Le Havre", "Le Mans", "Lens",
                "Metz", "Montpellier", "Mulhouse", "Nancy", "Nice", "Nîmes", "Orléans", "Reims", "Rouen",
                "Saint-Étienne", "Toulon", "Tours", "Troyes",
            ),
            cities(choices.others),
        )
        assertEquals(23, choices.all.size)
    }

    @Test
    fun theWidgetNetworkThenTheNearestThenTheLastChosenComeFirst() {
        val choices = NetworkChoices.of(catalog, widgetNetwork = id("Rouen"), nearest = id("Bordeaux"), last = id("Amiens"))
        assertEquals(listOf("Rouen", "Bordeaux", "Amiens"), cities(choices.leading))
        // Les autres suivent par ville ; l'Île-de-France y prend sa place alphabétique, entre Dijon et Le Havre.
        assertEquals(listOf("Angers", "Brest", "Clermont-Ferrand", "Dijon", "Île-de-France", "Le Havre"), cities(choices.others).take(6))
        assertEquals(23, choices.all.size)
        assertEquals("Aucun réseau en double", 23, choices.all.map { it.id }.toSet().size)
    }

    @Test
    fun aNetworkThatHasSeveralReasonsAppearsOnce() {
        val same = NetworkChoices.of(catalog, widgetNetwork = id("Tours"), nearest = id("Tours"), last = id("Tours"), checked = id("Tours"))
        assertEquals(listOf("Tours"), cities(same.leading))
        val paris = NetworkChoices.of(catalog, widgetNetwork = null, nearest = Networks.IDFM, last = id("Nice"))
        assertEquals(listOf("Île-de-France", "Nice"), cities(paris.leading))
        assertFalse("Île-de-France" in cities(paris.others))
    }

    /**
     * Le réseau coché reste en vue. À Rouen, l'usager coche Toulon, continue, puis revient : Toulon
     * n'est ni le plus proche ni le dernier enregistré, il serait à la vingtième ligne.
     */
    @Test
    fun theCheckedNetworkStaysInSight() {
        val back = NetworkChoices.of(catalog, widgetNetwork = null, nearest = id("Rouen"), last = id("Amiens"), checked = id("Toulon"))
        assertEquals(listOf("Rouen", "Amiens", "Toulon"), cities(back.leading))
        assertFalse("Toulon" in cities(back.others))
        // L'Île-de-France cochée d'avance, alors que le dernier réseau choisi vient d'arriver au catalogue.
        val restored = NetworkChoices.of(catalog, widgetNetwork = null, nearest = null, last = id("Dijon"), checked = Networks.IDFM)
        assertEquals(listOf("Dijon", "Île-de-France"), cities(restored.leading))
    }

    @Test
    fun aNetworkThatLeftTheCatalogIsNotProposed() {
        val choices = NetworkChoices.of(catalog, widgetNetwork = "parti", nearest = null, last = "parti-aussi", checked = "parti")
        assertEquals(listOf("Île-de-France"), cities(choices.leading))
        assertEquals(22, choices.others.size)
    }

    /** Un réseau coupé reste dans la liste, à sa place, mais ne passe devant que pour le widget qui s'y trouve. */
    @Test
    fun aNetworkThatIsOffDoesNotLeadExceptForItsOwnWidget() {
        val off = NetworkCatalog(networks.map { if (it.city == "Dijon") it.copy(status = NetworkStatus.OFF) else it })
        val asLast = NetworkChoices.of(off, widgetNetwork = null, nearest = null, last = id("Dijon"))
        assertEquals(listOf("Île-de-France"), cities(asLast.leading))
        assertTrue("Dijon" in cities(asLast.others))

        val asWidget = NetworkChoices.of(off, widgetNetwork = id("Dijon"), nearest = null, last = null, checked = id("Dijon"))
        assertEquals(listOf("Dijon"), cities(asWidget.leading))
    }

    @Test
    fun twoNetworksOfTheSameCityAreOrderedByName() {
        val twins = NetworkCatalog(listOf(network(1, "Rouen", "Réseau B"), network(2, "Rouen", "Réseau A")))
        assertEquals(listOf("Réseau A", "Réseau B"), NetworkChoices.of(twins, null, Networks.IDFM, null).others.map { it.name })
    }

    // --- Liste courte, liste longue, catalogue vide --------------------------------------------

    @Test
    fun onlyALongListIsSearchedAndSplitInTwoCards() {
        val long = NetworkChoices.of(catalog, null, id("Bordeaux"), id("Amiens"))
        assertTrue(long.searchable)
        assertEquals(listOf(listOf("Bordeaux", "Amiens"), cities(long.others)), long.cards.map(::cities))
        assertEquals(21, long.others.size)

        // Le pilote : l'Île-de-France et un réseau tiennent sur l'écran, dans une seule carte, sans recherche.
        val pilot = NetworkChoices.of(NetworkCatalog(networks.take(1)), null, null, null)
        assertFalse(pilot.searchable)
        assertEquals(listOf(listOf("Île-de-France", "Toulon")), pilot.cards.map(::cities))
        // L'ordre y suit la même règle : là-bas, le réseau le plus proche passe devant l'Île-de-France.
        val there = NetworkChoices.of(NetworkCatalog(networks.take(1)), null, id("Toulon"), null, checked = id("Toulon"))
        assertEquals(listOf(listOf("Toulon", "Île-de-France")), there.cards.map(::cities))

        val seven = NetworkChoices.of(NetworkCatalog(networks.take(NetworkChoices.SEARCH_FROM - 2)), null, null, null)
        assertFalse(seven.searchable)
        assertEquals(1, seven.cards.size)
        val eight = NetworkChoices.of(NetworkCatalog(networks.take(NetworkChoices.SEARCH_FROM - 1)), null, null, null)
        assertTrue(eight.searchable)
        assertEquals(2, eight.cards.size)
    }

    @Test
    fun anEmptyCatalogStillOffersIleDeFrance() {
        val choices = NetworkChoices.of(NetworkCatalog(), widgetNetwork = null, nearest = null, last = null)
        assertEquals(listOf(Networks.IDFM), choices.all.map { it.id })
        assertFalse(choices.searchable)
        assertEquals(1, choices.cards.size)
    }

    // --- Recherche -----------------------------------------------------------------------------

    /**
     * Les lignes de la liste, avec les mêmes cartes et le même texte cherché que l'écran. Les
     * sous-titres portent ce que l'écran y ajoute : l'état du réseau et la raison de sa place.
     */
    private fun rows(choices: NetworkChoices, withFindRow: Boolean = false): List<RowItem> {
        val items = ArrayList<RowItem>()
        if (withFindRow) {
            items += RowItem(
                title = FIND_MY_NETWORK,
                subtitle = "Avec votre position approximative : elle sert seulement à reconnaître votre ville, sur ce téléphone.",
                style = RowStyle.CARD,
                searchOnly = "",
            )
        }
        choices.cards.forEach { group ->
            group.forEachIndexed { index, item ->
                val reason = if (item in choices.leading) "Le plus proche de vous" else "Temps réel interrompu pour le moment"
                items += RowItem(
                    title = item.city,
                    subtitle = "${item.name} · $reason",
                    style = RowStyle.inGroup(index, group.size),
                    radio = true,
                    payload = item,
                    searchOnly = NetworkChoices.searchText(item),
                )
            }
        }
        return items
    }

    private fun found(query: String): List<String> =
        RowSearch.filter(rows(NetworkChoices.of(catalog, null, null, null), withFindRow = true), query).map { it.title }

    @Test
    fun aNetworkIsFoundByItsCityOrByItsName() {
        assertEquals(listOf("Bordeaux"), found("bord"))
        assertEquals(listOf("Bordeaux"), found("TBM"))
        assertEquals(listOf("Nice"), found("azur"))
        assertEquals(listOf("Reims"), found("grand reims"))
        // Chaque mot tapé doit se retrouver, où qu'il soit dans la ville ou le nom.
        assertEquals(listOf("Toulon", "Tours"), found("tou"))
        assertEquals(listOf("Le Havre"), found("le havre"))
        assertTrue(found("zzz").isEmpty())
        assertEquals(24, found("  ").size)
    }

    @Test
    fun theSearchIgnoresAccentsCaseAndPunctuation() {
        assertEquals(listOf("Nîmes"), found("nimes"))
        assertEquals(listOf("Saint-Étienne"), found("saint etienne"))
        assertEquals(listOf("Saint-Étienne"), found("SAINT-ÉTIENNE"))
        assertEquals(listOf("Île-de-France"), found("ile de france"))
        assertEquals(listOf("Orléans"), found("orleans"))
        assertEquals(listOf("Metz"), found("le met"))
        assertEquals(listOf("Nice"), found("lignes d'azur"))
        assertEquals(listOf("Clermont-Ferrand"), found("clermont ferrand"))
    }

    /** Seuls la ville et le nom se cherchent : ni l'état du réseau, ni la raison de sa place, ni l'aide. */
    @Test
    fun theSearchLooksAtNothingButTheCityAndTheName() {
        // « mo » se trouve dans des villes et des noms (Montpellier, Clermont, « Mobilités »), pas dans
        // « pour le moment », que portent ici tous les réseaux de la seconde carte.
        assertEquals(listOf("Île-de-France", "Clermont-Ferrand", "Montpellier", "Reims"), found("mo"))
        assertTrue(found("proche").isEmpty())
        assertTrue(found("interrompu").isEmpty())
        assertEquals(listOf("Rouen"), found("rouen"))
        assertEquals(listOf("Troyes"), found("tro"))
        // Un réseau dont le nom est celui de la ville ne l'affiche pas deux fois, mais se trouve quand même.
        val same = network(99, "Ville", "Ville")
        assertEquals("Ville Ville", NetworkChoices.searchText(same))
    }

    /** « Trouver mon réseau » est une action : elle s'efface dès qu'on cherche, quel que soit le texte. */
    @Test
    fun theFindMyNetworkRowLeavesWhenSearching() {
        assertEquals(FIND_MY_NETWORK, found("").first())
        for (query in listOf("ro", "tro", "re", "mon", "le", "ville", "trouver", "position")) {
            assertFalse(query, FIND_MY_NETWORK in found(query))
        }
    }

    @Test
    fun ileDeFranceIsAlsoFoundByParis() {
        assertEquals(listOf("Île-de-France"), found("paris"))
        assertEquals(listOf("Île-de-France"), found("ratp"))
        assertEquals(listOf("Île-de-France"), found("idf"))
        assertFalse(NetworkChoices.searchText(networks.first()).contains("Paris"))
    }

    // --- Cartes --------------------------------------------------------------------------------

    @Test
    fun theLeadingNetworksHaveTheirOwnCard() {
        val items = rows(NetworkChoices.of(catalog, widgetNetwork = null, nearest = id("Bordeaux"), last = id("Amiens")))
        val styles = RowSearch.styles(items, filtered = false)
        assertEquals(listOf(RowStyle.GROUP_TOP, RowStyle.GROUP_BOTTOM, RowStyle.GROUP_TOP), styles.take(3))
        assertEquals(RowStyle.GROUP_BOTTOM, styles.last())
        assertEquals(2, styles.count { it.startsBlock })
        // Une liste courte n'a qu'une carte, quel que soit son ordre.
        val short = rows(NetworkChoices.of(NetworkCatalog(networks.take(3)), null, id("Toulon"), null))
        assertEquals(1, RowSearch.styles(short, filtered = false).count { it.startsBlock })
    }

    /** Filtrée, la liste reste faite de cartes entières : jamais un milieu de carte sans ses bords. */
    @Test
    fun aFilteredListIsStillMadeOfWholeCards() {
        val items = rows(NetworkChoices.of(catalog, null, id("Bordeaux"), id("Amiens")))
        val two = RowSearch.filter(items, "tou")
        assertEquals(listOf("Toulon", "Tours"), two.map { it.title })
        assertEquals(listOf(RowStyle.GROUP_TOP, RowStyle.GROUP_BOTTOM), RowSearch.styles(two, filtered = true))

        val one = RowSearch.filter(items, "rouen")
        assertEquals(listOf(RowStyle.CARD), RowSearch.styles(one, filtered = true))

        // À la jonction des deux cartes, les lignes restantes n'en forment qu'une.
        val across = RowSearch.filter(items, "am")
        assertEquals(listOf("Amiens", "Le Mans", "Montpellier"), across.map { it.title })
        assertEquals(
            listOf(RowStyle.GROUP_TOP, RowStyle.GROUP_MIDDLE, RowStyle.GROUP_BOTTOM),
            RowSearch.styles(across, filtered = true),
        )

        // Une ligne hors carte (une action, une ligne simple) garde sa présentation.
        val mixed = listOf(
            RowItem(title = FIND_MY_NETWORK, style = RowStyle.CARD),
            RowItem(title = "Nancy", style = RowStyle.GROUP_MIDDLE),
            RowItem(title = "Nice", style = RowStyle.GROUP_MIDDLE),
            RowItem(title = "Nîmes", style = RowStyle.GROUP_MIDDLE),
            RowItem(title = "Clé", style = RowStyle.PLAIN),
        )
        assertEquals(
            listOf(RowStyle.CARD, RowStyle.GROUP_TOP, RowStyle.GROUP_MIDDLE, RowStyle.GROUP_BOTTOM, RowStyle.PLAIN),
            RowSearch.styles(mixed, filtered = true),
        )
    }

    /** Le réseau coché que la recherche masque n'est plus à l'écran : « Continuer » ne doit pas le valider. */
    @Test
    fun aCheckedNetworkHiddenByTheSearchIsNotShown() {
        val items = rows(NetworkChoices.of(catalog, null, null, null))
        items.first { it.title == "Île-de-France" }.checked = true
        assertEquals("Île-de-France", RowSearch.filter(items, "").first { it.checked }.title)
        assertEquals("Île-de-France", RowSearch.filter(items, "paris").first { it.checked }.title)
        assertTrue(RowSearch.filter(items, "bord").none { it.checked })
        assertTrue(RowSearch.filter(items, "lyon").none { it.checked })
    }
}
