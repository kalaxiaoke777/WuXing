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
import studio.cheng.daily.data.bmr
import studio.cheng.daily.data.branchDone
import studio.cheng.daily.data.cycleReading
import studio.cheng.daily.data.fireHint
import studio.cheng.daily.data.fireMarks
import studio.cheng.daily.data.intakeTarget
import studio.cheng.daily.data.phaseNow
import studio.cheng.daily.data.phaseOf
import studio.cheng.daily.data.phaseWeek
import studio.cheng.daily.data.tenthText
import studio.cheng.daily.data.todayKey

fun Phase.tint(): Color = when (this) {
    Phase.Wood -> Ink.moss
    Phase.Fire -> Ink.cinnabar
    Phase.Earth -> Ink.bronze
    Phase.Metal -> Ink.moon
    Phase.Water -> Ink.celadon
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
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            CycleWheel(
                tides = rows.map { it.tide },
                onSelect = { index -> onOpen(Phase.entries[index]) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.padding(horizontal = 26.dp, vertical = 4.dp)) {
            InkText("外环相生 · 内星相克", size = 11.sp, color = Ink.faint, spacing = 2.sp)
            InkText(cycleReading(state), modifier = Modifier.padding(top = 8.dp), size = 13.sp, color = Ink.bronze, line = 20.sp)
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
                state.branches.filter { it.phase == row.phase.name }.forEach { branch ->
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
            }
        }
    }
}

@Composable
fun FirePane(state: RitualState, store: RitualStore) {
    val today = state.moodsByDay[todayKey()].orEmpty()
    val code = today.lastOrNull()
    val tended = todayKey() in state.fireTended
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("情绪", if (code == null) "尚未看见" else fireMarks[code], state.notice)
        InkText(
            "只看此刻的火势，不评好坏。弱是提不起，旺是烦怒，思是停不下来。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fireMarks.forEachIndexed { index, mark ->
                val on = code == index
                val tint = when (index) {
                    0 -> Ink.faint
                    1 -> Ink.celadon
                    2 -> Ink.cinnabar
                    else -> Ink.moon
                }
                Box(
                    Modifier
                        .graphicsLayer {
                            rotationY = (index - 1.5f) * 12f
                            rotationX = 10f
                            cameraDistance = 8f * density
                        }
                        .border(1.dp, if (on) tint else Ink.line, CircleShape)
                        .background(if (on) tint.copy(alpha = 0.85f) else Color.Transparent, CircleShape)
                        .clickable(
                            remember { MutableInteractionSource() },
                            null,
                            role = Role.Button,
                            onClick = { store.markMood(index) },
                        )
                        .semantics { contentDescription = "观火$mark" }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    InkText(mark, size = 20.sp, color = if (on) Ink.bg else Ink.text, display = true)
                }
            }
        }
        if (code != null) {
            InkText(fireHint(code), modifier = Modifier.padding(top = 14.dp), size = 14.sp, color = Ink.cinnabar, line = 22.sp)
            if (code == 3 && state.noteWhenThink) {
                BasicTextField(
                    value = state.fireNotes[todayKey()].orEmpty(),
                    onValueChange = store::setFireNote,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
                            if (state.fireNotes[todayKey()].isNullOrEmpty()) {
                                InkText("把那件事写成一句", size = 14.sp, color = Ink.faint)
                            }
                            inner()
                        }
                    },
                )
            }
            if (code != 1) {
                Seal(
                    if (tended) "已照做" else "照做了",
                    "做完这一次收束",
                    store::tendFire,
                    Modifier.padding(top = 10.dp),
                    accent = !tended,
                    enabled = !tended,
                )
            }
            if (today.isNotEmpty()) {
                InkText(
                    "今日  " + today.joinToString("  ") { fireMarks.getOrElse(it) { "·" } },
                    modifier = Modifier.padding(top = 12.dp),
                    size = 13.sp,
                    color = Ink.muted,
                )
                Seal("撤回", "撤回一次观火", store::undoMood, Modifier.padding(top = 8.dp))
            }
        }
        BranchRail(Phase.Fire, state, store)
    }
}

@Composable
fun SelfPane(state: RitualState, store: RitualStore) {
    val basal = bmr(state)
    val target = intakeTarget(state)
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
        InkText("身体", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        InkText(
            "身高、体重、体脂和年龄用来估基础代谢。土和木的热量差都从这里来。",
            modifier = Modifier.padding(top = 6.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        InkText("性别", modifier = Modifier.padding(top = 14.dp), size = 12.sp, color = Ink.muted)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("男", "按男性估算", { store.setMale(true) }, accent = state.male)
            Seal("女", "按女性估算", { store.setMale(false) }, accent = !state.male)
        }
        BodyStep("身高", "${state.heightCm} cm", { store.setHeight(state.heightCm - 1) }, { store.setHeight(state.heightCm + 1) }, "降低身高", "提高身高")
        BodyStep("体重", "${tenthText(state.weightTenth)} kg", { store.setWeight(state.weightTenth - 5) }, { store.setWeight(state.weightTenth + 5) }, "降低体重", "提高体重")
        BodyStep(
            "体脂",
            if (state.fatTenth == 0) "未填" else "${tenthText(state.fatTenth)}%",
            { store.setFat(if (state.fatTenth <= 50) 0 else state.fatTenth - 5) },
            { store.setFat(if (state.fatTenth == 0) 180 else state.fatTenth + 5) },
            "降低体脂",
            "提高体脂",
        )
        BodyStep("年龄", "${state.age}", { store.setAge(state.age - 1) }, { store.setAge(state.age + 1) }, "降低年龄", "提高年龄")
        InkText("基础代谢", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        InkText(
            if (state.bmrManual > 0) "正在用你填的数" else if (state.fatTenth >= 50) "按瘦体重估算 $basal kcal" else "按身高体重估算 $basal kcal",
            modifier = Modifier.padding(top = 6.dp),
            size = 13.sp,
            color = Ink.faint,
        )
        Stepper(
            value = if (state.bmrManual == 0) "估算 $basal" else "${state.bmrManual} kcal",
            onMinus = { store.setBmrManual(if (state.bmrManual == 0) basal - 50 else state.bmrManual - 50) },
            onPlus = { store.setBmrManual(if (state.bmrManual == 0) basal + 50 else state.bmrManual + 50) },
            minusDesc = "降低基础代谢",
            plusDesc = "提高基础代谢",
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.bmrManual > 0) {
            Seal("改回估算", "基础代谢改回估算", { store.setBmrManual(0) }, Modifier.padding(top = 8.dp))
        }
        InkText(
            "当前${studio.cheng.daily.data.seasonLabel(state.season)}，建议摄入约 $target kcal。阶段在土的设置里改。",
            modifier = Modifier.padding(top = 10.dp),
            size = 13.sp,
            color = Ink.bronze,
            line = 20.sp,
        )
        InkText("一周从哪天算起", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("周一", "一周从周一开始", { store.setMondayWeek(true) }, accent = state.mondayWeek)
            Seal("周日", "一周从周日开始", { store.setMondayWeek(false) }, accent = !state.mondayWeek)
        }
        InkText("关于", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
        InkText("澄  0.1.0", modifier = Modifier.padding(top = 8.dp), size = 20.sp, display = true)
        InkText(
            "五行养生。运动养情绪，情绪助消化，饮食养睡眠，睡眠敛欲望，精气再撑起运动。",
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
    }
}

@Composable
private fun BodyStep(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDesc: String,
    plusDesc: String,
) {
    Column(Modifier.padding(top = 14.dp)) {
        InkText(label, size = 12.sp, color = Ink.muted)
        Stepper(value, onMinus, onPlus, minusDesc, plusDesc, Modifier.padding(top = 6.dp))
    }
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

@Composable
fun BranchDesk(phase: Phase, state: RitualState, store: RitualStore) {
    var title by remember(phase) { mutableStateOf("") }
    var weekly by remember(phase) { mutableIntStateOf(3) }
    InkText("另加的分支", modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
    InkText(
        "固定的日常写在上面。这里挂的是按周计次的附加项。",
        modifier = Modifier.padding(top = 6.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
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
            store.addBranch(phase, title, weekly)
            title = ""
        },
        modifier = Modifier.padding(top = 10.dp),
        accent = true,
        enabled = title.isNotBlank(),
    )
    state.branches.filter { it.phase == phase.name }.forEach { branch ->
        val host = phaseOf(branch.phase) ?: return@forEach
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkText(host.glyph, size = 18.sp, color = host.tint(), display = true)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                InkText(branch.title, size = 15.sp)
                InkText("每周 ${branch.weekly} 次", size = 12.sp, color = Ink.muted)
            }
            Seal("撤下", "撤下分支${branch.title}", { store.removeBranch(branch.id) })
        }
    }
}
