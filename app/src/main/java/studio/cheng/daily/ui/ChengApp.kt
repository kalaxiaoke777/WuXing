package studio.cheng.daily.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.cheng.daily.data.Phase
import studio.cheng.daily.device.CloudApi
import studio.cheng.daily.device.Session
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
    var tune by remember { mutableStateOf<Phase?>(null) }
    var ledger by remember { mutableStateOf(false) }
    var figure by remember { mutableStateOf<Ancient?>(null) }
    var rank by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) {
        Session.restore(context)
        var job: kotlinx.coroutines.Job? = null
        store.onLocalChange = {
            job?.cancel()
            job = scope.launch {
                delay(800)
                runCatching { CloudApi.push(context, store) }
            }
        }
    }
    val activity = LocalContext.current as Activity
    BackHandler {
        when {
            drawer -> drawer = false
            phase != null -> phase = null
            ledger -> ledger = false
            figure != null && tab == Tab.Ancient -> figure = null
            tune != null && tab == Tab.Tune -> tune = null
            rank -> rank = false
            else -> activity.finish()
        }
    }
    ChengTheme {
        Box(Modifier.fillMaxSize().background(Ink.bg)) {
            Atmosphere()
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                TopMark(onDate = { ledger = true }, onMenu = { drawer = true })
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val open = phase
                    val tuning = tune
                    if (ledger) {
                        LedgerBody(state) { ledger = false }
                    } else if (open != null) {
                        PhaseBody(open, state, store) { phase = null }
                    } else if (figure != null && tab == Tab.Ancient) {
                        AncientBody(figure!!) { figure = null }
                    } else if (tuning != null && tab == Tab.Tune) {
                        TuneBody(tuning, state, store) { tune = null }
                    } else if (rank && tab == Tab.Self) {
                        RankPane { rank = false }
                    } else {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(160)) },
                            label = "tab",
                        ) { current ->
                            when (current) {
                                Tab.Home -> HomePane(state) { phase = it }
                                Tab.Ancient -> AncientIndex { figure = it }
                                Tab.Tune -> TuneIndex(state) { tune = it }
                                Tab.Self -> SelfPane(state, store) { rank = true }
                            }
                        }
                    }
                    PhaseDrawer(
                        open = drawer,
                        state = state,
                        onClose = { drawer = false },
                        onOpen = {
                            drawer = false
                            ledger = false
                            phase = it
                        },
                    )
                    if (!drawer && open == null && !ledger && tune == null && tab == Tab.Home) {
                        DrawerHandle { drawer = true }
                    }
                }
                IndexBar(tab) {
                    tab = it
                    phase = null
                    tune = null
                    ledger = false
                    figure = null
                    rank = false
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
private fun TopMark(onDate: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkText("五行养生", size = 22.sp, display = true)
        Box(Modifier.weight(1f))
        InkText(
            formatToday(),
            modifier = Modifier
                .clickable(
                    remember { MutableInteractionSource() },
                    null,
                    role = Role.Button,
                    onClick = onDate,
                )
                .semantics { contentDescription = "打开日历" }
                .padding(vertical = 6.dp),
            size = 13.sp,
            color = Ink.bronze,
        )
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
                Phase.Wood -> WoodPane(state, store)
                Phase.Fire -> FirePane(state, store)
                Phase.Earth -> MealPane(state, store)
                Phase.Metal -> SleepPane(state, store)
                Phase.Water -> DesirePane(state, store)
            }
        }
    }
}

@Composable
private fun TuneBody(
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
            Seal("收回", "回到五行设置", onBack)
            Column(Modifier.padding(start = 14.dp)) {
                InkText("${phase.glyph} · 设置", size = 16.sp, color = phase.tint(), display = true)
                InkText(phase.office, modifier = Modifier.padding(top = 2.dp), size = 12.sp, color = Ink.muted)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PhaseTune(phase, state, store)
        }
    }
}

@Composable
private fun LedgerBody(state: RitualState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Seal("收回", "回到首页", onBack)
            InkText("日历", modifier = Modifier.padding(start = 14.dp), size = 16.sp, display = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LedgerPane(state)
        }
    }
}
