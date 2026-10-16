package studio.cheng.daily.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.formatToday
import kotlin.math.max

@Composable
fun ChengApp() {
    val context = LocalContext.current
    val store = remember { RitualStore(context.applicationContext) }
    val state by store.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Home) }
    var phase by remember { mutableStateOf<Phase?>(null) }
    var drawer by remember { mutableStateOf(false) }
    ChengTheme {
        Box(Modifier.fillMaxSize().background(Ink.bg)) {
            Atmosphere()
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                TopMark(onMenu = { drawer = true })
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val open = phase
                    if (open == null) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(160)) },
                            label = "tab",
                        ) { current ->
                            when (current) {
                                Tab.Home -> HomePane(state) { phase = it }
                                Tab.Tune -> TunePane(state, store)
                                Tab.Self -> SelfPane(state, store)
                            }
                        }
                    } else {
                        PhaseBody(open, state, store) { phase = null }
                    }
                    PhaseDrawer(
                        open = drawer,
                        state = state,
                        onClose = { drawer = false },
                        onOpen = {
                            drawer = false
                            phase = it
                        },
                    )
                    if (!drawer && open == null && tab == Tab.Home) {
                        DrawerHandle { drawer = true }
                    }
                }
                IndexBar(tab) {
                    tab = it
                    phase = null
                    drawer = false
                }
            }
        }
    }
}

@Composable
private fun Atmosphere() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF241E18), Color(0xFF100E0C)),
                    center = Offset(w * 0.3f, h * 0.42f),
                    radius = max(w, h) * 0.72f,
                ),
            ),
        )
    }
}

@Composable
private fun TopMark(onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("澄", size = 34.sp, display = true)
        InkText(
            "五行观测",
            modifier = Modifier.padding(start = 12.dp),
            size = 13.sp,
            color = Ink.muted,
            spacing = 2.sp,
        )
        Box(Modifier.weight(1f))
        InkText(formatToday(), size = 13.sp, color = Ink.muted)
        Box(Modifier.width(8.dp))
        Box(
            Modifier
                .clickable(
                    remember { MutableInteractionSource() },
                    null,
                    role = Role.Button,
                    onClick = onMenu,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "打开五行菜单"
                }
                .padding(6.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(16.dp).height(1.dp).background(Ink.bronze))
                Box(Modifier.padding(top = 4.dp).width(10.dp).height(1.dp).background(Ink.bronze))
                Box(Modifier.padding(top = 4.dp).width(16.dp).height(1.dp).background(Ink.bronze))
            }
        }
    }
}

@Composable
private fun PhaseBody(
    phase: Phase,
    state: RitualState,
    store: RitualStore,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Seal("收回", "回到首页观测", onBack)
            Column(Modifier.padding(start = 14.dp)) {
                InkText("${phase.glyph} · ${phase.practice}", size = 16.sp, color = phase.tint(), display = true)
                InkText(phase.verse, modifier = Modifier.padding(top = 2.dp), size = 12.sp, color = Ink.muted, line = 18.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (phase) {
                Phase.Water -> WaterPane(state, store)
                Phase.Fire -> FirePane(state, store)
                Phase.Metal -> SleepPane(state, store)
                Phase.Earth -> MealPane(state, store)
                Phase.Wood -> StrengthPane(state, store)
            }
        }
    }
}
