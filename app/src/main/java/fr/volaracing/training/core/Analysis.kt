package fr.volaracing.training.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

data class GpsRow(
    val tNs: Long, val utcMs: Long, val lat: Double, val lon: Double, val alt: Double,
    val speed: Double, val bearing: Double, val hAcc: Float, val sAcc: Float, val sats: Int,
)

data class TrackPt(
    val tNs: Long, val lat: Double, val lon: Double,
    val x: Double, val y: Double, val speed: Double, val dist: Double,
    val alt: Double = 0.0, val hAcc: Float = 0f, val sats: Int = 0,
)

object Track {
    /** Ligne GPS interpolee a l'instant t (null si t sort de la plage). */
    fun interpolate(rows: List<GpsRow>, t: Long): GpsRow? {
        if (rows.size < 2 || t < rows.first().tNs || t > rows.last().tNs) return null
        var lo = 0
        var hi = rows.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (rows[mid].tNs <= t) lo = mid else hi = mid
        }
        val a = rows[lo]
        val b = rows[hi]
        val span = b.tNs - a.tNs
        val f = if (span <= 0L) 0.0 else (t - a.tNs).toDouble() / span
        fun m(x: Double, y: Double) = x + (y - x) * f
        return a.copy(
            tNs = t,
            utcMs = (a.utcMs + (b.utcMs - a.utcMs) * f).toLong(),
            lat = m(a.lat, b.lat), lon = m(a.lon, b.lon), alt = m(a.alt, b.alt),
            speed = m(a.speed, b.speed),
        )
    }

    /** Lignes GPS entre startNs et endNs, bornes interpolees. */
    fun window(rows: List<GpsRow>, startNs: Long, endNs: Long): List<GpsRow> {
        val pts = ArrayList<GpsRow>()
        interpolate(rows, startNs)?.let { pts.add(it) }
        rows.filterTo(pts) { it.tNs > startNs && it.tNs < endNs }
        interpolate(rows, endNs)?.let { pts.add(it) }
        return pts
    }

    /**
     * Points projetes dans le repere `frame`, avec distance cumulee en metres.
     * Si la vitesse GPS est absente (0), elle est deduite des positions.
     */
    fun fromRows(pts: List<GpsRow>, frame: LocalFrame): List<TrackPt> {
        var d = 0.0
        var prev: Pt? = null
        var prevT = 0L
        return pts.map { r ->
            val p = frame.toLocal(r.lat, r.lon)
            var v = r.speed
            prev?.let { q ->
                val seg = hypot(p.x - q.x, p.y - q.y)
                d += seg
                val dt = (r.tNs - prevT) / 1e9
                if (v <= 0.0 && dt > 0.05) v = seg / dt
            }
            prev = p
            prevT = r.tNs
            TrackPt(r.tNs, r.lat, r.lon, p.x, p.y, v, d, r.alt, r.hAcc, r.sats)
        }
    }

    /** Trace entre startNs et endNs, bornes interpolees, distance cumulee en metres. */
    fun build(rows: List<GpsRow>, startNs: Long, endNs: Long, frame: LocalFrame): List<TrackPt> =
        fromRows(window(rows, startNs, endNs), frame)
}

data class Stats(
    val durationS: Double, val distM: Double, val avgMs: Double,
    val maxMs: Double, val timeAtMaxS: Double,
)

object StatsCalc {
    /** Vitesse lissee par moyenne glissante sur +/- windowS/2 (fenetre glissante, cout lineaire). */
    fun smoothed(track: List<TrackPt>, windowS: Double): DoubleArray {
        val half = (windowS / 2.0 * 1e9).toLong()
        val n = track.size
        val out = DoubleArray(n)
        var lo = 0
        var hi = 0 // fenetre [lo, hi)
        var sum = 0.0
        for (i in 0 until n) {
            while (hi < n && track[hi].tNs - track[i].tNs <= half) { sum += track[hi].speed; hi++ }
            while (lo < n && track[i].tNs - track[lo].tNs > half) { sum -= track[lo].speed; lo++ }
            val cnt = hi - lo
            out[i] = if (cnt > 0) sum / cnt else track[i].speed
        }
        return out
    }

    fun compute(track: List<TrackPt>, windowS: Double): Stats {
        if (track.size < 2) return Stats(0.0, 0.0, 0.0, 0.0, 0.0)
        val dur = (track.last().tNs - track.first().tNs) / 1e9
        val dist = track.last().dist
        val sm = smoothed(track, windowS)
        val vmax = sm.max()
        var atMax = 0.0
        for (i in 1 until track.size) {
            if (sm[i] >= 0.95 * vmax) atMax += (track[i].tNs - track[i - 1].tNs) / 1e9
        }
        return Stats(dur, dist, if (dur > 0) dist / dur else 0.0, vmax, atMax)
    }
}

object Compare {
    fun interp(xs: List<Double>, ys: List<Double>, x: Double): Double? {
        if (xs.size < 2 || x < xs.first() - 1e-9 || x > xs.last() + 1e-9) return null
        for (i in 1 until xs.size) {
            if (xs[i] >= x) {
                val span = xs[i] - xs[i - 1]
                val f = if (span <= 1e-9) 1.0 else ((x - xs[i - 1]) / span).coerceIn(0.0, 1.0)
                return ys[i - 1] + (ys[i] - ys[i - 1]) * f
            }
        }
        return ys.last() // x depasse xs.last() de moins que la tolerance
    }

    data class Aligned(val curve: List<Pair<Double, Double>>, val overlap: Double)

    /**
     * Ecart cumule de B par rapport a A (positif = B perd du temps) sur la partie commune,
     * en fonction de la distance le long de A. B est projete sur le trace de A, donc deux
     * passages au meme endroit se comparent meme avec des departs et arrivees differents.
     * overlap = part des points de B situes a moins de 30 m du trace de A.
     */
    fun deltaAligned(a: List<TrackPt>, b: List<TrackPt>, step: Double = 10.0): Aligned {
        if (a.size < 2 || b.size < 2) return Aligned(emptyList(), 0.0)
        val pr = Align.progress(a, b)
        val overlap = pr.count { it.d <= 30.0 }.toDouble() / pr.size
        val bs = pr.map { it.s }
        val bt = b.map { (it.tNs - b.first().tNs) / 1e9 }
        val ad = a.map { it.dist }
        val at = a.map { (it.tNs - a.first().tNs) / 1e9 }
        val lo = maxOf(0.0, bs.first())
        val hi = minOf(a.last().dist, bs.last())
        if (hi - lo < step) return Aligned(emptyList(), overlap)
        val ta0 = interp(ad, at, lo) ?: return Aligned(emptyList(), overlap)
        val tb0 = interp(bs, bt, lo) ?: return Aligned(emptyList(), overlap)
        val out = ArrayList<Pair<Double, Double>>()
        var d = lo
        while (d <= hi + 1e-9) {
            val ta = interp(ad, at, d)
            val tb = interp(bs, bt, d)
            if (ta != null && tb != null) out.add(Pair(d, (tb - tb0) - (ta - ta0)))
            d += step
        }
        return Aligned(out, overlap)
    }

    /** Variation de l'ecart sur n sections egales : (debut m, fin m, gain(-)/perte(+) en s). */
    fun sections(curve: List<Pair<Double, Double>>, n: Int = 5): List<Triple<Double, Double, Double>> {
        if (curve.size < 2) return emptyList()
        val xs = curve.map { it.first }
        val ys = curve.map { it.second }
        val d0 = xs.first()
        val w = (xs.last() - d0) / n
        return (0 until n).map { k ->
            val s = d0 + k * w
            val e = s + w
            val v0 = interp(xs, ys, s) ?: ys.first()
            val v1 = interp(xs, ys, e) ?: ys.last()
            Triple(s, e, v1 - v0)
        }
    }

    /** Secondes ecoulees depuis le debut de la trace quand la distance cumulee atteint d. */
    fun timeAtDistance(track: List<TrackPt>, d: Double): Double? {
        if (track.size < 2 || d < 0.0 || d > track.last().dist) return null
        val t0 = track.first().tNs
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            if (b.dist >= d) {
                val span = b.dist - a.dist
                val f = if (span <= 1e-9) 1.0 else (d - a.dist) / span
                return ((a.tNs - t0) + (b.tNs - a.tNs) * f) / 1e9
            }
        }
        return null
    }

    /**
     * Ecart cumule (secondes) de B par rapport a A en fonction de la distance :
     * positif = B a perdu du temps. Pas en metres. Les distances sont mesurees sur
     * chaque trace, l'alignement est donc approximatif si les trajectoires different.
     */
    fun deltaCurve(a: List<TrackPt>, b: List<TrackPt>, step: Double = 10.0): List<Pair<Double, Double>> {
        if (a.size < 2 || b.size < 2) return emptyList()
        val max = minOf(a.last().dist, b.last().dist)
        val out = ArrayList<Pair<Double, Double>>()
        var d = 0.0
        while (d <= max) {
            val ta = timeAtDistance(a, d)
            val tb = timeAtDistance(b, d)
            if (ta != null && tb != null) out.add(Pair(d, tb - ta))
            d += step
        }
        if (out.isNotEmpty() && out.last().first < max) {
            val ta = timeAtDistance(a, max)
            val tb = timeAtDistance(b, max)
            if (ta != null && tb != null) out.add(Pair(max, tb - ta))
        }
        return out
    }
}

/** Temps intermediaires (portes d'etape) : decalages en ns depuis le depart, vide si manque. */
object SplitCodec {
    fun encode(l: List<Long?>): String = l.joinToString(",") { it?.toString() ?: "" }
    fun parse(s: String): List<Long?> = if (s.isEmpty()) emptyList() else s.split(',').map { it.toLongOrNull() }
}

object Splits {
    /** Instant (ns) de franchissement de chaque porte d'etape, dans l'ordre ; null si manquee. */
    fun compute(track: List<TrackPt>, gates: List<Gate>): List<Long?> {
        val out = ArrayList<Long?>()
        var from = 1
        for (g in gates) {
            var found: Long? = null
            var i = from
            while (i < track.size) {
                val a = track[i - 1]
                val b = track[i]
                val t = g.crossing(Pt(a.x, a.y), Pt(b.x, b.y))
                if (t != null) { found = lerpNs(a.tNs, b.tNs, t); from = i; break }
                i++
            }
            out.add(found)
        }
        return out
    }
}

/** Alignement de deux traces par position (et non par distance parcourue). */
object Align {
    data class Proj(val s: Double, val d: Double) // s = progression le long de ref (m), d = ecart lateral (m)

    private fun project(ref: List<TrackPt>, x: Double, y: Double, lo: Double, hi: Double): Proj {
        var best = Double.MAX_VALUE
        var bestS = 0.0
        for (i in 1 until ref.size) {
            val a = ref[i - 1]
            val b = ref[i]
            if (b.dist < lo || a.dist > hi) continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val l2 = dx * dx + dy * dy
            val t = if (l2 <= 1e-12) 0.0 else (((x - a.x) * dx + (y - a.y) * dy) / l2).coerceIn(0.0, 1.0)
            val d = hypot(x - (a.x + t * dx), y - (a.y + t * dy))
            if (d < best) { best = d; bestS = a.dist + t * (b.dist - a.dist) }
        }
        return Proj(bestS, best)
    }

    /** Progression de chaque point de `other` le long de `ref` (croissante), avec l'ecart lateral. */
    fun progress(ref: List<TrackPt>, other: List<TrackPt>): List<Proj> {
        val out = ArrayList<Proj>()
        var prevS = 0.0
        for (p in other) {
            var pr = project(ref, p.x, p.y, prevS - 20.0, prevS + 300.0)
            if (pr.d > 50.0) pr = project(ref, p.x, p.y, -1e12, 1e12) // repere perdu : recherche globale
            val s = maxOf(pr.s, prevS)
            out.add(Proj(s, pr.d))
            prevS = s
        }
        return out
    }
}

object Fmt {
    fun time(ns: Long): String {
        val cs = ns / 10_000_000L
        return String.format(Locale.US, "%d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100)
    }

    fun spoken(ns: Long): String {
        val cs = ns / 10_000_000L
        val m = cs / 6000
        val s = (cs / 100) % 60
        val d = (cs % 100) / 10
        val minutes = if (m > 0) "$m minute${if (m > 1) "s" else ""} " else ""
        return "$minutes$s virgule $d secondes"
    }

    fun kmh(ms: Double) = ms * 3.6
}
