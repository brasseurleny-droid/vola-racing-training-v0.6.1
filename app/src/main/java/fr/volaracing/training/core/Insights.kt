package fr.volaracing.training.core

import java.util.Locale
import kotlin.math.abs

/** Un run vu par le moteur d'analyse (independant de l'interface). */
class InsightRun(
    val label: String,
    val ch: Map<String, Series>,
    /** t (s) -> distance le long du trace de reference (m). */
    val sOfT: Series,
    val durationS: Double,
    val stat: RunStat?,
)

data class Insight(val score: Double, val text: String)

/**
 * Analyse intelligente LOCALE : regles et statistiques sur des mesures deja calculees (pas de modele de langage,
 * pas de reseau). Chaque phrase s'appuie sur des chiffres verifiables dans les courbes.
 */
object Insights {
    private fun fr(f: String, vararg a: Any) = String.format(Locale.FRANCE, f, *a)

    private class Zone(val t0: Double, val t1: Double, val peak: Double, val tPeak: Double)

    /** Zones contigues ou `v` depasse le seuil (sous -seuil si below, sinon en valeur absolue). */
    private fun zones(s: Series, below: Boolean, thr: Double): List<Zone> {
        val out = ArrayList<Zone>()
        val n = s.t.size
        fun hit(i: Int) = if (below) s.v[i] < -thr else abs(s.v[i]) > thr
        var i = 0
        while (i < n) {
            if (hit(i)) {
                var j = i
                var pk = abs(s.v[i])
                var tp = s.t[i]
                while (j + 1 < n && hit(j + 1)) {
                    j++
                    if (abs(s.v[j]) > pk) { pk = abs(s.v[j]); tp = s.t[j] }
                }
                out.add(Zone(s.t[i], s.t[j], pk, tp))
                i = j + 1
            } else i++
        }
        return out
    }

    /** Constats propres a un run : freinages, virages, vitesse maxi, regularite. */
    fun events(run: InsightRun, brakeThr: Double): List<Insight> {
        val out = ArrayList<Insight>()
        val speed = run.ch["speed"] ?: return out
        run.ch["along"]?.let { along ->
            zones(along, true, brakeThr).sortedByDescending { it.peak * maxOf(it.t1 - it.t0, 1.0) }.take(3).forEach { z ->
                val d = run.sOfT.at(z.tPeak) ?: 0.0
                out.add(Insight(z.peak, fr(
                    "%s : freinage à %.0f m, %.1f m/s² pendant %.1f s (%.0f -> %.0f km/h).",
                    run.label, d, z.peak, z.t1 - z.t0, speed.at(z.t0) ?: 0.0, speed.at(z.t1) ?: 0.0,
                )))
            }
        }
        run.ch["alat"]?.let { alat ->
            zones(alat, false, 3.0).sortedByDescending { it.peak }.take(2).forEach { z ->
                val d = run.sOfT.at(z.tPeak) ?: 0.0
                out.add(Insight(z.peak * 0.8, fr(
                    "%s : virage rapide à %.0f m, %.1f m/s² à %.0f km/h.", run.label, d, z.peak, speed.at(z.tPeak) ?: 0.0,
                )))
            }
        }
        val vmax = speed.v.maxOrNull()
        if (vmax != null) {
            val k = speed.v.indexOfFirst { it == vmax }
            val d = run.sOfT.at(speed.t[k]) ?: 0.0
            out.add(Insight(1.0, fr("%s : vitesse maximale %.0f km/h vers %.0f m.", run.label, vmax, d)))
        }
        run.stat?.let { st ->
            if (st.distM > 200.0) {
                out.add(Insight(2.0, fr(
                    "%s : %d freinage(s) sur %.0f m (%.1f par km) ; %d relance(s).",
                    run.label, st.brakes, st.distM, st.brakes / (st.distM / 1000.0), st.relaunches,
                )))
            }
        }
        return out
    }

    private class Sec(val entry: Double, val minV: Double, val meanV: Double, val exit: Double)

    private fun secStats(r: InsightRun, s0: Double, s1: Double): Sec? {
        val speed = r.ch["speed"] ?: return null
        val tOfS = Series(r.sOfT.v, r.sOfT.t)
        val t0 = tOfS.at(s0) ?: return null
        val t1 = tOfS.at(s1) ?: return null
        val idx = speed.t.indices.filter { speed.t[it] >= t0 && speed.t[it] <= t1 }
        if (idx.size < 2) return null
        val vals = idx.map { speed.v[it] }
        return Sec(speed.at(t0) ?: vals.first(), vals.min(), vals.average(), speed.at(t1) ?: vals.last())
    }

    private fun cause(a: Sec, b: Sec, slower: Boolean): String {
        val dEntry = b.entry - a.entry
        val dMin = b.minV - a.minV
        val dExit = b.exit - a.exit
        val dMean = b.meanV - a.meanV
        return if (slower) when {
            dEntry < -2.0 -> fr("vitesse d'entrée plus basse (%.1f km/h)", dEntry)
            dMin < -2.0 -> fr("vitesse minimale plus basse (%.1f km/h) : freinage plus long ou virage plus lent", dMin)
            dExit < -2.0 -> fr("sortie plus lente (%.1f km/h) : relance plus faible", dExit)
            else -> fr("vitesse moyenne plus basse (%.1f km/h)", dMean)
        } else when {
            dEntry > 2.0 -> fr("vitesse d'entrée plus haute (+%.1f km/h)", dEntry)
            dMin > 2.0 -> fr("vitesse minimale plus haute (+%.1f km/h) : freinage plus court ou virage plus rapide", dMin)
            dExit > 2.0 -> fr("sortie plus rapide (+%.1f km/h) : meilleure relance", dExit)
            else -> fr("vitesse moyenne plus haute (+%.1f km/h)", dMean)
        }
    }

    /** Ecart de temps par section avec la cause probable (comparaison de deux runs alignes). */
    fun compare(ref: InsightRun, run: InsightRun, sections: List<Triple<Double, Double, Double>>): List<Insight> {
        val out = ArrayList<Insight>()
        for ((s0, s1, dv) in sections) {
            if (abs(dv) < 0.15) continue
            val a = secStats(ref, s0, s1) ?: continue
            val b = secStats(run, s0, s1) ?: continue
            out.add(Insight(abs(dv) * 10.0, fr(
                "%s %s %.1f s entre %.0f et %.0f m : %s.",
                run.label, if (dv > 0) "perd" else "gagne", abs(dv), s0, s1, cause(a, b, dv > 0),
            )))
        }
        return out
    }

    /** Regularite et tendance sur plusieurs runs (dans l'ordre de la liste). */
    fun consistency(runs: List<InsightRun>): List<Insight> {
        val n = runs.size
        if (n < 3) return emptyList()
        val t = runs.map { it.durationS }
        val out = ArrayList<Insight>()
        out.add(Insight(3.0, fr("Écart entre le meilleur et le pire run : %.1f s sur %d runs.", t.max() - t.min(), n)))
        val mi = (n - 1) / 2.0
        val mt = t.average()
        var cov = 0.0
        var vr = 0.0
        for (i in 0 until n) { cov += (i - mi) * (t[i] - mt); vr += (i - mi) * (i - mi) }
        val slope = if (vr > 0) cov / vr else 0.0
        if (slope > 0.3) out.add(Insight(6.0, fr("Le temps augmente de %.1f s par run : baisse de rythme ou fatigue possible.", slope)))
        else if (slope < -0.3) out.add(Insight(5.0, fr("Progression régulière : %.1f s par run.", slope)))
        return out
    }

    /** Toutes les analyses, classees par importance. sections : pour chaque index de run (hors reference), ses sections. */
    fun analyze(
        runs: List<InsightRun>, refIdx: Int, brakeThr: Double,
        sections: Map<Int, List<Triple<Double, Double, Double>>>,
    ): List<Insight> {
        val all = ArrayList<Insight>()
        if (runs.isEmpty()) return all
        runs.forEachIndexed { i, r ->
            if (i != refIdx) sections[i]?.let { all.addAll(compare(runs[refIdx], r, it)) }
        }
        all.addAll(consistency(runs))
        runs.forEach { all.addAll(events(it, brakeThr)) }
        return all.sortedByDescending { it.score }
    }
}

/** Resume structure a copier dans un assistant IA externe (rien n'est envoye par l'application). */
object AiPrompt {
    private fun fr(f: String, vararg a: Any) = String.format(Locale.FRANCE, f, *a)

    fun build(context: String, runs: List<InsightRun>, refIdx: Int, insights: List<Insight>): String {
        val sb = StringBuilder()
        sb.append("Je suis un sportif de montagne. Voici l'analyse de mes données GPS et capteurs (calculée sur mon téléphone). ")
        sb.append("Contexte : ").append(context).append(".\n\nRuns :\n")
        runs.forEachIndexed { i, r ->
            val st = r.stat
            sb.append("- ").append(r.label).append(if (i == refIdx) " (référence)" else "").append(" : ")
            sb.append(fr("durée %.1f s", r.durationS))
            if (st != null) {
                sb.append(fr(
                    ", distance %.0f m, D+ %.0f m, D- %.0f m, vitesse moy. %.1f km/h, max %.1f km/h, %d freinage(s) (max %.1f m/s²), %d relance(s)",
                    st.distM, st.dPlus, st.dMinus, st.vAvg, st.vMax, st.brakes, st.brakeMax, st.relaunches,
                ))
                st.gMax?.let { sb.append(fr(", G max %.2f", it)) }
            }
            sb.append("\n")
        }
        sb.append("\nConstats calculés :\n")
        insights.take(12).forEach { sb.append("- ").append(it.text).append("\n") }
        sb.append("\nQuestion : explique en quelques lignes où je perds du temps et pourquoi, puis propose 3 conseils ")
        sb.append("de pilotage ou d'entraînement concrets et mesurables. Ne suppose rien au-delà de ces chiffres.\n")
        return sb.toString()
    }
}
