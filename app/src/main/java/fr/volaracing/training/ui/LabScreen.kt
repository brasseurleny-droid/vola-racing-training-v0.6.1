package fr.volaracing.training.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.volaracing.training.core.AiPrompt
import fr.volaracing.training.core.Channels
import fr.volaracing.training.core.Compare
import fr.volaracing.training.core.Heat
import fr.volaracing.training.core.InsightRun
import fr.volaracing.training.core.Insights
import fr.volaracing.training.core.Report
import fr.volaracing.training.core.RunStat
import fr.volaracing.training.core.RunStats
import fr.volaracing.training.core.Ticks
import fr.volaracing.training.data.Act
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

private const val HEAT_N = 10
private val HEAT_CHANS = listOf("speed", "along", "alat", "slope", "g", "alt", "delta", "dspeed")

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

private fun shortName(id: String) = when (id) {
    "speed" -> "Vit."; "alt" -> "Alt."; "slope" -> "Pente"; "dist" -> "Dist."
    "along" -> "Acc. L"; "alat" -> "Acc. T"; "g" -> "G"; "gyro" -> "Gyro"; "baro" -> "Pres."
    "hacc" -> "Préc."; "sats" -> "Sat."; "delta" -> "Δt"; "dspeed" -> "Δv"
    else -> id
}

private fun heatColor(k: Int): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(120f * k / (HEAT_N - 1), 0.85f, 0.85f))

/** Classes de couleur (0 rouge ... 9 vert) de chaque segment du tracé de reference. */
private class HeatData(val cls: IntArray, val lo: Double, val hi: Double, val inv: Boolean)

private fun computeHeat(run: LabRun?, chan: String, invert: Boolean): HeatData? {
    val r = run ?: return null
    val s = r.ch[chan] ?: return null
    if (r.track.size < 2) return null
    val t0 = r.track.first().tNs
    val t = DoubleArray(r.track.size) { (r.track[it].tNs - t0) / 1e9 }
    val vals = Heat.segmentValues(s, t)
    val lo = Heat.percentile(vals, 5.0)
    val hi = Heat.percentile(vals, 95.0)
    val inv = (chan == "delta") != invert // ecart de temps : bas = bon
    return HeatData(IntArray(vals.size) { Heat.classOf(vals[it], lo, hi, HEAT_N, inv) }, lo, hi, inv)
}

@Composable
fun LabScreen(vm: AppViewModel, nav: NavController, initial: List<Long>) {
    val ui = vm.labUi
    val density = LocalDensity.current
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val ids = remember { mutableStateListOf<Long>().also { it.addAll(initial.take(6)) } }
    var refId by remember { mutableStateOf(initial.firstOrNull() ?: -1L) }
    var runs by remember { mutableStateOf<List<LabRun>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var view by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var cursor by remember { mutableStateOf<Double?>(null) }
    var anchor by remember { mutableStateOf<Double?>(null) }
    var showChannels by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var addFilter by remember { mutableStateOf(0) } // 0 tous, 1 mes runs, 2 runs d'amis
    var hint by remember { mutableStateOf<String?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val allP by vm.allPassages.collectAsState()
    val allS by vm.sessions.collectAsState()
    val brake = vm.prefs.profile().brakeThreshold
    val setHint: (String) -> Unit = { hint = it }

    val idKey = ids.toList()
    LaunchedEffect(idKey, refId, ui.filter) { loading = true; runs = vm.loadLab(idKey, refId, ui.filter); loading = false }
    LaunchedEffect(hint) { if (hint != null) { delay(1600); hint = null } }

    val full: Pair<Double, Double> = remember(runs, ui.xDist) {
        if (runs.isEmpty()) Pair(0.0, 1.0)
        else if (ui.xDist) Pair(runs.minOf { it.sOfT.v.first() }, runs.maxOf { it.sOfT.v.last() })
        else Pair(0.0, runs.maxOf { it.duration })
    }
    LaunchedEffect(full) { view = null; anchor = null; cursor = (full.first + full.second) / 2.0 }
    val v = view ?: full
    val v0 = v.first
    val v1 = v.second
    val effRef = runs.firstOrNull { it.passage.id == refId }?.passage?.id ?: runs.firstOrNull()?.passage?.id ?: -1L
    val refRun = runs.firstOrNull { it.passage.id == effRef }
    val heat = remember(refRun, ui.colorChan, ui.invert) { computeHeat(refRun, ui.colorChan, ui.invert) }

    fun tOf(r: LabRun, x: Double): Double? = if (ui.xDist) r.tOfS.at(x) else x
    fun valueAt(r: LabRun, c: String, x: Double): Double? {
        val t = tOf(r, x) ?: return null
        return r.ch[c]?.at(t)
    }
    fun applyTransform(zoom: Float, cFrac: Float, panFrac: Float) {
        val span = v1 - v0
        val fullSpan = (full.second - full.first).coerceAtLeast(1e-6)
        val newSpan = (span / zoom.toDouble()).coerceIn(fullSpan * 0.01, fullSpan)
        val cx = v0 + cFrac * span
        val na = (cx - cFrac * newSpan - panFrac * newSpan).coerceIn(full.first, maxOf(full.first, full.second - newSpan))
        view = Pair(na, na + newSpan)
    }
    fun setChan(id: String, on: Boolean) {
        if (on) {
            if (id !in ui.channels) ui.channels.add(id)
        } else {
            ui.channels.remove(id)
        }
    }
    fun fitCamera() {
        val pts = runs.flatMap { r -> r.track.map { LatLng(it.lat, it.lon) } }
        if (pts.size >= 2) mapRef?.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(), 60))
    }
    fun cycleHeat() {
        val avail = HEAT_CHANS.filter { refRun?.ch?.containsKey(it) == true }
        if (avail.isEmpty()) return
        val i = avail.indexOf(ui.colorChan)
        ui.colorChan = avail[(i + 1) % avail.size]
        ui.invert = false
    }

    // ---- carte : traces, trace coloree, surbrillance du zoom, marqueurs de curseur
    LaunchedEffect(style, runs, ui.mapOpen, heat, effRef) {
        val s = style ?: return@LaunchedEffect
        if (!ui.mapOpen || runs.isEmpty()) return@LaunchedEffect
        rebuildLabMap(s, runs, effRef, heat)
    }
    LaunchedEffect(style, runs, ui.mapOpen) {
        if (style == null || !ui.mapOpen || runs.isEmpty()) return@LaunchedEffect
        fitCamera()
    }
    LaunchedEffect(style, view, runs, effRef, ui.xDist, ui.mapOpen) {
        val s = style ?: return@LaunchedEffect
        if (!ui.mapOpen) return@LaunchedEffect
        val vw = view
        val rr = runs.firstOrNull { it.passage.id == effRef }
        val feats = ArrayList<Feature>()
        if (vw != null && rr != null) {
            val pts = ArrayList<Pair<Double, Double>>()
            for (i in rr.track.indices) {
                val x = if (ui.xDist) rr.sOfT.v[i] else rr.sOfT.t[i]
                if (x >= vw.first && x <= vw.second) pts.add(Pair(rr.track[i].lat, rr.track[i].lon))
            }
            if (pts.size >= 2) feats.add(lineFeature(pts))
        }
        s.getSourceAs<GeoJsonSource>("lab-zoom")?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }
    LaunchedEffect(style, cursor, runs, ui.xDist, ui.mapOpen) {
        val s = style ?: return@LaunchedEffect
        val cu = cursor ?: return@LaunchedEffect
        if (!ui.mapOpen) return@LaunchedEffect
        val feats = runs.mapNotNull { r ->
            val t = tOf(r, cu) ?: return@mapNotNull null
            val la = r.latS.at(t)
            val lo = r.lonS.at(t)
            if (la != null && lo != null) Feature.fromGeometry(Point.fromLngLat(lo, la)) else null
        }
        s.getSourceAs<GeoJsonSource>("lab-cursor")?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    // ------------------------------------------------------------ panneau carte
    val mapPanel: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            MapPane(
                Modifier.fillMaxSize(), satellite = ui.sat,
                onClick = { lat, lon ->
                    val ref = runs.firstOrNull { it.passage.id == effRef }
                    if (ref != null) {
                        val cosL = cos(Math.toRadians(lat))
                        var best = -1
                        var bd = Double.MAX_VALUE
                        for (i in ref.track.indices) {
                            val dx = (ref.track[i].lon - lon) * cosL
                            val dy = ref.track[i].lat - lat
                            val d = dx * dx + dy * dy
                            if (d < bd) { bd = d; best = i }
                        }
                        if (best >= 0) cursor = if (ui.xDist) ref.sOfT.v[best] else ref.sOfT.t[best]
                    }
                },
            ) { mp, st -> mapRef = mp; style = st }
            Column(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                ToolButton("sat", if (ui.sat) "Fond satellite (toucher : plan)" else "Fond plan (toucher : satellite)", active = ui.sat, onHint = setHint) { ui.sat = !ui.sat }
                ToolButton("center", "Recentrer sur les tracés", onHint = setHint) { fitCamera() }
                ToolButton("palette", "Couleur de la trace : ${shortName(ui.colorChan)} (toucher : changer)", onHint = setHint) { cycleHeat() }
                ToolButton("fullscreen", "Carte plein écran", active = ui.fullMap, onHint = setHint) { ui.fullMap = !ui.fullMap }
            }
            val hd = heat
            if (hd != null) {
                HeatLegend(ui.colorChan, hd, Modifier.align(Alignment.BottomStart).padding(6.dp)) { ui.invert = !ui.invert }
            }
            hint?.let {
                Text(
                    it, Modifier.align(Alignment.TopStart).padding(6.dp).background(VDark.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(6.dp),
                    color = Color.White, fontSize = 12.sp,
                )
            }
        }
    }

    // ------------------------------------------------------------ panneau graphique
    val chartPanel: @Composable (Modifier) -> Unit = { mod ->
        Column(mod.verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                loading && runs.isEmpty() -> Text("Chargement des courbes...", color = VGray)
                runs.isEmpty() -> Text("Données insuffisantes pour analyser ce run.", color = VGray)
                ui.channels.isEmpty() -> Text("Choisissez au moins une voie (icône en haut à droite).", color = VGray)
                else -> {
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) {
                            LabChart(
                                runs = runs, channels = ui.channels.toList(), axisOf = ui.axisOf.toMap(),
                                stacked = ui.stacked, xDist = ui.xDist, v0 = v0, v1 = v1, cursor = cursor, anchor = anchor,
                                onCursorFrac = { f -> cursor = v0 + f * (v1 - v0) },
                                onTransform = { z, c, p -> applyTransform(z, c, p) },
                            )
                            val cu = cursor
                            if (cu != null) {
                                val cf = ((cu - v0) / (v1 - v0).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
                                CursorPanel(
                                    runs, ui.channels.toList(), cu, ui.xDist, { r, c -> valueAt(r, c, cu) },
                                    ui.panelCollapsed, { ui.panelCollapsed = !ui.panelCollapsed },
                                    Modifier.align(if (cf < 0.5) Alignment.TopEnd else Alignment.TopStart).padding(start = 50.dp, top = 4.dp, end = 4.dp),
                                )
                            }
                        }
                        // barre d'outils miniature sur le cote du graphique
                        Column(Modifier.width(44.dp)) {
                            ToolButton("menu", "Outils", active = ui.toolsOpen, onHint = setHint) { ui.toolsOpen = !ui.toolsOpen }
                            if (ui.toolsOpen) {
                                ToolButton("channels", "Voies à afficher", onHint = setHint) { showChannels = true }
                                ToolButton(
                                    if (ui.stacked) "stack" else "overlay",
                                    if (ui.stacked) "Voies empilées (toucher : superposer)" else "Voies superposées (toucher : empiler)",
                                    onHint = setHint,
                                ) { ui.stacked = !ui.stacked }
                                ToolButton(
                                    "xaxis", if (ui.xDist) "Axe X : distance (toucher : temps)" else "Axe X : temps (toucher : distance)",
                                    onHint = setHint,
                                ) { ui.xDist = !ui.xDist }
                                ToolButton(
                                    "filter", if (ui.filter) "Données filtrées (toucher : brutes)" else "Données brutes (toucher : filtrées)",
                                    active = ui.filter, onHint = setHint,
                                ) { ui.filter = !ui.filter }
                                ToolButton("zplus", "Zoom +", onHint = setHint) { applyTransform(1.6f, 0.5f, 0f) }
                                ToolButton("zminus", "Zoom -", onHint = setHint) { applyTransform(1f / 1.6f, 0.5f, 0f) }
                                ToolButton("fit", "Vue complète", onHint = setHint) { view = null }
                                ToolButton("anchor", if (anchor == null) "Fixer le repère (mesure d'intervalle)" else "Effacer le repère", active = anchor != null, onHint = setHint) {
                                    anchor = if (anchor == null) cursor else null
                                }
                                ToolButton("map", if (ui.mapOpen) "Masquer la carte" else "Afficher la carte", active = ui.mapOpen, onHint = setHint) { ui.mapOpen = !ui.mapOpen }
                                ToolButton("fullscreen", "Graphique plein écran", active = ui.fullChart, onHint = setHint) { ui.fullChart = !ui.fullChart }
                            }
                        }
                    }
                    hint?.let {
                        Text(
                            it, Modifier.background(VDark.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(6.dp),
                            color = Color.White, fontSize = 12.sp,
                        )
                    }
                }
            }

            val cx = cursor
            val an = anchor

            // ---- mesure d'intervalle
            if (an != null && cx != null && runs.isNotEmpty()) {
                val lo = minOf(an, cx)
                val hi = maxOf(an, cx)
                SectionTitle("INTERVALLE : ${if (ui.xDist) f1(hi - lo) + " m" else String.format(Locale.US, "%.2f", hi - lo) + " s"}")
                Text("Δ = valeur au curseur moins valeur au repère.", color = VGray, fontSize = 12.sp)
                runs.forEach { r ->
                    val ta = tOf(r, lo)
                    val tb = tOf(r, hi)
                    if (ta != null && tb != null) {
                        Text(r.label, color = r.color, fontWeight = FontWeight.Bold)
                        ui.channels.forEach { c ->
                            val s = r.ch[c]
                            val idx: List<Int> = if (s == null) emptyList() else s.t.indices.filter { s.t[it] >= ta && s.t[it] <= tb }
                            val va = tOf(r, an)?.let { s?.at(it) }
                            val vc = tOf(r, cx)?.let { s?.at(it) }
                            if (s != null && idx.isNotEmpty() && va != null && vc != null) {
                                val vals = idx.map { s.v[it] }
                                Text(
                                    "${Channels.def(c).label} : Δ ${Channels.fmt(c, vc - va)}  moy ${Channels.fmt(c, vals.average())}  " +
                                        "min ${Channels.fmt(c, vals.min())}  max ${Channels.fmt(c, vals.max())} ${Channels.def(c).unit}",
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ---- statistiques (run entier, ou intervalle si un repere est fixe)
            if (runs.isNotEmpty()) {
                SectionTitle(if (an != null && cx != null) "STATISTIQUES SUR L'INTERVALLE" else "STATISTIQUES DU RUN")
                val stats: List<RunStat?> = runs.map { r ->
                    if (an != null && cx != null) {
                        val ta = tOf(r, minOf(an, cx))
                        val tb = tOf(r, maxOf(an, cx))
                        if (ta != null && tb != null) RunStats.compute(r.ch, ta, tb, brake) else null
                    } else r.stat
                }
                StatsTable(runs, stats)
                runs.forEach { Text("${it.label} - filtrage : ${it.info}", fontSize = 11.sp, color = VGray) }
            }

            // ---- rapport automatique
            val report = remember(runs, effRef) {
                val ref = runs.firstOrNull { it.passage.id == effRef }
                if (ref == null) emptyList()
                else runs.filter { it !== ref }.flatMap { r ->
                    val al = Compare.deltaAligned(ref.track, r.track)
                    val lines = Report.lines(r.label, ref.stat, r.stat, Compare.sections(al.curve))
                    if (al.overlap < 0.6) {
                        listOf("${r.label} : seulement ${(al.overlap * 100).toInt()} % du tracé suit la référence, comparaison peu fiable.") + lines
                    } else lines
                }
            }
            if (report.isNotEmpty()) {
                SectionTitle("RAPPORT AUTOMATIQUE")
                report.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }

            // ---- analyse intelligente locale (regles et statistiques, aucune donnee envoyee)
            val analysis = remember(runs, effRef, brake) {
                val iruns = runs.map { r -> InsightRun(r.label, r.ch, r.sOfT, r.passage.timeNs / 1e9, r.stat) }
                val ri = runs.indexOfFirst { it.passage.id == effRef }.coerceAtLeast(0)
                val secs = HashMap<Int, List<Triple<Double, Double, Double>>>()
                if (runs.isNotEmpty()) {
                    runs.forEachIndexed { i, r ->
                        if (i != ri) secs[i] = Compare.sections(Compare.deltaAligned(runs[ri].track, r.track).curve)
                    }
                }
                Triple(iruns, ri, Insights.analyze(iruns, ri, brake, secs))
            }
            if (analysis.third.isNotEmpty()) {
                SectionTitle("ANALYSE INTELLIGENTE")
                Text(
                    "Calculée sur votre téléphone à partir de vos mesures (règles et statistiques). Rien n'est envoyé sur Internet.",
                    color = VGray, fontSize = 12.sp,
                )
                analysis.third.take(8).forEach { Text("- ${it.text}", fontSize = 14.sp) }
                BigButton("Copier le résumé pour une IA", Modifier.fillMaxWidth(), filled = false, height = 48.dp) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = AiPrompt.build("${Act.label(vm.activity)}, ${runs.size} run(s)", analysis.first, analysis.second, analysis.third)
                    cm.setPrimaryClip(ClipData.newPlainText("Vola Racing", text))
                    Toast.makeText(ctx, "Résumé copié : collez-le dans votre assistant IA.", Toast.LENGTH_LONG).show()
                }
            }

            // ---- detail des valeurs au curseur (replie par defaut)
            BigButton(if (showDetail) "Masquer le détail des valeurs" else "Détail des valeurs au curseur", Modifier.fillMaxWidth(), filled = false, height = 48.dp) {
                showDetail = !showDetail
            }
            if (showDetail && cx != null && runs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1.5f))
                    runs.forEach { r -> Text(r.label, Modifier.weight(1f), color = r.color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                ui.channels.forEach { c ->
                    val d = Channels.def(c)
                    Row(Modifier.fillMaxWidth()) {
                        Text("${d.label} (${d.unit})", Modifier.weight(1.5f), color = VGray, fontSize = 12.sp)
                        runs.forEach { r -> Text(valueAt(r, c, cx)?.let { Channels.fmt(c, it) } ?: "-", Modifier.weight(1f), fontSize = 14.sp) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ mise en page
    Column(Modifier.fillMaxSize()) {
        val chrome = !(ui.fullChart || ui.fullMap)
        if (chrome) ScreenHeader("Laboratoire d'analyse", onBack = { nav.popBackStack() })

        // tracés superposés (puces)
        if (chrome) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            runs.forEach { r ->
                val isRef = r.passage.id == effRef
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier.background(r.color.copy(alpha = 0.15f), shape)
                        .then(if (isRef) Modifier.border(2.dp, r.color, shape) else Modifier)
                        .clickable { refId = r.passage.id }.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(r.color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${r.label} - ${fmtT(r.passage.timeNs)}" + if (isRef) " (réf.)" else "",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
                    )
                    if (runs.size > 1) {
                        Box(
                            Modifier.size(36.dp).clickable {
                                ids.remove(r.passage.id)
                                if (refId == r.passage.id) refId = ids.firstOrNull() ?: -1L
                            },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", fontSize = 22.sp) }
                    } else Spacer(Modifier.width(10.dp))
                }
            }
            BigButton("+ Tracé", height = 36.dp) { showAdd = true }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.fullChart -> chartPanel(Modifier.fillMaxSize())
                ui.fullMap && ui.mapOpen -> mapPanel(Modifier.fillMaxSize())
                landscape -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val layoutMaxWidth = maxWidth
                    val wPx = with(density) { layoutMaxWidth.toPx() }
                    Row(Modifier.fillMaxSize()) {
                        if (ui.mapOpen) {
                            mapPanel(Modifier.weight(ui.split).fillMaxHeight())
                            Box(
                                Modifier.width(16.dp).fillMaxHeight().background(VArgent)
                                    .pointerInput(wPx) { detectDragGestures { change, drag -> change.consume(); ui.split = (ui.split + drag.x / wPx).coerceIn(0.2f, 0.7f) } },
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.width(4.dp).height(40.dp).background(VGray, RoundedCornerShape(2.dp))) }
                        }
                        chartPanel(Modifier.weight(if (ui.mapOpen) 1f - ui.split else 1f).fillMaxHeight())
                    }
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val layoutMaxHeight = maxHeight // capture : maxHeight n'est pas accessible depuis la lambda de Column
                    val hPx = with(density) { layoutMaxHeight.toPx() }
                    Column(Modifier.fillMaxSize()) {
                        if (ui.mapOpen) {
                            Box(Modifier.fillMaxWidth().height(layoutMaxHeight * ui.split)) { mapPanel(Modifier.fillMaxSize()) }
                            Box(
                                Modifier.fillMaxWidth().height(18.dp).background(VArgent)
                                    .pointerInput(hPx) { detectDragGestures { change, drag -> change.consume(); ui.split = (ui.split + drag.y / hPx).coerceIn(0.15f, 0.7f) } },
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.width(40.dp).height(4.dp).background(VGray, RoundedCornerShape(2.dp))) }
                        }
                        chartPanel(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }

    // ---- choix des voies
    if (showChannels) {
        AlertDialog(
            onDismissRequest = { showChannels = false },
            title = { Text("Voies à afficher") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(Channels.ALL, key = { it.id }) { d ->
                        val on = d.id in ui.channels
                        Row(
                            Modifier.fillMaxWidth().clickable { setChan(d.id, !on) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { checked -> setChan(d.id, checked) })
                            Column(Modifier.weight(1f)) {
                                Text(d.label, fontSize = 15.sp)
                                Text(d.unit, color = VGray, fontSize = 12.sp)
                            }
                            if (on && !ui.stacked) {
                                val cur = ui.axisOf[d.id] ?: (ui.channels.indexOf(d.id) % 3)
                                BigButton("Axe ${cur + 1}", Modifier.width(96.dp), filled = false, height = 44.dp) { ui.axisOf[d.id] = (cur + 1) % 3 }
                            }
                        }
                    }
                }
            },
            confirmButton = { BigButton("OK") { showChannels = false } },
        )
    }

    // ---- ajouter un tracé
    if (showAdd) {
        val refP = runs.firstOrNull { it.passage.id == effRef }?.passage
        val cands = allP.filter { it.id !in ids }
            .filter { p ->
                val origin = allS.firstOrNull { it.id == p.sessionId }?.origin ?: 0
                addFilter == 0 || (addFilter == 1 && origin == 0) || (addFilter == 2 && origin == 1)
            }
            .sortedWith(
                compareByDescending<fr.volaracing.training.data.Passage> { it.sessionId == refP?.sessionId }
                    .thenByDescending { refP != null && it.courseId >= 0 && it.courseId == refP.courseId }
                    .thenByDescending { it.id }
            )
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (ids.size >= 6) "6 tracés maximum" else "Ajouter un tracé") },
            text = {
              Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton("Tous", Modifier.weight(1f), filled = addFilter == 0, height = 40.dp) { addFilter = 0 }
                    BigButton("Moi", Modifier.weight(1f), filled = addFilter == 1, height = 40.dp) { addFilter = 1 }
                    BigButton("Amis", Modifier.weight(1f), filled = addFilter == 2, height = 40.dp) { addFilter = 2 }
                }
                if (ids.size >= 6) Text("Retirez un tracé pour en ajouter un autre.")
                else LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cands.isEmpty()) item { Text("Aucun autre run enregistré.", color = VGray) }
                    items(cands, key = { it.id }) { p ->
                        val s = allS.firstOrNull { it.id == p.sessionId }
                        Row(
                            Modifier.fillMaxWidth().clickable { ids.add(p.id); showAdd = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RunThumb(vm, p, 48)
                            Column(Modifier.weight(1f)) {
                                Text(if (s != null) sessionTitle(s) else "Session ${p.sessionId}", fontSize = 14.sp, maxLines = 1)
                                Text(runTag(p) ?: "Run chronométré", color = VGray, fontSize = 12.sp, maxLines = 1)
                            }
                            TimeChip(fmtT(p.timeNs))
                        }
                    }
                }
              }
            },
            confirmButton = { BigButton("Fermer", filled = false) { showAdd = false } },
        )
    }
}

// ------------------------------------------------------------------ carte

private fun rebuildLabMap(style: Style, runs: List<LabRun>, effRef: Long, heat: HeatData?) {
    style.removeLayer("lab-cursor-c") // avant sa source
    for (i in 0 until 8) {
        style.removeLayer("lab-line-$i-layer"); style.removeSource("lab-line-$i")
    }
    for (k in 0 until HEAT_N) {
        style.removeLayer("lab-heat-$k-l"); style.removeSource("lab-heat-$k")
    }
    for (id in listOf("lab-casing", "lab-zoom", "lab-cursor")) {
        style.removeLayer("$id-layer"); style.removeSource(id)
    }

    val ref = runs.firstOrNull { it.passage.id == effRef } ?: runs.first()

    // surbrillance de la portion zoomee (alimentee par un effet dedie)
    style.addSource(GeoJsonSource("lab-zoom"))
    style.addLayer(LineLayer("lab-zoom-layer", "lab-zoom").withProperties(
        PropertyFactory.lineColor(VAccent.toArgb()), PropertyFactory.lineWidth(13f), PropertyFactory.lineOpacity(0.35f)))

    // liseré clair sous la trace de reference (lisible sur plan comme sur satellite)
    addLine(style, "lab-casing", ref.track, android.graphics.Color.WHITE, 9f)

    runs.forEachIndexed { i, r ->
        if (r.passage.id != ref.passage.id) addLine(style, "lab-line-$i", r.track, r.color.toArgb(), 3f)
    }

    if (heat == null) {
        val i = runs.indexOf(ref)
        addLine(style, "lab-line-$i", ref.track, ref.color.toArgb(), 5f)
    } else {
        val buckets = Array(HEAT_N) { ArrayList<Feature>() }
        for (i in 0 until ref.track.size - 1) {
            val c = heat.cls[i]
            if (c < 0) continue
            buckets[c].add(lineFeature(listOf(
                Pair(ref.track[i].lat, ref.track[i].lon), Pair(ref.track[i + 1].lat, ref.track[i + 1].lon),
            )))
        }
        for (k in 0 until HEAT_N) {
            if (buckets[k].isEmpty()) continue
            style.addSource(GeoJsonSource("lab-heat-$k", FeatureCollection.fromFeatures(buckets[k])))
            style.addLayer(LineLayer("lab-heat-$k-l", "lab-heat-$k").withProperties(
                PropertyFactory.lineColor(heatColor(k)), PropertyFactory.lineWidth(5f)))
        }
    }

    style.addSource(GeoJsonSource("lab-cursor"))
    style.addLayer(
        CircleLayer("lab-cursor-c", "lab-cursor").withProperties(
            PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(VDark.toArgb()),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(2f),
        )
    )
}

@Composable
private fun HeatLegend(chan: String, heat: HeatData, modifier: Modifier, onInvert: () -> Unit) {
    val d = Channels.def(chan)
    val base = (0 until 5).map { Color(heatColor(it * (HEAT_N - 1) / 4)) }
    val colors = if (heat.inv) base.reversed() else base
    Column(modifier.background(VSurface.copy(alpha = 0.88f), RoundedCornerShape(6.dp)).clickable { onInvert() }.padding(6.dp)) {
        Text("${d.label} (${d.unit})", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Box(Modifier.width(120.dp).height(8.dp).background(Brush.horizontalGradient(colors), RoundedCornerShape(4.dp)))
        Row(Modifier.width(120.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Channels.fmt(chan, heat.lo), fontSize = 10.sp, color = VGray)
            Text(Channels.fmt(chan, heat.hi), fontSize = 10.sp, color = VGray)
        }
    }
}

// ------------------------------------------------------------------ valeurs au curseur (miniature)

@Composable
private fun CursorPanel(
    runs: List<LabRun>, channels: List<String>, cursor: Double, xDist: Boolean,
    value: (LabRun, String) -> Double?, collapsed: Boolean, onToggle: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.background(VSurface.copy(alpha = 0.9f), RoundedCornerShape(6.dp)).clickable { onToggle() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            if (xDist) String.format(Locale.US, "%.0f m", cursor) else String.format(Locale.US, "%.1f s", cursor),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
        if (!collapsed) {
            channels.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shortName(c), Modifier.width(34.dp), fontSize = 10.sp, color = VGray, maxLines = 1)
                    runs.forEach { r ->
                        Box(Modifier.size(6.dp).background(r.color, CircleShape))
                        Text(
                            value(r, c)?.let { Channels.fmt(c, it) } ?: "-",
                            Modifier.width(42.dp).padding(start = 2.dp), fontSize = 11.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ graphique

private class Ln(val run: Int, val xs: DoubleArray, val ys: DoubleArray)

@Composable
private fun LabChart(
    runs: List<LabRun>, channels: List<String>, axisOf: Map<String, Int>,
    stacked: Boolean, xDist: Boolean, v0: Double, v1: Double, cursor: Double?, anchor: Double?,
    onCursorFrac: (Double) -> Unit, onTransform: (Float, Float, Float) -> Unit,
) {
    val data: Map<String, List<Ln>> = remember(runs, channels, xDist) {
        channels.associateWith { c ->
            runs.mapIndexedNotNull { ri, run ->
                val s = run.ch[c] ?: return@mapIndexedNotNull null
                val xs = ArrayList<Double>()
                val ys = ArrayList<Double>()
                for (i in s.t.indices) {
                    val x = if (xDist) run.sOfT.at(s.t[i]) else s.t[i]
                    if (x != null) { xs.add(x); ys.add(s.v[i]) }
                }
                if (xs.size < 2) null else Ln(ri, xs.toDoubleArray(), ys.toDoubleArray())
            }
        }
    }
    val onCursor by rememberUpdatedState(onCursorFrac)
    val onTr by rememberUpdatedState(onTransform)
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val heightDp = (if (stacked) 120 * channels.size.coerceAtLeast(1) else 300).dp + 36.dp

    Canvas(
        Modifier.fillMaxWidth().height(heightDp).background(VSurface, RoundedCornerShape(8.dp))
            .pointerInput(stacked) {
                val pl = 46.dp.toPx()
                val pr = if (stacked) 8.dp.toPx() else 46.dp.toPx()
                awaitEachGesture {
                    val plotW = (size.width - pl - pr).coerceAtLeast(1f)
                    fun frac(x: Float) = ((x - pl) / plotW).toDouble().coerceIn(0.0, 1.0)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onCursor(frac(down.position.x))
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val c = ev.calculateCentroid()
                            onTr(zoom, frac(c.x).toFloat(), pan.x / plotW)
                            ev.changes.forEach { it.consume() }
                        } else if (pressed == 1) {
                            val ch = ev.changes.first { it.pressed }
                            onCursor(frac(ch.position.x))
                            // On ne capte le geste que s'il est plutot horizontal (le defilement vertical reste possible).
                            if (abs(ch.position.x - down.position.x) > abs(ch.position.y - down.position.y)) ch.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        val padL = 46.dp.toPx()
        val padR = if (stacked) 8.dp.toPx() else 46.dp.toPx()
        val axisH = 36.dp.toPx()
        val plotW = size.width - padL - padR
        val bandsH = size.height - axisH
        val span = (v1 - v0).coerceAtLeast(1e-9)
        fun px(x: Double) = padL + ((x - v0) / span).toFloat() * plotW
        fun text(s: String, x: Float, y: Float, color: Color, align: android.graphics.Paint.Align) {
            paint.textSize = 10.sp.toPx()
            paint.color = color.toArgb()
            paint.textAlign = align
            drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
        }

        // quadrillage vertical et axe X gradue (valeurs, unite, titre)
        val xt = Ticks.nice(v0, v1, 6)
        val xStep = Ticks.step(xt)
        for (tk in xt) {
            val x = px(tk)
            drawLine(VArgent, Offset(x, 0f), Offset(x, bandsH), 1f)
            drawLine(VGray, Offset(x, bandsH), Offset(x, bandsH + 4.dp.toPx()), 1f)
            text(Ticks.fmtX(tk, xDist, xStep), x, bandsH + 16.dp.toPx(), VGray, android.graphics.Paint.Align.CENTER)
        }
        drawLine(VGray, Offset(padL, bandsH), Offset(padL + plotW, bandsH), 1f)
        text(if (xDist) "Distance sur le tracé de référence (m)" else "Temps depuis le départ du run (s)",
            padL + plotW / 2f, bandsH + 30.dp.toPx(), VText, android.graphics.Paint.Align.CENTER)

        val bands: List<List<String>> = if (stacked) channels.map { listOf(it) } else listOf(channels)
        val bandH = bandsH / bands.size.coerceAtLeast(1)
        for ((bi, chs) in bands.withIndex()) {
            val top = bi * bandH + 8.dp.toPx()
            val bottom = (bi + 1) * bandH - 8.dp.toPx()
            fun axisIdx(c: String) = if (stacked) 0 else (axisOf[c] ?: (channels.indexOf(c) % 3))
            val groups = chs.groupBy { axisIdx(it) }.toSortedMap()

            // echelle de chaque axe sur la plage visible
            val yr = HashMap<Int, Pair<Double, Double>>()
            for ((g, cs) in groups) {
                var lo = Double.MAX_VALUE
                var hi = -Double.MAX_VALUE
                for (c in cs) for (ln in data[c].orEmpty()) for (k in ln.xs.indices) {
                    if (ln.xs[k] >= v0 && ln.xs[k] <= v1) { lo = minOf(lo, ln.ys[k]); hi = maxOf(hi, ln.ys[k]) }
                }
                if (cs.any { it == "delta" || it == "dspeed" }) { lo = minOf(lo, 0.0); hi = maxOf(hi, 0.0) }
                if (lo > hi) { lo = 0.0; hi = 1.0 }
                if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 } else { val m = (hi - lo) * 0.05; lo -= m; hi += m }
                yr[g] = Pair(lo, hi)
            }

            drawLine(VGray, Offset(padL, top), Offset(padL, bottom), 1f)
            fun yOf(v: Double, lo: Double, hi: Double) = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top)

            // graduations Y (au moins 3), axe de gauche pour le premier groupe, de droite pour le second
            var gi = 0
            for ((g, cs) in groups) {
                val (lo, hi) = yr[g]!!
                val id = cs.first()
                val yt = Ticks.nice(lo, hi, 4)
                for (tk in yt) {
                    val y = yOf(tk, lo, hi)
                    if (gi == 0) {
                        drawLine(VArgent, Offset(padL, y), Offset(padL + plotW, y), 1f)
                        text(Channels.fmt(id, tk), padL - 4.dp.toPx(), y + 3.dp.toPx(), VGray, android.graphics.Paint.Align.RIGHT)
                    } else if (gi == 1) {
                        text(Channels.fmt(id, tk), padL + plotW + 4.dp.toPx(), y + 3.dp.toPx(), VGray, android.graphics.Paint.Align.LEFT)
                    }
                }
                gi++
            }
            if (stacked) {
                val d = Channels.def(chs.first())
                text("${d.label} (${d.unit})", padL + 6.dp.toPx(), top + 10.dp.toPx(), VText, android.graphics.Paint.Align.LEFT)
            }

            // courbes
            clipRect(padL, top - 2.dp.toPx(), padL + plotW, bottom + 2.dp.toPx()) {
                for ((ci, c) in chs.withIndex()) {
                    val (lo, hi) = yr[axisIdx(c)] ?: Pair(0.0, 1.0)
                    val effect = if (!stacked && runs.size > 1) when (ci % 3) {
                        0 -> null
                        1 -> PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f)
                        else -> PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
                    } else null
                    for (ln in data[c].orEmpty()) {
                        val color = if (runs.size == 1 && !stacked) ChanPalette[channels.indexOf(c) % ChanPalette.size] else runs[ln.run].color
                        val path = Path()
                        for (k in ln.xs.indices) {
                            val x = px(ln.xs[k])
                            val y = yOf(ln.ys[k], lo, hi)
                            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = effect))
                    }
                }
            }
        }

        // intervalle, repere et curseur (avec pastille de valeur sur l'axe X)
        if (anchor != null && cursor != null) {
            val xa = px(minOf(anchor, cursor)).coerceIn(padL, padL + plotW)
            val xb = px(maxOf(anchor, cursor)).coerceIn(padL, padL + plotW)
            drawRect(VAccent.copy(alpha = 0.12f), Offset(xa, 0f), Size(xb - xa, bandsH))
        }
        if (anchor != null) {
            val x = px(anchor)
            if (x >= padL && x <= padL + plotW) {
                drawLine(VGray, Offset(x, 0f), Offset(x, bandsH), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
            }
        }
        if (cursor != null) {
            val x = px(cursor)
            if (x >= padL && x <= padL + plotW) {
                drawLine(VText, Offset(x, 0f), Offset(x, bandsH), 1.5.dp.toPx())
                val label = if (xDist) String.format(Locale.US, "%.0f m", cursor) else String.format(Locale.US, "%.1f s", cursor)
                paint.textSize = 10.sp.toPx()
                val w = paint.measureText(label) + 10.dp.toPx()
                val left = (x - w / 2f).coerceIn(0f, size.width - w)
                drawRoundRect(VText, Offset(left, bandsH + 3.dp.toPx()), Size(w, 15.dp.toPx()), CornerRadius(6.dp.toPx()))
                text(label, left + w / 2f, bandsH + 14.dp.toPx(), Color.White, android.graphics.Paint.Align.CENTER)
            }
        }
    }
}

// ------------------------------------------------------------------ statistiques

@Composable
private fun StatLine(label: String, cells: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1.3f), color = VGray, fontSize = 13.sp)
        cells.forEach { Text(it, Modifier.weight(1f), fontSize = 13.sp) }
    }
}

@Composable
private fun StatsTable(runs: List<LabRun>, stats: List<RunStat?>) {
    Row(Modifier.fillMaxWidth()) {
        Text("", Modifier.weight(1.3f))
        runs.forEach { r -> Text(r.label, Modifier.weight(1f), color = r.color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
    val rows = listOf<Pair<String, (RunStat) -> String>>(
        "Durée" to { s -> fmtT((s.durationS * 1e9).toLong()) },
        "Distance" to { s -> "${f1(s.distM)} m" },
        "D+" to { s -> "${f1(s.dPlus)} m" },
        "D-" to { s -> "${f1(s.dMinus)} m" },
        "Pente moy." to { s -> "${f1(s.slopeAvg)} %" },
        "Pente max" to { s -> "${f1(s.slopeMax)} %" },
        "Vitesse min" to { s -> "${f1(s.vMin)} km/h" },
        "Vitesse moy." to { s -> "${f1(s.vAvg)} km/h" },
        "Vitesse max" to { s -> "${f1(s.vMax)} km/h" },
        "G max" to { s -> s.gMax?.let { String.format(Locale.US, "%.2f", it) } ?: "-" },
        "Freinages" to { s -> "${s.brakes}" },
        "Freinage max" to { s -> "${f1(s.brakeMax)} m/s²" },
        "Relances" to { s -> "${s.relaunches}" },
    )
    rows.forEach { (label, f) -> StatLine(label, stats.map { if (it == null) "-" else f(it) }) }
}
