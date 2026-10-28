package fr.volaracing.training.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersInsightsTest {

    private fun pt(k: Int, x: Double, y: Double = 0.0, v: Double = 10.0, acc: Float = 5f, alt: Double = 1000.0) =
        TrackPt(k * 1_000_000_000L, 0.0, 0.0, x, y, v, x, alt, acc, 8)

    @Test fun sautDePositionRejete() {
        val tr = (0..20).map { k -> if (k == 10) pt(k, 10.0 * k, 500.0) else pt(k, 10.0 * k) }
        val (out, info) = Filters.cleanTrack(tr)
        assertEquals(1, info.droppedJump)
        assertEquals(20, out.size)
        assertTrue(out.zipWithNext().all { (a, b) -> b.dist >= a.dist })
        assertEquals(tr.first().tNs, out.first().tNs)
        assertEquals(tr.last().tNs, out.last().tNs)
    }

    @Test fun mauvaisePrecisionRejetee() {
        val tr = (0..20).map { k -> pt(k, 10.0 * k, acc = if (k == 7) 200f else 5f) }
        val (out, info) = Filters.cleanTrack(tr)
        assertEquals(1, info.droppedAcc)
        assertEquals(20, out.size)
    }

    @Test fun picDeVitesseLisse() {
        val tr = (0..20).map { k -> pt(k, 10.0 * k, v = if (k == 12) 80.0 else 10.0) }
        val (out, info) = Filters.cleanTrack(tr)
        assertTrue(info.spikes >= 1)
        assertEquals(10.0, out[12].speed, 1e-9)
    }

    @Test fun signalPropreInchange() {
        val tr = (0..20).map { k -> pt(k, 10.0 * k) }
        val (out, info) = Filters.cleanTrack(tr)
        assertEquals(0, info.total)
        assertEquals(21, out.size)
    }

    @Test fun altitudeBarometrique() {
        val t = DoubleArray(50) { it.toDouble() }
        val hpa = DoubleArray(50) { 1000.0 - 0.1 * it }   // pression qui baisse : on monte (~0,84 m par s)
        val gps = DoubleArray(50) { 500.0 }
        val alt = Filters.baroAltitude(t, hpa, t, gps)
        assertNotNull(alt)
        assertEquals(37.0, alt!!.last() - alt.first(), 3.0)
        assertNull(Filters.baroAltitude(DoubleArray(3), DoubleArray(3), t, gps))
    }

    @Test fun aggregationImuAllerRetour() {
        val g = Series(doubleArrayOf(0.05, 0.15, 0.25), doubleArrayOf(1.0, 2.5, 1.2))
        val lines = Imu.toLines(mapOf("g" to g), 1_000_000_000L)
        val back = Imu.aggregate(lines.asSequence(), 1_000_000_000L, 2_000_000_000L)["g"]!!
        assertEquals(3, back.size)
        assertEquals(2.5, back.v[1], 1e-6)
    }

    private fun insightRun(label: String, speedKmh: DoubleArray): InsightRun {
        val n = speedKmh.size
        val t = DoubleArray(n) { it.toDouble() }
        val along = DoubleArray(n) { i -> if (i == 0) 0.0 else (speedKmh[i] - speedKmh[i - 1]) / 3.6 }
        var d = 0.0
        val s = DoubleArray(n) { i -> if (i > 0) d += speedKmh[i] / 3.6; d }
        val ch = mapOf(
            "speed" to Series(t, speedKmh), "along" to Series(t, along), "alat" to Series(t, DoubleArray(n)),
        )
        return InsightRun(label, ch, Series(t, s), (n - 1).toDouble(), null)
    }

    @Test fun causeDeLaPerteDeTemps() {
        val ref = insightRun("A", DoubleArray(30) { 40.0 })
        val slow = insightRun("B", DoubleArray(30) { i -> if (i in 10..14) 25.0 else 40.0 })
        val total = ref.sOfT.v.last()
        val lines = Insights.compare(ref, slow, listOf(Triple(total * 0.3, total * 0.55, 1.4)))
        assertEquals(1, lines.size)
        assertTrue(lines[0].text.contains("perd"))
        assertTrue(lines[0].text.contains("minimale"))
    }

    @Test fun regulariteEtTendance() {
        val runs = listOf(30.0, 31.0, 32.5, 34.0).map { dur ->
            InsightRun("R", emptyMap(), Series(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, 1.0)), dur, null)
        }
        val l = Insights.consistency(runs)
        assertTrue(l.any { it.text.contains("fatigue") })
    }

    @Test fun resumePourIA() {
        val r = insightRun("S1 Run 1", DoubleArray(20) { 30.0 })
        val txt = AiPrompt.build("VTT, descente", listOf(r), 0, listOf(Insight(1.0, "Exemple de constat.")))
        assertTrue(txt.contains("S1 Run 1"))
        assertTrue(txt.contains("Constats calculés"))
        assertTrue(txt.contains("Exemple de constat."))
    }
}
