package studio.cheng.daily.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Ink.text,
    display: Boolean = false,
    spacing: TextUnit = 0.sp,
    line: TextUnit = TextUnit.Unspecified,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = if (display) LocalDisplay.current else FontFamily.SansSerif,
            letterSpacing = spacing,
            lineHeight = line,
            textAlign = align ?: TextAlign.Unspecified,
            fontFeatureSettings = "tnum",
        ),
    )
}

@Composable
fun Seal(
    label: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "seal")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.dp, if (accent) Ink.bronze else Ink.line, RoundedCornerShape(2.dp))
            .background(if (accent) Color(0x22C6A36A) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = desc }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 14.sp, color = if (enabled) Ink.text else Ink.faint, display = accent)
    }
}

@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDesc: String,
    plusDesc: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Mini("−", minusDesc, onMinus)
        InkText(value, modifier = Modifier.padding(horizontal = 14.dp), size = 16.sp, display = true)
        Mini("+", plusDesc, onPlus)
    }
}

@Composable
private fun Mini(label: String, desc: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer {
                val s = if (pressed) 0.92f else 1f
                scaleX = s
                scaleY = s
            }
            .border(1.dp, Ink.line, CircleShape)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 18.sp)
    }
}

@Composable
fun VowLine(
    glyph: String,
    title: String,
    meta: String,
    value: String,
    progress: Float,
    desc: String,
    tint: Color = Ink.bronze,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = if (pressed) 8f else 0f }
            .clickable(interaction, null, onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkText(glyph, modifier = Modifier.width(36.dp), size = 26.sp, color = tint, display = true)
            Column(Modifier.weight(1f)) {
                InkText(title, size = 16.sp)
                InkText(meta, size = 12.sp, color = Ink.muted)
            }
            InkText(value, size = 18.sp, display = true)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line)) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(1.dp)
                    .background(tint),
            )
        }
    }
}

enum class Tab(val label: String) {
    Home("首页"),
    Tune("五行设置"),
    Self("个人"),
}

@Composable
fun IndexBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            IndexChip(tab = tab, selected = tab == current, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun IndexChip(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "打开${tab.label}" }
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        InkText(
            tab.label,
            size = 14.sp,
            color = if (selected) Ink.text else Ink.faint,
            display = selected,
        )
        Box(
            Modifier
                .padding(top = 5.dp)
                .height(1.dp)
                .width(if (selected) 18.dp else 0.dp)
                .background(Ink.bronze),
        )
    }
}

@Composable
fun PaneHeader(kicker: String, title: String, notice: String) {
    Column {
        InkText(kicker, size = 12.sp, color = Ink.muted, spacing = 3.sp)
        InkText(title, size = 34.sp, display = true)
        if (notice.isNotBlank()) {
            InkText(notice, modifier = Modifier.padding(top = 4.dp), size = 13.sp, color = Ink.bronze)
        }
    }
}
