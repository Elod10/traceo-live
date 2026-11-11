package fr.gotraceo.live.data

/**
 * Un choix de sens proposé à l'utilisateur.
 *
 * @param stopId point d'arrêt du référentiel (quai) concerné
 * @param key clé de sens ([Departure.directionKey]) ou null si aucun passage n'est connu
 *            actuellement pour ce point d'arrêt (on retiendra alors tous ses passages)
 */
data class DirectionOption(
    val stopId: String,
    val key: String?,
    val title: String,
    val subtitle: String?,
)

/** Logique de choix et de filtrage des sens (sans dépendance Android). */
object Directions {

    /**
     * Construit la liste des sens possibles à partir des passages observés en temps réel
     * sur chacun des points d'arrêt de l'arrêt choisi.
     *
     * @param results passages par point d'arrêt ; null si la requête a échoué
     */
    fun buildOptions(stop: Stop, results: Map<String, List<Departure>?>): List<DirectionOption> {
        val multi = stop.stopIds.size > 1
        val options = ArrayList<DirectionOption>()
        stop.stopIds.forEachIndexed { index, stopId ->
            val pointLabel = if (multi) "Point d'arrêt ${index + 1}" else null
            val deps = results[stopId]
            when {
                deps == null -> options += DirectionOption(
                    stopId, null,
                    title = pointLabel ?: "Tous les passages",
                    subtitle = "Sens inconnu : impossible de récupérer les passages",
                )
                deps.isEmpty() -> options += DirectionOption(
                    stopId, null,
                    title = pointLabel ?: "Tous les passages",
                    subtitle = "Aucun passage en ce moment : sens inconnu",
                )
                else -> {
                    val groups = LinkedHashMap<String, MutableList<Departure>>()
                    deps.sortedBy { it.displayTime ?: Long.MAX_VALUE }
                        .forEach { groups.getOrPut(normalizeLabel(it.directionKey)) { ArrayList() }.add(it) }
                    groups.values.forEach { group ->
                        val key = group.first().directionKey
                        val destinations = group.map { it.destination }
                            .distinctBy(::normalizeLabel)
                            .filter { normalizeLabel(it) != normalizeLabel(key) }
                        val platforms = group.mapNotNull { it.platform }.distinctBy(::normalizeLabel)
                        val details = ArrayList<String>()
                        if (destinations.isNotEmpty()) details += "Destinations : " + destinations.joinToString(", ")
                        if (platforms.isNotEmpty()) details += "Voie/quai " + platforms.joinToString(", ")
                        pointLabel?.let { details += it }
                        options += DirectionOption(
                            stopId, key,
                            title = "Vers $key",
                            subtitle = details.joinToString(" · ").ifEmpty { null },
                        )
                    }
                }
            }
        }
        return options
    }

    /** Sélection « tous les sens » : tous les points d'arrêt, sans filtre. */
    fun allDirections(stop: Stop): Map<String, Set<String>> =
        stop.stopIds.associateWith { emptySet<String>() }

    /** Convertit les options cochées en filtre enregistrable dans [WidgetConfig.directions]. */
    fun toSelection(options: List<DirectionOption>, selected: Collection<DirectionOption>): Map<String, Set<String>> {
        val result = LinkedHashMap<String, Set<String>>()
        options.groupBy { it.stopId }.forEach { (stopId, stopOptions) ->
            val chosen = stopOptions.filter { it in selected }
            if (chosen.isEmpty()) return@forEach
            result[stopId] = if (chosen.any { it.key == null } || chosen.size == stopOptions.size) {
                emptySet()
            } else {
                chosen.mapNotNull { it.key }.toCollection(LinkedHashSet())
            }
        }
        return result
    }

    /** Libellé court du sens retenu, affiché dans le widget. */
    fun label(options: List<DirectionOption>, selected: Collection<DirectionOption>, stop: Stop): String {
        if (selected.isEmpty()) return ALL_DIRECTIONS
        val selection = toSelection(options, selected)
        val coversAll = stop.stopIds.all { selection[it]?.isEmpty() == true }
        val keyed = options.filter { it.key != null }
        if (coversAll && (keyed.isEmpty() || selected.containsAll(keyed))) return ALL_DIRECTIONS
        val names = selected.mapNotNull { it.key }.distinctBy(::normalizeLabel)
        if (names.isEmpty()) return selected.joinToString(", ") { it.title }
        return "→ " + names.joinToString(", ")
    }

    /**
     * Applique le filtre de sens d'un widget aux passages récupérés. La destination compte aussi : un
     * widget configuré sans clé retient des destinations, alors que PRIM renvoie parfois un
     * `DirectionName` différent.
     */
    fun filter(departures: List<Departure>, selection: Map<String, Set<String>>): List<Departure> {
        if (selection.isEmpty()) return departures
        return departures.filter { d ->
            val keys = selection[d.stopId] ?: return@filter false
            keys.isEmpty() || keys.any {
                val key = normalizeLabel(it)
                key == normalizeLabel(d.directionKey) || key == normalizeLabel(d.destination)
            }
        }
    }

    /**
     * Retire les arrivées au terminus, pour ne pas afficher « → Auber » à Auber. Le suffixe de commune
     * final, « (Grande Arche) » par exemple, ne compte pas dans la comparaison. Au bout d'une branche,
     * un train annoncé « Aller » ne peut qu'arriver, quel que soit son terminus annoncé : le flux annonce
     * par exemple « Sucy - Bonneuil » sur des trains qui vont jusqu'à Boissy.
     */
    fun withoutTerminusArrivals(departures: List<Departure>, stopName: String): List<Departure> {
        val stop = terminusKey(stopName)
        if (stop.isEmpty()) return departures
        return departures.filter { terminusKey(it.destination) != stop && !arrivesAtBranchEnd(it, stop) }
    }

    private fun arrivesAtBranchEnd(departure: Departure, stop: String): Boolean =
        normalizeLabel(departure.directionRef) == ALLER &&
            BRANCH_ENDS[Referential.bareLineId(departure.lineRef.orEmpty())] == stop

    private fun terminusKey(name: String): String = normalizeLabel(name.replace(FINAL_TOWN_SUFFIX, ""))

    private val FINAL_TOWN_SUFFIX = Regex("\\s*\\([^()]*\\)\\s*$")

    /**
     * Passé ce délai après son heure, un véhicule « à quai » est parti : le suivant prend sa place
     * sans attendre la mise à jour.
     */
    const val AT_STOP_GRACE_MS = 2L * 60 * 1000

    /**
     * Passages à venir triés par heure. Un véhicule à quai reste affiché un peu après son heure, mais
     * pas sans fin : faute de nouvelles de la source (hors connexion, actualisation manuelle), il
     * resterait « à quai » en tête du widget.
     */
    fun upcoming(departures: List<Departure>, now: Long, graceMs: Long = 60_000L): List<Departure> =
        departures
            .filter { d ->
                val leave = d.leaveTime ?: return@filter false
                leave >= now - if (d.atStop) maxOf(graceMs, AT_STOP_GRACE_MS) else graceMs
            }
            .distinctBy { Triple(normalizeLabel(it.destination), it.displayTime, it.stopId) }
            .sortedBy { it.displayTime ?: Long.MAX_VALUE }

    /**
     * Le sens réel d'un passage. Le flux SIRI ET annonce « Retour » pour les trains Saint-Germain - Chessy
     * dans les deux sens, et pour Torcy tantôt « Aller », tantôt « Retour » (relevés du 5 novembre 2026).
     * Sur le RER A, on compare donc la gare et le terminus dans l'ordre réel de la ligne : un terminus déjà
     * dépassé est vers l'ouest, les autres vers l'est. Le sens annoncé ne sert que vers les branches ouest,
     * où il reste fiable.
     *
     * @param stopName gare où l'on se trouve, sans laquelle un terminus de la liste compte comme vers l'est
     */
    fun realDirection(lineId: String, destination: String, directionRef: String?, stopName: String = ""): String? {
        if (Referential.bareLineId(lineId) != RER_A) return directionRef
        val terminus = terminusKey(destination)
        val routes = RER_A_ROUTES.filter { terminus in it }
        if (routes.isEmpty()) return if (normalizeLabel(directionRef) == ALLER) RER_A_EAST else RER_A_WEST
        val here = terminusKey(stopName)
        val alreadyPassed = routes.any { here in it && it.indexOf(here) > it.indexOf(terminus) }
        return if (alreadyPassed) RER_A_WEST else RER_A_EAST
    }

    private const val ALLER = "aller"
    private const val RER_A = "C01742"
    private const val RER_B = "C01743"
    private const val RER_A_EAST = "est"
    private const val RER_A_WEST = "ouest"

    /** Tronc commun du RER A, d'ouest en est : ordre vérifié sur les trains du flux, le 5 novembre 2026. */
    private val RER_A_TRUNK = listOf(
        "Nanterre - Préfecture",
        "La Défense",
        "Charles de Gaulle - Étoile",
        "Auber",
        "Châtelet - Les Halles",
        "Gare de Lyon",
        "Nation",
        "Vincennes",
    )

    /** Les deux parcours du tronc commun vers les branches est. */
    private val RER_A_ROUTES = listOf(
        RER_A_TRUNK + listOf(
            "Fontenay-sous-Bois",
            "Nogent-sur-Marne",
            "Joinville-le-Pont",
            "Saint-Maur - Créteil",
            "Le Parc de Saint-Maur",
            "Champigny",
            "La Varenne - Chennevières",
            "Sucy - Bonneuil",
            "Boissy-Saint-Léger",
        ),
        RER_A_TRUNK + listOf(
            "Val de Fontenay",
            "Neuilly-Plaisance",
            "Bry-sur-Marne",
            "Noisy-le-Grand - Mont d'Est",
            "Noisy - Champs",
            "Noisiel",
            "Lognes",
            "Torcy",
            "Bussy-Saint-Georges",
            "Val d'Europe",
            "Marne-la-Vallée Chessy",
        ),
    ).map { route -> route.map(::terminusKey) }

    /** Bouts de branche où des trains annoncés « Aller » portent un autre terminus (relevés du 5 novembre 2026). */
    private val BRANCH_ENDS = mapOf(
        RER_A to terminusKey("Boissy-Saint-Léger"),
        RER_B to terminusKey("Robinson"),
    )

    const val ALL_DIRECTIONS = "Tous les sens"
}
