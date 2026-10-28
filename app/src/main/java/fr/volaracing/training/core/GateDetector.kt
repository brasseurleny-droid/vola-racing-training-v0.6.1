package fr.volaracing.training.core

import kotlin.math.hypot

/**
 * Machine a etats : attend le franchissement de A, puis de B (sens A vers B uniquement).
 * L'instant est interpole entre les deux fixes GPS qui encadrent la porte.
 * Un passage dont l'un des fixes depasse le seuil de precision est conserve mais marque
 * "qualite faible" (decision a valider, voir README).
 */
class GateDetector(
    private val gateA: Gate,
    private val gateB: Gate,
    private val minSpeedMs: Double,
    private val accuracyThreshold: Float,
) {
    sealed class Event {
        data class Started(val tNs: Long, val lowQuality: Boolean) : Event()
        data class Finished(val startNs: Long, val endNs: Long, val lowQuality: Boolean) : Event()
    }

    private var prev: Fix? = null
    private var startNs = 0L
    private var startLow = false
    var running = false
        private set

    fun reset() {
        prev = null
        running = false
    }

    fun onFix(f: Fix): List<Event> {
        val p = prev
        prev = f
        if (p == null || f.tNs <= p.tNs) return emptyList()

        val a = Pt(p.x, p.y)
        val b = Pt(f.x, f.y)
        val dtS = (f.tNs - p.tNs) / 1e9
        val speed = hypot(f.x - p.x, f.y - p.y) / dtS
        val low = maxOf(p.accuracy, f.accuracy) > accuracyThreshold

        if (running) {
            val tb = gateB.crossing(a, b)
            if (tb != null) {
                running = false
                return listOf(Event.Finished(startNs, lerpNs(p.tNs, f.tNs, tb), startLow || low))
            }
        }
        val ta = gateA.crossing(a, b)
        if (ta != null && speed >= minSpeedMs) {
            // Si on repasse A pendant une course (retour en arriere), on redemarre.
            running = true
            startNs = lerpNs(p.tNs, f.tNs, ta)
            startLow = low
            return listOf(Event.Started(startNs, low))
        }
        return emptyList()
    }
}
