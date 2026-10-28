package fr.volaracing.training.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

data class FilterInfo(val droppedAcc: Int, val droppedJump: Int, val spikes: Int) {
    val total: Int get() = droppedAcc + droppedJump + spikes
    fun text(): String =
        if (total == 0) "aucun point rejeté"
        else "$droppedAcc imprécis, $droppedJump sauts, $spikes pics lissés"
}

/**
 * Nettoyage des donnees brutes AVANT analyse. Les fichiers CSV bruts ne sont jamais modifies :
 * le filtre ne s'applique qu'aux courbes calculees.
 */
object Filters {
    fun median(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    /** Hampel : une valeur trop eloignee de la mediane locale (fenetre +/- k) est remplacee par cette mediane. */
    fun hampel(a: DoubleArray, k: Int, nMad: Double, minMad: Double): Pair<DoubleArray, Int> {
        val out = a.copyOf()
        var n = 0
        for (i in a.indices) {
            val w = ArrayList<Double>()
            for (j in maxOf(0, i - k)..minOf(a.size - 1, i + k)) w.add(a[j])
            val med = median(w)
            val mad = median(w.map { abs(it - med) })
            if (abs(a[i] - med) > nMad * 1.4826 * maxOf(mad, minMad)) { out[i] = med; n++ }
        }
        return Pair(out, n)
    }

    /**
     * 1) points a mauvaise precision annoncee (> max(20 m, 3 x mediane)), 2) sauts de position irrealistes
     * (vitesse implicite > vMaxMs), 3) pics de vitesse et d'altitude (Hampel). Premier et dernier point conserves.
     * Si une regle rejetterait plus de 30 % des points, elle est ignoree (signal globalement mauvais).
     */
    fun cleanTrack(track: List<TrackPt>, vMaxMs: Double = 45.0): Pair<List<TrackPt>, FilterInfo> {
        val n = track.size
        if (n < 5) return Pair(track, FilterInfo(0, 0, 0))
        val keep = BooleanArray(n) { true }

        val accs = track.map { it.hAcc.toDouble() }.filter { it >= 0.0 && it <= 500.0 }
        var dAcc = 0
        if (accs.isNotEmpty()) {
            val thr = minOf(100.0, maxOf(20.0, 3.0 * median(accs)))
            for (i in 1 until n - 1) {
                val a = track[i].hAcc.toDouble()
                if (a >= 0.0 && a <= 500.0 && a > thr) { keep[i] = false; dAcc++ }
            }
            if (dAcc > n * 0.3) { for (i in 0 until n) keep[i] = true; dAcc = 0 }
        }

        var dJump = 0
        val jumped = ArrayList<Int>()
        var last = 0
        for (i in 1 until n - 1) {
            if (!keep[i]) continue
            val dt = (track[i].tNs - track[last].tNs) / 1e9
            val d = hypot(track[i].x - track[last].x, track[i].y - track[last].y)
            if (dt > 0 && d / dt > vMaxMs) { jumped.add(i); dJump++ } else last = i
        }
        if (dJump > n * 0.3) { dJump = 0 } else for (i in jumped) keep[i] = false

        val kept = ArrayList<TrackPt>()
        for (i in 0 until n) if (keep[i]) kept.add(track[i])
        if (kept.size < 3) return Pair(track, FilterInfo(0, 0, 0))

        val (sp, nSp) = hampel(DoubleArray(kept.size) { kept[it].speed }, 3, 3.5, 0.3)
        val (al, nAl) = hampel(DoubleArray(kept.size) { kept[it].alt }, 3, 3.5, 1.5)

        var d = 0.0
        val res = ArrayList<TrackPt>(kept.size)
        for (i in kept.indices) {
            if (i > 0) d += hypot(kept[i].x - kept[i - 1].x, kept[i].y - kept[i - 1].y)
            res.add(kept[i].copy(speed = sp[i], alt = al[i], dist = d))
        }
        return Pair(res, FilterInfo(dAcc, dJump, nSp + nAl))
    }

    /**
     * Altitude relative issue du barometre (formule internationale), lissee, interpolee aux instants GPS puis
     * recalee sur l'altitude GPS (mediane de l'ecart). Plus stable que l'altitude GPS pour la pente.
     * null si les donnees barometriques sont absentes ou invraisemblables.
     */
    fun baroAltitude(baroT: DoubleArray, baroHpa: DoubleArray, gpsT: DoubleArray, gpsAlt: DoubleArray): DoubleArray? {
        if (baroT.size < 20 || baroT.size != baroHpa.size || gpsT.size < 2 || gpsT.size != gpsAlt.size) return null
        val p0 = baroHpa[0]
        if (p0 < 300.0 || p0 > 1100.0) return null
        if (baroHpa.max() - baroHpa.min() > 120.0) return null
        val rel = DoubleArray(baroT.size) { 44330.0 * (1.0 - (baroHpa[it] / p0).pow(0.190284)) }
        val sm = DoubleArray(rel.size) { i ->
            var s = 0.0
            var c = 0
            for (j in maxOf(0, i - 5)..minOf(rel.size - 1, i + 5)) { s += rel[j]; c++ }
            s / c
        }
        val series = Series(baroT, sm)
        val atGps = DoubleArray(gpsT.size) { i ->
            series.at(gpsT[i]) ?: if (gpsT[i] < baroT.first()) sm.first() else sm.last()
        }
        val off = median(gpsAlt.indices.map { gpsAlt[it] - atGps[it] })
        return DoubleArray(gpsT.size) { atGps[it] + off }
    }
}
