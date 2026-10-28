package fr.volaracing.training.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterTest {

    /** Phases successives : (duree s, vitesse km/h, pente %). 1 point par seconde. */
    private fun profile(phases: List<Triple<Int, Double, Double>>): Array<DoubleArray> {
        val t = ArrayList<Double>(); val v = ArrayList<Double>(); val a = ArrayList<Double>(); val d = ArrayList<Double>()
        var alt = 1000.0; var dist = 0.0; var k = 0
        for ((dur, kmh, slope) in phases) {
            repeat(dur) {
                val ms = kmh / 3.6
                dist += ms
                alt += ms * slope / 100.0
                t.add(k.toDouble()); v.add(kmh); a.add(alt); d.add(dist); k++
            }
        }
        return arrayOf(t.toDoubleArray(), v.toDoubleArray(), a.toDoubleArray(), d.toDoubleArray())
    }

    private val vtt = SegParams.preset("VTT")

    @Test fun deuxDescentesSeparees() {
        val p = profile(listOf(
            Triple(30, 0.0, 0.0), Triple(60, 30.0, -10.0), Triple(20, 0.0, 0.0),
            Triple(120, 8.0, 6.0), Triple(60, 30.0, -10.0),
        ))
        val segs = Segmenter.segment(p[0], p[1], p[2], p[3], vtt)
        val descents = segs.filter { it.type == SegType.DESCENT }
        assertEquals(2, descents.size)
        assertEquals(30.0, descents[0].t0, 10.0)
        assertEquals(90.0, descents[0].t1, 10.0)
        assertTrue(descents[1].t0 > 200.0)
        assertTrue(segs.any { it.type == SegType.CLIMB })
        assertTrue(descents.all { it.dropM > 15.0 })
    }

    @Test fun remonteeMecaniqueNEstPasUneDescente() {
        val p = profile(listOf(Triple(120, 15.0, 20.0)))
        val segs = Segmenter.segment(p[0], p[1], p[2], p[3], vtt)
        assertTrue(segs.none { it.type == SegType.DESCENT })
        assertTrue(segs.any { it.type == SegType.LIFT })
    }

    @Test fun descenteTropCourteIgnoree() {
        val p = profile(listOf(Triple(30, 0.0, 0.0), Triple(8, 30.0, -10.0), Triple(30, 0.0, 0.0)))
        val segs = Segmenter.segment(p[0], p[1], p[2], p[3], vtt)
        assertTrue(segs.none { it.type == SegType.DESCENT })
    }

    @Test fun trouCourtEntreDeuxDescentesFusionne() {
        val p = profile(listOf(Triple(60, 30.0, -10.0), Triple(5, 30.0, 0.0), Triple(60, 30.0, -10.0)))
        val segs = Segmenter.segment(p[0], p[1], p[2], p[3], vtt)
        assertEquals(1, segs.count { it.type == SegType.DESCENT })
    }

    private fun rows(n: Int, speedField: Double, ms: Double): List<GpsRow> =
        (0 until n).map { k ->
            GpsRow((k * 1_000_000_000L), 0L, 45.0, 6.0 + k * ms / 78_700.0, 1000.0, speedField, 0.0, 5f, 1f, 8)
        }

    @Test fun sessionLenteDonneUnRunLibre() {
        // 20 s a 1 m/s (3,6 km/h) : sous la vitesse minimale mais enregistrement complet conserve
        val plan = RunPlanner.plan(rows(21, 1.0, 1.0), 1.0, 8.0, vtt)
        assertEquals(1, plan.size)
        assertEquals(1, plan[0].kind)
        assertEquals(20_000_000_000L, plan[0].endNs - plan[0].startNs)
    }

    @Test fun sessionSansVitesseGpsUtiliseLesPositions() {
        // champ vitesse = 0 mais deplacement de 10 m/s : vitesse deduite des positions
        val tr = Track.fromRows(rows(10, 0.0, 10.0), LocalFrame(45.0, 6.0))
        assertEquals(10.0, tr[5].speed, 0.5)
        val plan = RunPlanner.plan(rows(30, 0.0, 10.0), 1.0, 8.0, vtt)
        assertEquals(1, plan.size)
    }

    @Test fun enregistrementTropCourtSansRun() {
        assertTrue(RunPlanner.plan(rows(3, 5.0, 5.0), 1.0, 8.0, vtt).isEmpty())
    }
}
