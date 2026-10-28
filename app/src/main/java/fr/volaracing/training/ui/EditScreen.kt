package fr.volaracing.training.ui

import android.Manifest
import android.content.res.Configuration
import android.graphics.PointF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.volaracing.training.core.Gate
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.Pt
import fr.volaracing.training.data.Act
import fr.volaracing.training.data.Course
import fr.volaracing.training.data.Waypoints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

private class Snap(
    val aLat: Double?, val aLon: Double?, val bLat: Double?, val bLon: Double?,
    val wps: List<Pair<Double, Double>>, val width: Double,
)

@Composable
fun EditScreen(vm: AppViewModel, nav: NavController, courseId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var name by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(vm.activity) }
    var uuid by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }
    var width by remember { mutableStateOf(vm.prefs.profile().gateWidth) }
    var aLat by remember { mutableStateOf<Double?>(null) }
    var aLon by remember { mutableStateOf<Double?>(null) }
    var bLat by remember { mutableStateOf<Double?>(null) }
    var bLon by remember { mutableStateOf<Double?>(null) }
    val waypoints = remember { mutableStateListOf<Pair<Double, Double>>() }
    var mode by remember { mutableStateOf("A") } // "A", "B" ou "W" (etape)
    var step by remember { mutableStateOf(1.0) }
    var sat by remember { mutableStateOf(false) }
    var showNudge by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var hasLoc by remember { mutableStateOf(hasFine(ctx)) }
    var centeredOnMe by remember { mutableStateOf(false) }
    var camTick by remember { mutableStateOf(0) }
    val undo = remember { mutableStateListOf<Snap>() }
    val redo = remember { mutableStateListOf<Snap>() }
    val setHint: (String) -> Unit = { hint = it }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasLoc = r[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!hasLoc) permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(hint) { if (hint != null) { delay(1800); hint = null } }
    DisposableEffect(hasLoc) {
        val stop = if (hasLoc) startLocationUpdates(ctx) { me = it } else null
        onDispose { stop?.invoke() }
    }

    LaunchedEffect(courseId) {
        val c = if (courseId == 0L) null else vm.course(courseId)
        if (c != null) {
            name = c.name; activity = c.activity; width = c.gateWidth; uuid = c.uuid; groupId = c.groupId
            aLat = c.aLat; aLon = c.aLon; bLat = c.bLat; bLon = c.bLon
            waypoints.clear(); waypoints.addAll(Waypoints.decode(c.waypoints))
        } else {
            name = "Nouveau parcours"
        }
        loaded = true
    }

    // ---- historique (defaire / refaire)
    fun snap() = Snap(aLat, aLon, bLat, bLon, waypoints.toList(), width)
    fun push() {
        undo.add(snap()); redo.clear()
        if (undo.size > 50) undo.removeAt(0)
    }
    fun restore(s: Snap) {
        aLat = s.aLat; aLon = s.aLon; bLat = s.bLat; bLon = s.bLon; width = s.width
        waypoints.clear(); waypoints.addAll(s.wps)
    }
    fun doUndo() {
        if (undo.isNotEmpty()) { redo.add(snap()); restore(undo.removeAt(undo.lastIndex)) }
    }
    fun doRedo() {
        if (redo.isNotEmpty()) { undo.add(snap()); restore(redo.removeAt(redo.lastIndex)) }
    }

    fun setPoint(lat: Double, lon: Double) {
        push()
        when (mode) {
            "A" -> { aLat = lat; aLon = lon }
            "B" -> { bLat = lat; bLon = lon }
            else -> waypoints.add(Pair(lat, lon))
        }
    }

    // Deplacement fin du point actif (A, B ou derniere etape), en metres (x = est, y = nord).
    fun nudge(dx: Double, dy: Double) {
        val cur: Pair<Double, Double>? = when (mode) {
            "A" -> if (aLat != null && aLon != null) Pair(aLat!!, aLon!!) else null
            "B" -> if (bLat != null && bLon != null) Pair(bLat!!, bLon!!) else null
            else -> waypoints.lastOrNull()
        }
        if (cur == null) {
            Toast.makeText(ctx, "Placez d'abord ce point", Toast.LENGTH_SHORT).show()
            return
        }
        push()
        val n = Pair(cur.first + dy / 111320.0, cur.second + dx / (111320.0 * cos(Math.toRadians(cur.first))))
        when (mode) {
            "A" -> { aLat = n.first; aLon = n.second }
            "B" -> { bLat = n.first; bLon = n.second }
            else -> waypoints[waypoints.lastIndex] = n
        }
    }

    // ---- deplacement par glisser : un delta ecran (px) est converti en coordonnees
    fun moveBy(kind: String, idx: Int, dx: Float, dy: Float) {
        val m = map ?: return
        val cur: Pair<Double, Double> = when (kind) {
            "A" -> Pair(aLat ?: return, aLon ?: return)
            "B" -> Pair(bLat ?: return, bLon ?: return)
            else -> waypoints.getOrNull(idx) ?: return
        }
        val p = m.projection.toScreenLocation(LatLng(cur.first, cur.second))
        val ll = m.projection.fromScreenLocation(PointF(p.x + dx, p.y + dy))
        when (kind) {
            "A" -> { aLat = ll.latitude; aLon = ll.longitude }
            "B" -> { bLat = ll.latitude; bLon = ll.longitude }
            else -> waypoints[idx] = Pair(ll.latitude, ll.longitude)
        }
    }

    // Premiere porte (depart) : sa poignee d'extremite regle la largeur de toutes les portes.
    fun firstGate(): Pair<LocalFrame, Gate>? {
        val a1 = aLat
        val a2 = aLon
        val b1 = bLat
        val b2 = bLon
        if (a1 == null || a2 == null || b1 == null || b2 == null) return null
        val fr = LocalFrame(a1, a2)
        val pts = ArrayList<Pt>()
        pts.add(Pt(0.0, 0.0))
        waypoints.forEach { pts.add(fr.toLocal(it.first, it.second)) }
        pts.add(fr.toLocal(b1, b2))
        return try { Pair(fr, Gate.chain(pts, width).first()) } catch (e: IllegalArgumentException) { null }
    }
    fun widthDrag(fr: LocalFrame, g: Gate, dx: Float, dy: Float) {
        val m = map ?: return
        val end = fr.toLatLon(g.p2)
        val p = m.projection.toScreenLocation(LatLng(end.first, end.second))
        val ll = m.projection.fromScreenLocation(PointF(p.x + dx, p.y + dy))
        val pt = fr.toLocal(ll.latitude, ll.longitude)
        width = (2.0 * abs(pt.x * (-g.dirY) + pt.y * g.dirX)).coerceIn(5.0, 100.0)
    }

    fun save() {
        scope.launch {
            vm.saveCourse(
                Course(
                    id = courseId, name = name.ifBlank { "Parcours" }, activity = activity,
                    aLat = aLat, aLon = aLon, bLat = bLat, bLon = bLon, gateWidth = width,
                    waypoints = Waypoints.encode(waypoints.toList()), uuid = uuid, groupId = groupId,
                )
            )
            nav.popBackStack()
        }
    }

    LaunchedEffect(map, loaded) {
        val m = map ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        val target = if (aLat != null && aLon != null) Triple(aLat!!, aLon!!, 16.0)
        else lastKnown(ctx)?.let { Triple(it.first, it.second, 15.0) } ?: Triple(45.92, 6.87, 10.0)
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), target.third))
    }
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        m.addOnCameraMoveListener { camTick++ }
        m.addOnCameraIdleListener { camTick++ }
    }
    LaunchedEffect(me, map, loaded) {
        val m = map
        val p = me
        if (!centeredOnMe && m != null && p != null && loaded && aLat == null) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.first, p.second), 17.0))
            centeredOnMe = true
        }
    }
    val wKey = waypoints.toList()
    LaunchedEffect(style, aLat, aLon, bLat, bLon, width, wKey) {
        val s = style ?: return@LaunchedEffect
        updateEditSources(s, aLat, aLon, bLat, bLon, width, wKey)
    }
    LaunchedEffect(style, me) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(pointFc(me?.first, me?.second))
    }

    // Barre d'icones : ligne en bas (portrait) ou colonne a droite (paysage), defilable.
    val tools: @Composable () -> Unit = {
        ModeChip("D", VGreen, mode == "A", "Départ : toucher la carte ou glisser D", setHint) { mode = "A" }
        ModeChip("A", VRed, mode == "B", "Arrivée : toucher la carte ou glisser A", setHint) { mode = "B" }
        ModeChip("É", VOrange, mode == "W", "Étapes (${waypoints.size}) : toucher la carte pour en ajouter", setHint) { mode = "W" }
        ToolButton("target", "Placer le point choisi au centre de la carte", onHint = setHint) {
            map?.cameraPosition?.target?.let { setPoint(it.latitude, it.longitude) }
        }
        ToolButton("pin", "Placer le point choisi à ma position", onHint = setHint) {
            val p = me
            if (p != null) setPoint(p.first, p.second)
            else Toast.makeText(ctx, "Position GPS indisponible", Toast.LENGTH_SHORT).show()
        }
        if (mode == "W") {
            ToolButton("remove", "Retirer la dernière étape", onHint = setHint) {
                if (waypoints.isNotEmpty()) { push(); waypoints.removeAt(waypoints.lastIndex) }
            }
        }
        ToolButton("nudge", "Réglage fin : flèches et largeur des portes", active = showNudge, onHint = setHint) { showNudge = !showNudge }
        ToolButton("undo", "Défaire", onHint = setHint) { doUndo() }
        ToolButton("redo", "Refaire", onHint = setHint) { doRedo() }
        ToolButton("sat", if (sat) "Fond satellite (toucher : plan)" else "Fond plan (toucher : satellite)", active = sat, onHint = setHint) { sat = !sat }
        ToolButton("center", "Recentrer sur ma position", onHint = setHint) {
            val mm = map
            val p = me
            when {
                !hasLoc -> permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                p == null -> Toast.makeText(ctx, "Recherche de la position GPS...", Toast.LENGTH_SHORT).show()
                mm != null -> mm.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.first, p.second), 17.0))
            }
        }
        ToolButton("save", "Enregistrer le parcours", onHint = setHint) { save() }
        ToolButton("close", "Quitter sans enregistrer", onHint = setHint) { nav.popBackStack() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Parcours : $name  (modifier)", onBack = { nav.popBackStack() }, onTitleClick = { renameOpen = true })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapPane(
                Modifier.fillMaxSize(), satellite = sat,
                onClick = { lat, lon -> setPoint(lat, lon) },
            ) { m, st ->
                ensureEditLayers(st)
                map = m
                style = st
            }

            // ---- poignees a glisser (depart, etapes, arrivee, largeur de porte)
            val m = map
            val tick = camTick
            if (m != null && style != null && tick >= 0) {
                val aLa = aLat
                val aLo = aLon
                if (aLa != null && aLo != null) {
                    PointHandle("D", VGreen, m.projection.toScreenLocation(LatLng(aLa, aLo)), { push() }) { dx, dy -> moveBy("A", -1, dx, dy) }
                }
                waypoints.forEachIndexed { i, w ->
                    PointHandle("${i + 1}", VOrange, m.projection.toScreenLocation(LatLng(w.first, w.second)), { push() }) { dx, dy -> moveBy("W", i, dx, dy) }
                }
                val bLa = bLat
                val bLo = bLon
                if (bLa != null && bLo != null) {
                    PointHandle("A", VRed, m.projection.toScreenLocation(LatLng(bLa, bLo)), { push() }) { dx, dy -> moveBy("B", -1, dx, dy) }
                }
                val fg = firstGate()
                if (fg != null) {
                    val end = fg.first.toLatLon(fg.second.p2)
                    WidthHandle(m.projection.toScreenLocation(LatLng(end.first, end.second)), { push() }) { dx, dy -> widthDrag(fg.first, fg.second, dx, dy) }
                }
            }

            // ---- barre d'icones
            if (landscape) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().verticalScroll(rememberScrollState())
                        .background(VSurface.copy(alpha = 0.6f)).padding(2.dp),
                ) { tools() }
            } else {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(VSurface.copy(alpha = 0.7f)).padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { tools() }
            }

            // ---- palette de reglage fin
            if (showNudge) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = if (landscape) 6.dp else 54.dp)
                        .background(VSurface.copy(alpha = 0.94f), RoundedCornerShape(10.dp)).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BigButton("←", Modifier.width(46.dp), filled = false, height = 44.dp) { nudge(-step, 0.0) }
                        BigButton("↑", Modifier.width(46.dp), filled = false, height = 44.dp) { nudge(0.0, step) }
                        BigButton("↓", Modifier.width(46.dp), filled = false, height = 44.dp) { nudge(0.0, -step) }
                        BigButton("→", Modifier.width(46.dp), filled = false, height = 44.dp) { nudge(step, 0.0) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BigButton("${fmtNum(step)} m", Modifier.width(66.dp), height = 44.dp) {
                            step = when (step) { 1.0 -> 5.0; 5.0 -> 20.0; else -> 1.0 }
                        }
                        BigButton("-", Modifier.width(40.dp), filled = false, height = 44.dp) { push(); width = (width - 5.0).coerceAtLeast(5.0) }
                        Text("Porte ${fmtNum(width)} m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        BigButton("+", Modifier.width(40.dp), filled = false, height = 44.dp) { push(); width = (width + 5.0).coerceAtMost(100.0) }
                    }
                }
            }

            // ---- conseil (ferme d'un geste) et libelles d'icones
            if (tip) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(6.dp).fillMaxWidth(if (landscape) 0.6f else 0.9f)
                        .background(VDark.copy(alpha = 0.82f), RoundedCornerShape(8.dp)).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Glissez D, 1, A pour déplacer les points. Carré blanc : largeur des portes. Appui long sur une icône : son rôle.",
                        Modifier.weight(1f), color = Color.White, fontSize = 12.sp,
                    )
                    Box(Modifier.size(40.dp).clickable { tip = false }, contentAlignment = Alignment.Center) {
                        Text("×", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
            hint?.let {
                Text(
                    it, Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
                        .background(VDark.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(8.dp),
                    color = Color.White, fontSize = 12.sp,
                )
            }
        }
    }

    // ---- nom et activite : boite de dialogue (le clavier ne masque plus la carte)
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Nom du parcours") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Act.all.forEach { a -> BigButton(Act.label(a), Modifier.weight(1f), filled = activity == a, height = 44.dp) { activity = a } }
                    }
                }
            },
            confirmButton = { BigButton("OK") { renameOpen = false } },
        )
    }
}

/** Pastille a glisser : zone tactile 48 dp, etiquette au-dessus du point (le doigt ne la masque pas). */
@Composable
private fun PointHandle(label: String, color: Color, pos: PointF, onStart: () -> Unit, onDelta: (Float, Float) -> Unit) {
    val start by rememberUpdatedState(onStart)
    val delta by rememberUpdatedState(onDelta)
    val half = with(LocalDensity.current) { 24.dp.toPx() }
    Box(
        Modifier.offset { IntOffset((pos.x - half).roundToInt(), (pos.y - half).roundToInt()) }
            .size(48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start() },
                    onDrag = { change, amount -> change.consume(); delta(amount.x, amount.y) },
                )
            },
    ) {
        Box(Modifier.align(Alignment.TopCenter).size(18.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Petit carre blanc a l'extremite de la porte de depart : glisser pour regler la largeur. */
@Composable
private fun WidthHandle(pos: PointF, onStart: () -> Unit, onDelta: (Float, Float) -> Unit) {
    val start by rememberUpdatedState(onStart)
    val delta by rememberUpdatedState(onDelta)
    val half = with(LocalDensity.current) { 22.dp.toPx() }
    Box(
        Modifier.offset { IntOffset((pos.x - half).roundToInt(), (pos.y - half).roundToInt()) }
            .size(44.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start() },
                    onDrag = { change, amount -> change.consume(); delta(amount.x, amount.y) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).background(Color.White).border(2.dp, VDark))
    }
}

private const val SRC_LINES = "edit-lines"
private const val SRC_A = "edit-a"
private const val SRC_B = "edit-b"
private const val SRC_W = "edit-w"
private const val SRC_ME = "edit-me"

private fun pointFc(lat: Double?, lon: Double?): FeatureCollection =
    if (lat != null && lon != null) FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))))
    else FeatureCollection.fromFeatures(emptyList<Feature>())

private fun ensureEditLayers(style: Style) {
    for (id in listOf(SRC_LINES, SRC_A, SRC_B, SRC_W, SRC_ME)) style.addSource(GeoJsonSource(id))
    style.addLayer(LineLayer("$SRC_LINES-l", SRC_LINES).withProperties(
        PropertyFactory.lineColor(VOrange.toArgb()), PropertyFactory.lineWidth(3f)))
    fun dot(id: String, color: Int, radius: Float) = style.addLayer(CircleLayer("$id-c", id).withProperties(
        PropertyFactory.circleRadius(radius), PropertyFactory.circleColor(color),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(1.5f)))
    dot(SRC_ME, VAccent.toArgb(), 5f)
    dot(SRC_W, VOrange.toArgb(), 3.5f)
    dot(SRC_A, VGreen.toArgb(), 4f)
    dot(SRC_B, VRed.toArgb(), 4f)
}

private fun updateEditSources(
    style: Style, aLat: Double?, aLon: Double?, bLat: Double?, bLon: Double?, width: Double,
    wps: List<Pair<Double, Double>>,
) {
    style.getSourceAs<GeoJsonSource>(SRC_A)?.setGeoJson(pointFc(aLat, aLon))
    style.getSourceAs<GeoJsonSource>(SRC_B)?.setGeoJson(pointFc(bLat, bLon))
    style.getSourceAs<GeoJsonSource>(SRC_W)?.setGeoJson(
        FeatureCollection.fromFeatures(wps.map { Feature.fromGeometry(Point.fromLngLat(it.second, it.first)) })
    )
    val lines = ArrayList<Feature>()
    if (aLat != null && aLon != null && bLat != null && bLon != null) {
        val fr = LocalFrame(aLat, aLon)
        val ll = ArrayList<Pair<Double, Double>>()
        ll.add(Pair(aLat, aLon)); ll.addAll(wps); ll.add(Pair(bLat, bLon))
        lines.add(lineFeature(ll))
        try {
            for (g in Gate.chain(ll.map { fr.toLocal(it.first, it.second) }, width)) {
                lines.add(lineFeature(listOf(fr.toLatLon(g.p1), fr.toLatLon(g.p2))))
            }
        } catch (_: IllegalArgumentException) { /* deux points confondus : pas de portes */ }
    }
    style.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(FeatureCollection.fromFeatures(lines))
}
