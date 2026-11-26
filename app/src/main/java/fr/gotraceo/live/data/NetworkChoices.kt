package fr.gotraceo.live.data

import java.text.Collator
import java.util.Locale

// L'ordre dans lequel la configuration propose les réseaux. Avec une vingtaine de réseaux, la liste
// ne tient plus sur un écran : ceux qui ont une raison d'être choisis par cet usager passent devant.
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/**
 * Les réseaux à proposer, dans l'ordre de la liste.
 *
 * @param leading en tête : le réseau du widget à modifier, le plus proche, le dernier choisi, et
 *        celui qui est coché s'il n'est aucun des trois
 * @param others les autres, par ville
 */
class NetworkChoices(val leading: List<Network>, val others: List<Network>) {

    val all: List<Network> get() = leading + others

    /** Vrai si la liste est assez longue pour qu'on y cherche, et que ses premiers choix aient leur carte. */
    val searchable: Boolean get() = leading.size + others.size >= SEARCH_FROM

    /** Les cartes de la liste : une seule tant qu'elle tient sur l'écran, sinon les premiers choix puis les autres. */
    val cards: List<List<Network>> get() = if (searchable) listOf(leading, others) else listOf(all)

    companion object {
        /** À partir de ce nombre de réseaux, la liste ne tient plus sur un écran de téléphone. */
        const val SEARCH_FROM = 8

        /**
         * Ordre alphabétique français des villes : les accents et la casse ne comptent pas, « Île-de-France »
         * se range à I et « Saint-Étienne » avant « Toulon ». À ville égale, le nom du réseau départage.
         */
        val byCity: Comparator<Network> = Comparator { a, b ->
            val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
            collator.compare(a.city, b.city).takeIf { it != 0 }
                ?: collator.compare(a.name, b.name).takeIf { it != 0 }
                ?: a.id.compareTo(b.id)
        }

        /**
         * Ce qui se cherche d'un réseau : sa ville et son nom, rien d'autre. Son état ou la raison de
         * sa place dans la liste ne doivent pas le faire remonter à la saisie de « mo » ou de « pro ».
         * L'Île-de-France se trouve aussi par « Paris » et par ses exploitants.
         */
        fun searchText(network: Network): String =
            listOfNotNull(network.city, network.name, IDF_SEARCH_WORDS.takeIf { network.isIdfm }).joinToString(" ")

        private const val IDF_SEARCH_WORDS = "Paris IDF IDFM RATP SNCF Transilien"

        /**
         * @param widgetNetwork réseau du widget à modifier, null pour un nouveau widget
         * @param nearest réseau de l'endroit où se trouve l'usager, null s'il est inconnu
         * @param last dernier réseau choisi, null s'il n'y en a pas
         * @param checked réseau coché à l'écran : il doit rester en vue, quelle que soit sa ville
         */
        fun of(catalog: NetworkCatalog, widgetNetwork: String?, nearest: String?, last: String?, checked: String? = null): NetworkChoices {
            val leading = listOf(widgetNetwork, nearest, last, checked)
                .mapNotNull { id -> id?.let(catalog::byId) }
                // Un réseau coupé ne se propose pas d'abord, sauf au widget qui s'y trouve déjà.
                .filter { it.status != NetworkStatus.OFF || it.id == widgetNetwork }
                .distinctBy { it.id }
                // Sans aucune de ces raisons, l'Île-de-France reste en vue.
                .ifEmpty { listOf(Networks.ILE_DE_FRANCE) }
            val ids = leading.map { it.id }.toSet()
            return NetworkChoices(leading, catalog.networks.filter { it.id !in ids }.sortedWith(byCity))
        }
    }
}
