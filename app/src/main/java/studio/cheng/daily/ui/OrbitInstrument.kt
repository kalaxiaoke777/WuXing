package studio.cheng.daily.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.Tide
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val phaseColors = listOf(Ink.moss, Ink.cinnabar, Ink.bronze, Ink.moon, Ink.celadon)

private data class Node(val index: Int, val at: Offset)

@Composable
fun CycleWheel(
    tides: List<Tide>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodes = remember { ArrayList<Node>(5) }
    val onSelectState = rememberUpdatedState(onSelect)
    var clock by remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer()
    val glyphStyle = TextStyle(fontFamily = LocalDisplay.current, fontSize = 20.sp, color = Ink.text)
    val nameStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = Ink.muted)
    val centerStyle = TextStyle(fontFamily = LocalDisplay.current, fontSize = 18.sp, color = Ink.paper)

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { clock = it }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "五行环，外环相生，内星相克，点字进入" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            val hit = nodes.minByOrNull { (it.at - down.position).getDistance() }
                            if (moved < 18f && hit != null && (hit.at - down.position).getDistance() < 78f) {
                                onSelectState.value(hit.index)
                            }
                            break
                        }
                        val delta = change.position - change.previousPosition
                        moved += kotlin.math.abs(delta.x) + kotlin.math.abs(delta.y)
                    }
                }
            },
    ) {
        val tick = clock
        if (tick < 0L) return@Canvas
        val flow = (tick % 9_000_000_000L) / 9_000_000_000f
        val pulse = sin(flow * 6.2831855f)
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val ring = min(size.width, size.height) * 0.24f
        nodes.clear()

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x44C6A36A), Color(0x00000000)),
                center = Offset(cx, cy),
                radius = ring * 1.7f,
            ),
            radius = ring * 1.7f,
            center = Offset(cx, cy),
        )
        drawCircle(Ink.bronzeDeep.copy(alpha = 0.55f), ring, Offset(cx, cy), style = Stroke(1.2f))
        drawCircle(Ink.line.copy(alpha = 0.7f), ring * 0.46f, Offset(cx, cy), style = Stroke(1f))

        fun at(index: Int, radius: Float): Offset {
            val rad = Math.toRadians((-90.0 + index * 72.0))
            return Offset(cx + cos(rad).toFloat() * radius, cy + sin(rad).toFloat() * radius)
        }

        repeat(5) { index ->
            val color = phaseColors[index]
            val tide = tides.getOrElse(index) { Tide.Empty }
            val alpha = when (tide) {
                Tide.Even -> 0.95f
                Tide.Low -> 0.38f
                Tide.High -> 0.5f
                Tide.Empty -> 0.16f
            }
            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = -90f + index * 72f + 8f,
                sweepAngle = 56f,
                useCenter = false,
                topLeft = Offset(cx - ring, cy - ring),
                size = Size(ring * 2f, ring * 2f),
                style = Stroke(if (tide == Tide.Even) 2.6f else 1.4f, cap = StrokeCap.Round),
            )
        }

        val head = at(0, ring).let {
            val rad = Math.toRadians((-90.0 + flow * 360.0))
            Offset(cx + cos(rad).toFloat() * ring, cy + sin(rad).toFloat() * ring)
        }
        drawCircle(Ink.paper, 5.5f, head)
        drawCircle(Ink.bronze.copy(alpha = 0.35f), 12f, head)

        repeat(5) { index ->
            val from = at(index, ring * 0.78f)
            val to = at((index + 2) % 5, ring * 0.78f)
            val warn = restrainWarn(tides, index)
            drawLine(
                color = if (warn) Ink.cinnabar.copy(alpha = 0.85f) else Ink.bronzeDeep.copy(alpha = 0.45f),
                start = from,
                end = to,
                strokeWidth = if (warn) 2.2f else 1f,
                cap = StrokeCap.Round,
            )
        }

        val centerTop = measurer.measure("修身", centerStyle)
        val centerBot = measurer.measure("养性", centerStyle)
        drawText(
            centerTop,
            Ink.paper,
            Offset(cx - centerTop.size.width / 2f, cy - centerTop.size.height - 1f),
        )
        drawText(
            centerBot,
            Ink.bronze,
            Offset(cx - centerBot.size.width / 2f, cy + 2f),
        )

        Phase.entries.forEachIndexed { index, phase ->
            val tide = tides.getOrElse(index) { Tide.Empty }
            val wobble = if (tide == Tide.High) 3f * pulse else 0f
            val point = at(index, ring)
            nodes += Node(index, point)
            val color = phaseColors[index]
            val radius = 16f + wobble + if (tide == Tide.Even) 2f else 0f
            drawCircle(Color(0xFF100E0C), radius + 3f, point)
            drawCircle(color.copy(alpha = if (tide == Tide.Empty) 0.35f else 0.95f), radius, point, style = Stroke(1.6f))
            if (tide == Tide.Even || tide == Tide.High) {
                drawCircle(color.copy(alpha = 0.22f), radius * 0.72f, point)
            }
            val glyph = measurer.measure(phase.glyph, glyphStyle)
            drawText(
                glyph,
                color = if (tide == Tide.Empty) Ink.faint else Ink.text,
                topLeft = Offset(point.x - glyph.size.width / 2f, point.y - glyph.size.height / 2f - 1f),
            )
            val name = measurer.measure(phase.practice, nameStyle)
            val nameAt = at(index, ring + 92f)
            drawText(
                name,
                color = if (tide == Tide.Empty) Ink.faint else Ink.muted,
                topLeft = Offset(nameAt.x - name.size.width / 2f, nameAt.y - name.size.height / 2f),
            )
        }
    }
}

private fun restrainWarn(tides: List<Tide>, index: Int): Boolean {
    val controller = tides.getOrElse(index) { Tide.Empty }
    val target = tides.getOrElse((index + 2) % 5) { Tide.Empty }
    if (controller == Tide.High) return true
    return target == Tide.High && controller != Tide.Even
}
