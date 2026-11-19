package fr.gotraceo.live.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * L'index des arrêts d'un grand réseau, à l'échelle du plus gros du plan : 7 422 quais et 202 lignes.
 * L'index est fabriqué ici, à la forme que sert Tracéo ; seuls ses nombres sont ceux du plan. Le test
 * borne ce qui compte dans le processus d'un widget : la taille de la réponse, celle du fichier gardé
 * sur le téléphone, et ce qu'une recherche autour d'un point en lit.
 *
 * Les durées et la mémoire sont mesurées sur la machine qui lance les tests, pas sur un téléphone :
 * elles sont affichées, et bornées très largement pour ne signaler qu'un changement d'ordre de grandeur.
 */
class IndexScaleTest {

    private companion object {
        const val NETWORK = "grande-ville"
        const val INDEX_PATH = "/api/reseaux/$NETWORK/index"
        const val QUAYS = 7_422
        const val LINES = 202

        /** Emprise d'une grande agglomération : une quarantaine de kilomètres de côté. */
        const val LAT_MIN = 44.70
        const val LON_MIN = -0.80
        const val SPAN = 0.36

        const val MEGABYTE = 1024 * 1024
    }

    private lateinit var server: FakeServer
    private val files = ArrayList<File>()

    @Before
    fun setUp() {
        server = FakeServer()
    }

    @After
    fun tearDown() {
        server.close()
        files.forEach { it.delete() }
    }

    /** Générateur déterministe : le même index à chaque lancement. */
    private class Lcg(private var state: Long) {
        fun next(bound: Int): Int {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            return ((state ushr 33) % bound).toInt()
        }
    }

    private val words = listOf(
        "Gare", "Mairie", "Église", "Stade", "Collège", "Lycée", "Hôpital", "Place", "Avenue", "Rue", "Pont",
        "Quai", "Parc", "Marché", "Centre", "Bourg", "Les Aubiers", "Saint-Jean", "Jean Jaurès", "Victor Hugo",
        "République", "Libération", "Général de Gaulle", "Quatre Pavillons", "Cité Administrative",
    )

    private fun indexJson(): String {
        val random = Lcg(42)
        val json = StringBuilder(1_200_000)
        json.append("""{"version": 1, "builtAt": "2026-11-18T03:10:00Z", "lines": [""")
        for (i in 0 until LINES) {
            if (i > 0) json.append(", ")
            val mode = when {
                i < 4 -> "tram"
                i < 6 -> "boat"
                else -> "bus"
            }
            // Un tiers des lignes sans couleur publiée, comme dans le plus gros réseau du plan.
            val color = if (i % 3 == 0) "null" else "\"#%06X\"".format(random.next(0xFFFFFF))
            val text = if (i % 3 == 0) "null" else "\"#FFFFFF\""
            json.append(
                """{"id": "${1000 + i}", "code": "${i + 1}", "name": "${words[i % words.size]} - ${words[(i * 7 + 3) % words.size]}", """ +
                    """"color": $color, "textColor": $text, "mode": {"key": "$mode", "label": "$mode"}}""",
            )
        }
        json.append("""], "stops": [""")
        for (i in 0 until QUAYS) {
            if (i > 0) json.append(", ")
            val name = "${words[random.next(words.size)]} ${words[random.next(words.size)]}"
            val lat = LAT_MIN + random.next(36_000) / 100_000.0
            val lon = LON_MIN + random.next(36_000) / 100_000.0
            val served = (0..random.next(4)).map { random.next(LINES) }.distinct()
            // Deux quais par zone, comme deux sens d'un arrêt.
            json.append(
                """{"zone": "1:Z${i / 2}", "name": "$name", "town": null, "lat": ${"%.5f".format(java.util.Locale.ROOT, lat)}, """ +
                    """"lon": ${"%.5f".format(java.util.Locale.ROOT, lon)}, "lines": $served}""",
            )
        }
        json.append("]}")
        return json.toString()
    }

    /**
     * Octets alloués par ce fil depuis son démarrage, si la machine virtuelle sait le dire. Les classes
     * de mesure ne font pas partie d'Android : elles sont cherchées à l'exécution.
     */
    private fun allocatedBytes(): Long? = try {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
            .invoke(bean, Thread.currentThread().id) as Long
    } catch (e: Throwable) {
        null
    }

    @Test
    fun theIndexOfALargeNetworkFitsAWidgetProcess() {
        val body = indexJson()
        val bodyBytes = body.toByteArray(Charsets.UTF_8).size
        server.respond(INDEX_PATH, 200, body)
        val file = File.createTempFile("index", ".bin").also { files += it }
        val store = StopIndexStore(file, NETWORK) { TraceoClient(server.url).index(it) }

        // Téléchargement : la réponse entière est lue, puis décodée, puis écrite.
        val allocatedBefore = allocatedBytes()
        val downloadStarted = System.nanoTime()
        val stats = store.download()
        val downloadMs = (System.nanoTime() - downloadStarted) / 1_000_000
        val allocated = allocatedBefore?.let { before -> allocatedBytes()?.minus(before) }

        assertEquals(QUAYS, stats.stops)
        assertEquals(LINES, stats.lines)
        assertEquals(0, stats.skippedRows)

        // Recherche autour d'un point, au centre : c'est ce que fait un widget à chaque position lue.
        val centre = GeoPoint(LAT_MIN + SPAN / 2, LON_MIN + SPAN / 2)
        val readStarted = System.nanoTime()
        var area: StopIndexArea? = null
        repeat(20) { area = store.around(centre.lat, centre.lon, NearbyRules.READ_RADIUS_M) }
        val readMs = (System.nanoTime() - readStarted) / 1_000_000.0 / 20
        val around = assertNotNullArea(area)

        println(
            "Index de $QUAYS quais et $LINES lignes : réponse ${bodyBytes / 1024} Ko, fichier ${file.length() / 1024} Ko, " +
                "téléchargement et écriture $downloadMs ms, mémoire allouée ${allocated?.div(MEGABYTE)} Mo, " +
                "recherche autour d'un point ${"%.1f".format(readMs)} ms pour ${around.stops.size} quais gardés",
        )

        // La réponse tient sous la limite du client HTTP, avec de la marge pour un réseau plus gros encore.
        assertTrue("Réponse de $bodyBytes octets", bodyBytes < Http.MAX_BODY_BYTES / 4)
        // Le fichier du téléphone est plus petit que la réponse : pas de texte répété, coordonnées entières.
        assertTrue("Fichier de ${file.length()} octets", file.length() < bodyBytes / 2)
        // Une recherche ne garde que le voisinage : les autres quais sont sautés sans être décodés.
        assertTrue("${around.stops.size} quais gardés", around.stops.size in 1 until QUAYS / 10)
        assertTrue(around.stops.all { GeoPoint(it.lat, it.lon).distanceTo(centre) <= NearbyRules.READ_RADIUS_M * 2 })
        // Ordres de grandeur seulement : une lecture en secondes, ou des centaines de mégaoctets, seraient un défaut.
        assertTrue("Téléchargement en $downloadMs ms", downloadMs < 10_000)
        assertTrue("Recherche en $readMs ms", readMs < 1_000)
        if (allocated != null) assertTrue("${allocated / MEGABYTE} Mo alloués", allocated < 200L * MEGABYTE)
    }

    private fun assertNotNullArea(area: StopIndexArea?): StopIndexArea {
        assertNotNull(area)
        return area!!
    }
}
