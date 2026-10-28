package fr.volaracing.training.data

import android.content.Context
import fr.volaracing.training.core.RunPlanner
import fr.volaracing.training.core.SegParams
import fr.volaracing.training.recording.RecordingService
import java.io.File

object Recovery {
    /**
     * Sessions orphelines (service tue, plantage) : un dossier avec gps.csv mais aucun run en base.
     * Les runs manquants sont recrees avec la meme logique qu'a l'arret normal.
     */
    suspend fun run(ctx: Context, dao: AppDao, prefs: Prefs) {
        val root = File(ctx.getExternalFilesDir(null), "sessions")
        val dirs = root.listFiles() ?: return
        val prof = prefs.profile()
        val recording = RecordingService.state.value.active
        val currentId = RecordingService.lastSession.value
        for (d in dirs) {
            val id = d.name.toLongOrNull() ?: continue
            if (recording && id == currentId) continue
            val gps = File(d, "gps.csv")
            if (!gps.exists()) continue
            // Evite de croiser un arret en cours de finalisation.
            if (System.currentTimeMillis() - gps.lastModified() < 60_000L) continue
            val session = dao.session(id) ?: continue
            if (dao.passagesOfSessionNow(id).isNotEmpty()) continue
            val rows = Csv.readGps(gps)
            val baro = if (rows.size >= 2) ImuReader.read(File(d, "imu.csv"), rows.first().tNs, rows.last().tNs)["baro"] else null
            val plans = RunPlanner.plan(rows, prof.windowS, prof.minSpeedKmh, SegParams.preset(session.activity), baro)
            for (p in plans) {
                dao.insertPassage(
                    RunBuilder.passage(id, session.courseId, d, rows, p.startNs, p.endNs, p.kind, prof.windowS)
                )
            }
        }
    }
}
