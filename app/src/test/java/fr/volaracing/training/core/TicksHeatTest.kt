package fr.volaracing.training.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicksHeatTest {
    @Test fun graduationsRondes() {
        assertEquals(listOf(0.0, 50.0, 100.0, 150.0, 200.0), Ticks.nice(0.0, 237.0, 5))
        assertEquals(listOf(-2.0, 0.0, 2.0, 4.0, 6.0), Ticks.nice(-3.0, 7.0, 5))
        val t = Ticks.nice(0.0, 1.0, 5)
        assertEquals(6, t.size)
        assertEquals(0.2, Ticks.step(t), 1e-9)
    }

    @Test fun etiquettesAxeX() {
        assertEquals("250 m", Ticks.fmtX(250.0, true, 50.0))
        assertEquals("2.0 km", Ticks.fmtX(2000.0, true, 1000.0))
        assertEquals("12 s", Ticks.fmtX(12.0, false, 2.0))
        assertEquals("1:30", Ticks.fmtX(90.0, false, 30.0 * 2))
    }

    @Test fun percentiles() {
        val v = DoubleArray(101) { it.toDouble() }
        assertEquals(5.0, Heat.percentile(v, 5.0), 1e-9)
        assertEquals(95.0, Heat.percentile(v, 95.0), 1e-9)
    }

    @Test fun classesDeCouleur() {
        assertEquals(0, Heat.classOf(0.0, 0.0, 10.0, 10, false))
        assertEquals(9, Heat.classOf(10.0, 0.0, 10.0, 10, false))
        assertEquals(9, Heat.classOf(0.0, 0.0, 10.0, 10, true))
        assertEquals(-1, Heat.classOf(Double.NaN, 0.0, 10.0, 10, false))
        assertEquals(5, Heat.classOf(3.0, 3.0, 3.0, 10, false))
    }

    @Test fun valeursParSegment() {
        val s = Series(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 10.0, 30.0))
        val v = Heat.segmentValues(s, doubleArrayOf(0.0, 1.0, 2.0))
        assertEquals(5.0, v[0], 1e-9)
        assertEquals(20.0, v[1], 1e-9)
        assertTrue(Heat.segmentValues(s, doubleArrayOf(0.0, 5.0))[0].isNaN())
    }
}
