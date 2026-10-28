package fr.volaracing.training.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignTest {

    /** Trace rectiligne le long de x : de x0 a x1 a la vitesse v, un point par seconde. */
    private fun track(x0: Double, x1: Double, v: Double, yOffset: Double = 0.0): List<TrackPt> {
        val out = ArrayList<TrackPt>()
        var t = 0.0
        while (x0 + v * t <= x1 + 1e-9) {
            out.add(TrackPt((t * 1e9).toLong(), 0.0, 0.0, x0 + v * t, yOffset, v, v * t))
            t += 1.0
        }
        return out
    }

    @Test fun chaineDePortes() {
        val g = Gate.chain(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(200.0, 0.0)), 20.0)
        assertEquals(3, g.size)
        assertEquals(0.5, g[1].crossing(Pt(90.0, 0.0), Pt(110.0, 0.0))!!, 1e-9)
        assertNull(g[1].crossing(Pt(110.0, 0.0), Pt(90.0, 0.0)))
    }

    @Test fun tempsDEtape() {
        val tr = track(0.0, 300.0, 10.0)
        val gates = Gate.chain(listOf(Pt(0.0, 0.0), Pt(95.0, 0.0), Pt(300.0, 0.0)), 20.0)
        val s = Splits.compute(tr, gates.subList(1, 2))
        assertEquals(1, s.size)
        assertEquals(9_500_000_000L, s[0]!!)
    }

    @Test fun etapeManqueeDonneNull() {
        val tr = track(0.0, 300.0, 10.0)
        val g = Gate.at(Pt(150.0, 80.0), 1.0, 0.0, 20.0) // loin du trace
        assertNull(Splits.compute(tr, listOf(g))[0])
    }

    @Test fun codecDesSplits() {
        val l = listOf(5L, null, 9L)
        assertEquals("5,,9", SplitCodec.encode(l))
        assertEquals(l, SplitCodec.parse("5,,9"))
        assertTrue(SplitCodec.parse("").isEmpty())
    }

    @Test fun ecartAligneAvecDepartsDifferents() {
        val a = track(0.0, 1000.0, 10.0)      // 100 s
        val b = track(200.0, 1000.0, 12.5)    // part a 200 m, 64 s
        val al = Compare.deltaAligned(a, b)
        assertEquals(1.0, al.overlap, 1e-9)
        // Partie commune 200-1000 m : A 80 s, B 64 s => B gagne 16 s
        assertEquals(-16.0, al.curve.last().second, 0.1)
        assertEquals(0.0, al.curve.first().second, 1e-9)
    }

    @Test fun recouvrementFaibleSiTracesDifferents() {
        val a = track(0.0, 1000.0, 10.0)
        val b = track(0.0, 1000.0, 10.0, yOffset = 200.0) // 200 m a cote
        assertTrue(Compare.deltaAligned(a, b).overlap < 0.1)
    }

    @Test fun sectionsGainsEtPertes() {
        val curve = listOf(Pair(0.0, 0.0), Pair(100.0, 2.0), Pair(200.0, 1.0))
        val s = Compare.sections(curve, 2)
        assertEquals(2.0, s[0].third, 1e-9)   // B perd 2 s sur la 1re moitie
        assertEquals(-1.0, s[1].third, 1e-9)  // B regagne 1 s ensuite
    }
}
