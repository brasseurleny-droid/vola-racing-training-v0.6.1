package fr.volaracing.training.data

import fr.volaracing.training.core.GpsRow
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.StatsCalc
import fr.volaracing.training.core.Track
import java.io.File

object RunBuilder {
    /** Construit un passage (stats, G max) a partir des fichiers d'une session. kind : 1 = libre, 2 = descente auto. */
    fun passage(
        sessionId: Long, courseId: Long, dir: File, rows: List<GpsRow>,
        startNs: Long, endNs: Long, kind: Int, windowS: Double,
    ): Passage {
        val track = if (rows.size >= 2) {
            Track.build(rows, startNs, endNs, LocalFrame(rows.first().lat, rows.first().lon))
        } else emptyList()
        val st = StatsCalc.compute(track, windowS)
        val g = ImuReader.read(File(dir, "imu.csv"), startNs, endNs)["g"]?.v?.maxOrNull() ?: 0.0
        return Passage(
            sessionId = sessionId, courseId = courseId, timeNs = endNs - startNs, lowQuality = false,
            vMax = st.maxMs, gMax = g, startNs = startNs, endNs = endNs,
            path = dir.absolutePath, splits = "", kind = kind,
        )
    }
}
