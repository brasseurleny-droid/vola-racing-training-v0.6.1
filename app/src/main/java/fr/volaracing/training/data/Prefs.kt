package fr.volaracing.training.data

import android.content.Context
import java.util.UUID

object Act {
    const val VTT = "VTT"
    const val SKI = "SKI"
    const val OTHER = "AUTRE"
    val all = listOf(VTT, SKI, OTHER)
    fun label(a: String) = when (a) { SKI -> "Ski"; OTHER -> "Autre"; else -> "VTT" }
}

/** Reglages de detection, uniques pour toute l'application (les preregles servent de point de depart). */
data class Profile(
    val gateWidth: Double,
    val minSpeedKmh: Double,
    val windowS: Double,
    val accThreshold: Double,
    val brakeThreshold: Double, // m/s2 : deceleration au-dela de laquelle on compte un freinage
)

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("vola", Context.MODE_PRIVATE)

    var warningAccepted: Boolean
        get() = sp.getBoolean("warning", false)
        set(v) = sp.edit().putBoolean("warning", v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding", false)
        set(v) = sp.edit().putBoolean("onboarding", v).apply()

    /** Identifiant anonyme de l'athlete (genere une fois) ; le pseudo remplace le nom reel dans les echanges. */
    val athleteId: String
        get() {
            val existing = sp.getString("athleteId", null)
            if (existing != null) return existing
            val id = UUID.randomUUID().toString()
            sp.edit().putString("athleteId", id).apply()
            return id
        }

    var pseudo: String
        get() = sp.getString("pseudo", "Moi") ?: "Moi"
        set(v) = sp.edit().putString("pseudo", v.trim().ifEmpty { "Moi" }).apply()

    var athleteColor: Int
        get() = sp.getInt("athleteColor", 0xFF2E9AD8.toInt())
        set(v) = sp.edit().putInt("athleteColor", v).apply()

    var lastActivity: String
        get() = sp.getString("activity", Act.VTT) ?: Act.VTT
        set(v) = sp.edit().putString("activity", v).apply()

    /** Valeurs de depart par activite : HYPOTHESES a valider sur le terrain (TODO). */
    fun preset(act: String): Profile = when (act) {
        Act.SKI -> Profile(30.0, 15.0, 1.0, 25.0, 2.5)
        Act.OTHER -> Profile(20.0, 5.0, 1.0, 25.0, 3.0)
        else -> Profile(20.0, 8.0, 1.0, 25.0, 3.0)
    }

    fun profile(): Profile {
        val base = preset(lastActivity)
        // Repli sur les anciens reglages par activite (v0.3) tant que les nouveaux n'existent pas.
        val old = lastActivity
        return Profile(
            gateWidth = sp.getFloat("gate", sp.getFloat("${old}_gate", base.gateWidth.toFloat())).toDouble(),
            minSpeedKmh = sp.getFloat("minspeed", sp.getFloat("${old}_minspeed", base.minSpeedKmh.toFloat())).toDouble(),
            windowS = sp.getFloat("window", sp.getFloat("${old}_window", base.windowS.toFloat())).toDouble(),
            accThreshold = sp.getFloat("acc", sp.getFloat("${old}_acc", base.accThreshold.toFloat())).toDouble(),
            brakeThreshold = sp.getFloat("brake", base.brakeThreshold.toFloat()).toDouble(),
        )
    }

    fun saveProfile(p: Profile) {
        sp.edit()
            .putFloat("gate", p.gateWidth.toFloat())
            .putFloat("minspeed", p.minSpeedKmh.toFloat())
            .putFloat("window", p.windowS.toFloat())
            .putFloat("acc", p.accThreshold.toFloat())
            .putFloat("brake", p.brakeThreshold.toFloat())
            .apply()
    }
}
