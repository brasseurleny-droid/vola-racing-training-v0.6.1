package fr.volaracing.training.core

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Courbe echantillonnee : t en secondes (croissant), v = valeur. */
class Series(val t: DoubleArray, val v: DoubleArray) {
    val size: Int get() = t.size

    /** Valeur interpolee en x (null hors de la plage). */
    fun at(x: Double): Double? {
        if (t.isEmpty() || x < t.first() - 1e-9 || x > t.last() + 1e-9) return null
        if (t.size == 1) return v[0]
        var lo = 0
        var hi = t.size - 1
        while (hi - lo > 1) {
            val m = (lo + hi) / 2
            if (t[m] <= x) lo = m else hi = m
        }
        val span = t[hi] - t[lo]
        val f = if (span <= 1e-12) 0.0 else ((x - t[lo]) / span).coerceIn(0.0, 1.0)
        return v[lo] + (v[hi] - v[lo]) * f
    }
}

data class ChannelDef(val id: String, val label: String, val unit: String, val needsRef: Boolean = false)

/** Voies d'analyse. Architecture extensible : une voie = un identifiant + une Series par run. */
object Channels {
    val ALL = listOf(
        ChannelDef("speed", "Vitesse", "km/h"),
        ChannelDef("alt", "Altitude", "m"),
        ChannelDef("slope", "Pente", "%"),
        ChannelDef("dist", "Distance parcourue", "m"),
        ChannelDef("along", "Accélération longitudinale", "m/s²"),
        ChannelDef("alat", "Accélération latérale (+ = droite)", "m/s²"),
        ChannelDef("g", "Force G (norme)", "G"),
        ChannelDef("gyro", "Vitesse angulaire", "rad/s"),
        ChannelDef("baro", "Pression", "hPa"),
        ChannelDef("hacc", "Précision GPS", "m"),
        ChannelDef("sats", "Satellites", "nb"),
        ChannelDef("delta", "Écart de temps vs référence", "s", true),
        ChannelDef("dspeed", "Écart de vitesse vs référence", "km/h", true),
    )

    fun def(id: String): ChannelDef = ALL.first { it.id == id }

    fun fmt(id: String, v: Double): String {
        val d = when (id) {
            "g", "along", "alat", "gyro", "delta" -> 2
            "sats" -> 0
            else -> 1
        }
        return String.format(Locale.US, "%.${d}f", v)
    }

    private fun movingAvg(a: DoubleArray, k: Int): DoubleArray = DoubleArray(a.size) { i ->
        var sum = 0.0
        var n = 0
        for (j in maxOf(0, i - k)..minOf(a.size - 1, i + k)) { sum += a[j]; n++ }
        sum / n
    }

    /** Voies derivees du GPS : vitesse lissee, altitude, pente, accelerations, precision, satellites. */
    fun gps(track: List<TrackPt>, windowS: Double, altOverride: DoubleArray? = null): Map<String, Series> {
        val n = track.size
        if (n < 2) return emptyMap()
        val t0 = track.first().tNs
        val t = DoubleArray(n) { (track[it].tNs - t0) / 1e9 }
        val sm = StatsCalc.smoothed(track, windowS) // m/s
        val speed = DoubleArray(n) { sm[it] * 3.6 }
        val dist = DoubleArray(n) { track[it].dist }
        val alt = movingAvg(altOverride ?: DoubleArray(n) { track[it].alt }, 2)

        val along = DoubleArray(n) { i ->
            val a = maxOf(i - 1, 0)
            val b = minOf(i + 1, n - 1)
            val dt = t[b] - t[a]
            if (dt > 1e-9) ((sm[b] - sm[a]) / dt).coerceIn(-20.0, 20.0) else 0.0 // bornes physiques
        }

        // Cap (radians, deroule) puis acceleration laterale = v * d(cap)/dt.
        val psi = DoubleArray(n)
        var last = 0.0
        for (i in 0 until n) {
            val a = maxOf(i - 1, 0)
            val b = minOf(i + 1, n - 1)
            val dx = track[b].x - track[a].x
            val dy = track[b].y - track[a].y
            var p = if (hypot(dx, dy) > 0.5) atan2(dx, dy) else last
            if (i > 0) {
                while (p - last > PI) p -= 2 * PI
                while (p - last < -PI) p += 2 * PI
            }
            psi[i] = p
            last = p
        }
        val alat = DoubleArray(n) { i ->
            val a = maxOf(i - 1, 0)
            val b = minOf(i + 1, n - 1)
            val dt = t[b] - t[a]
            // a l'arret le cap GPS n'a pas de sens : acceleration laterale nulle sous 1,5 m/s
            if (dt > 1e-9 && sm[i] >= 1.5) (sm[i] * (psi[b] - psi[a]) / dt).coerceIn(-30.0, 30.0) else 0.0
        }

        val altByDist = Series(dist, alt)
        val slope = DoubleArray(n) { i ->
            val sa = maxOf(dist[i] - 10.0, dist.first())
            val sb = minOf(dist[i] + 10.0, dist.last())
            // pente : au moins 10 m d'horizon, nulle a l'arret, bornee a +/- 60 %
            if (sb - sa < 10.0 || sm[i] < 1.0) 0.0
            else (((altByDist.at(sb) ?: alt[i]) - (altByDist.at(sa) ?: alt[i])) / (sb - sa) * 100.0).coerceIn(-60.0, 60.0)
        }

        return mapOf(
            "speed" to Series(t, speed),
            "alt" to Series(t, alt),
            "slope" to Series(t, slope),
            "dist" to Series(t, dist),
            "along" to Series(t, along),
            "alat" to Series(t, alat),
            "hacc" to Series(t, DoubleArray(n) { track[it].hAcc.toDouble() }),
            "sats" to Series(t, DoubleArray(n) { track[it].sats.toDouble() }),
        )
    }

    /**
     * Voies relatives a la reference : ecart de temps cumule (positif = plus lent) et ecart de vitesse,
     * mesures a position egale sur le trace de reference. prog = null pour la reference elle-meme.
     */
    fun vsReference(
        ref: List<TrackPt>, refSpeedKmh: Series,
        trk: List<TrackPt>, trkSpeedKmh: Series, prog: List<Align.Proj>?,
    ): Map<String, Series> {
        val n = trk.size
        val tt = DoubleArray(n) { (trk[it].tNs - trk.first().tNs) / 1e9 }
        if (prog == null) {
            return mapOf("delta" to Series(tt, DoubleArray(n)), "dspeed" to Series(tt, DoubleArray(n)))
        }
        val rd = ref.map { it.dist }
        val rt = ref.map { (it.tNs - ref.first().tNs) / 1e9 }
        val rv = refSpeedKmh.v.toList()
        val ts = ArrayList<Double>()
        val dl = ArrayList<Double>()
        val ds = ArrayList<Double>()
        var off: Double? = null
        for (i in 0 until n) {
            val s = prog[i].s
            val tr = Compare.interp(rd, rt, s) ?: continue
            val o = off ?: (tt[i] - tr).also { off = it }
            ts.add(tt[i])
            dl.add(tt[i] - tr - o)
            val vr = Compare.interp(rd, rv, s)
            ds.add(if (vr != null) trkSpeedKmh.v[i] - vr else 0.0)
        }
        return mapOf(
            "delta" to Series(ts.toDoubleArray(), dl.toDoubleArray()),
            "dspeed" to Series(ts.toDoubleArray(), ds.toDoubleArray()),
        )
    }
}

data class RunStat(
    val durationS: Double, val distM: Double, val dPlus: Double, val dMinus: Double,
    val slopeAvg: Double, val slopeMax: Double,
    val vMin: Double, val vAvg: Double, val vMax: Double,
    val gMax: Double?, val brakes: Int, val brakeMax: Double, val relaunches: Int,
)

object RunStats {
    private const val RELAUNCH = 1.0 // m/s2 : seuil de relance (TODO: reglable)

    /** Statistiques sur la plage [t0, t1] (secondes depuis le debut du run). */
    fun compute(ch: Map<String, Series>, t0: Double, t1: Double, brakeThr: Double): RunStat? {
        val speed = ch["speed"] ?: return null
        val along = ch["along"] ?: return null
        val slope = ch["slope"] ?: return null
        val alt = ch["alt"] ?: return null
        val dist = ch["dist"] ?: return null
        val lo = minOf(t0, t1)
        val hi = maxOf(t0, t1)
        val idx = speed.t.indices.filter { speed.t[it] >= lo - 1e-9 && speed.t[it] <= hi + 1e-9 }
        if (idx.size < 2) return null

        val dur = speed.t[idx.last()] - speed.t[idx.first()]
        val d = dist.v[idx.last()] - dist.v[idx.first()]
        var up = 0.0
        var down = 0.0
        for (k in 1 until idx.size) {
            val dz = alt.v[idx[k]] - alt.v[idx[k - 1]]
            if (dz > 0) up += dz else down -= dz
        }
        var brakes = 0
        var brakeMax = 0.0
        var relaunches = 0
        var inBrake = false
        var inRelaunch = false
        for (i in idx) {
            val a = along.v[i]
            if (a < -brakeThr) {
                if (!inBrake) brakes++
                inBrake = true
                brakeMax = maxOf(brakeMax, -a)
            } else inBrake = false
            if (a > RELAUNCH) {
                if (!inRelaunch) relaunches++
                inRelaunch = true
            } else inRelaunch = false
        }
        val g = ch["g"]?.let { s ->
            val vals = s.t.indices.filter { s.t[it] >= lo && s.t[it] <= hi }.map { s.v[it] }
            vals.maxOrNull()
        }
        return RunStat(
            durationS = dur, distM = d, dPlus = up, dMinus = down,
            slopeAvg = if (d > 1.0) (alt.v[idx.last()] - alt.v[idx.first()]) / d * 100.0 else 0.0,
            slopeMax = idx.maxOf { abs(slope.v[it]) },
            vMin = idx.minOf { speed.v[it] },
            vAvg = if (dur > 0) d / dur * 3.6 else 0.0,
            vMax = idx.maxOf { speed.v[it] },
            gMax = g, brakes = brakes, brakeMax = brakeMax, relaunches = relaunches,
        )
    }
}

/** Rapport en texte, uniquement a partir de valeurs calculees (aucun modele de langage). */
object Report {
    private fun fr(f: String, vararg a: Any) = String.format(Locale.FRANCE, f, *a)

    fun lines(
        label: String, ref: RunStat?, st: RunStat?,
        sections: List<Triple<Double, Double, Double>>,
    ): List<String> {
        val out = ArrayList<String>()
        sections.maxByOrNull { it.third }?.let {
            if (it.third > 0.05) out.add(fr("%s perd %.1f s entre %.0f et %.0f m.", label, it.third, it.first, it.second))
        }
        sections.minByOrNull { it.third }?.let {
            if (it.third < -0.05) out.add(fr("%s gagne %.1f s entre %.0f et %.0f m.", label, -it.third, it.first, it.second))
        }
        if (ref != null && st != null) {
            val dv = st.vMax - ref.vMax
            if (abs(dv) >= 0.5) out.add(fr("Vitesse max : %+.1f km/h par rapport à la référence.", dv))
            if (st.brakes != ref.brakes || abs(st.brakeMax - ref.brakeMax) >= 0.5) {
                out.add(fr("Freinages : %d contre %d (référence) ; le plus fort : %.1f contre %.1f m/s².",
                    st.brakes, ref.brakes, st.brakeMax, ref.brakeMax))
            }
        }
        return out
    }
}
