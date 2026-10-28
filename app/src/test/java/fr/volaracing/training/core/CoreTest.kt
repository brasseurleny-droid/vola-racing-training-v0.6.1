package fr.volaracing.training.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    private val gate = Gate.at(Pt(100.0, 0.0), 1.0, 0.0, 20.0)

    @Test fun projectionLocale() {
        val f = LocalFrame(45.0, 6.0)
        val p = f.toLocal(45.001, 6.0)
        assertEquals(111.2, p.y, 0.5)
        val back = f.toLatLon(f.toLocal(45.0007, 6.0012))
        assertEquals(45.0007, back.first, 1e-9)
        assertEquals(6.0012, back.second, 1e-9)
    }

    @Test fun intersectionMilieu() {
        val t = gate.crossing(Pt(86.0, 0.0), Pt(114.0, 0.0))
        assertNotNull(t)
        assertEquals(0.5, t!!, 1e-9)
    }

    @Test fun mauvaisSensIgnore() {
        assertNull(gate.crossing(Pt(114.0, 0.0), Pt(86.0, 0.0)))
    }

    @Test fun horsLargeurIgnore() {
        assertNull(gate.crossing(Pt(86.0, 15.0), Pt(114.0, 15.0)))
    }

    @Test fun segmentQuiNeTouchePasLaPorte() {
        assertNull(gate.crossing(Pt(10.0, 0.0), Pt(60.0, 0.0)))
    }

    @Test fun interpolationInstant() {
        assertEquals(1_500_000_000L, lerpNs(1_000_000_000L, 2_000_000_000L, 0.5))
    }

    /** Trajet simule : fixes toutes les dtS secondes, vitesse constante le long de x. */
    private fun simulate(v: Double, dtS: Double, x0: Double, x1: Double, t0Ns: Long, accuracy: Float = 5f): List<Fix> {
        val out = ArrayList<Fix>()
        var k = 0
        while (true) {
            val t = k * dtS
            val x = x0 + v * t
            out.add(Fix(t0Ns + (t * 1e9).toLong(), x, 0.0, accuracy))
            if (x > x1) break
            k++
        }
        return out
    }

    private fun detect(fixes: List<Fix>, length: Double, minSpeed: Double = 2.0): List<GateDetector.Event> {
        val (ga, gb) = Gate.pair(Pt(0.0, 0.0), Pt(length, 0.0), 20.0)
        val det = GateDetector(ga, gb, minSpeed, 25f)
        return fixes.flatMap { det.onFix(it) }
    }

    private fun checkDuration(v: Double, dtS: Double) {
        val length = 1000.0
        val fixes = simulate(v, dtS, -37.3, length + 60.0, 5_000_000_000L)
        val finished = detect(fixes, length).filterIsInstance<GateDetector.Event.Finished>()
        assertEquals(1, finished.size)
        val dur = (finished[0].endNs - finished[0].startNs) / 1e9
        assertEquals(length / v, dur, 0.05)
    }

    @Test fun chronoVitesseModeree() = checkDuration(8.0, 1.0)

    /** 100 km/h = 27,8 m/s avec un seul point par seconde : ~28 m entre deux fixes. */
    @Test fun chronoCentKmhUnHertz() = checkDuration(100.0 / 3.6, 1.0)

    /** Cas defavorable : un point toutes les 2 s, ~55 m entre deux fixes. */
    @Test fun chronoCentKmhDeuxSecondes() = checkDuration(100.0 / 3.6, 2.0)

    @Test fun chronoDixHertz() = checkDuration(100.0 / 3.6, 0.1)

    @Test fun instantAbsoluCorrect() {
        val t0 = 7_000_000_000L
        val fixes = simulate(20.0, 1.0, -50.0, 1100.0, t0)
        val started = detect(fixes, 1000.0).filterIsInstance<GateDetector.Event.Started>().first()
        // x = -50 + 20 t = 0  ->  t = 2,5 s
        assertTrue(kotlin.math.abs(started.tNs - (t0 + 2_500_000_000L)) < 1_000L)
    }

    @Test fun pasDeDepartSousLaVitesseMinimale() {
        val fixes = simulate(1.0, 1.0, -10.0, 1100.0, 0L) // marche a 3,6 km/h
        assertTrue(detect(fixes, 1000.0, minSpeed = 2.0).isEmpty())
    }

    @Test fun sensInverseNeDeclenchePas() {
        val fixes = simulate(10.0, 1.0, -10.0, 1100.0, 0L).map { it.copy(x = 1000.0 - it.x) }
        assertTrue(detect(fixes, 1000.0).isEmpty())
    }

    @Test fun qualiteFaibleMarquee() {
        val fixes = simulate(10.0, 1.0, -30.0, 1060.0, 0L, accuracy = 40f)
        val fin = detect(fixes, 1000.0).filterIsInstance<GateDetector.Event.Finished>()
        assertEquals(1, fin.size)
        assertTrue(fin[0].lowQuality)
    }

    @Test fun plusieursPassagesDansUneSession() {
        val l = 500.0
        val a = simulate(10.0, 1.0, -30.0, l + 40.0, 0L)
        val retourOffset = 400_000_000_000L
        val b = simulate(12.0, 1.0, -30.0, l + 40.0, retourOffset)
        // Le saut entre la fin de la 1re descente et le debut de la 2e est un "teleport" vers A :
        // il va dans le sens inverse, donc sans effet.
        val ev = detect(a + b, l).filterIsInstance<GateDetector.Event.Finished>()
        assertEquals(2, ev.size)
        assertEquals(l / 10.0, (ev[0].endNs - ev[0].startNs) / 1e9, 0.05)
        assertEquals(l / 12.0, (ev[1].endNs - ev[1].startNs) / 1e9, 0.05)
    }

    private fun lineTrack(speed: Double, length: Double, dtS: Double): List<TrackPt> {
        val out = ArrayList<TrackPt>()
        var t = 0.0
        while (t * speed <= length + 1e-9) {
            out.add(TrackPt((t * 1e9).toLong(), 0.0, 0.0, speed * t, 0.0, speed, speed * t))
            t += dtS
        }
        return out
    }

    @Test fun tempsALaDistance() {
        val tr = listOf(
            TrackPt(0L, 0.0, 0.0, 0.0, 0.0, 10.0, 0.0),
            TrackPt(10_000_000_000L, 0.0, 0.0, 100.0, 0.0, 10.0, 100.0),
        )
        assertEquals(2.5, Compare.timeAtDistance(tr, 25.0)!!, 1e-9)
        assertNull(Compare.timeAtDistance(tr, 101.0))
    }

    @Test fun ecartCumule() {
        val a = lineTrack(12.5, 1000.0, 1.0)
        val b = lineTrack(10.0, 1000.0, 1.0)
        val delta = Compare.deltaCurve(a, b, 10.0)
        assertEquals(0.0, delta.first().second, 1e-9)
        assertEquals(20.0, delta.last().second, 1e-6) // 100 s - 80 s
        // B perd du temps de facon croissante
        assertTrue(delta.zipWithNext().all { (p, q) -> q.second >= p.second - 1e-9 })
    }

    @Test fun statsVitesseEtDuree() {
        val s = StatsCalc.compute(lineTrack(10.0, 100.0, 1.0), 1.0)
        assertEquals(10.0, s.durationS, 1e-9)
        assertEquals(100.0, s.distM, 1e-9)
        assertEquals(10.0, s.avgMs, 1e-9)
        assertEquals(10.0, s.maxMs, 1e-9)
        assertEquals(10.0, s.timeAtMaxS, 1e-9)
    }

    @Test fun formatTemps() {
        assertEquals("1:12.34", Fmt.time(72_340_000_000L))
        assertEquals("0:05.00", Fmt.time(5_000_000_000L))
    }

    @Test fun traceInterpoleeAuxBornes() {
        val f = LocalFrame(45.0, 6.0)
        fun row(t: Long, lon: Double) = GpsRow(t, 0L, 45.0, lon, 0.0, 10.0, 0.0, 5f, 1f, 8)
        val rows = listOf(row(0L, 6.0), row(1_000_000_000L, 6.0001), row(2_000_000_000L, 6.0002))
        val tr = Track.build(rows, 500_000_000L, 1_500_000_000L, f)
        assertEquals(3, tr.size)
        assertEquals(500_000_000L, tr.first().tNs)
        assertEquals(1_500_000_000L, tr.last().tNs)
        assertEquals(0.0, tr.first().dist, 1e-9)
        assertTrue(tr.last().dist > 0.0)
    }
}
