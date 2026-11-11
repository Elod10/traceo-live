package fr.gotraceo.live.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.roundToInt

// Index local des arrêts : tout l'open data utile au mode automatique, dans un fichier du téléphone.
// La recherche des arrêts proches se fait donc sans réseau, sans quota, et la position ne quitte pas
// l'appareil. Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

/** Un point d'arrêt de l'index. [zone] est la zone comprise par Tracéo et par PRIM. */
data class IndexedStop(
    val name: String,
    val town: String?,
    val lat: Double,
    val lon: Double,
    val zone: String,
    val lineIds: List<String>,
)

/** Les arrêts d'un secteur, avec la description de leurs lignes. */
class StopIndexArea(val lines: Map<String, Line>, val stops: List<IndexedStop>)

/** Lecture de CSV au fil de l'eau : séparateur « ; », guillemets possibles, BOM en tête toléré. */
object Csv {

    fun split(line: String): List<String> {
        if (line.indexOf('"') < 0) return line.split(';')
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> {
                    out.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        out.add(current.toString())
        return out
    }

    /** Appelle [row] avec les valeurs des [columns], dans cet ordre. */
    @Throws(IOException::class)
    fun read(reader: BufferedReader, columns: List<String>, row: (List<String>) -> Unit) {
        val header = reader.readLine()?.removePrefix("\uFEFF") ?: throw IOException("Fichier vide")
        val names = split(header).map { it.trim() }
        val indexes = columns.map { column ->
            names.indexOf(column).also { if (it < 0) throw IOException("Colonne absente : $column") }
        }
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) continue
            val fields = split(line)
            row(indexes.map { fields.getOrElse(it) { "" } })
        }
    }
}

/** Compile les jeux open data en un index compact. */
object StopIndexBuilder {

    data class Stats(val stops: Int, val lines: Int, val skippedRows: Int)

    private class Acc(val name: String, val town: String?, val lat: Double, val lon: Double, val zone: String) {
        val lineIds = LinkedHashSet<String>()
    }

    /**
     * @param lines référentiel des lignes
     * @param arrets colonnes `arrid`, `zdaid` du jeu « arrets »
     * @param zones colonnes `zdaid`, `zdcid` du jeu « zones-d-arrets »
     * @param stopLines colonnes `id`, `stop_id`, `stop_name`, `nom_commune`, `stop_lon`, `stop_lat` du jeu « arrets-lignes »
     */
    @Throws(IOException::class)
    fun build(
        lines: List<Line>,
        arrets: BufferedReader,
        zones: BufferedReader,
        stopLines: BufferedReader,
        out: OutputStream,
        savedAt: Long,
    ): Stats {
        val areaByStop = HashMap<String, String>()
        Csv.read(arrets, listOf("arrid", "zdaid")) { (arrid, zdaid) -> areaByStop[arrid] = zdaid }
        val zoneByArea = HashMap<String, String>()
        Csv.read(zones, listOf("zdaid", "zdcid")) { (zdaid, zdcid) -> zoneByArea[zdaid] = zdcid }

        val known = lines.associateBy { it.id }
        val stops = LinkedHashMap<String, Acc>()
        var skipped = 0
        Csv.read(stopLines, listOf("id", "stop_id", "stop_name", "nom_commune", "stop_lon", "stop_lat")) { fields ->
            val lineId = Referential.bareLineId(fields[0])
            val stopId = fields[1]
            val bare = stopId.substringAfterLast(':')
            val area = if (stopId.contains("monomodal", ignoreCase = true)) bare else areaByStop[bare]
            val zone = area?.let(zoneByArea::get)
            val lat = fields[5].toDoubleOrNull()
            val lon = fields[4].toDoubleOrNull()
            if (lineId !in known || zone == null || lat == null || lon == null || fields[2].isBlank()) {
                skipped++
                return@read
            }
            stops.getOrPut(stopId) { Acc(fields[2].trim(), fields[3].trim().ifEmpty { null }, lat, lon, zone) }
                .lineIds.add(lineId)
        }

        val used = stops.values.flatMapTo(LinkedHashSet()) { it.lineIds }.mapNotNull(known::get)
        StopIndexFile.write(out, savedAt, used, stops.values.map { IndexedStop(it.name, it.town, it.lat, it.lon, it.zone, it.lineIds.toList()) })
        return Stats(stops.size, used.size, skipped)
    }
}

/** Format du fichier : les lignes, puis un enregistrement par arrêt, qu'on peut sauter sans le décoder. */
object StopIndexFile {

    private const val MAGIC = 0x54524958
    private const val VERSION = 1
    private const val COORD_SCALE = 100_000.0
    private const val METERS_PER_DEGREE = 111_320.0

    @Throws(IOException::class)
    fun write(out: OutputStream, savedAt: Long, lines: List<Line>, stops: List<IndexedStop>) {
        val data = DataOutputStream(BufferedOutputStream(out))
        data.writeInt(MAGIC)
        data.writeInt(VERSION)
        data.writeLong(savedAt)
        data.writeInt(lines.size)
        val indexOfLine = HashMap<String, Int>()
        lines.forEachIndexed { index, line ->
            indexOfLine[line.id] = index
            listOf(line.id, line.name, line.shortName, line.mode, line.subMode, line.network, line.operator, line.color, line.textColor)
                .forEach { data.writeUTF(it.orEmpty()) }
        }
        data.writeInt(stops.size)
        val buffer = ByteArrayOutputStream()
        stops.forEach { stop ->
            buffer.reset()
            val payload = DataOutputStream(buffer)
            payload.writeUTF(stop.name)
            payload.writeUTF(stop.town.orEmpty())
            payload.writeUTF(stop.zone)
            payload.writeShort(stop.lineIds.size)
            stop.lineIds.forEach { payload.writeShort(indexOfLine.getValue(it)) }
            payload.flush()
            data.writeInt((stop.lat * COORD_SCALE).roundToInt())
            data.writeInt((stop.lon * COORD_SCALE).roundToInt())
            data.writeShort(buffer.size())
            buffer.writeTo(data)
        }
        data.flush()
    }

    /** Date de constitution du fichier, ou null s'il est illisible. */
    fun savedAt(input: InputStream): Long? = try {
        val data = DataInputStream(input)
        if (data.readInt() == MAGIC && data.readInt() == VERSION) data.readLong() else null
    } catch (e: IOException) {
        null
    }

    /** Arrêts situés dans le carré de côté 2 × [radiusM] autour du point : les autres sont sautés. */
    @Throws(IOException::class)
    fun around(input: InputStream, lat: Double, lon: Double, radiusM: Int): StopIndexArea {
        val data = DataInputStream(BufferedInputStream(input))
        if (data.readInt() != MAGIC || data.readInt() != VERSION) throw IOException("Index d'arrêts illisible")
        data.readLong()
        val lineCount = data.readInt()
        val lines = ArrayList<Line>(lineCount)
        repeat(lineCount) {
            val values = List(9) { data.readUTF() }
            lines.add(
                Line(
                    id = values[0],
                    name = values[1],
                    shortName = values[2],
                    mode = values[3],
                    subMode = values[4].ifEmpty { null },
                    network = values[5].ifEmpty { null },
                    operator = values[6].ifEmpty { null },
                    color = values[7].ifEmpty { null },
                    textColor = values[8].ifEmpty { null },
                ),
            )
        }
        val dLat = radiusM / METERS_PER_DEGREE
        val dLon = radiusM / (METERS_PER_DEGREE * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        val minLat = ((lat - dLat) * COORD_SCALE).toInt()
        val maxLat = ((lat + dLat) * COORD_SCALE).toInt()
        val minLon = ((lon - dLon) * COORD_SCALE).toInt()
        val maxLon = ((lon + dLon) * COORD_SCALE).toInt()

        val stops = ArrayList<IndexedStop>()
        val usedLines = HashMap<String, Line>()
        repeat(data.readInt()) {
            val stopLat = data.readInt()
            val stopLon = data.readInt()
            val size = data.readUnsignedShort()
            if (stopLat !in minLat..maxLat || stopLon !in minLon..maxLon) {
                skipFully(data, size)
                return@repeat
            }
            val bytes = ByteArray(size)
            data.readFully(bytes)
            val payload = DataInputStream(ByteArrayInputStream(bytes))
            val name = payload.readUTF()
            val town = payload.readUTF().ifEmpty { null }
            val zone = payload.readUTF()
            val ids = List(payload.readUnsignedShort()) { lines[payload.readUnsignedShort()] }
            ids.forEach { usedLines[it.id] = it }
            stops.add(IndexedStop(name, town, stopLat / COORD_SCALE, stopLon / COORD_SCALE, zone, ids.map { it.id }))
        }
        return StopIndexArea(usedLines, stops)
    }

    private fun skipFully(data: DataInputStream, size: Int) {
        var left = size
        while (left > 0) {
            val skipped = data.skipBytes(left)
            if (skipped <= 0) {
                if (data.read() < 0) throw EOFException()
                left--
            } else {
                left -= skipped
            }
        }
    }
}

/** Le fichier d'index sur le téléphone : téléchargement, mise à jour et lecture. */
class StopIndexStore(private val file: File) {

    fun savedAt(): Long? {
        if (!file.exists()) return null
        return try {
            file.inputStream().use(StopIndexFile::savedAt)
        } catch (e: IOException) {
            null
        }
    }

    /** Vrai s'il faut retélécharger : chaque semaine en Wi-Fi, chaque mois sur le réseau mobile. */
    fun isStale(now: Long, unmetered: Boolean): Boolean {
        val saved = savedAt() ?: return true
        return now - saved >= if (unmetered) REFRESH_UNMETERED_MS else REFRESH_METERED_MS
    }

    /** Remplace l'index. En cas d'échec, le fichier précédent reste en place. */
    @Throws(IOException::class)
    fun download(now: Long = System.currentTimeMillis()): StopIndexBuilder.Stats = synchronized(LOCK) {
        val lines = Referential.downloadLines(ALL_MODES)
        if (lines.isEmpty()) throw IOException("Référentiel des lignes vide")
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        val arrets = File(file.parentFile, file.name + ".arrets")
        val zones = File(file.parentFile, file.name + ".zones")
        try {
            saveTo(arrets, Referential.csvUrl("arrets", "arrid,zdaid"))
            saveTo(zones, Referential.csvUrl("zones-d-arrets", "zdaid,zdcid"))
            val stats = Http.stream(Referential.csvUrl("arrets-lignes", "id,stop_id,stop_name,nom_commune,stop_lon,stop_lat")) { input ->
                tmp.outputStream().use { out ->
                    arrets.bufferedReader().use { a ->
                        zones.bufferedReader().use { z ->
                            StopIndexBuilder.build(lines, a, z, input.bufferedReader(), out, now)
                        }
                    }
                }
            }
            if (stats.stops == 0) throw IOException("Aucun arrêt dans les données reçues")
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("Écriture de l'index impossible")
            }
            stats
        } finally {
            tmp.delete()
            arrets.delete()
            zones.delete()
        }
    }

    private fun saveTo(target: File, url: String) {
        Http.stream(url) { input -> target.outputStream().use { input.copyTo(it) } }
    }

    /** Arrêts autour du point, ou null si l'index n'existe pas encore ou ne se lit plus. */
    fun around(lat: Double, lon: Double, radiusM: Int): StopIndexArea? {
        if (!file.exists()) return null
        return try {
            file.inputStream().use { StopIndexFile.around(it, lat, lon, radiusM) }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        private val LOCK = Any()
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val REFRESH_UNMETERED_MS = 7 * DAY_MS
        private const val REFRESH_METERED_MS = 30 * DAY_MS

        val ALL_MODES: List<String> = TransportMode.IDFM.flatMap { it.apiModes }.distinct()
    }
}
