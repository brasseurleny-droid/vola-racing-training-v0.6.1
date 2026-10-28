package fr.volaracing.training.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

/** Point en metres dans le repere local (x vers l'est, y vers le nord). */
data class Pt(val x: Double, val y: Double)

/** Position GPS deja projetee, avec son horodatage elapsedRealtimeNanos. */
data class Fix(val tNs: Long, val x: Double, val y: Double, val accuracy: Float = 0f)

/** Projection equirectangulaire locale : suffisante sur quelques kilometres. */
class LocalFrame(val lat0: Double, val lon0: Double) {
    private val r = 6371008.8
    private val cosLat = cos(Math.toRadians(lat0))

    fun toLocal(lat: Double, lon: Double) =
        Pt(Math.toRadians(lon - lon0) * r * cosLat, Math.toRadians(lat - lat0) * r)

    fun toLatLon(p: Pt): Pair<Double, Double> =
        Pair(lat0 + Math.toDegrees(p.y / r), lon0 + Math.toDegrees(p.x / (r * cosLat)))
}

/**
 * Porte = segment [p1, p2] perpendiculaire a la direction du parcours (dirX, dirY, unitaire).
 * Le franchissement n'est valide que dans le sens de la direction.
 */
class Gate(val p1: Pt, val p2: Pt, val dirX: Double, val dirY: Double) {

    /**
     * Fraction t dans ]0, 1] ou le segment a->b coupe la porte, ou null.
     * Le point exactement en a est exclu (evite le double comptage entre deux segments).
     */
    fun crossing(a: Pt, b: Pt): Double? {
        val rx = b.x - a.x
        val ry = b.y - a.y
        if (rx * dirX + ry * dirY <= 0.0) return null // mauvais sens
        val sx = p2.x - p1.x
        val sy = p2.y - p1.y
        val denom = rx * sy - ry * sx
        if (abs(denom) < 1e-12) return null // parallele
        val qx = p1.x - a.x
        val qy = p1.y - a.y
        val t = (qx * sy - qy * sx) / denom
        val u = (qx * ry - qy * rx) / denom
        return if (t > 0.0 && t <= 1.0 && u >= 0.0 && u <= 1.0) t else null
    }

    companion object {
        fun at(c: Pt, dirX: Double, dirY: Double, width: Double): Gate {
            val nx = -dirY
            val ny = dirX
            val h = width / 2.0
            return Gate(Pt(c.x - nx * h, c.y - ny * h), Pt(c.x + nx * h, c.y + ny * h), dirX, dirY)
        }

        /**
         * Portes pour une polyligne A -> etapes -> B. Chaque porte est perpendiculaire a la
         * direction locale du parcours (voisin suivant moins voisin precedent).
         */
        fun chain(points: List<Pt>, width: Double): List<Gate> {
            require(points.size >= 2) { "Il faut au moins A et B" }
            return points.indices.map { i ->
                val a = points[maxOf(i - 1, 0)]
                val b = points[minOf(i + 1, points.size - 1)]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val l = hypot(dx, dy)
                require(l > 0.0) { "Deux points du parcours sont confondus" }
                at(points[i], dx / l, dy / l, width)
            }
        }

        /** Portes de depart (A) et d'arrivee (B), orientees de A vers B. */
        fun pair(a: Pt, b: Pt, width: Double): Pair<Gate, Gate> {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val l = hypot(dx, dy)
            require(l > 0.0) { "A et B sont confondus" }
            val ux = dx / l
            val uy = dy / l
            return Pair(at(a, ux, uy, width), at(b, ux, uy, width))
        }
    }
}

/** Instant interpole lineairement entre deux horodatages. */
fun lerpNs(t0: Long, t1: Long, f: Double): Long = t0 + ((t1 - t0) * f).roundToLong()
