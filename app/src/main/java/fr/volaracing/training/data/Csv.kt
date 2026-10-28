package fr.volaracing.training.data

import fr.volaracing.training.core.GpsRow
import java.io.File

object Csv {
    fun readGps(f: File): List<GpsRow> {
        if (!f.exists()) return emptyList()
        val out = ArrayList<GpsRow>()
        f.useLines { lines ->
            lines.drop(1).forEach { l ->
                val p = l.split(',')
                if (p.size >= 10) {
                    try {
                        out.add(GpsRow(
                            p[0].toLong(), p[1].toLong(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble(),
                            p[5].toDouble(), p[6].toDouble(), p[7].toFloat(), p[8].toFloat(), p[9].toInt()))
                    } catch (_: NumberFormatException) { /* ligne tronquee : ignoree */ }
                }
            }
        }
        return out
    }
}
