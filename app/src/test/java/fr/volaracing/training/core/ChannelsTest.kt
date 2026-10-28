package fr.volaracing.training.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {

    /** Trace rectiligne le long de x : vitesse donnee par speedAt(t), altitude par altAt(dist). */
    private fun track(
        seconds: Int, speedAt: (Double) -> Double, altAt: (Double) -> Double = { 0.0 },
    ): List<TrackPt> {
        val out = ArrayList<TrackPt>()
        var x = 0.0
        for (k in 0..seconds) {
            val t = k.toDouble()
            val v = speedAt(t)
            if (k > 0) x += v
            out.add(TrackPt((t * 1e9).toLong(), 0.0, 0.0, x, 0.0, v, x, altAt(x)))
        }
        return out
    }

    @Test fun vitesseConstante() {
        val ch = Channels.gps(track(20, { 10.0 }), 1.0)
        assertEquals(36.0, ch["speed"]!!.v[10], 1e-9)
        assertEquals(0.0, ch["along"]!!.v[10], 1e-9)
        assertEquals(0.0, ch["alat"]!!.v[10], 1e-9)
    }

    @Test fun penteDeDixPourCent() {
        val ch = Channels.gps(track(40, { 10.0 }, { d -> -0.10 * d }), 1.0)
        assertEquals(-10.0, ch["slope"]!!.v[20], 0.2)
    }

    @Test fun freinageEtRelance() {
        // 10 m/s, freinage a -4 m/s2 pendant 2 s, puis relance +2 m/s2
        val prof = { t: Double -> when { t < 5 -> 10.0; t < 7 -> 10.0 - 4.0 * (t - 5); t < 10 -> 2.0 + 2.0 * (t - 7); else -> 8.0 } }
        val tr = track(14, prof)
        val ch = Channels.gps(tr, 0.5).toMutableMap()
        val st = RunStats.compute(ch, 0.0, 14.0, 3.0)!!
        assertEquals(1, st.brakes)
        assertTrue(st.brakeMax >= 3.5)
        assertTrue(st.relaunches >= 1)
    }

    @Test fun ecartDeTempsContreReference() {
        val ref = track(40, { 10.0 })
        val slow = track(50, { 8.0 })
        val chRef = Channels.gps(ref, 1.0)
        val chSlow = Channels.gps(slow, 1.0)
        val prog = Align.progress(ref, slow)
        val v = Channels.vsReference(ref, chRef["speed"]!!, slow, chSlow["speed"]!!, prog)
        val delta = v["delta"]!!
        assertEquals(0.0, delta.v.first(), 1e-6)
        assertTrue(delta.v.last() > 5.0) // plus lent : l'ecart croit
        val own = Channels.vsReference(ref, chRef["speed"]!!, ref, chRef["speed"]!!, null)
        assertEquals(0.0, own["delta"]!!.v.max(), 1e-12)
    }

    @Test fun agregationImu() {
        val lines = sequenceOf(
            "0,acc,0,0,9.80665", "50000000,acc,0,0,19.6133", "100000000,acc,0,0,9.80665",
            "150000000,acc,0,0,9.80665", "10000000,gyr,0,0,2", "110000000,gyr,0,0,1",
            "0,baro,1000.0,0,0", "100000000,baro,1002.0,0,0",
        )
        val out = Imu.aggregate(lines, 0L, 1_000_000_000L)
        val g = out["g"]!!
        assertEquals(2.0, g.v[0], 1e-6)  // pic conserve dans la tranche 0
        assertEquals(1.0, g.v[1], 1e-6)
        assertNotNull(out["gyro"])
        assertEquals(1002.0, out["baro"]!!.v[1], 1e-9)
    }

    @Test fun serieInterpolee() {
        val s = Series(doubleArrayOf(0.0, 10.0), doubleArrayOf(0.0, 100.0))
        assertEquals(25.0, s.at(2.5)!!, 1e-9)
        assertTrue(s.at(11.0) == null)
    }

    @Test fun rapportTexte() {
        val st = RunStat(60.0, 600.0, 0.0, 100.0, -16.0, 20.0, 5.0, 36.0, 60.0, 1.5, 5, 5.0, 2)
        val ref = st.copy(brakes = 3, brakeMax = 4.0, vMax = 58.0)
        val l = Report.lines("B", ref, st, listOf(Triple(0.0, 200.0, 1.2), Triple(200.0, 400.0, -0.5)))
        assertTrue(l.any { it.contains("perd") })
        assertTrue(l.any { it.contains("gagne") })
        assertTrue(l.any { it.startsWith("Freinages") })
    }
}
