package fr.volaracing.training.data

import fr.volaracing.training.core.GpsRow
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Ecriture des CSV sur un thread dedie : aucun acces disque sur le thread principal
 * ni sur le thread des capteurs. Horodatage commun : elapsed_ns (elapsedRealtimeNanos).
 */
class SessionLogger(val dir: File) {
    init { dir.mkdirs() }

    private val ex = Executors.newSingleThreadExecutor()
    private val wGps = open("gps.csv",
        "elapsed_ns,utc_ms,lat,lon,alt_m,speed_ms,bearing_deg,h_acc_m,speed_acc_ms,satellites")
    private val wImu = open("imu.csv", "elapsed_ns,type,x,y,z")   // type: acc (m/s2), gyr (rad/s), baro (hPa dans x)
    private val wMarks = open("marks.csv", "elapsed_ns,label")      // label: MARK, A, B
    private var imuCount = 0

    private fun open(name: String, header: String): BufferedWriter =
        File(dir, name).bufferedWriter().also { it.write(header); it.newLine(); it.flush() }

    private fun run(block: () -> Unit) {
        try { ex.execute { try { block() } catch (_: Exception) { } } } catch (_: RejectedExecutionException) { }
    }

    fun gps(r: GpsRow) {
        val s = "${r.tNs},${r.utcMs},${r.lat},${r.lon},${r.alt},${r.speed},${r.bearing},${r.hAcc},${r.sAcc},${r.sats}"
        run { wGps.write(s); wGps.newLine(); wGps.flush() }
    }

    fun imu(t: Long, type: String, x: Float, y: Float, z: Float) {
        val s = "$t,$type,$x,$y,$z"
        run { wImu.write(s); wImu.newLine(); if (++imuCount % 500 == 0) wImu.flush() }
    }

    fun mark(t: Long, label: String) {
        val s = "$t,$label"
        run { wMarks.write(s); wMarks.newLine(); wMarks.flush() }
    }

    fun close() {
        run { wGps.close(); wImu.close(); wMarks.close() }
        ex.shutdown()
    }
}
