package fr.volaracing.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bandeau illustre « sport outdoor » dessine en vectoriel : montagnes, neige, sapins, piste orange. */
@Composable
fun HeroBanner(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6FC3F0), Color(0xFFCDEAFA), Color(0xFFFFE2BF))))
            drawCircle(Color(0xFFFFF3D1), 20.dp.toPx(), Offset(w * 0.82f, h * 0.26f))

            fun ridge(color: Color, pts: List<Pair<Float, Float>>) {
                val p = Path()
                p.moveTo(0f, h)
                p.lineTo(0f, pts.first().second * h)
                for (q in pts) p.lineTo(q.first * w, q.second * h)
                p.lineTo(w, h)
                p.close()
                drawPath(p, color)
            }
            fun cap(cx: Float, top: Float) {
                val p = Path()
                p.moveTo(cx * w, top * h)
                p.lineTo((cx - 0.035f) * w, (top + 0.09f) * h)
                p.lineTo((cx - 0.01f) * w, (top + 0.075f) * h)
                p.lineTo((cx + 0.012f) * w, (top + 0.10f) * h)
                p.lineTo((cx + 0.035f) * w, (top + 0.09f) * h)
                p.close()
                drawPath(p, Color.White)
            }
            fun pine(cx: Float, base: Float, ph: Float) {
                val p = Path()
                p.moveTo(cx * w, (base - ph) * h)
                p.lineTo((cx - 0.018f) * w, base * h)
                p.lineTo((cx + 0.018f) * w, base * h)
                p.close()
                drawPath(p, Color(0xFF16382B))
            }

            ridge(Color(0xFF9DB9D3), listOf(0.00f to 0.56f, 0.16f to 0.36f, 0.30f to 0.50f, 0.46f to 0.24f, 0.64f to 0.50f, 0.80f to 0.34f, 1.00f to 0.52f))
            cap(0.16f, 0.36f); cap(0.46f, 0.24f); cap(0.80f, 0.34f)
            ridge(Color(0xFF4F7A9C), listOf(0.00f to 0.72f, 0.15f to 0.56f, 0.36f to 0.74f, 0.56f to 0.52f, 0.76f to 0.72f, 0.90f to 0.60f, 1.00f to 0.68f))
            ridge(Color(0xFF1F4D3A), listOf(0.00f to 0.88f, 0.20f to 0.74f, 0.42f to 0.88f, 0.62f to 0.76f, 0.82f to 0.90f, 1.00f to 0.80f))

            // piste qui descend : depart blanc et trait orange en pointilles
            val tr = Path()
            tr.moveTo(0.50f * w, 0.56f * h)
            tr.cubicTo(0.40f * w, 0.62f * h, 0.62f * w, 0.68f * h, 0.50f * w, 0.76f * h)
            tr.cubicTo(0.40f * w, 0.82f * h, 0.66f * w, 0.86f * h, 0.56f * w, 0.97f * h)
            drawPath(
                tr, VOrange,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)),
            )
            drawCircle(Color.White, 5.dp.toPx(), Offset(0.50f * w, 0.56f * h))
            drawCircle(VOrange, 3.dp.toPx(), Offset(0.50f * w, 0.56f * h))

            for (x in listOf(0.06f, 0.11f, 0.17f, 0.27f, 0.33f, 0.70f, 0.77f, 0.86f, 0.93f)) pine(x, 0.95f, 0.16f)

            // voile sombre en bas : lisibilite du texte
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)), startY = h * 0.45f, endY = h),
                topLeft = Offset(0f, h * 0.45f), size = Size(w, h * 0.55f),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(title, color = Color.White, fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(subtitle, color = Color.White, fontSize = 13.sp)
        }
    }
}
