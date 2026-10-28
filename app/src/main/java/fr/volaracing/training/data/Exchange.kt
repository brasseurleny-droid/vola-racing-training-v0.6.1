package fr.volaracing.training.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.volaracing.training.core.GpsRow
import fr.volaracing.training.core.Imu
import fr.volaracing.training.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Echange hors ligne entre amis : un parcours (.volacourse, JSON) ou une session (.volasession, ZIP avec
 * manifest, trace GPS et IMU decimee de chaque run), partages par la feuille de partage Android.
 * Aucun serveur, aucune transmission automatique. Dedoublonnage par uuid.
 */
object Exchange {
    private const val MAX_BYTES = 60L * 1024L * 1024L
    private const val GPS_HEADER = "elapsed_ns,utc_ms,lat,lon,alt_m,speed_ms,bearing_deg,h_acc_m,speed_acc_ms,satellites"
    private const val IMU_HEADER = "elapsed_ns,type,x,y,z"

    private fun courseObj(c: Course): JSONObject = JSONObject().apply {
        put("uuid", c.uuid)
        put("name", c.name)
        put("activity", c.activity)
        put("aLat", c.aLat ?: JSONObject.NULL)
        put("aLon", c.aLon ?: JSONObject.NULL)
        put("bLat", c.bLat ?: JSONObject.NULL)
        put("bLon", c.bLon ?: JSONObject.NULL)
        put("gateWidth", c.gateWidth)
        put("waypoints", c.waypoints)
    }

    private fun dbl(o: JSONObject, k: String): Double? {
        if (o.isNull(k)) return null
        val v = o.optDouble(k)
        return if (v.isNaN()) null else v
    }

    private fun parseCourse(o: JSONObject, groupId: String): Course = Course(
        name = o.optString("name", "Parcours"),
        activity = o.optString("activity", "VTT"),
        aLat = dbl(o, "aLat"), aLon = dbl(o, "aLon"), bLat = dbl(o, "bLat"), bLon = dbl(o, "bLon"),
        gateWidth = o.optDouble("gateWidth", 20.0),
        waypoints = o.optString("waypoints", ""),
        uuid = o.optString("uuid", ""),
        groupId = groupId,
    )

    private fun safe(name: String): String =
        name.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(40).ifEmpty { "x" }

    private fun shareFile(ctx: Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        return Intent(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun courseShareIntent(ctx: Context, c: Course): Intent {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "parcours_${safe(c.name)}.volacourse")
        f.writeText(JSONObject().put("type", "volacourse").put("version", 1).put("course", courseObj(c)).toString())
        return shareFile(ctx, f)
    }

    private fun gpsLine(r: GpsRow) =
        "${r.tNs},${r.utcMs},${r.lat},${r.lon},${r.alt},${r.speed},${r.bearing},${r.hAcc},${r.sAcc},${r.sats}"

    private fun sessionTitleOf(s: Session) = s.name.ifBlank { "session_${s.startUtcMs}" }

    /** Paquet de session : trace GPS fenetree et IMU decimee (10 Hz) de chaque run, sans les fichiers bruts complets. */
    fun sessionShareIntent(ctx: Context, s: Session, runs: List<Passage>, course: Course?, prefs: Prefs): Intent {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "session_${safe(sessionTitleOf(s))}.volasession")
        val uuid = s.uuid.ifEmpty { UUID.randomUUID().toString() }
        val manifest = JSONObject()
        manifest.put("type", "volasession")
        manifest.put("version", 1)
        manifest.put("athlete", JSONObject().put("id", prefs.athleteId).put("name", prefs.pseudo).put("color", prefs.athleteColor))
        manifest.put(
            "session",
            JSONObject().put("uuid", uuid).put("name", s.name).put("place", s.place)
                .put("activity", s.activity).put("startUtcMs", s.startUtcMs).put("courseUuid", course?.uuid ?: ""),
        )
        if (course != null) manifest.put("course", courseObj(course))
        val arr = JSONArray()
        ZipOutputStream(f.outputStream().buffered()).use { z ->
            runs.sortedBy { it.id }.forEachIndexed { k, p ->
                val i = k + 1
                arr.put(
                    JSONObject().put("index", i).put("timeNs", p.timeNs).put("kind", p.kind).put("vMax", p.vMax)
                        .put("gMax", p.gMax).put("startNs", p.startNs).put("endNs", p.endNs).put("splits", p.splits)
                )
                val rows = Track.window(Csv.readGps(File(p.path, "gps.csv")), p.startNs, p.endNs)
                val gps = StringBuilder(GPS_HEADER).append('\n')
                rows.forEach { gps.append(gpsLine(it)).append('\n') }
                z.putNextEntry(ZipEntry("run_$i/gps.csv")); z.write(gps.toString().toByteArray()); z.closeEntry()
                val imu = StringBuilder(IMU_HEADER).append('\n')
                Imu.toLines(ImuReader.read(File(p.path, "imu.csv"), p.startNs, p.endNs), p.startNs)
                    .forEach { imu.append(it).append('\n') }
                z.putNextEntry(ZipEntry("run_$i/imu.csv")); z.write(imu.toString().toByteArray()); z.closeEntry()
            }
            manifest.put("runs", arr)
            z.putNextEntry(ZipEntry("manifest.json")); z.write(manifest.toString().toByteArray()); z.closeEntry()
        }
        return shareFile(ctx, f)
    }

    /** Importe un paquet (parcours ou session). Retourne le message a afficher. */
    suspend fun import(ctx: Context, uri: Uri, dao: AppDao, groupId: String): String = withContext(Dispatchers.IO) {
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext "Fichier illisible."
            if (bytes.size < 4 || bytes.size > MAX_BYTES) return@withContext "Fichier invalide ou trop volumineux."
            if (bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) importSession(ctx, bytes, dao, groupId)
            else importCourse(String(bytes, Charsets.UTF_8), dao, groupId)
        } catch (e: Exception) {
            "Import impossible : ${e.message}"
        }
    }

    private suspend fun importCourse(json: String, dao: AppDao, groupId: String): String {
        val o = JSONObject(json)
        if (o.optString("type") != "volacourse") return "Ce fichier n'est pas un parcours Vola."
        val c = parseCourse(o.getJSONObject("course"), groupId)
        if (c.uuid.isNotEmpty() && dao.courseByUuid(c.uuid) != null) return "Parcours « ${c.name} » déjà présent."
        dao.insertCourse(c.copy(uuid = c.uuid.ifEmpty { UUID.randomUUID().toString() }))
        return "Parcours « ${c.name} » importé."
    }

    private suspend fun importSession(ctx: Context, bytes: ByteArray, dao: AppDao, groupId: String): String {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory && files.size < 400) files[e.name] = zis.readBytes()
                e = zis.nextEntry
            }
        }
        val mb = files["manifest.json"] ?: return "Paquet invalide (manifeste manquant)."
        val m = JSONObject(String(mb, Charsets.UTF_8))
        if (m.optString("type") != "volasession") return "Ce fichier n'est pas une session Vola."
        val sess = m.getJSONObject("session")
        val ath = m.optJSONObject("athlete")
        val uuid = sess.optString("uuid", "")
        if (uuid.isNotEmpty() && dao.sessionByUuid(uuid) != null) return "Cette session est déjà importée."
        val safeUuid = uuid.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { UUID.randomUUID().toString() }

        var courseId = -1L
        m.optJSONObject("course")?.let { co ->
            val c = parseCourse(co, groupId)
            val existing = if (c.uuid.isNotEmpty()) dao.courseByUuid(c.uuid) else null
            courseId = existing?.id ?: dao.insertCourse(c.copy(uuid = c.uuid.ifEmpty { UUID.randomUUID().toString() }))
        }

        val owner = ath?.optString("name", "Ami") ?: "Ami"
        val sid = dao.insertSession(
            Session(
                courseId = courseId, startUtcMs = sess.optLong("startUtcMs", 0L),
                name = sess.optString("name", ""), place = sess.optString("place", ""),
                activity = sess.optString("activity", "VTT"), uuid = safeUuid,
                ownerId = ath?.optString("id", "") ?: "", ownerName = owner,
                ownerColor = ath?.optInt("color", 0) ?: 0, origin = 1, groupId = groupId,
            )
        )
        val base = File(ctx.getExternalFilesDir(null), "imports/$safeUuid")
        val arr = m.optJSONArray("runs") ?: JSONArray()
        var n = 0
        for (k in 0 until arr.length()) {
            val ro = arr.getJSONObject(k)
            val i = ro.getInt("index")
            val gps = files["run_$i/gps.csv"] ?: continue
            val d = File(base, "run_$i")
            d.mkdirs()
            File(d, "gps.csv").writeBytes(gps)
            files["run_$i/imu.csv"]?.let { File(d, "imu.csv").writeBytes(it) }
            dao.insertPassage(
                Passage(
                    sessionId = sid, courseId = courseId, timeNs = ro.getLong("timeNs"), lowQuality = false,
                    vMax = ro.optDouble("vMax", 0.0), gMax = ro.optDouble("gMax", 0.0),
                    startNs = ro.getLong("startNs"), endNs = ro.getLong("endNs"),
                    path = d.absolutePath, splits = ro.optString("splits", ""), kind = ro.optInt("kind", 1),
                )
            )
            n++
        }
        return "Session de $owner importée : $n run(s)."
    }
}
