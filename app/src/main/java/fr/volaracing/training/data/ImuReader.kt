package fr.volaracing.training.data

import fr.volaracing.training.core.Imu
import fr.volaracing.training.core.Series
import java.io.File

object ImuReader {
    /** Voies IMU (g, gyro, baro) d'un run, lues en flux et decimees (pas de chargement complet en memoire). */
    fun read(file: File, startNs: Long, endNs: Long): Map<String, Series> =
        if (!file.exists()) emptyMap() else file.useLines { Imu.aggregate(it, startNs, endNs) }
}
