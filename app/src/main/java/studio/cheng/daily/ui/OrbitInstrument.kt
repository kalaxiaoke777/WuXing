package studio.cheng.daily.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import studio.cheng.daily.data.OrbitMarks
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class V3(val x: Float, val y: Float, val z: Float)

private data class Seg(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val z: Float,
    val color: Color,
    val stroke: Float,
)

private data class Anchor(val index: Int, val at: Offset, val z: Float)

private data class Band(
    val index: Int,
    val glyph: String,
    val tilt: Float,
    val roll: Float,
    val color: Color,
    val progress: Float,
)

private data class Glyph(
    val index: Int,
    val text: String,
    val color: Color,
    val head: Triple<Float, Float, Float>,
    val label: Triple<Float, Float, Float>,
)

private class Gyro {
    var yaw = 0.45f
    var pitch = 0.78f
    var velYaw = 0.18f
    var velPitch = 0f
    var dragging = false
}

@Composable
fun OrbitInstrument(
    marks: OrbitMarks,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gyro = remember { Gyro() }
    val anchors = remember { ArrayList<Anchor>(4) }
    val onSelectState = rememberUpdatedState(onSelect)
    var frame by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val glyphStyle = TextStyle(fontFamily = LocalDisplay.current, fontSize = 18.sp, color = Ink.text)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { time ->
                if (last != 0L && !gyro.dragging) {
                    val dt = ((time - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    gyro.velYaw += (0.16f - gyro.velYaw) * dt * 0.85f
                    gyro.velPitch *= (1f - dt * 2.2f).coerceIn(0f, 1f)
                    gyro.yaw += gyro.velYaw * dt
                    gyro.pitch = (gyro.pitch + gyro.velPitch * dt).coerceIn(0.28f, 1.02f)
                }
                last = time
            }
            frame++
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "观测仪，可拖动，点环上的字进入事项" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = 0f
                    gyro.dragging = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - change.previousPosition
                        moved += kotlin.math.abs(delta.x) + kotlin.math.abs(delta.y)
                        if (moved > 8f) {
                            gyro.yaw += delta.x * 0.0052f
                            gyro.pitch = (gyro.pitch + delta.y * 0.0042f).coerceIn(0.28f, 1.02f)
                            gyro.velYaw = delta.x * 0.11f
                            gyro.velPitch = delta.y * 0.09f
                            change.consume()
                        }
                    }
                    gyro.dragging = false
                    if (moved < 14f) {
                        val hit = anchors.minByOrNull { (it.at - down.position).getDistance() }
                        if (hit != null && hit.z > 0.05f && (hit.at - down.position).getDistance() < 72f) {
                            onSelectState.value(hit.index)
                        }
                    }
                }
            },
    ) {
        val tick = frame
        if (tick < 0) return@Canvas
        val cx = size.width * 0.5f
        val cy = size.height * 0.46f
        val scale = min(size.width, size.height) * 0.30f
        anchors.clear()
        val segs = ArrayList<Seg>(640)

        fun project(v: V3): Triple<Float, Float, Float> {
            val spun = rotX(rotY(v, gyro.yaw), gyro.pitch)
            val depth = spun.z + 4.15f
            val s = scale * 2.35f / depth
            return Triple(cx + spun.x * s, cy - spun.y * s, spun.z)
        }

        fun push(a: V3, b: V3, color: Color, stroke: Float, lift: Float = 0f) {
            val pa = project(a)
            val pb = project(b)
            segs += Seg(pa.first, pa.second, pb.first, pb.second, (pa.third + pb.third) * 0.5f + lift, color, stroke)
        }

        fun ring(radius: Float, tilt: Float, roll: Float, steps: Int, color: Color, stroke: Float, until: Float = 1f, lift: Float = 0f) {
            val count = (steps * until).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                val t0 = (i / steps.toFloat()) * 6.2831855f
                val t1 = ((i + 1) / steps.toFloat()) * 6.2831855f
                push(ringPoint(t0, radius, tilt, roll), ringPoint(t1, radius, tilt, roll), color, stroke, lift)
            }
        }

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x55C6A36A), Color(0x00000000)),
                center = Offset(cx, cy),
                radius = scale * 1.55f,
            ),
            radius = scale * 1.55f,
            center = Offset(cx, cy),
        )
        drawOval(
            color = Color(0x33000000),
            topLeft = Offset(cx - scale * 0.72f, cy + scale * 0.55f),
            size = androidx.compose.ui.geometry.Size(scale * 1.44f, scale * 0.22f),
        )

        ring(1f, 1.5708f, 0f, 72, Ink.bronzeDeep.copy(alpha = 0.85f), 1.3f)
        ring(1f, 0f, 0f, 72, Ink.bronzeDeep.copy(alpha = 0.7f), 1.2f)
        ring(1f, 0.42f, 0.7f, 72, Ink.bronzeDeep.copy(alpha = 0.55f), 1.1f)
        push(V3(0f, -1.12f, 0f), V3(0f, 1.12f, 0f), Ink.bronzeDeep.copy(alpha = 0.8f), 1.2f)

        val bands = listOf(
            Band(0, "水", 0.32f, 0.35f, Ink.celadon, marks.water),
            Band(1, "火", 0.98f, 1.55f, Ink.cinnabar, marks.fire),
            Band(2, "金", 1.28f, 2.75f, Ink.moon, marks.metal),
            Band(3, "土", 0.55f, 3.95f, Ink.bronze, marks.earth),
            Band(4, "木", 1.12f, 5.15f, Ink.moss, marks.wood),
        )
        val glyphs = ArrayList<Glyph>(4)
        bands.forEach { band ->
            ring(1.34f, band.tilt, band.roll, 80, band.color.copy(alpha = 0.28f), 1.15f)
            if (band.progress > 0.02f) {
                ring(1.36f, band.tilt, band.roll, 80, band.color, 2.4f, band.progress, 0.03f)
            }
            val angle = band.progress.coerceAtLeast(0.02f) * 6.2831855f
            val slot = 0.55f + band.index * 1.2f
            val head = project(ringPoint(angle, 1.36f, band.tilt, band.roll))
            val label = project(ringPoint(slot, 1.74f, band.tilt, band.roll))
            glyphs += Glyph(band.index, band.glyph, band.color, head, label)
            anchors += Anchor(band.index, Offset(label.first, label.second), label.third)
        }

        fun paint(seg: Seg) {
            val fade = (0.28f + (seg.z + 1.15f) / 2.8f).coerceIn(0.16f, 1f)
            drawLine(
                color = seg.color.copy(alpha = seg.color.alpha * fade),
                start = Offset(seg.x1, seg.y1),
                end = Offset(seg.x2, seg.y2),
                strokeWidth = seg.stroke,
                cap = StrokeCap.Round,
            )
        }

        segs.sortBy { it.z }
        val pearl = project(V3(0f, 0f, 0f))
        segs.forEach { if (it.z < pearl.third) paint(it) }
        val pearlR = (scale * 0.22f).coerceAtLeast(10f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFF3E7D4), Color(0xFF8A6844), Color(0xFF2A2118)),
                center = Offset(pearl.first - pearlR * 0.28f, pearl.second - pearlR * 0.32f),
                radius = pearlR * 1.5f,
            ),
            radius = pearlR,
            center = Offset(pearl.first, pearl.second),
        )
        segs.forEach { if (it.z >= pearl.third) paint(it) }
        glyphs.forEach { glyph ->
            if (glyph.head.third > -0.25f) {
                drawCircle(glyph.color, radius = 4.2f, center = Offset(glyph.head.first, glyph.head.second))
            }
            val alpha = ((glyph.label.third + 0.9f) / 1.8f).coerceIn(0.55f, 1f)
            val layout = measurer.measure(glyph.text, glyphStyle)
            drawText(
                textLayoutResult = layout,
                color = Ink.text.copy(alpha = alpha),
                topLeft = Offset(
                    glyph.label.first - layout.size.width / 2f,
                    glyph.label.second - layout.size.height / 2f,
                ),
            )
        }
        cornerMarks()
    }
}

private fun ringPoint(theta: Float, radius: Float, tilt: Float, roll: Float): V3 {
    return rotZ(rotX(V3(cos(theta) * radius, sin(theta) * radius, 0f), tilt), roll)
}

private fun rotX(v: V3, a: Float): V3 {
    val c = cos(a)
    val s = sin(a)
    return V3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
}

private fun rotY(v: V3, a: Float): V3 {
    val c = cos(a)
    val s = sin(a)
    return V3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
}

private fun rotZ(v: V3, a: Float): V3 {
    val c = cos(a)
    val s = sin(a)
    return V3(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cornerMarks() {
    val i = 16f
    val l = 18f
    val c = Color(0xFF6E604E)
    val w = 1.2f
    val right = size.width - i
    val bottom = size.height - i
    drawLine(c, Offset(i, i), Offset(i + l, i), w)
    drawLine(c, Offset(i, i), Offset(i, i + l), w)
    drawLine(c, Offset(right, i), Offset(right - l, i), w)
    drawLine(c, Offset(right, i), Offset(right, i + l), w)
    drawLine(c, Offset(i, bottom), Offset(i + l, bottom), w)
    drawLine(c, Offset(i, bottom), Offset(i, bottom - l), w)
    drawLine(c, Offset(right, bottom), Offset(right - l, bottom), w)
    drawLine(c, Offset(right, bottom), Offset(right, bottom - l), w)
}
