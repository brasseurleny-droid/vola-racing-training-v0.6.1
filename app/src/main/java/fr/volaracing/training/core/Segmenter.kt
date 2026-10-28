package fr.volaracing.training.core

import kotlin.math.sqrt

enum class SegType { DESCENT, CLIMB, LIFT, STOP, LINK }

data class Seg(val type: SegType, val i0: Int, val i1: Int, val t0: Double, val t1: Double, val dropM: Double)

/** Seuils de segmentation : HYPOTHESES a valider sur le terrain (TODO). */
data class SegParams(
    val descentSlopePct: Double,
    val minDescentS: Double,
    val minDropM: Double,
    val liftSlopePct: Double,
    val climbSlopePct: Double,
    val stopKmh: Double,
    val gapMergeS: Double,
    val horizonS: Double = 15.0,
) {
    companion object {
        fun preset(act: String): SegParams = when (act) {
            "SKI" -> SegParams(6.0, 15.0, 20.0, 12.0, 4.0, 2.0, 8.0)
            else -> SegParams(4.0, 20.0, 15.0, 10.0, 3.0, 2.0, 8.0)
        }
    }
}

object Segmenter {
    private class R(var type: SegType, val i0: Int, val i1: Int)

    private fun rle(types: Array<SegType>): MutableList<R> {
        val out = ArrayList<R>()
        var s = 0
        for (i in 1..types.size) {
            if (i == types.size || types[i] != types[s]) {
                out.add(R(types[s], s, i - 1))
                s = i
            }
        }
        return out
    }

    private fun flatten(runs: List<R>, n: Int): Array<SegType> {
        val a = Array(n) { SegType.LINK }
        for (r in runs) for (i in r.i0..r.i1) a[i] = r.type
        return a
    }

    /**
     * Classe chaque echantillon (pente et vitesse sur un horizon centre), regroupe en segments,
     * absorbe les trous courts entre deux descentes et supprime les descentes trop courtes ou trop plates.
     * t en secondes, vitesse en km/h, altitude et distance en metres.
     */
    fun segment(t: DoubleArray, speedKmh: DoubleArray, alt: DoubleArray, dist: DoubleArray, p: SegParams): List<Seg> {
        val n = t.size
        if (n < 3) return emptyList()
        val types = Array(n) { SegType.LINK }
        for (i in 0 until n) {
            var a = i
            while (a > 0 && t[i] - t[a - 1] <= p.horizonS / 2) a--
            var b = i
            while (b < n - 1 && t[b + 1] - t[i] <= p.horizonS / 2) b++
            val dd = dist[b] - dist[a]
            val slope = if (dd > 5.0) (alt[b] - alt[a]) / dd * 100.0 else 0.0
            var sum = 0.0
            var sum2 = 0.0
            for (k in a..b) { sum += speedKmh[k]; sum2 += speedKmh[k] * speedKmh[k] }
            val m = (b - a + 1).toDouble()
            val mean = sum / m
            val sd = sqrt(maxOf(0.0, sum2 / m - mean * mean))
            types[i] = when {
                mean < p.stopKmh -> SegType.STOP
                slope <= -p.descentSlopePct -> SegType.DESCENT
                slope >= p.liftSlopePct && mean >= 4.0 && mean <= 35.0 && sd < 0.15 * mean -> SegType.LIFT
                slope >= p.climbSlopePct -> SegType.CLIMB
                else -> SegType.LINK
            }
        }

        // 1) un arret ou une liaison courte entre deux descentes ne coupe pas la descente
        var runs = rle(types)
        for (k in 1 until runs.size - 1) {
            val r = runs[k]
            val shortGap = (r.type == SegType.LINK || r.type == SegType.STOP) && t[r.i1] - t[r.i0] < p.gapMergeS
            if (shortGap && runs[k - 1].type == SegType.DESCENT && runs[k + 1].type == SegType.DESCENT) r.type = SegType.DESCENT
        }
        // 2) une descente trop courte ou trop plate devient une liaison
        runs = rle(flatten(runs, n))
        for (r in runs) {
            if (r.type == SegType.DESCENT && (t[r.i1] - t[r.i0] < p.minDescentS || alt[r.i0] - alt[r.i1] < p.minDropM)) {
                r.type = SegType.LINK
            }
        }
        runs = rle(flatten(runs, n))
        return runs.map { Seg(it.type, it.i0, it.i1, t[it.i0], t[it.i1], alt[it.i0] - alt[it.i1]) }
    }
}

data class Planned(val startNs: Long, val endNs: Long, val kind: Int)

/** Decide quels runs creer pour un enregistrement sans portes (kind 2 = descente auto, 1 = run libre). */
object RunPlanner {
    fun plan(rows: List<GpsRow>, windowS: Double, minKmh: Double, params: SegParams, baro: Series? = null): List<Planned> {
        if (rows.size < 2) return emptyList()
        val t0 = rows.first().tNs
        if ((rows.last().tNs - t0) / 1e9 < 5.0) return emptyList()
        val track = Filters.cleanTrack(Track.fromRows(rows, LocalFrame(rows.first().lat, rows.first().lon))).first
        val gpsT = DoubleArray(track.size) { (track[it].tNs - track.first().tNs) / 1e9 }
        val altOv = baro?.let { b -> Filters.baroAltitude(b.t, b.v, gpsT, DoubleArray(track.size) { track[it].alt }) }
        val ch = Channels.gps(track, windowS, altOv)
        val sp = ch["speed"]
        val al = ch["alt"]
        val di = ch["dist"]
        if (sp != null && al != null && di != null) {
            val descents = Segmenter.segment(sp.t, sp.v, al.v, di.v, params).filter { it.type == SegType.DESCENT }
            if (descents.isNotEmpty()) {
                return descents.map { Planned(t0 + (it.t0 * 1e9).toLong(), t0 + (it.t1 * 1e9).toLong(), 2) }
            }
        }
        // Repli : partie en mouvement, sinon enregistrement complet.
        val minMs = minKmh / 3.6
        val first = track.indexOfFirst { it.speed >= minMs }
        val end = track.indexOfLast { it.speed >= minMs }
        return if (first >= 0 && end > first && (track[end].tNs - track[first].tNs) > 5_000_000_000L) {
            listOf(Planned(track[first].tNs, track[end].tNs, 1))
        } else {
            listOf(Planned(t0, rows.last().tNs, 1))
        }
    }
}
