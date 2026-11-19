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
        network.attribution?.text?.let { lines += escape(it) }
        lines += sentence(network).replaceFirstChar { it.uppercase() }
        if (indexDate != null) lines += "Horaires compilés par Tracéo le ${escape(indexDate)}."
        return lines.joinToString("<br>")
    }

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
        val source = link(attribution.source, attribution.url)
        val licence = attribution.licence
            ?: return "contient des informations de $source, relevées par Tracéo."
        return "contient des informations de $source, présentement mises à disposition aux conditions de la licence " +
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
