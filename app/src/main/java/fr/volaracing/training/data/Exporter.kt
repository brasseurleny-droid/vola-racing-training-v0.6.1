package fr.volaracing.training.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import fr.volaracing.training.core.GpsRow
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    private val CSV_FILES = listOf("gps.csv", "imu.csv", "marks.csv")

    fun gpx(rows: List<GpsRow>, name: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Vola Racing Training\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<trk><name>").append(name).append("</name><trkseg>\n")
        for (r in rows) {
            sb.append("<trkpt lat=\"${r.lat}\" lon=\"${r.lon}\"><ele>${r.alt}</ele>")
            if (r.utcMs > 0) sb.append("<time>${Instant.ofEpochMilli(r.utcMs)}</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg></trk></gpx>\n")
        return sb.toString()
    }

    private fun save(ctx: Context, display: String, mime: String, write: (OutputStream) -> Unit) {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, display)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/VolaRacing")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: error("Ecriture dans Telechargements impossible")
        ctx.contentResolver.openOutputStream(uri)!!.use(write)
    }

    /** CSV bruts + GPX vers Telechargements/VolaRacing. Retourne le nombre de fichiers ecrits. */
    fun exportToDownloads(ctx: Context, sessionId: Long, dir: File): Int {
        var n = 0
        for (name in CSV_FILES) {
            val f = File(dir, name)
            if (!f.exists()) continue
            save(ctx, "session_${sessionId}_$name", "text/csv") { out -> f.inputStream().use { it.copyTo(out) } }
            n++
        }
        val gpx = gpx(Csv.readGps(File(dir, "gps.csv")), "Session $sessionId")
        save(ctx, "session_${sessionId}_trace.gpx", "application/gpx+xml") { it.write(gpx.toByteArray()) }
        return n + 1
    }

    /** Archive ZIP (CSV + GPX) partageable via la feuille de partage Android. */
    fun shareIntent(ctx: Context, sessionId: Long, dir: File): Intent {
        val outDir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val zip = File(outDir, "vola_session_$sessionId.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            for (name in CSV_FILES) {
                val f = File(dir, name)
                if (!f.exists()) continue
                z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
            z.putNextEntry(ZipEntry("trace.gpx"))
            z.write(gpx(Csv.readGps(File(dir, "gps.csv")), "Session $sessionId").toByteArray())
            z.closeEntry()
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", zip)
        return Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
