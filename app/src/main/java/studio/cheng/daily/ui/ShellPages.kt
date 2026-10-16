package studio.cheng.daily.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.branchDone
import studio.cheng.daily.data.clock
import studio.cheng.daily.data.moodMarks
import studio.cheng.daily.data.orbitMarks
import studio.cheng.daily.data.phaseNow
import studio.cheng.daily.data.phaseOf
import studio.cheng.daily.data.phaseWeek
import studio.cheng.daily.data.todayKey
import studio.cheng.daily.data.todayReading

fun Phase.tint(): Color = when (this) {
    Phase.Water -> Ink.celadon
    Phase.Fire -> Ink.cinnabar
    Phase.Metal -> Ink.moon
    Phase.Earth -> Ink.bronze
    Phase.Wood -> Ink.moss
}

@Composable
fun HomePane(state: RitualState, onOpen: (Phase) -> Unit) {
    val rows = phaseNow(state)
    val week = phaseWeek(state)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            OrbitInstrument(
                marks = orbitMarks(state),
                onSelect = { index -> onOpen(Phase.entries[index]) },
                modifier = Modifier.fillMaxSize(),
            )
            InkText(
                "拖动观测仪",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                size = 11.sp,
                color = Ink.faint,
                spacing = 2.sp,
            )
        }
        Column(Modifier.padding(horizontal = 26.dp, vertical = 6.dp)) {
            InkText(todayReading(state), size = 13.sp, color = Ink.bronze, line = 20.sp)
            InkText("今日", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
            rows.forEach { row ->
                VowLine(
                    glyph = row.phase.glyph,
                    title = row.phase.practice,
                    meta = row.phase.quote,
                    value = row.value,
                    progress = row.progress,
                    desc = "打开${row.phase.practice}",
                    tint = row.phase.tint(),
                ) { onOpen(row.phase) }
                val branches = state.branches.filter { it.phase == row.phase.name }
                branches.forEach { branch ->
                    val done = branchDone(state, branch)
                    InkText(
                        "└  ${branch.title}   $done / ${branch.weekly}",
                        modifier = Modifier.padding(start = 36.dp, top = 6.dp),
                        size = 12.sp,
                        color = Ink.faint,
                    )
                }
            }
            InkText("本周", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                week.forEachIndexed { index, mark ->
                    WeekCoin(mark.phase, mark.done, mark.goal, index)
                }
            }
        }
    }
}

@Composable
private fun WeekCoin(phase: Phase, done: Int, goal: Int, index: Int) {
    val filled = goal > 0 && done >= goal
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .graphicsLayer {
                    rotationX = 58f
                    rotationY = (index - 2) * 8f
                    cameraDistance = 10f * density
                }
                .width(46.dp)
                .height(46.dp)
                .border(1.dp, phase.tint(), CircleShape)
                .background(if (filled) phase.tint().copy(alpha = 0.85f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            InkText(
                phase.glyph,
                size = 18.sp,
                color = if (filled) Ink.bg else phase.tint(),
                display = true,
            )
        }
        InkText("$done/$goal", modifier = Modifier.padding(top = 8.dp), size = 12.sp, color = Ink.muted)
        InkText(phase.practice, size = 11.sp, color = Ink.faint)
    }
}

@Composable
fun DrawerHandle(onOpen: () -> Unit) {
    Column(
        Modifier
            .padding(top = 18.dp)
            .width(28.dp)
            .clickable(
                remember { MutableInteractionSource() },
                null,
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics { contentDescription = "打开五行菜单" }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InkText("五", size = 12.sp, color = Ink.bronze, display = true)
        InkText("行", modifier = Modifier.padding(top = 2.dp), size = 12.sp, color = Ink.bronze, display = true)
    }
}

@Composable
fun PhaseDrawer(
    open: Boolean,
    state: RitualState,
    onClose: () -> Unit,
    onOpen: (Phase) -> Unit,
) {
    val shift by animateFloatAsState(if (open) 0f else 1f, tween(320), label = "drawer")
    if (!open && shift > 0.98f) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f * (1f - shift)))
                .clickable(
                    remember { MutableInteractionSource() },
                    null,
                    onClick = onClose,
                )
                .semantics { contentDescription = "关闭五行菜单" },
        )
        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.86f)
                .graphicsLayer { translationX = -shift * 900f }
                .background(Color(0xF2100E0C))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            InkText("五行", size = 28.sp, display = true)
            InkText("点一行，进入它的日常。", modifier = Modifier.padding(top = 4.dp, bottom = 8.dp), size = 12.sp, color = Ink.faint)
            Phase.entries.forEach { phase ->
                val reading = phaseNow(state).first { it.phase == phase }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clickable(
                            remember { MutableInteractionSource() },
                            null,
                            role = Role.Button,
                            onClick = { onOpen(phase) },
                        )
                        .semantics { contentDescription = "打开${phase.practice}" },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InkText(phase.glyph, size = 32.sp, color = phase.tint(), display = true)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            InkText(phase.practice, size = 16.sp)
                            InkText(phase.quote, size = 12.sp, color = Ink.muted)
                        }
                        InkText(reading.value, size = 14.sp, color = phase.tint(), display = true)
                    }
                    InkText(
                        phase.verse,
                        modifier = Modifier.padding(top = 6.dp),
                        size = 12.sp,
                        color = Ink.faint,
                        line = 18.sp,
                    )
                }
                state.branches.filter { it.phase == phase.name }.forEach { branch ->
                    val done = branchDone(state, branch)
                    Row(
                        Modifier.padding(start = 18.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InkText("└  ${branch.title}", modifier = Modifier.weight(1f), size = 13.sp, color = Ink.muted)
                        InkText("$done/${branch.weekly}", size = 12.sp, color = Ink.faint)
                    }
                }
            }
        }
    }
}

@Composable
fun TunePane(state: RitualState, store: RitualStore) {
    var host by remember { mutableStateOf(Phase.Wood) }
    var title by remember { mutableStateOf("") }
    var weekly by remember { mutableIntStateOf(3) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("五行设置", "参数", state.notice)
        InkText(
            "五行是固定的日常。分支挂在某一行下面，按周计次。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        TuneBlock(Phase.Water, "每日 ${state.waterGoalMl} ml") {
            Stepper(
                value = "${state.waterGoalMl} ml",
                onMinus = { store.setWaterGoal(state.waterGoalMl - 250) },
                onPlus = { store.setWaterGoal(state.waterGoalMl + 250) },
                minusDesc = "降低饮水目标",
                plusDesc = "提高饮水目标",
            )
        }
        TuneBlock(Phase.Fire, "每日 ${state.moodGoal} 次") {
            Stepper(
                value = "${state.moodGoal} 次",
                onMinus = { store.setMoodGoal(state.moodGoal - 1) },
                onPlus = { store.setMoodGoal(state.moodGoal + 1) },
                minusDesc = "降低情绪次数",
                plusDesc = "提高情绪次数",
            )
        }
        TuneBlock(Phase.Metal, "约定 ${clock(state.sleepHour, state.sleepMinute)}") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper(
                    value = "%02d".format(state.sleepHour),
                    onMinus = { store.setSleep(state.sleepHour - 1, state.sleepMinute) },
                    onPlus = { store.setSleep(state.sleepHour + 1, state.sleepMinute) },
                    minusDesc = "入眠提前一小时",
                    plusDesc = "入眠推后一小时",
                )
                Stepper(
                    value = "%02d".format(state.sleepMinute),
                    onMinus = { shiftSleep(store, state, -5) },
                    onPlus = { shiftSleep(store, state, 5) },
                    minusDesc = "入眠提前五分钟",
                    plusDesc = "入眠推后五分钟",
                )
            }
        }
        TuneBlock(Phase.Earth, "每日 ${state.mealGoal} 餐") {
            Stepper(
                value = "${state.mealGoal} 餐",
                onMinus = { store.setMealGoal(state.mealGoal - 1) },
                onPlus = { store.setMealGoal(state.mealGoal + 1) },
                minusDesc = "降低每日餐数",
                plusDesc = "提高每日餐数",
            )
        }
        TuneBlock(Phase.Wood, "每周 ${state.strengthGoal} 次") {
            Stepper(
                value = "${state.strengthGoal} 次",
                onMinus = { store.setStrengthGoal(state.strengthGoal - 1) },
                onPlus = { store.setStrengthGoal(state.strengthGoal + 1) },
                minusDesc = "降低每周次数",
                plusDesc = "提高每周次数",
            )
        }
        InkText("新的分支", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase.entries.forEach { phase ->
                val on = phase == host
                Box(
                    Modifier
                        .border(1.dp, if (on) phase.tint() else Ink.line, CircleShape)
                        .background(if (on) phase.tint().copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                        .clickable(
                            remember { MutableInteractionSource() },
                            null,
                            role = Role.Button,
                            onClick = { host = phase },
                        )
                        .semantics { contentDescription = "分支挂在${phase.practice}" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    InkText(phase.glyph, size = 16.sp, color = if (on) phase.tint() else Ink.muted, display = true)
                }
            }
        }
        BasicTextField(
            value = title,
            onValueChange = { title = it.take(16) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            textStyle = TextStyle(color = Ink.text, fontSize = 16.sp, fontFamily = FontFamily.SansSerif),
            cursorBrush = SolidColor(Ink.bronze),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Ink.line)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (title.isEmpty()) InkText("例如：晨间拉伸", size = 14.sp, color = Ink.faint)
                    inner()
                }
            },
        )
        Stepper(
            value = "每周 $weekly 次",
            onMinus = { weekly = (weekly - 1).coerceAtLeast(1) },
            onPlus = { weekly = (weekly + 1).coerceAtMost(7) },
            minusDesc = "降低分支次数",
            plusDesc = "提高分支次数",
            modifier = Modifier.padding(top = 10.dp),
        )
        Seal(
            "接入分支",
            "接入这条分支",
            {
                store.addBranch(host, title, weekly)
                title = ""
            },
            modifier = Modifier.padding(top = 10.dp),
            accent = true,
            enabled = title.isNotBlank(),
        )
        state.branches.forEach { branch ->
            val phase = phaseOf(branch.phase) ?: return@forEach
            val done = branchDone(state, branch)
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkText(phase.glyph, size = 18.sp, color = phase.tint(), display = true)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    InkText(branch.title, size = 15.sp)
                    InkText("本周 $done / ${branch.weekly}", size = 12.sp, color = Ink.muted)
                }
                Seal("撤下", "撤下分支${branch.title}", { store.removeBranch(branch.id) })
            }
        }
        Box(Modifier.height(18.dp))
    }
}

@Composable
private fun TuneBlock(phase: Phase, meta: String, control: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkText(phase.glyph, size = 22.sp, color = phase.tint(), display = true)
            Column(Modifier.padding(start = 10.dp)) {
                InkText(phase.practice, size = 15.sp)
                InkText(meta, size = 12.sp, color = Ink.muted)
            }
        }
        InkText(phase.verse, modifier = Modifier.padding(top = 6.dp), size = 12.sp, color = Ink.faint, line = 18.sp)
        Box(Modifier.padding(top = 8.dp)) { control() }
    }
}

@Composable
fun SelfPane(state: RitualState, store: RitualStore) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("个人", if (state.name.isBlank()) "未署名" else state.name, state.notice)
        InkText("称呼", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        BasicTextField(
            value = state.name,
            onValueChange = store::setName,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textStyle = TextStyle(color = Ink.text, fontSize = 18.sp, fontFamily = LocalDisplay.current),
            cursorBrush = SolidColor(Ink.bronze),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Ink.line)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (state.name.isEmpty()) InkText("怎么称呼", size = 14.sp, color = Ink.faint)
                    inner()
                }
            },
        )
        InkText("一周从哪天算起", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("周一", "一周从周一开始", { store.setMondayWeek(true) }, accent = state.mondayWeek)
            Seal("周日", "一周从周日开始", { store.setMondayWeek(false) }, accent = !state.mondayWeek)
        }
        InkText("关于", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        InkText("澄  0.1.0", modifier = Modifier.padding(top = 8.dp), size = 20.sp, display = true)
        InkText(
            "日常的五行观测。账户和同步以后再接进来。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
    }
}

@Composable
fun FirePane(state: RitualState, store: RitualStore) {
    val today = state.moodsByDay[todayKey()].orEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("情绪", if (today.isEmpty()) "尚未看见" else "今日 ${today.size} / ${state.moodGoal}", state.notice)
        InkText(
            "记下的是此刻的火，不是好坏。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            moodMarks.forEachIndexed { index, mark ->
                val on = today.lastOrNull() == index
                Box(
                    Modifier
                        .graphicsLayer {
                            rotationY = (index - 1.5f) * 14f
                            rotationX = 12f
                            cameraDistance = 8f * density
                        }
                        .border(1.dp, if (on) Ink.cinnabar else Ink.line, CircleShape)
                        .background(if (on) Ink.cinnabar.copy(alpha = 0.85f) else Color.Transparent, CircleShape)
                        .clickable(
                            remember { MutableInteractionSource() },
                            null,
                            role = Role.Button,
                            onClick = { store.markMood(index) },
                        )
                        .semantics { contentDescription = "记下情绪$mark" }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    InkText(mark, size = 20.sp, color = if (on) Ink.bg else Ink.text, display = true)
                }
            }
        }
        if (today.isNotEmpty()) {
            InkText(
                "今日  " + today.joinToString("  ") { moodMarks.getOrElse(it) { "·" } },
                modifier = Modifier.padding(top = 14.dp),
                size = 14.sp,
                color = Ink.cinnabar,
            )
            Seal("撤回", "撤回一次情绪", store::undoMood, Modifier.padding(top = 10.dp))
        }
        InkText("每日次数", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted)
        Stepper(
            value = "${state.moodGoal} 次",
            onMinus = { store.setMoodGoal(state.moodGoal - 1) },
            onPlus = { store.setMoodGoal(state.moodGoal + 1) },
            minusDesc = "降低情绪次数",
            plusDesc = "提高情绪次数",
            modifier = Modifier.padding(top = 8.dp),
        )
        BranchRail(Phase.Fire, state, store)
    }
}

private fun shiftSleep(store: RitualStore, state: RitualState, delta: Int) {
    var minute = state.sleepMinute + delta
    var hour = state.sleepHour
    while (minute >= 60) {
        minute -= 60
        hour = (hour + 1).mod(24)
    }
    while (minute < 0) {
        minute += 60
        hour = (hour - 1).mod(24)
    }
    store.setSleep(hour, minute)
}

@Composable
fun BranchRail(phase: Phase, state: RitualState, store: RitualStore) {
    val branches = state.branches.filter { it.phase == phase.name }
    if (branches.isEmpty()) return
    InkText("分支", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
    branches.forEach { branch ->
        val done = branchDone(state, branch)
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                InkText(branch.title, size = 15.sp)
                InkText("本周 $done / ${branch.weekly}", size = 12.sp, color = Ink.muted)
            }
            Seal("记一笔", "记下分支${branch.title}", { store.checkBranch(branch.id) }, accent = true)
            if (done > 0) {
                Seal("撤回", "撤回分支${branch.title}", { store.undoBranch(branch.id) }, Modifier.padding(start = 8.dp))
            }
        }
    }
    Box(Modifier.height(12.dp))
}
