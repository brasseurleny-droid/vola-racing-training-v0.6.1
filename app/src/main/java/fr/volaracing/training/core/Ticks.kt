package fr.volaracing.training.core

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Graduations d'axes en « nombres ronds » (1, 2, 5 x 10^n). */
object Ticks {
    fun nice(lo: Double, hi: Double, target: Int = 6): List<Double> {
        if (!(hi > lo)) return listOf(lo)
        val rough = (hi - lo) / maxOf(target, 1)
        val mag = 10.0.pow(floor(log10(rough)))
        val res = rough / mag
        val step = (when {
            res <= 1.5 -> 1.0
            res <= 3.0 -> 2.0
            res <= 7.0 -> 5.0
            else -> 10.0
        }) * mag
        val first = ceil(lo / step - 1e-9).toLong()
        val last = floor(hi / step + 1e-9).toLong()
        val out = ArrayList<Double>()
        for (k in first..last) out.add(k * step)
        return out
    }

    fun step(ticks: List<Double>): Double = if (ticks.size >= 2) ticks[1] - ticks[0] else 1.0

    /** Etiquette d'axe X : metres ou km (distance), secondes ou min:s (temps). */
    fun fmtX(v: Double, xDist: Boolean, step: Double): String =
        if (xDist) {
            if (step >= 1000.0) String.format(Locale.US, "%.1f km", v / 1000.0)
            else String.format(Locale.US, "%.0f m", v)
        } else if (step < 60.0) {
            if (step < 1.0) String.format(Locale.US, "%.1f s", v) else String.format(Locale.US, "%.0f s", v)
        } else {
            val s = v.roundToInt()
            String.format(Locale.US, "%d:%02d", s / 60, s % 60)
        }
}

/** Coloration d'une trace selon une voie : classes 0 (rouge) a n-1 (vert). */
object Heat {
    fun percentile(values: DoubleArray, p: Double): Double {
        val v = values.filter { !it.isNaN() }.sorted()
        if (v.isEmpty()) return 0.0
        val pos = (p / 100.0) * (v.size - 1)
        val i = floor(pos).toInt()
        val f = pos - i
        return if (i + 1 < v.size) v[i] + (v[i + 1] - v[i]) * f else v[i]
    }

    /** -1 si la valeur est absente. */
    fun classOf(v: Double, lo: Double, hi: Double, n: Int, invert: Boolean): Int {
        if (v.isNaN()) return -1
        if (!(hi > lo)) return n / 2
        var f = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
        if (invert) f = 1.0 - f
        return minOf(n - 1, (f * n).toInt())
    }

    /** Valeur moyenne de la voie sur chaque segment [t[i], t[i+1]] (NaN si absente). */
    fun segmentValues(s: Series, t: DoubleArray): DoubleArray = DoubleArray(maxOf(t.size - 1, 0)) { i ->
        val a = s.at(t[i])
        val b = s.at(t[i + 1])
        if (a != null && b != null) (a + b) / 2.0 else Double.NaN
    }
}
