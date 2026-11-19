package fr.gotraceo.live.data

// Mentions de source et de licence des réseaux hors Île-de-France, composées d'après le catalogue de
// Tracéo. Celles de l'Île-de-France restent écrites dans l'application (strings.xml).
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

object NetworkMentions {

    /**
     * Mention courte d'un réseau, en HTML, pour les écrans qui montrent ses données : le nom de la base
     * avec son lien, et la licence avec le lien vers son texte, comme le demande l'ODbL (section 4.3).
     * La ville vient en tête, pour distinguer les mentions de plusieurs réseaux.
     */
    fun short(network: Network): String = "${escape(network.city)} : ${sentence(network)}"

    /**
     * Paragraphe d'un réseau pour l'écran Sources et licences, en HTML : son nom, la mention que le
     * catalogue demande d'afficher, la source et la licence avec leurs liens, et la date des horaires.
     *
     * @param indexDate date de compilation des horaires, déjà mise en forme, ou null si elle est inconnue
     */
    fun paragraph(network: Network, indexDate: String?): String {
        val lines = ArrayList<String>()
        lines += "<b>${escape(title(network))}</b>"
        val attribution = network.attribution
        val text = attribution?.text
        if (text != null) lines += escape(text)
        // Tracéo sert la mention complète du réseau : la redire avec d'autres mots ferait deux fois la
        // même phrase. Il reste à donner les liens, que son texte ne porte pas.
        lines += if (attribution != null && text != null && saysItAll(text)) {
            references(attribution)
        } else {
            sentence(network).replaceFirstChar { it.uppercase() }
        }
        // La Licence Ouverte demande la date des données réutilisées, l'ODbL de pouvoir retrouver
        // la base : la date des horaires que Tracéo a compilés est celle que l'application connaît.
        if (indexDate != null) lines += "Horaires compilés par Tracéo le ${escape(indexDate)}."
        return lines.joinToString("<br>")
    }

    /** Vrai si la mention du catalogue est déjà la mention complète, et non un simple rappel. */
    private fun saysItAll(text: String): Boolean = normalizeLabel(text).startsWith("contient des informations")

    /** « Source : … Licence : … », avec leurs liens. */
    private fun references(attribution: Attribution): String {
        val source = "Source : ${link(attribution.source, attribution.url)}."
        val licence = attribution.licence ?: return source
        return "$source Licence : ${link(licence, attribution.licenceUrl)}."
    }

    /** Réseaux nommés au plus dans la mention condensée : au-delà, elle renvoie à l'écran des sources. */
    const val MAX_NAMED = 4

    /**
     * Mention de plusieurs réseaux en une phrase, en HTML, pour le bas d'un écran : une mention
     * entière par réseau y prendrait plus de place que l'écran lui-même. Chaque réseau y garde son
     * nom, lié à sa source, et sa licence, liée à son texte ; le détail est à l'écran Sources et
     * licences, qui les cite tous.
     *
     * @param networks réseaux hors Île-de-France, dans l'ordre où les citer
     */
    fun compact(networks: List<Network>, maxNamed: Int = MAX_NAMED, afterIdf: Boolean = true): String {
        if (networks.isEmpty()) return ""
        // Compter un seul réseau restant ne gagnerait pas de place : il est nommé comme les autres.
        val shown = if (networks.size == maxNamed + 1) networks else networks.take(maxNamed)
        val named = shown.map { network ->
            val attribution = network.attribution
            val licence = attribution?.licence?.let { " (${link(it, attribution.licenceUrl)})" }.orEmpty()
            link(network.name, attribution?.url) + licence
        }
        val left = networks.size - shown.size
        val parts = if (left > 0) named + "$left autres" else named
        val list = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " et " + parts.last()
        // « Autres réseaux » n'a de sens qu'après la mention de l'Île-de-France.
        val opening = if (afterIdf) "Autres réseaux : contient" else "Contient"
        val noun = if (networks.size == 1) "du réseau" else "des réseaux"
        return "$opening des informations $noun $list, relevées par Tracéo. Détail dans Sources et licences."
    }

    /**
     * La mention, en HTML, des réseaux hors Île-de-France dont un écran montre les données, ou null
     * s'il n'y en a pas : rangés par ville, un seul garde sa mention entière, plusieurs partagent
     * une phrase.
     *
     * @param afterIdf vrai si la mention de l'Île-de-France la précède à l'écran
     */
    fun others(networks: List<Network>, afterIdf: Boolean): String? {
        val sorted = networks.filter { !it.isIdfm }.distinctBy { it.id }.sortedWith(NetworkChoices.byCity)
        return when (sorted.size) {
            0 -> null
            1 -> short(sorted.single())
            else -> compact(sorted, afterIdf = afterIdf)
        }
    }

    /** « de » ou « d' » devant un nom, selon sa première lettre : « d'Irigo », « de TBM ». */
    private fun of(name: String): String =
        if (normalizeLabel(name).firstOrNull() in VOWELS) "d'" else "de "

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y')

    /** « Troyes · TCAT », ou le nom seul quand la ville porte celui de son réseau. */
    fun title(network: Network): String =
        if (network.name == network.city) network.city else "${network.city} · ${network.name}"

    /** Qui publie les données d'un réseau : c'est de lui que l'application dit ne pas dépendre. */
    fun publisher(network: Network): String = network.attribution?.source ?: network.name

    /** Nom d'un réseau à côté de l'heure d'un relevé, sur le widget : « TCAT via Tracéo ». */
    fun sourceDetail(networkName: String?): String =
        if (networkName.isNullOrBlank()) TraceoClient.SOURCE_NAME else "$networkName via ${TraceoClient.SOURCE_NAME}"

    private fun sentence(network: Network): String {
        val attribution = network.attribution
            // Catalogue perdu, ou réseau qui n'y est plus : la source reste nommée, faute de mieux.
            ?: return "données du réseau ${escape(network.name)}, relevées par Tracéo."
        val source = of(attribution.source) + link(attribution.source, attribution.url)
        val licence = attribution.licence
            ?: return "contient des informations $source, relevées par Tracéo."
        // « Licence Ouverte » porte déjà le mot : « aux conditions de la licence Licence Ouverte » le dirait deux fois.
        val label = normalizeLabel(licence)
        val named = when {
            !label.contains("licence") && !label.contains("license") -> "de la licence "
            label.firstOrNull() in VOWELS -> "de l'"
            else -> "de la "
        }
        return "contient des informations $source, présentement mises à disposition aux conditions $named" +
            link(licence, attribution.licenceUrl) + "."
    }

    /** Un lien seulement vers une adresse web : le catalogue vient du réseau, pas de l'application. */
    private fun link(text: String, url: String?): String {
        val safe = url?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: return escape(text)
        return "<a href=\"${escape(safe)}\">${escape(text)}</a>"
    }

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
