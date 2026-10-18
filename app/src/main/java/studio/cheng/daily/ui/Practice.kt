package studio.cheng.daily.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import studio.cheng.daily.data.Phase
import studio.cheng.daily.device.PhoneSteps
import studio.cheng.daily.device.StepRead
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.dayBurn
import studio.cheng.daily.data.energyLine
import studio.cheng.daily.data.dayMoves
import studio.cheng.daily.data.daySteps
import studio.cheng.daily.data.moveLabel
import studio.cheng.daily.data.tenthText
import studio.cheng.daily.data.todayKey
import studio.cheng.daily.data.vowDay
import studio.cheng.daily.data.vowStreak

@Composable
fun WoodPane(state: RitualState, store: RitualStore) {
    var lift by remember { mutableIntStateOf(40) }
    var cardio by remember { mutableIntStateOf(30) }
    var steps by remember { mutableIntStateOf(state.stepGoal) }
    val moves = dayMoves(state)
    val burn = dayBurn(state)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("运动", if (moves.isEmpty()) "尚未展开" else "$burn kcal", state.notice)
        InkText(
            "无氧、有氧、步数合成今天的消耗。缺口用饮食减去代谢，再减去这里的运动。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        InkText(energyLine(state), modifier = Modifier.padding(top = 6.dp), size = 13.sp, color = Ink.moss, line = 20.sp)
        MoveBlock("无氧", "$lift 分钟") {
            MeasureStepper(
                label = "时长",
                tenth = lift,
                suffix = "分钟",
                step = 5,
                min = 5,
                max = 180,
                onCommit = { lift = it },
                minusDesc = "减少无氧分钟",
                plusDesc = "增加无氧分钟",
                decimal = false,
            )
            Seal("记一笔", "记下无氧", { store.logMove("lift", lift) }, Modifier.padding(top = 8.dp), accent = true)
        }
        MoveBlock("有氧", "$cardio 分钟") {
            MeasureStepper(
                label = "时长",
                tenth = cardio,
                suffix = "分钟",
                step = 5,
                min = 5,
                max = 180,
                onCommit = { cardio = it },
                minusDesc = "减少有氧分钟",
                plusDesc = "增加有氧分钟",
                decimal = false,
            )
            Seal("记一笔", "记下有氧", { store.logMove("cardio", cardio) }, Modifier.padding(top = 8.dp), accent = true)
        }
        MoveBlock("日常步数", "目标 ${state.stepGoal}") {
            PhoneStepLine(store)
            MeasureStepper(
                label = "步数",
                tenth = steps,
                suffix = "步",
                step = 500,
                min = 500,
                max = 40000,
                onCommit = { steps = it },
                minusDesc = "减少步数",
                plusDesc = "增加步数",
                decimal = false,
            )
            Seal("手记", "手记今日步数", { store.logMove("steps", steps) }, Modifier.padding(top = 8.dp))
            val walked = daySteps(state)
            if (walked > 0) {
                InkText("今日 $walked 步", modifier = Modifier.padding(top = 6.dp), size = 12.sp, color = Ink.muted)
            }
        }
        if (moves.isNotEmpty()) {
            InkText("今天", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
            moves.forEach { move ->
                Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        InkText(moveLabel(move.kind), size = 15.sp)
                        val unit = if (move.kind == "steps") "${move.amount} 步" else "${move.amount} 分钟"
                        InkText("$unit · ${move.kcal} kcal", size = 12.sp, color = Ink.muted)
                    }
                    Seal("撤下", "撤下${moveLabel(move.kind)}", { store.undoMove(move.id) })
                }
            }
        }
        BranchRail(Phase.Wood, state, store)
    }
}

@Composable
private fun PhoneStepLine(store: RitualStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf("打开这一页，会读手机上今天已经走的步数。") }
    fun apply(read: StepRead) {
        when (read) {
            is StepRead.Ready -> {
                if (read.steps > 0) store.logMove("steps", read.steps, fromPhone = true)
                else hint = "手机上今天还是 0 步"
            }
            StepRead.NeedPermission -> hint = "要允许读取步数"
            StepRead.NeedSensor -> hint = "要允许活动识别，才能读计步器"
            StepRead.NeedProvider -> hint = "先把健康数据准备好"
            is StepRead.Failed -> hint = read.reason
        }
    }
    val healthPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        scope.launch {
            if (PhoneSteps.healthPermission in granted) apply(PhoneSteps.read(context))
            else hint = "没有允许读取步数"
        }
    }
    val sensorPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            if (granted) apply(PhoneSteps.read(context))
            else hint = "没有活动识别权限"
        }
    }
    LaunchedEffect(Unit) {
        val read = PhoneSteps.read(context)
        if (read is StepRead.Ready) apply(read)
    }
    Seal(
        "读取手机步数",
        "读取手机上今天的步数",
        {
            scope.launch {
                when (val read = PhoneSteps.read(context)) {
                    StepRead.NeedPermission -> healthPermission.launch(setOf(PhoneSteps.healthPermission))
                    StepRead.NeedSensor -> sensorPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    StepRead.NeedProvider -> {
                        hint = "健康数据还没准备好"
                        runCatching {
                            context.startActivity(PhoneSteps.providerIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    else -> apply(read)
                }
            }
        },
        accent = true,
    )
    InkText(hint, modifier = Modifier.padding(top = 8.dp), size = 12.sp, color = Ink.faint, line = 18.sp)
}

@Composable
private fun MoveBlock(title: String, meta: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        InkText(title, size = 15.sp, color = Ink.moss)
        InkText(meta, size = 12.sp, color = Ink.muted)
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
fun SleepPane(state: RitualState, store: RitualStore) {
    val night = state.nightsByDay[todayKey()]
    var hours by remember(night?.hoursTenth) { mutableIntStateOf(night?.hoursTenth ?: 70) }
    var wakes by remember(night?.wakes) { mutableIntStateOf(night?.wakes ?: 0) }
    val goal = tenthText(state.sleepGoalTenth)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("睡眠", if (night == null) "昨夜未记" else "${tenthText(night.hoursTenth)} 小时", state.notice)
        InkText(
            "次日补记昨夜。要收到 $goal 小时以上，并写下夜里起来几次。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        NightArc(hours / state.sleepGoalTenth.toFloat(), Modifier.fillMaxWidth().height(128.dp).padding(top = 8.dp))
        InkText("睡了多久", modifier = Modifier.padding(top = 8.dp), size = 12.sp, color = Ink.muted)
        MeasureStepper(
            label = "时长",
            tenth = hours,
            suffix = "小时",
            step = 5,
            min = 0,
            max = 140,
            onCommit = { hours = it },
            minusDesc = "减少睡眠时长",
            plusDesc = "增加睡眠时长",
            modifier = Modifier.padding(top = 8.dp),
        )
        InkText("夜里起来", modifier = Modifier.padding(top = 14.dp), size = 12.sp, color = Ink.muted)
        MeasureStepper(
            label = "次数",
            tenth = wakes,
            suffix = "次",
            step = 1,
            min = 0,
            max = 12,
            onCommit = { wakes = it },
            minusDesc = "减少夜醒",
            plusDesc = "增加夜醒",
            modifier = Modifier.padding(top = 8.dp),
            decimal = false,
        )
        val verdict = when {
            hours + 2 < state.sleepGoalTenth -> "不到 $goal 小时，收敛不足。"
            wakes >= 3 -> "时长够了，起来太多次，收敛不稳。"
            else -> "这样记，昨夜算收住了。"
        }
        InkText(verdict, modifier = Modifier.padding(top = 12.dp), size = 14.sp, color = Ink.moon, line = 22.sp)
        Seal(
            if (night == null) "记下昨夜" else "改记昨夜",
            "记下昨夜睡眠",
            { store.logNight(hours, wakes) },
            Modifier.padding(top = 12.dp),
            accent = true,
        )
        BranchRail(Phase.Metal, state, store)
    }
}

@Composable
private fun NightArc(level: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.42f
        val top = Offset(size.width / 2f - radius, size.height * 0.72f - radius)
        drawArc(
            color = Ink.line,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = top,
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(6f, cap = StrokeCap.Round),
        )
        drawArc(
            color = Ink.moon,
            startAngle = 200f,
            sweepAngle = 140f * level.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = top,
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(6f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun DesirePane(state: RitualState, store: RitualStore) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("欲望", "潜藏", state.notice)
        InkText(
            "色欲和贪财分开守。守住往下数，破了从明天重新计。期限在这一行的设置里改。",
            modifier = Modifier.padding(top = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        state.vows.filter { it.id == "lust" || it.id == "greed" }.forEach { vow ->
            val day = vowDay(vow)
            val streak = vowStreak(vow)
            val mark = vow.marks[todayKey()]
            Column(Modifier.padding(top = 18.dp)) {
                InkText(vow.title, size = 22.sp, color = Ink.celadon, display = true)
                val progress = when {
                    day <= 0 -> "明日再起 · 共 ${vow.spanDays} 天"
                    day > vow.spanDays -> "本期已满"
                    else -> "第 $day / ${vow.spanDays} 天"
                }
                InkText(progress, modifier = Modifier.padding(top = 4.dp), size = 13.sp, color = Ink.muted)
                InkText("连续守住 $streak 天", modifier = Modifier.padding(top = 2.dp), size = 12.sp, color = Ink.faint)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Seal("守住", "守住${vow.title}", { store.markVow(vow.id, true) }, accent = mark == true)
                    Seal("破了", "破了${vow.title}", { store.markVow(vow.id, false) }, accent = mark == false)
                }
            }
        }
        BranchRail(Phase.Water, state, store)
    }
}
