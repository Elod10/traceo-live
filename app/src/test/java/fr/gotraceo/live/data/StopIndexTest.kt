package fr.gotraceo.live.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class StopIndexTest {

    private val m1 = Line("C01371", "1", "1", "metro", color = "FFBE00", textColor = "000000")
    private val bus = Line("C01127", "96", "96", "bus", subMode = "localBus", network = "RATP", operator = "RATP", color = "DC9600")
    private val rer = Line("C01742", "RER A", "A", "rail", network = "RER", color = "EB2132", textColor = "FFFFFF")

    private val arrets = "\uFEFFarrid;zdaid\n22091;1001\n39821;1002\n99999;1003\n"
    private val zones = "zdaid;zdcid\n1001;415852\n1002;415852\n45102;73794\n"
    private val stopLines = """
        id;stop_id;stop_name;nom_commune;stop_lon;stop_lat
        IDFM:C01371;IDFM:22091;Hôtel de Ville;Paris;2.3510;48.8575
        IDFM:C01127;IDFM:22091;Hôtel de Ville;Paris;2.3510;48.8575
        IDFM:C01127;IDFM:39821;Coutellerie;Paris;2.3500;48.8570
        IDFM:C01742;IDFM:monomodalStopPlace:45102;Châtelet - Les Halles;Paris;2.3469;48.8618
        IDFM:C09999;IDFM:22091;Hôtel de Ville;Paris;2.3510;48.8575
        IDFM:C01371;IDFM:99999;Sans zone;Paris;2.3510;48.8575
        IDFM:C01371;IDFM:88888;Inconnu;Paris;2.3510;48.8575
        IDFM:C01371;IDFM:22091;;Paris;2.3510;48.8575
    """.trimIndent() + "\n"

    private fun build(saved: Long = 1234L): Pair<ByteArray, StopIndexBuilder.Stats> {
        val out = ByteArrayOutputStream()
        val stats = StopIndexBuilder.build(
            listOf(m1, bus, rer),
            arrets.reader().buffered(),
            zones.reader().buffered(),
            stopLines.reader().buffered(),
            out,
            saved,
        )
        return out.toByteArray() to stats
    }

    @Test
    fun csvSplitsPlainAndQuotedFields() {
        assertEquals(listOf("a", "b", ""), Csv.split("a;b;"))
        assertEquals(listOf("Saint-Denis; Porte", "2"), Csv.split("\"Saint-Denis; Porte\";2"))
        assertEquals(listOf("dit \"Le Pont\"", "x"), Csv.split("\"dit \"\"Le Pont\"\"\";x"))
    }

    @Test
    fun csvToleratesBomAndReordersColumns() {
        val seen = ArrayList<List<String>>()
        Csv.read("\uFEFFa;b\n1;2\n\n3;4\n".reader().buffered(), listOf("b", "a")) { seen.add(it) }
        assertEquals(listOf(listOf("2", "1"), listOf("4", "3")), seen)
    }

    @Test(expected = java.io.IOException::class)
    fun csvRefusesAMissingColumn() {
        Csv.read("a;b\n1;2\n".reader().buffered(), listOf("c")) { }
    }

    @Test
    fun buildKeepsOnlyRowsWithAKnownLineAndAZone() {
        val (_, stats) = build()
        assertEquals(3, stats.stops)
        assertEquals(3, stats.lines)
        // Ligne inconnue, arrêt sans zone (99999), arrêt absent (88888) et arrêt sans nom.
        assertEquals(4, stats.skippedRows)
    }

    @Test
    fun aMonomodalStopPlaceIsAlreadyAnArea() {
        val area = StopIndexFile.around(ByteArrayInputStream(build().first), 48.8618, 2.3469, 50)
        val stop = area.stops.single()
        assertEquals("Châtelet - Les Halles", stop.name)
        assertEquals("73794", stop.zone)
        assertEquals(listOf("C01742"), stop.lineIds)
    }

    @Test
    fun aroundReturnsTheLinesAndSkipsFarStops() {
        val area = StopIndexFile.around(ByteArrayInputStream(build().first), 48.8575, 2.3510, 150)
        assertEquals(setOf("Hôtel de Ville", "Coutellerie"), area.stops.map { it.name }.toSet())
        assertEquals(setOf("C01371", "C01127"), area.lines.keys)
        val hotel = area.stops.first { it.name == "Hôtel de Ville" }
        assertEquals("Paris", hotel.town)
        assertEquals("415852", hotel.zone)
        assertEquals(48.8575, hotel.lat, 1e-5)
        assertEquals(2.3510, hotel.lon, 1e-5)
        assertEquals(setOf("C01371", "C01127"), hotel.lineIds.toSet())
    }

    @Test
    fun linesSurviveTheFile() {
        val area = StopIndexFile.around(ByteArrayInputStream(build().first), 48.8575, 2.3510, 150)
        assertEquals(bus, area.lines["C01127"])
        assertEquals(m1, area.lines["C01371"])
    }

    @Test
    fun savedAtIsReadWithoutTheRest() {
        assertEquals(1234L, StopIndexFile.savedAt(ByteArrayInputStream(build(1234L).first)))
        assertNull(StopIndexFile.savedAt(ByteArrayInputStream(ByteArray(3))))
        assertNull(StopIndexFile.savedAt(ByteArrayInputStream(ByteArray(64))))
    }

    @Test(expected = java.io.IOException::class)
    fun aroundRefusesAnotherFile() {
        StopIndexFile.around(ByteArrayInputStream(ByteArray(64)), 48.8, 2.3, 100)
    }

    @Test
    fun storeSaysWhenToRefresh() {
        val file = File.createTempFile("index", ".bin")
        try {
            val saved = 10_000_000_000L
            file.writeBytes(build(saved).first)
            val store = StopIndexStore(file)
            assertEquals(saved, store.savedAt())
            val day = 24L * 60 * 60 * 1000
            assertFalse(store.isStale(saved + 6 * day, unmetered = true))
            assertTrue(store.isStale(saved + 7 * day, unmetered = true))
            assertFalse(store.isStale(saved + 29 * day, unmetered = false))
            assertTrue(store.isStale(saved + 30 * day, unmetered = false))
            assertNotNull(store.around(48.8575, 2.3510, 150))
        } finally {
            file.delete()
        }
    }

    @Test
    fun aMissingOrBrokenFileMeansNoIndex() {
        val missing = StopIndexStore(File("n-existe-pas.bin"))
        assertNull(missing.savedAt())
        assertTrue(missing.isStale(0L, unmetered = false))
        assertNull(missing.around(48.8, 2.3, 100))

        val broken = File.createTempFile("index", ".bin")
        try {
            broken.writeBytes(ByteArray(64))
            assertNull(StopIndexStore(broken).savedAt())
            assertNull(StopIndexStore(broken).around(48.8, 2.3, 100))
        } finally {
            broken.delete()
        }
    }

    // --- Lignes sans temps réel (Q11 de Tracéo) -------------------------------------------------

    /** Un fichier de la version 1, d'avant `realtime` : une ligne, un quai. */
    private fun versionOne(savedAt: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeInt(0x54524958)
        data.writeInt(1)
        data.writeLong(savedAt)
        data.writeInt(1)
        listOf("2B", "Bréviandes - Voves", "2B", "bus", "", "", "", "F29400", "FFFFFF").forEach(data::writeUTF)
        data.writeInt(1)
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply {
            writeUTF("Acacias")
            writeUTF("")
            writeUTF("1:ACACI")
            writeShort(1)
            writeShort(0)
            flush()
        }
        data.writeInt(4_829_731)
        data.writeInt(407_412)
        data.writeShort(payload.size())
        payload.writeTo(data)
        data.flush()
        return out.toByteArray()
    }

    @Test
    fun theLinesWithoutRealtimeAreKeptInTheFile() {
        val cable = Line("C", "Téléphérique", "C", "cableway", networkId = "brest", realtime = false)
        val tram = Line("A", "Tram A", "A", "tram", networkId = "brest")
        val out = ByteArrayOutputStream()
        StopIndexFile.write(out, 5L, listOf(cable, tram), listOf(IndexedStop("Capucins", null, 48.38, -4.50, "CAP", listOf("C", "A"))))
        val bytes = out.toByteArray()

        assertTrue(StopIndexFile.header(ByteArrayInputStream(bytes))!!.knowsRealtime)
        val area = StopIndexFile.all(ByteArrayInputStream(bytes), "brest")
        assertFalse(area.lines.getValue("C").realtime)
        assertTrue(area.lines.getValue("A").realtime)
        assertEquals(listOf("C", "A"), area.stops.single().lineIds)
    }

    @Test
    fun aFileOfTheFirstVersionIsStillReadWithRealtimeEverywhere() {
        val bytes = versionOne(1234L)
        val header = StopIndexFile.header(ByteArrayInputStream(bytes))!!
        assertEquals(1234L, header.savedAt)
        assertFalse(header.knowsRealtime)
        assertEquals(1234L, StopIndexFile.savedAt(ByteArrayInputStream(bytes)))

        val area = StopIndexFile.around(ByteArrayInputStream(bytes), 48.2973, 4.0741, 100, "troyes")
        assertEquals(listOf("1:ACACI"), area.stops.map { it.zone })
        assertTrue(area.lines.getValue("2B").realtime)
    }

    @Test
    fun anIndexFromTraceoWithoutRealtimeIsReloadedOnce() {
        val file = File.createTempFile("index", ".bin")
        try {
            file.writeBytes(versionOne(1234L))
            assertEquals(1234L, StopIndexStore(file, "troyes").savedAt())
            assertTrue(StopIndexStore(file, "troyes").isOutdated(1000L))
            assertTrue(StopIndexStore(file, "troyes").isOutdated(null))
            // Celui de l'Île-de-France, que le téléphone construit, n'a que des lignes à temps réel.
            assertFalse(StopIndexStore(file).isOutdated(1000L))
        } finally {
            file.delete()
        }
    }
}
