package fr.volaracing.training.core

import java.util.TreeMap
import kotlin.math.sqrt

object Imu {
    /**
     * Agrege des lignes d'imu.csv (elapsed_ns,type,x,y,z) en voies g, gyro, baro sur [startNs, endNs],
     * par tranches de bucketNs (max de la norme pour g et gyro, moyenne pour la pression).
     * t = secondes depuis startNs. Les pics sont conserves (max) malgre la decimation.
     */
    fun aggregate(lines: Sequence<String>, startNs: Long, endNs: Long, bucketNs: Long = 100_000_000L): Map<String, Series> {
        val g = TreeMap<Long, Double>()
        val gyro = TreeMap<Long, Double>()
        val baroSum = TreeMap<Long, Double>()
        val baroN = TreeMap<Long, Int>()
        for (line in lines) {
            val c1 = line.indexOf(',')
            if (c1 <= 0) continue
            val t = line.substring(0, c1).toLongOrNull() ?: continue
            if (t > endNs + 5_000_000_000L) break
            if (t < startNs || t > endNs) continue
            val p = line.split(',')
            if (p.size < 5) continue
            val x = p[2].toDoubleOrNull() ?: continue
            val y = p[3].toDoubleOrNull() ?: continue
            val z = p[4].toDoubleOrNull() ?: continue
            val b = (t - startNs) / bucketNs
            when (p[1]) {
                "acc" -> { val v = sqrt(x * x + y * y + z * z) / 9.80665; if (v > (g[b] ?: -1.0)) g[b] = v }
                "gyr" -> { val v = sqrt(x * x + y * y + z * z); if (v > (gyro[b] ?: -1.0)) gyro[b] = v }
                "baro" -> { baroSum[b] = (baroSum[b] ?: 0.0) + x; baroN[b] = (baroN[b] ?: 0) + 1 }
            }
        }
        fun series(m: Map<Long, Double>): Series? {
            if (m.size < 2) return null
            val keys = m.keys.toList()
            return Series(
                DoubleArray(keys.size) { (keys[it] * bucketNs + bucketNs / 2) / 1e9 },
                DoubleArray(keys.size) { m[keys[it]]!! },
            )
        }
        val baro = TreeMap<Long, Double>()
        for ((k, s) in baroSum) baro[k] = s / baroN[k]!!
        val out = HashMap<String, Series>()
        series(g)?.let { out["g"] = it }
        series(gyro)?.let { out["gyro"] = it }
        series(baro)?.let { out["baro"] = it }
        return out
    }

    /** Inverse de aggregate : lignes imu.csv synthetiques (pour les paquets d'echange entre amis). */
    fun toLines(series: Map<String, Series>, startNs: Long): List<String> {
        val out = ArrayList<String>()
        fun ns(t: Double) = startNs + (t * 1e9).toLong()
        series["g"]?.let { s -> for (i in s.t.indices) out.add("${ns(s.t[i])},acc,${s.v[i] * 9.80665},0,0") }
        series["gyro"]?.let { s -> for (i in s.t.indices) out.add("${ns(s.t[i])},gyr,${s.v[i]},0,0") }
        series["baro"]?.let { s -> for (i in s.t.indices) out.add("${ns(s.t[i])},baro,${s.v[i]},0,0") }
        return out
    }
}
