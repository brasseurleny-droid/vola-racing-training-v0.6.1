package fr.volaracing.training.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.volaracing.training.core.Fmt
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.TrackPt
import fr.volaracing.training.data.Session
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.cos
import android.content.res.Configuration
import java.util.Locale

/** Bouton a zone tactile large (56 dp minimum, utilisable avec des gants). */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    container: Color = VDark,
    content: Color? = null,
    border: Color = VText,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val pad = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    if (filled) {
        Button(
            onClick = onClick, modifier = modifier.heightIn(min = height), enabled = enabled,
            shape = shape, contentPadding = pad,
            colors = ButtonDefaults.buttonColors(
                containerColor = container, contentColor = content ?: Color.White,
                disabledContainerColor = VGray.copy(alpha = 0.35f), disabledContentColor = Color.White,
            ),
        ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = modifier.heightIn(min = height), enabled = enabled,
            shape = shape, contentPadding = pad, border = BorderStroke(1.5.dp, border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = content ?: VText, disabledContentColor = VGray),
        ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
    }
}

/** Logo provisoire (texte). TODO: remplacer par le vrai logo Vola Racing (PNG/SVG). */
@Composable
fun VolaLogo() {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            "VOLA", fontFamily = VCondensed, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            fontSize = 26.sp, lineHeight = 26.sp, color = VText,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(3.dp).background(VText))
            Text(" RACING ", fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = VText)
            Box(Modifier.width(10.dp).height(3.dp).background(VText))
        }
    }
}

/** Bandeau bleu compact (encore plus bas en paysage). Le titre peut etre touche (ex. renommer). */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, onTitleClick: (() -> Unit)? = null) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Row(
        Modifier.fillMaxWidth().background(VBand).heightIn(min = if (landscape) 34.dp else 44.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(if (landscape) 34.dp else 40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("<", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VText)
            }
        }
        Text(
            title,
            Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier),
            style = MaterialTheme.typography.titleMedium, color = VText, maxLines = 1,
        )
        VolaMark()
    }
}

@Composable
fun VolaMark() {
    Text(
        "VOLA RACING", fontFamily = VCondensed, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
        fontSize = 13.sp, letterSpacing = 1.sp, color = VText,
    )
}

/** Pastille de mode (D, A, E) : appui = choisir, appui long = libelle. */
@Composable
fun ModeChip(label: String, color: Color, selected: Boolean, hint: String, onHint: (String) -> Unit, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    val h by rememberUpdatedState(onHint)
    Box(
        Modifier.size(44.dp).pointerInput(Unit) { detectTapGestures(onTap = { click() }, onLongPress = { h(hint) }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).background(if (selected) color else VSurface.copy(alpha = 0.9f), CircleShape).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = if (selected) Color.White else color, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

/** Barre de titre de section (equivalent de "CLASSEMENT LIVE"). */
@Composable
fun SectionTitle(text: String) {
    Box(Modifier.fillMaxWidth().background(VBand, RoundedCornerShape(4.dp)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = VText)
    }
}

@Composable
fun Bib(n: Int) {
    Box(Modifier.border(1.dp, VText, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("$n", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VText)
    }
}

@Composable
fun TimeChip(text: String) {
    Box(Modifier.background(VChip, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = VText, maxLines = 1)
    }
}

/** Tuile coloree type "OR / ARGENT / BRONZE". */
@Composable
fun Tile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(color, RoundedCornerShape(8.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VGray, maxLines = 1)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VText, maxLines = 1)
    }
}

/** Ligne de classement : rang, dossard, titre, temps surligne, ecart au meilleur. */
@Composable
fun RankRow(
    rank: Int, bib: Int, title: String, sub: String, timeNs: Long, gapNs: Long?,
    selected: Boolean = false, tag: String? = null, thumb: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(VSurface, shape)
            .then(if (selected) Modifier.border(3.dp, VAccent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", Modifier.width(28.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = VText)
        Bib(bib)
        Spacer(Modifier.width(8.dp))
        if (thumb != null) { thumb(); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VText, maxLines = 1)
            Text(sub, color = VGray, fontSize = 13.sp, maxLines = 1)
            if (tag != null) Text(tag, color = VRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            TimeChip(fmtT(timeNs))
            if (gapNs != null && gapNs > 0) {
                Text(String.format(Locale.US, "+%.2f", gapNs / 1e9), color = VGray, fontSize = 13.sp)
            }
        }
    }
}

/** 30.39 sous la minute, 1:12.34 au-dela (comme l'affichage des resultats). */
fun fmtT(ns: Long): String =
    if (ns < 60_000_000_000L) String.format(Locale.US, "%.2f", (ns / 10_000_000L) / 100.0) else Fmt.time(ns)

/** Viseur au centre de la carte. */
@Composable
fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier.size(40.dp)) {
        val c = center
        drawLine(VText, Offset(0f, c.y), Offset(size.width, c.y), 2.dp.toPx())
        drawLine(VText, Offset(c.x, 0f), Offset(c.x, size.height), 2.dp.toPx())
        drawCircle(VOrange, radius = 7.dp.toPx(), style = Stroke(3.dp.toPx()))
    }
}

fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

@Composable
fun Stepper(label: String, value: Double, unit: String, step: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = VGray, style = MaterialTheme.typography.bodyMedium)
            Text("${fmtNum(value)} $unit", style = MaterialTheme.typography.titleLarge)
        }
        BigButton("-", Modifier.width(64.dp), filled = false) { onChange((value - step).coerceAtLeast(min)) }
        Spacer(Modifier.width(8.dp))
        BigButton("+", Modifier.width(64.dp), filled = false) { onChange((value + step).coerceAtMost(max)) }
    }
}

data class ChartLine(
    val pts: List<Pair<Double, Double>>,
    val color: Color,
    val segColors: List<Color>? = null,
)

/** Courbe simple dessinee sur Canvas (x = distance en m). */
@Composable
fun Chart(lines: List<ChartLine>, modifier: Modifier = Modifier, zeroLine: Boolean = false) {
    Canvas(modifier.fillMaxWidth().height(170.dp).background(VSurface, RoundedCornerShape(8.dp))) {
        val all = lines.flatMap { it.pts }
        if (all.size < 2) return@Canvas
        val xMax = all.maxOf { it.first }.coerceAtLeast(1.0)
        val yMin = if (zeroLine) minOf(all.minOf { it.second }, 0.0) else 0.0
        var yMax = if (zeroLine) maxOf(all.maxOf { it.second }, 0.0) else all.maxOf { it.second }
        if (yMax - yMin < 1e-6) yMax = yMin + 1.0
        val pad = 8.dp.toPx()
        fun px(x: Double) = pad + (x / xMax).toFloat() * (size.width - 2 * pad)
        fun py(y: Double) = size.height - pad - ((y - yMin) / (yMax - yMin)).toFloat() * (size.height - 2 * pad)
        if (zeroLine) drawLine(VGray, Offset(pad, py(0.0)), Offset(size.width - pad, py(0.0)), 1.dp.toPx())
        for (l in lines) {
            for (i in 1 until l.pts.size) {
                drawLine(
                    l.segColors?.getOrNull(i - 1) ?: l.color,
                    Offset(px(l.pts[i - 1].first), py(l.pts[i - 1].second)),
                    Offset(px(l.pts[i].first), py(l.pts[i].second)),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }
    }
}

/** Miniature du trace (dessinee a partir des points GPS, sans carte ni reseau). */
@Composable
fun Thumb(pts: List<TrackPt>?, modifier: Modifier = Modifier.size(64.dp), color: Color = VAccent) {
    Canvas(modifier.background(VBg, RoundedCornerShape(6.dp))) {
        if (pts == null || pts.size < 2) return@Canvas
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val w = maxOf(maxX - minX, 1.0).toFloat()
        val h = maxOf(maxY - minY, 1.0).toFloat()
        val pad = 7.dp.toPx()
        val sc = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val offX = (size.width - w * sc) / 2f
        val offY = (size.height - h * sc) / 2f
        fun px(x: Double) = offX + (x - minX).toFloat() * sc
        fun py(y: Double) = size.height - (offY + (y - minY).toFloat() * sc)
        for (i in 1 until pts.size) {
            drawLine(color, Offset(px(pts[i - 1].x), py(pts[i - 1].y)), Offset(px(pts[i].x), py(pts[i].y)), 2.5.dp.toPx())
        }
        drawCircle(VGreen, 3.5.dp.toPx(), Offset(px(pts.first().x), py(pts.first().y)))
        drawCircle(VRed, 3.5.dp.toPx(), Offset(px(pts.last().x), py(pts.last().y)))
    }
}

fun sessionTitle(s: Session): String {
    val base = s.name.ifBlank { "Session du " + SimpleDateFormat("dd/MM 'à' HH:mm", Locale.FRANCE).format(Date(s.startUtcMs)) }
    return if (s.origin == 1 && s.ownerName.isNotBlank()) "${s.ownerName} - $base" else base
}

fun dayLabel(ms: Long): String =
    SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date(ms)).replaceFirstChar { it.uppercase() }

/** Petite courbe d'evolution (les valeurs basses, meilleures, sont en haut). */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(52.dp), color: Color = VAccent) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        if (values.size == 1) { drawCircle(color, 5.dp.toPx(), center); return@Canvas }
        val mn = values.min()
        val rng = (values.max() - mn).coerceAtLeast(1e-9)
        val pad = 8.dp.toPx()
        fun px(i: Int) = pad + i.toFloat() / (values.size - 1) * (size.width - 2 * pad)
        fun py(v: Double) = pad + ((v - mn) / rng).toFloat() * (size.height - 2 * pad)
        for (i in 1 until values.size) {
            drawLine(color, Offset(px(i - 1), py(values[i - 1])), Offset(px(i), py(values[i])), 3.dp.toPx())
        }
        for (i in values.indices) drawCircle(color, 4.dp.toPx(), Offset(px(i), py(values[i])))
    }
}

/** Miniature d'un parcours (depart -> etapes -> arrivee) dessinee a partir de ses coordonnees. */
@Composable
fun LatLonThumb(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier.size(64.dp)) {
    Canvas(modifier.background(VBg, RoundedCornerShape(6.dp))) {
        if (points.size < 2) return@Canvas
        val fr = LocalFrame(points.first().first, points.first().second)
        val pts = points.map { fr.toLocal(it.first, it.second) }
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val w = maxOf(maxX - minX, 1.0).toFloat()
        val h = maxOf(maxY - minY, 1.0).toFloat()
        val pad = 7.dp.toPx()
        val sc = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val offX = (size.width - w * sc) / 2f
        val offY = (size.height - h * sc) / 2f
        fun px(x: Double) = offX + (x - minX).toFloat() * sc
        fun py(y: Double) = size.height - (offY + (y - minY).toFloat() * sc)
        for (i in 1 until pts.size) {
            drawLine(VOrange, Offset(px(pts[i - 1].x), py(pts[i - 1].y)), Offset(px(pts[i].x), py(pts[i].y)), 2.5.dp.toPx())
        }
        drawCircle(VGreen, 3.5.dp.toPx(), Offset(px(pts.first().x), py(pts.first().y)))
        drawCircle(VRed, 3.5.dp.toPx(), Offset(px(pts.last().x), py(pts.last().y)))
    }
}

/** Icones dessinees (pas d'emoji), en traits simples. */
fun DrawScope.drawToolIcon(kind: String, color: Color) {
    val w = size.width
    val h = size.height
    val m = minOf(w, h)
    val sw = 2.dp.toPx()
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), sw)
    when (kind) {
        "channels" -> { line(0.05f, 0.2f, 0.95f, 0.2f); line(0.05f, 0.5f, 0.7f, 0.5f); line(0.05f, 0.8f, 0.45f, 0.8f) }
        "stack" -> {
            drawRect(color, Offset(0.05f * w, 0.05f * h), Size(0.9f * w, 0.38f * h), style = Stroke(sw))
            drawRect(color, Offset(0.05f * w, 0.57f * h), Size(0.9f * w, 0.38f * h), style = Stroke(sw))
        }
        "overlay" -> {
            line(0.05f, 0.7f, 0.35f, 0.25f); line(0.35f, 0.25f, 0.65f, 0.7f); line(0.65f, 0.7f, 0.95f, 0.3f)
            line(0.05f, 0.35f, 0.35f, 0.8f); line(0.35f, 0.8f, 0.65f, 0.35f); line(0.65f, 0.35f, 0.95f, 0.75f)
        }
        "xaxis" -> {
            line(0.05f, 0.6f, 0.95f, 0.6f); line(0.2f, 0.45f, 0.2f, 0.75f); line(0.5f, 0.45f, 0.5f, 0.75f)
            line(0.95f, 0.6f, 0.8f, 0.45f); line(0.95f, 0.6f, 0.8f, 0.75f)
        }
        "zplus", "zminus" -> {
            drawCircle(color, 0.3f * m, Offset(0.42f * w, 0.42f * h), style = Stroke(sw))
            line(0.65f, 0.65f, 0.95f, 0.95f)
            line(0.27f, 0.42f, 0.57f, 0.42f)
            if (kind == "zplus") line(0.42f, 0.27f, 0.42f, 0.57f)
        }
        "fit" -> {
            drawRect(color, Offset(0.3f * w, 0.3f * h), Size(0.4f * w, 0.4f * h), style = Stroke(sw))
            line(0.05f, 0.05f, 0.25f, 0.25f); line(0.95f, 0.05f, 0.75f, 0.25f)
            line(0.05f, 0.95f, 0.25f, 0.75f); line(0.95f, 0.95f, 0.75f, 0.75f)
        }
        "fullscreen" -> {
            line(0.05f, 0.4f, 0.05f, 0.05f); line(0.05f, 0.05f, 0.4f, 0.05f)
            line(0.95f, 0.4f, 0.95f, 0.05f); line(0.95f, 0.05f, 0.6f, 0.05f)
            line(0.05f, 0.6f, 0.05f, 0.95f); line(0.05f, 0.95f, 0.4f, 0.95f)
            line(0.95f, 0.6f, 0.95f, 0.95f); line(0.95f, 0.95f, 0.6f, 0.95f)
        }
        "anchor" -> {
            drawRect(color.copy(alpha = 0.3f), Offset(0.25f * w, 0.1f * h), Size(0.5f * w, 0.8f * h))
            line(0.25f, 0.1f, 0.25f, 0.9f); line(0.75f, 0.1f, 0.75f, 0.9f)
        }
        "map" -> {
            drawRect(color, Offset(0.05f * w, 0.15f * h), Size(0.9f * w, 0.7f * h), style = Stroke(sw))
            line(0.35f, 0.15f, 0.35f, 0.85f); line(0.65f, 0.15f, 0.65f, 0.85f)
            drawCircle(color, 0.08f * m, Offset(0.5f * w, 0.5f * h))
        }
        "menu" -> {
            drawCircle(color, 0.08f * m, Offset(0.5f * w, 0.2f * h))
            drawCircle(color, 0.08f * m, Offset(0.5f * w, 0.5f * h))
            drawCircle(color, 0.08f * m, Offset(0.5f * w, 0.8f * h))
        }
        "sat" -> {
            drawCircle(color, 0.42f * m, Offset(0.5f * w, 0.5f * h), style = Stroke(sw))
            line(0.1f, 0.5f, 0.9f, 0.5f); line(0.5f, 0.1f, 0.5f, 0.9f)
        }
        "center" -> {
            drawCircle(color, 0.22f * m, Offset(0.5f * w, 0.5f * h), style = Stroke(sw))
            line(0.5f, 0.02f, 0.5f, 0.25f); line(0.5f, 0.75f, 0.5f, 0.98f)
            line(0.02f, 0.5f, 0.25f, 0.5f); line(0.75f, 0.5f, 0.98f, 0.5f)
        }
        "palette" -> {
            drawRect(VRed, Offset(0.05f * w, 0.3f * h), Size(0.3f * w, 0.4f * h))
            drawRect(VChip, Offset(0.35f * w, 0.3f * h), Size(0.3f * w, 0.4f * h))
            drawRect(VGreen, Offset(0.65f * w, 0.3f * h), Size(0.3f * w, 0.4f * h))
        }
        "target" -> {
            drawCircle(color, 0.38f * m, Offset(0.5f * w, 0.5f * h), style = Stroke(sw))
            drawCircle(color, 0.1f * m, Offset(0.5f * w, 0.5f * h))
        }
        "pin" -> {
            drawCircle(color, 0.24f * m, Offset(0.5f * w, 0.36f * h), style = Stroke(sw))
            line(0.5f, 0.6f, 0.5f, 0.95f)
        }
        "undo" -> {
            line(0.1f, 0.45f, 0.35f, 0.2f); line(0.1f, 0.45f, 0.35f, 0.7f)
            line(0.1f, 0.45f, 0.65f, 0.45f); line(0.65f, 0.45f, 0.9f, 0.7f)
        }
        "redo" -> {
            line(0.9f, 0.45f, 0.65f, 0.2f); line(0.9f, 0.45f, 0.65f, 0.7f)
            line(0.9f, 0.45f, 0.35f, 0.45f); line(0.35f, 0.45f, 0.1f, 0.7f)
        }
        "save" -> { line(0.12f, 0.55f, 0.4f, 0.85f); line(0.4f, 0.85f, 0.9f, 0.18f) }
        "close" -> { line(0.15f, 0.15f, 0.85f, 0.85f); line(0.85f, 0.15f, 0.15f, 0.85f) }
        "remove" -> {
            drawCircle(color, 0.4f * m, Offset(0.5f * w, 0.5f * h), style = Stroke(sw))
            line(0.3f, 0.5f, 0.7f, 0.5f)
        }
        "nudge" -> {
            line(0.5f, 0.08f, 0.5f, 0.92f); line(0.08f, 0.5f, 0.92f, 0.5f)
            line(0.5f, 0.08f, 0.38f, 0.22f); line(0.5f, 0.08f, 0.62f, 0.22f)
            line(0.5f, 0.92f, 0.38f, 0.78f); line(0.5f, 0.92f, 0.62f, 0.78f)
            line(0.08f, 0.5f, 0.22f, 0.38f); line(0.08f, 0.5f, 0.22f, 0.62f)
            line(0.92f, 0.5f, 0.78f, 0.38f); line(0.92f, 0.5f, 0.78f, 0.62f)
        }
        "filter" -> {
            line(0.1f, 0.15f, 0.9f, 0.15f); line(0.1f, 0.15f, 0.42f, 0.55f); line(0.9f, 0.15f, 0.58f, 0.55f)
            line(0.42f, 0.55f, 0.42f, 0.9f); line(0.58f, 0.55f, 0.58f, 0.9f); line(0.42f, 0.9f, 0.58f, 0.9f)
        }
        else -> drawCircle(color, 0.3f * m, center, style = Stroke(sw))
    }
}

/** Bouton-icone miniature : visuel 32 dp, zone tactile 44 dp, appui long = libelle. */
@Composable
fun ToolButton(
    kind: String, label: String, modifier: Modifier = Modifier, active: Boolean = false,
    onHint: (String) -> Unit = {}, onClick: () -> Unit,
) {
    val click by rememberUpdatedState(onClick)
    val hint by rememberUpdatedState(onHint)
    Box(
        modifier.size(44.dp).pointerInput(Unit) {
            detectTapGestures(onTap = { click() }, onLongPress = { hint(label) })
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(32.dp)
                .background(if (active) VAccent.copy(alpha = 0.28f) else VSurface.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) { drawToolIcon(kind, if (active) VAccent else VText) }
    }
}
