package fr.gotraceo.live.data

// Hors Île-de-France, aucun réseau ne donne la commune de ses arrêts : rien, dans la liste des arrêts
// d'une ligne, ne sépare deux arrêts de même nom. Ce sont soit les deux sens d'un même arrêt, que le
// réseau ne rattache pas l'un à l'autre, soit deux endroits différents (« Mairie » dans deux communes).
// L'index des arrêts du réseau donne leur position : c'est elle qui tranche.
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/**
 * Un arrêt proposé à la configuration.
 *
 * @param hint ce qui le distingue d'un autre arrêt du même nom sur la ligne, sinon null
 * @param near nom de l'arrêt voisin que cite [hint], s'il en cite un
 */
data class StopChoice(val stop: Stop, val hint: String? = null, val near: String? = null) {

    /**
     * Ce qui se cherche de l'arrêt : son nom, sa commune quand il en a une, et le nom du voisin qui
     * le situe. Pas la phrase de l'indice : « de » ou « nom » ramèneraient tous les homonymes.
     */
    val searchText: String get() = listOfNotNull(stop.name, stop.town, near).joinToString(" ")
}

object SameNameStops {

    /**
     * Deux arrêts de même nom d'une ligne, à cette distance ou moins, sont les deux sens d'un seul
     * arrêt. Le rapprochement se fait de proche en proche : trois quais en file en font un seul.
     */
    const val SAME_PLACE_M = 200

    /** Au-delà, un arrêt n'est plus le voisin d'un autre : le citer ne situerait rien. */
    const val NEIGHBOUR_MAX_M = 800

    /** Vrai si la ligne a au moins deux arrêts de même nom, à la casse et aux accents près. */
    fun hasSameNames(stops: List<Stop>): Boolean {
        val seen = HashSet<String>()
        return stops.any { !seen.add(normalizeLabel(it.name)) }
    }

    /**
     * Position des arrêts d'une ligne d'après l'index du réseau : pour chaque identifiant d'arrêt, le
     * milieu de ses quais desservis par la ligne.
     */
    fun places(area: StopIndexArea, lineId: String): Map<String, GeoPoint> {
        val quays = HashMap<String, MutableList<IndexedStop>>()
        area.stops.forEach { quay -> if (lineId in quay.lineIds) quays.getOrPut(quay.zone) { ArrayList() }.add(quay) }
        return quays.mapValues { (_, list) -> GeoPoint(list.sumOf { it.lat } / list.size, list.sumOf { it.lon } / list.size) }
    }

    /**
     * Les arrêts d'une ligne tels qu'ils se présentent au choix, dans l'ordre reçu. Deux arrêts de
     * même nom et proches n'en font qu'un, qui porte leurs deux identifiants : le sens se choisit à
     * l'étape suivante. Deux arrêts de même nom et éloignés restent deux, chacun avec l'arrêt voisin
     * qui le situe. Sans position (index indisponible), ou sans voisin qui les départage, ils
     * restent deux et sont numérotés.
     *
     * @param places position de chaque identifiant d'arrêt (cf. [places]) ; vide si l'index manque
     */
    fun choices(stops: List<Stop>, places: Map<String, GeoPoint> = emptyMap()): List<StopChoice> {
        val groups = LinkedHashMap<String, MutableList<Stop>>()
        stops.forEach { groups.getOrPut(normalizeLabel(it.name)) { ArrayList() }.add(it) }
        // Seul un arrêt au nom unique sur la ligne peut en situer un autre : « Près de Église » ne
        // dit rien quand la ligne a deux « Église ».
        val landmarks = groups.values.filter { it.size == 1 }
            .mapNotNull { group -> pointOf(group.first(), places)?.let { group.first().name to it } }
        val result = ArrayList<StopChoice>()
        groups.values.forEach { group ->
            if (group.size == 1) {
                result += StopChoice(group.first())
                return@forEach
            }
            val points = group.map { pointOf(it, places) }
            if (points.any { it == null }) {
                result += numbered(group)
                return@forEach
            }
            val clusters = cluster(group, points.filterNotNull())
            if (clusters.size == 1) {
                result += StopChoice(merge(clusters.single().map { it.first }))
                return@forEach
            }
            val merged = clusters.map { cluster -> merge(cluster.map { it.first }) to middle(cluster.map { it.second }) }
            val neighbours = merged.map { (_, point) -> neighbourOf(point, merged.map { it.second }, landmarks) }
            // Un voisin manquant, ou le même pour deux arrêts, ne distingue rien : mieux vaut numéroter.
            result += if (neighbours.any { it == null } || neighbours.map(::normalizeLabel).toSet().size < neighbours.size) {
                numbered(merged.map { it.first })
            } else {
                merged.mapIndexed { index, (stop, _) -> StopChoice(stop, "Près de ${neighbours[index]}", neighbours[index]) }
            }
        }
        return result
    }

    /**
     * L'arrêt qui situe celui-ci : le plus proche de lui, pourvu qu'il soit à portée de marche et plus
     * près de lui que de tout autre arrêt du même nom. Sans cela, l'indice désignerait le mauvais.
     */
    private fun neighbourOf(point: GeoPoint, sameName: List<GeoPoint>, landmarks: List<Pair<String, GeoPoint>>): String? {
        val (name, place) = landmarks.minByOrNull { it.second.distanceTo(point) } ?: return null
        val distance = place.distanceTo(point)
        if (distance > NEIGHBOUR_MAX_M) return null
        return name.takeIf { sameName.all { other -> other == point || place.distanceTo(other) > distance } }
    }

    private fun numbered(group: List<Stop>): List<StopChoice> =
        group.mapIndexed { index, stop -> StopChoice(stop, "Arrêt ${index + 1} sur ${group.size} de ce nom") }

    private fun pointOf(stop: Stop, places: Map<String, GeoPoint>): GeoPoint? {
        val points = stop.stopIds.map { places[it] ?: return null }
        return if (points.isEmpty()) null else middle(points)
    }

    private fun middle(points: List<GeoPoint>): GeoPoint =
        GeoPoint(points.sumOf { it.lat } / points.size, points.sumOf { it.lon } / points.size)

    /** Regroupe de proche en proche : un arrêt rejoint, et réunit, tous les groupes dont un membre est à portée. */
    private fun cluster(group: List<Stop>, points: List<GeoPoint>): List<List<Pair<Stop, GeoPoint>>> {
        val clusters = ArrayList<MutableList<Pair<Stop, GeoPoint>>>()
        group.zip(points).forEach { member ->
            val near = clusters.filter { cluster -> cluster.any { it.second.distanceTo(member.second) <= SAME_PLACE_M } }
            if (near.isEmpty()) {
                clusters += arrayListOf(member)
            } else {
                val home = near.first()
                near.drop(1).forEach { other ->
                    home.addAll(other)
                    clusters.remove(other)
                }
                home.add(member)
            }
        }
        return clusters
    }

    private fun merge(group: List<Stop>): Stop {
        val first = group.first()
        return if (group.size == 1) first else first.copy(stopIds = group.flatMap { it.stopIds }.distinct())
    }
}
