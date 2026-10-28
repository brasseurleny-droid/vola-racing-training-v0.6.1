package fr.volaracing.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import fr.volaracing.training.core.SegType
import fr.volaracing.training.core.Ticks
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private fun typeName(i: Int) = when (SegType.values()[i]) {
    SegType.DESCENT -> "Descente"
    SegType.CLIMB -> "Montée"
    SegType.LIFT -> "Remontée mécanique"
    SegType.STOP -> "Arrêt"
    SegType.LINK -> "Liaison"
}

private fun typeColor(i: Int): Color = when (SegType.values()[i]) {
    SegType.DESCENT -> VGreen
    SegType.CLIMB -> VOrange
    SegType.LIFT -> VAccent
    SegType.STOP -> VGray
    SegType.LINK -> VArgent
}

/**
 * Decoupage d'une session en runs : la detection propose des segments, l'utilisateur deplace les bornes
 * au glisser, change le type, scinde ou fusionne, puis enregistre les descentes retenues.
 */
@Composable
fun SplitScreen(vm: AppViewModel, nav: NavController, sessionId: Long) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<SplitData?>(null) }
    var loading by remember { mutableStateOf(true) }
    val bounds = remember { mutableStateListOf<Double>() }
    val types = remember { mutableStateListOf<Int>() }
    val keep = remember { mutableStateListOf<Boolean>() }
    var sel by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        val d = vm.loadSplit(sessionId)
        data = d
        if (d != null) {
            bounds.clear(); bounds.addAll(d.bounds)
            types.clear(); types.addAll(d.types)
            keep.clear(); keep.addAll(d.types.map { it == SegType.DESCENT.ordinal })
        }
        loading = false
    }

    val d = data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Découpage de la session", onBack = { nav.popBackStack() })
        if (d == null) {
            Text(if (loading) "Analyse de la session..." else "Données GPS insuffisantes pour découper cette session.", Modifier.padding(16.dp), color = VGray)
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Les descentes sont proposées automatiquement (seuils à valider). Touchez un segment pour le sélectionner, " +
                    "glissez les traits verticaux pour déplacer les bornes.",
                color = VGray, style = MaterialTheme.typography.bodyMedium,
            )
            SplitChart(d, bounds, types, sel, onSelect = { sel = it }, onMove = { j, t -> bounds[j] = t })

            // ---- liste des segments
            for (i in types.indices) {
                val dur = bounds[i + 1] - bounds[i]
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier.fillMaxWidth().background(VSurface, shape)
                        .then(if (i == sel) Modifier.border(3.dp, VAccent, shape) else Modifier)
                        .clickable { sel = i }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keep[i], onCheckedChange = { keep[i] = it })
                    Box(Modifier.size(12.dp).background(typeColor(types[i]), CircleShape))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(typeName(types[i]), fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.US, "%s - %s  (%.0f s)", fmtClock(bounds[i]), fmtClock(bounds[i + 1]), dur),
                            color = VGray, fontSize = 13.sp,
                        )
                    }
                }
            }

            // ---- actions sur le segment selectionne
            if (sel in types.indices) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton("Type", Modifier.weight(1f), filled = false) {
                        types[sel] = (types[sel] + 1) % SegType.values().size
                        keep[sel] = types[sel] == SegType.DESCENT.ordinal
                    }
                    BigButton("Scinder", Modifier.weight(1f), filled = false) {
                        val mid = (bounds[sel] + bounds[sel + 1]) / 2.0
                        bounds.add(sel + 1, mid); types.add(sel + 1, types[sel]); keep.add(sel + 1, keep[sel])
                    }
                    BigButton("Fusionner", Modifier.weight(1f), filled = false, enabled = sel < types.size - 1) {
                        bounds.removeAt(sel + 1); types.removeAt(sel + 1); keep.removeAt(sel + 1)
                    }
                }
            }
            val nKeep = keep.count { it }
            BigButton("Enregistrer $nKeep run${if (nKeep > 1) "s" else ""}", Modifier.fillMaxWidth(), enabled = nKeep > 0, height = 64.dp) {
                confirm = true
            }
            msg?.let { Text(it, color = VGreen) }
        }
    }

    if (confirm && d != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Enregistrer les runs ?") },
            text = { Text("Les runs libres ou automatiques déjà présents dans cette session seront remplacés par les segments cochés.") },
            confirmButton = {
                BigButton("Enregistrer") {
                    val intervals = types.indices.filter { keep[it] }.map { Pair(bounds[it], bounds[it + 1]) }
                    scope.launch {
                        val n = vm.saveSplitRuns(d, intervals, true)
                        confirm = false
                        msg = "$n run(s) enregistré(s)."
                        nav.popBackStack()
                    }
                }
            },
            dismissButton = { BigButton("Annuler", filled = false) { confirm = false } },
        )
    }
}

private fun fmtClock(t: Double): String {
    val s = t.toInt()
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun SplitChart(
    d: SplitData, bounds: List<Double>, types: List<Int>, sel: Int,
    onSelect: (Int) -> Unit, onMove: (Int, Double) -> Unit,
) {
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val total = (d.t.last() - d.t.first()).coerceAtLeast(1e-6)

    Canvas(
        Modifier.fillMaxWidth().height(220.dp).background(VSurface, RoundedCornerShape(8.dp))
            .pointerInput(bounds.size) {
                val pl = 8.dp.toPx()
                val pr = 8.dp.toPx()
                val plotW = (size.width - pl - pr).coerceAtLeast(1f)
                fun tOf(x: Float) = d.t.first() + ((x - pl) / plotW).toDouble().coerceIn(0.0, 1.0) * total
                detectTapGestures { off ->
                    val t = tOf(off.x)
                    val i = (0 until bounds.size - 1).firstOrNull { t >= bounds[it] && t <= bounds[it + 1] }
                    if (i != null) onSelect(i)
                }
            }
            .pointerInput(bounds.size) {
                val pl = 8.dp.toPx()
                val pr = 8.dp.toPx()
                val plotW = (size.width - pl - pr).coerceAtLeast(1f)
                var j = -1
                detectDragGestures(
                    onDragStart = { off ->
                        // borne interieure la plus proche (a moins de 32 dp)
                        var best = -1
                        var bd = Float.MAX_VALUE
                        for (k in 1 until bounds.size - 1) {
                            val x = pl + ((bounds[k] - d.t.first()) / total).toFloat() * plotW
                            val dist = abs(x - off.x)
                            if (dist < bd) { bd = dist; best = k }
                        }
                        j = if (bd <= 32.dp.toPx()) best else -1
                    },
                    onDragEnd = { j = -1 },
                    onDragCancel = { j = -1 },
                    onDrag = { change, _ ->
                        if (j > 0) {
                            change.consume()
                            val t = d.t.first() + ((change.position.x - pl) / plotW).toDouble().coerceIn(0.0, 1.0) * total
                            val lo = bounds[j - 1] + 2.0
                            val hi = bounds[j + 1] - 2.0
                            if (lo < hi) onMove(j, t.coerceIn(lo, hi))
                        }
                    },
                )
            }
    ) {
        val pl = 8.dp.toPx()
        val pr = 8.dp.toPx()
        val axisH = 22.dp.toPx()
        val plotW = size.width - pl - pr
        val top = 6.dp.toPx()
        val bottom = size.height - axisH
        fun px(t: Double) = pl + ((t - d.t.first()) / total).toFloat() * plotW

        // bandes de segments
        for (i in types.indices) {
            val x0 = px(bounds[i])
            val x1 = px(bounds[i + 1])
            drawRect(typeColor(types[i]).copy(alpha = if (i == sel) 0.45f else 0.22f), Offset(x0, top), Size(x1 - x0, bottom - top))
        }
        // courbes altitude (bleu) et vitesse (orange), chacune a sa propre echelle
        fun curve(v: DoubleArray, color: Color) {
            val lo = v.min()
            val hi = v.max()
            val span = (hi - lo).coerceAtLeast(1e-6)
            val path = Path()
            for (k in v.indices) {
                val x = px(d.t[k])
                val y = bottom - ((v[k] - lo) / span).toFloat() * (bottom - top)
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        curve(d.alt, VAccent)
        curve(d.speed, VOrange)
        // bornes
        for (k in bounds.indices) {
            val x = px(bounds[k])
            drawLine(VText, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx())
            if (k in 1 until bounds.size - 1) drawCircle(VText, 6.dp.toPx(), Offset(x, top + 6.dp.toPx()))
        }
        // axe du temps
        val ticks = Ticks.nice(d.t.first(), d.t.last(), 6)
        val step = Ticks.step(ticks)
        paint.textSize = 10.sp.toPx()
        paint.color = VGray.toArgb()
        paint.textAlign = android.graphics.Paint.Align.CENTER
        for (tk in ticks) {
            val x = px(tk)
            drawLine(VArgent, Offset(x, bottom), Offset(x, bottom + 4.dp.toPx()), 1f)
            drawContext.canvas.nativeCanvas.drawText(Ticks.fmtX(tk, false, maxOf(step, 60.0)), x, bottom + 16.dp.toPx(), paint)
        }
    }
}
