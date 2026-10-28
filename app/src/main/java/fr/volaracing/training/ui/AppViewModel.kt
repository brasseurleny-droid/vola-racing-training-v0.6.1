package fr.volaracing.training.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.volaracing.training.core.Align
import fr.volaracing.training.core.Channels
import fr.volaracing.training.core.Filters
import fr.volaracing.training.core.GpsRow
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.RunStat
import fr.volaracing.training.core.RunStats
import fr.volaracing.training.core.SegParams
import fr.volaracing.training.core.SegType
import fr.volaracing.training.core.Segmenter
import fr.volaracing.training.core.Series
import fr.volaracing.training.core.Track
import fr.volaracing.training.core.TrackPt
import fr.volaracing.training.data.AppDb
import fr.volaracing.training.data.Course
import fr.volaracing.training.data.Csv
import fr.volaracing.training.data.Exchange
import fr.volaracing.training.data.Exporter
import fr.volaracing.training.data.GroupEntity
import fr.volaracing.training.data.ImuReader
import fr.volaracing.training.data.Passage
import fr.volaracing.training.data.Prefs
import fr.volaracing.training.data.Recovery
import fr.volaracing.training.data.RunBuilder
import fr.volaracing.training.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Un run charge dans le laboratoire : trace, voies, correspondance temps -> progression sur la reference. */
class LabRun(
    val passage: Passage,
    val label: String,
    val color: Color,
    val track: List<TrackPt>,
    val ch: Map<String, Series>,
    /** t (s depuis le debut du run) -> distance le long du trace de reference (m). */
    val sOfT: Series,
    val latS: Series,
    val lonS: Series,
    val stat: RunStat?,
    /** Resume du filtrage applique (affiche dans les statistiques). */
    val info: String,
) {
    /** Distance le long de la reference -> t. */
    val tOfS: Series = Series(sOfT.v, sOfT.t)
    val duration: Double get() = sOfT.t.last()
}

/** Etat d'affichage du laboratoire, conserve pendant la navigation et la rotation. */
class LabUi {
    var stacked by mutableStateOf(true)
    var xDist by mutableStateOf(true)
    var mapOpen by mutableStateOf(true)
    var sat by mutableStateOf(false)
    var split by mutableFloatStateOf(0.4f)
    val channels = mutableStateListOf("speed", "along", "alt")
    val axisOf = mutableStateMapOf<String, Int>()
    var colorChan by mutableStateOf("speed")
    var invert by mutableStateOf(false)
    var toolsOpen by mutableStateOf(true)
    var panelCollapsed by mutableStateOf(false)
    var fullChart by mutableStateOf(false)
    var fullMap by mutableStateOf(false)
    /** true = donnees nettoyees (points imprecis, sauts, pics), false = donnees brutes. */
    var filter by mutableStateOf(true)
}

/** Session complete proposee au decoupage : bornes (s depuis le premier point) et types de segments. */
class SplitData(
    val session: Session, val rows: List<GpsRow>,
    val t: DoubleArray, val alt: DoubleArray, val speed: DoubleArray,
    val bounds: List<Double>, val types: List<Int>,
)

private class Prepared(val track: List<TrackPt>, val ch: Map<String, Series>, val info: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val dao = AppDb.get(app).dao()
    val labUi = LabUi()

    init {
        // Sessions orphelines (service tue, plantage) : on recree les runs manquants.
        viewModelScope.launch(Dispatchers.IO) { try { Recovery.run(app, dao, prefs) } catch (_: Exception) { } }
    }

    var activity by mutableStateOf(prefs.lastActivity)
        private set

    val courses: StateFlow<List<Course>> =
        dao.courses().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions: StateFlow<List<Session>> =
        dao.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allPassages: StateFlow<List<Passage>> =
        dao.allPassages().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val groups: StateFlow<List<GroupEntity>> =
        dao.groups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectActivity(a: String) { activity = a; prefs.lastActivity = a }

    fun passagesOfCourse(id: Long): Flow<List<Passage>> = dao.passages(id)
    fun passagesOfSession(id: Long): Flow<List<Passage>> = dao.passagesOfSession(id)

    suspend fun course(id: Long): Course? = dao.course(id)
    suspend fun saveCourse(c: Course): Long {
        val c2 = if (c.uuid.isBlank()) c.copy(uuid = UUID.randomUUID().toString()) else c
        return if (c2.id == 0L) dao.insertCourse(c2) else { dao.updateCourse(c2); c2.id }
    }

    /** Supprime un parcours ; ses runs et sessions sont conserves (detaches). */
    suspend fun deleteCourse(id: Long) {
        dao.detachPassages(id); dao.detachSessions(id); dao.deleteCourse(id)
    }

    suspend fun session(id: Long): Session? = dao.session(id)
    suspend fun saveSession(s: Session) = dao.updateSession(s)

    /** Dossier des fichiers d'une session : enregistree ici (sessions/<id>) ou importee (imports/<uuid>). */
    private suspend fun dirFor(id: Long): File {
        val s = dao.session(id)
        val root = getApplication<Application>().getExternalFilesDir(null)
        return if (s != null && s.origin == 1) File(root, "imports/${s.uuid}") else File(root, "sessions/$id")
    }

    /** Suppression definitive d'une session (lignes en base et fichiers), apres confirmation de l'utilisateur. */
    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        val dir = dirFor(id)
        dao.deletePassagesOfSession(id)
        dao.deleteSession(id)
        dir.deleteRecursively()
        Unit
    }

    // ---- groupes et echanges entre amis ----
    suspend fun createGroup(name: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertGroup(GroupEntity(id, name.trim().ifEmpty { "Groupe" }, System.currentTimeMillis()))
        return id
    }

    suspend fun deleteGroup(id: String) {
        dao.detachGroupSessions(id); dao.detachGroupCourses(id); dao.deleteGroup(id)
    }

    suspend fun setCourseGroup(c: Course, gid: String) {
        val u = if (c.uuid.isBlank()) UUID.randomUUID().toString() else c.uuid
        dao.updateCourse(c.copy(groupId = gid, uuid = u))
    }

    suspend fun setSessionGroup(s: Session, gid: String) = dao.updateSession(s.copy(groupId = gid))

    suspend fun importPackage(ctx: Context, uri: Uri, groupId: String): String = Exchange.import(ctx, uri, dao, groupId)

    suspend fun courseShareIntent(ctx: Context, c: Course): Intent = withContext(Dispatchers.IO) {
        val c2 = if (c.uuid.isBlank()) c.copy(uuid = UUID.randomUUID().toString()).also { dao.updateCourse(it) } else c
        Exchange.courseShareIntent(ctx, c2)
    }

    suspend fun sessionShareIntent(ctx: Context, sessionId: Long): Intent? = withContext(Dispatchers.IO) {
        val s = dao.session(sessionId) ?: return@withContext null
        val runs = dao.passagesOfSessionNow(sessionId)
        val c = if (s.courseId >= 0) dao.course(s.courseId) else null
        Exchange.sessionShareIntent(ctx, s, runs, c, prefs)
    }

    // ---- lecture des traces (mises en cache) ----
    private val csvCache = ConcurrentHashMap<String, List<GpsRow>>()
    private val rowsCache = ConcurrentHashMap<Long, List<GpsRow>>()
    private val imuCache = ConcurrentHashMap<Long, Map<String, Series>>()
    private val thumbCache = ConcurrentHashMap<Long, List<TrackPt>>()

    private suspend fun rowsOf(p: Passage): List<GpsRow> {
        rowsCache[p.id]?.let { return it }
        val w = withContext(Dispatchers.IO) {
            val all = csvCache.getOrPut(p.path) { Csv.readGps(File(p.path, "gps.csv")) }
            Track.window(all, p.startNs, p.endNs)
        }
        rowsCache[p.id] = w
        return w
    }

    private suspend fun imuOf(p: Passage): Map<String, Series> {
        imuCache[p.id]?.let { return it }
        val m = withContext(Dispatchers.IO) { ImuReader.read(File(p.path, "imu.csv"), p.startNs, p.endNs) }
        imuCache[p.id] = m
        return m
    }

    /** Trace d'un run dans son propre repere (miniatures). */
    suspend fun thumb(p: Passage): List<TrackPt> {
        thumbCache[p.id]?.let { return it }
        val rows = rowsOf(p)
        val t = if (rows.size < 2) emptyList()
        else Filters.cleanTrack(Track.fromRows(rows, LocalFrame(rows.first().lat, rows.first().lon))).first
        thumbCache[p.id] = t
        return t
    }

    /** Trace + voies d'un run : nettoyage (optionnel), altitude barometrique, voies derivees, IMU. */
    private suspend fun prepare(p: Passage, rows: List<GpsRow>, frame: LocalFrame, windowS: Double, filter: Boolean): Prepared {
        var trk = Track.fromRows(rows, frame)
        var info = "brut"
        if (filter) {
            val r = Filters.cleanTrack(trk)
            trk = r.first
            info = r.second.text()
        }
        val imu = imuOf(p)
        val gpsT = DoubleArray(trk.size) { (trk[it].tNs - trk.first().tNs) / 1e9 }
        val baro = imu["baro"]
        val altOv = if (filter && baro != null) {
            Filters.baroAltitude(baro.t, baro.v, gpsT, DoubleArray(trk.size) { trk[it].alt })
        } else null
        val ch = HashMap<String, Series>(Channels.gps(trk, windowS, altOv))
        ch.putAll(imu)
        if (altOv != null) info += ", altitude baromètre"
        return Prepared(trk, ch, info)
    }

    /**
     * Charge les runs du laboratoire. Tous les tracés sont projetes dans le repere du run de reference,
     * puis alignes sur son trace (par position). Les voies derivees et l'IMU sont ajoutees.
     */
    suspend fun loadLab(ids: List<Long>, refId: Long, filter: Boolean): List<LabRun> = withContext(Dispatchers.Default) {
        val prof = prefs.profile()
        val ps = ids.mapNotNull { dao.passage(it) }
        if (ps.isEmpty()) return@withContext emptyList<LabRun>()
        val ref = ps.firstOrNull { it.id == refId } ?: ps.first()
        val refRows = rowsOf(ref)
        if (refRows.size < 2) return@withContext emptyList<LabRun>()
        val frame = LocalFrame(refRows.first().lat, refRows.first().lon)
        val refPrep = prepare(ref, refRows, frame, prof.windowS, filter)
        val refTrack = refPrep.track
        val refSpeed = refPrep.ch["speed"] ?: return@withContext emptyList<LabRun>()

        val out = ArrayList<LabRun>()
        for ((i, p) in ps.withIndex()) {
            val isRef = p.id == ref.id
            val rows = if (isRef) refRows else rowsOf(p)
            if (rows.size < 2) continue
            val pr = if (isRef) refPrep else prepare(p, rows, frame, prof.windowS, filter)
            val trk = pr.track
            val speed = pr.ch["speed"] ?: continue
            val prog = if (isRef) null else Align.progress(refTrack, trk)
            val ch = HashMap<String, Series>(pr.ch)
            ch.putAll(Channels.vsReference(refTrack, refSpeed, trk, speed, prog))
            val t = speed.t
            val s = DoubleArray(trk.size) { k -> prog?.get(k)?.s ?: trk[k].dist }
            val n = dao.passagesOfSessionNow(p.sessionId).indexOfFirst { it.id == p.id } + 1
            val sess = dao.session(p.sessionId)
            val label = if (sess != null && sess.origin == 1) "${sess.ownerName} Run $n" else "S${p.sessionId} Run $n"
            out.add(
                LabRun(
                    passage = p, label = label, color = TracePalette[i % TracePalette.size],
                    track = trk, ch = ch, sOfT = Series(t, s),
                    latS = Series(t, DoubleArray(trk.size) { trk[it].lat }),
                    lonS = Series(t, DoubleArray(trk.size) { trk[it].lon }),
                    stat = RunStats.compute(ch, 0.0, t.last(), prof.brakeThreshold),
                    info = pr.info,
                )
            )
        }
        out
    }

    // ---- decoupage d'une session en descentes ----
    suspend fun loadSplit(sessionId: Long): SplitData? = withContext(Dispatchers.Default) {
        val s = dao.session(sessionId) ?: return@withContext null
        val dir = dirFor(sessionId)
        val rows = withContext(Dispatchers.IO) { Csv.readGps(File(dir, "gps.csv")) }
        if (rows.size < 3) return@withContext null
        val prof = prefs.profile()
        val track = Filters.cleanTrack(Track.fromRows(rows, LocalFrame(rows.first().lat, rows.first().lon))).first
        val baro = withContext(Dispatchers.IO) { ImuReader.read(File(dir, "imu.csv"), rows.first().tNs, rows.last().tNs)["baro"] }
        val gpsT = DoubleArray(track.size) { (track[it].tNs - track.first().tNs) / 1e9 }
        val altOv = baro?.let { b -> Filters.baroAltitude(b.t, b.v, gpsT, DoubleArray(track.size) { track[it].alt }) }
        val ch = Channels.gps(track, prof.windowS, altOv)
        val sp = ch["speed"] ?: return@withContext null
        val al = ch["alt"] ?: return@withContext null
        val di = ch["dist"] ?: return@withContext null
        val segs = Segmenter.segment(sp.t, sp.v, al.v, di.v, SegParams.preset(s.activity))
        val bounds = ArrayList<Double>()
        val types = ArrayList<Int>()
        if (segs.isEmpty()) {
            bounds.add(sp.t.first()); bounds.add(sp.t.last()); types.add(SegType.LINK.ordinal)
        } else {
            bounds.add(sp.t.first())
            segs.forEachIndexed { i, g -> types.add(g.type.ordinal); if (i > 0) bounds.add(g.t0) }
            bounds.add(sp.t.last())
        }
        SplitData(s, rows, sp.t, al.v, sp.v, bounds, types)
    }

    /** Enregistre un run (kind 2) par intervalle ; `replace` supprime d'abord les runs libres/auto de la session. */
    suspend fun saveSplitRuns(d: SplitData, intervals: List<Pair<Double, Double>>, replace: Boolean): Int =
        withContext(Dispatchers.IO) {
            val prof = prefs.profile()
            val t0 = d.rows.first().tNs
            val dir = dirFor(d.session.id)
            if (replace) dao.deleteFreeRuns(d.session.id)
            var n = 0
            for ((a, b) in intervals) {
                val sNs = t0 + (a * 1e9).toLong()
                val eNs = t0 + (b * 1e9).toLong()
                if (eNs - sNs < 3_000_000_000L) continue
                dao.insertPassage(RunBuilder.passage(d.session.id, d.session.courseId, dir, d.rows, sNs, eNs, 2, prof.windowS))
                n++
            }
            rowsCache.clear(); thumbCache.clear(); imuCache.clear()
            n
        }

    suspend fun exportSession(ctx: Context, sessionId: Long): Int = withContext(Dispatchers.IO) {
        Exporter.exportToDownloads(ctx, sessionId, dirFor(sessionId))
    }

    suspend fun shareIntent(ctx: Context, sessionId: Long) = withContext(Dispatchers.IO) {
        Exporter.shareIntent(ctx, sessionId, dirFor(sessionId))
    }
}
