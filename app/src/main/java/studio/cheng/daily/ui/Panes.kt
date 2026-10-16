package studio.cheng.daily.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.clock
import studio.cheng.daily.data.inThisWeek
import studio.cheng.daily.data.onTime
import studio.cheng.daily.data.strengthSessions
import studio.cheng.daily.data.todayKey
import studio.cheng.daily.data.trainedDaysThisWeek
import studio.cheng.daily.data.weekDates
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaterPane(state: RitualState, store: RitualStore) {
    val ml = state.waterByDay[todayKey()] ?: 0
    val goal = state.waterGoalMl
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("饮水", "杯中", state.notice)
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaterVessel(ml / goal.toFloat(), Modifier.size(148.dp, 220.dp))
            Column(Modifier.padding(start = 16.dp)) {
                InkText("$ml", size = 52.sp, display = true)
                InkText("/ $goal ml", size = 13.sp, color = Ink.muted)
                if (ml >= goal) {
                    InkText("今日已满", modifier = Modifier.padding(top = 4.dp), size = 13.sp, color = Ink.celadon)
                }
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("+200", "加上200毫升", { store.addWater(200) })
            Seal("+350", "加上350毫升", { store.addWater(350) })
            Seal("+500", "加上500毫升", { store.addWater(500) })
        }
        Seal("回退 200", "减少200毫升", { store.addWater(-200) }, Modifier.padding(top = 8.dp))
        InkText("每日目标", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted)
        Stepper(
            value = "$goal ml",
            onMinus = { store.setWaterGoal(goal - 250) },
            onPlus = { store.setWaterGoal(goal + 250) },
            minusDesc = "降低饮水目标",
            plusDesc = "提高饮水目标",
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        BranchRail(Phase.Water, state, store)
    }
}

@Composable
private fun WaterVessel(level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier) {
        val cx = size.width / 2f
        val rx = size.width * 0.34f
        val ry = rx * 0.28f
        val top = size.height * 0.05f
        val bot = size.height * 0.94f
        val bodyTop = top + ry
        val bodyBot = bot - ry
        val ySurf = bodyBot - (bodyBot - bodyTop) * level.coerceIn(0f, 1f)
        drawOval(
            color = Color(0x44000000),
            topLeft = Offset(cx - rx * 0.8f, bot - ry * 0.15f),
            size = Size(rx * 1.6f, ry * 1.2f),
        )
        val glass = Path().apply {
            addOval(Rect(cx - rx, top, cx + rx, top + ry * 2f))
            addRect(Rect(cx - rx, bodyTop, cx + rx, bodyBot))
            addOval(Rect(cx - rx, bot - ry * 2f, cx + rx, bot))
        }
        drawPath(glass, Color(0x18E7E0D4))
        if (level > 0.015f) {
            clipPath(glass) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFD7E6E0), Color(0xFF4E736A)),
                        startY = ySurf,
                        endY = bot,
                    ),
                    topLeft = Offset(cx - rx, ySurf),
                    size = Size(rx * 2f, bot - ySurf),
                )
                val wave = Path()
                val steps = 28
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = cx - rx + t * rx * 2f
                    val y = ySurf + sin(phase + t * 6.2f) * ry * 0.45f
                    if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                }
                wave.lineTo(cx + rx, bot)
                wave.lineTo(cx - rx, bot)
                wave.close()
                drawPath(wave, Color(0x99F4FFFB))
            }
        }
        drawLine(
            Color(0x66F3EADA),
            Offset(cx - rx * 0.68f, bodyTop + 6f),
            Offset(cx - rx * 0.68f, bodyBot - 6f),
            2f,
        )
        drawOval(
            color = Ink.bronze,
            topLeft = Offset(cx - rx, top),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(1.5f),
        )
        drawOval(
            color = Ink.bronze.copy(alpha = 0.7f),
            topLeft = Offset(cx - rx, bot - ry * 2f),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(1.2f),
        )
    }
}

@Composable
fun StrengthPane(state: RitualState, store: RitualStore) {
    val trained = state.strengthEpochs.count { inThisWeek(it, state.mondayWeek) }
    val goal = state.strengthGoal
    val dates = weekDates(state.mondayWeek)
    val days = trainedDaysThisWeek(state)
    val today = LocalDate.now()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("运动", "本周 $trained / $goal", state.notice)
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy((-8).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mid = (goal - 1) / 2f
            repeat(goal) { index ->
                val filled = index < trained
                Box(
                    Modifier
                        .graphicsLayer {
                            rotationY = (index - mid) * 18f
                            rotationX = 16f
                            cameraDistance = 8f * density
                            translationY = abs(index - mid) * 5f * density
                        }
                        .size(if (goal >= 6) 52.dp else 64.dp)
                        .border(1.dp, if (filled) Ink.bronze else Ink.line, CircleShape)
                        .background(if (filled) Ink.bronze.copy(alpha = 0.9f) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    InkText(
                        if (filled) "成" else "${index + 1}",
                        size = 18.sp,
                        color = if (filled) Ink.bg else Ink.muted,
                        display = true,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            dates.forEach { date ->
                val label = listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1]
                val on = date in days
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InkText(label, size = 12.sp, color = if (date == today) Ink.text else Ink.faint)
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .background(if (on) Ink.bronze else Color.Transparent, CircleShape)
                            .border(1.dp, if (on) Ink.bronze else Ink.line, CircleShape),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("记下一练", "记下一练", store::addStrength, accent = true)
            if (trained > 0) Seal("撤回", "撤回一次运动", store::undoStrength)
        }
        InkText("每周次数", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted)
        Stepper(
            value = "$goal 次",
            onMinus = { store.setStrengthGoal(goal - 1) },
            onPlus = { store.setStrengthGoal(goal + 1) },
            minusDesc = "降低每周次数",
            plusDesc = "提高每周次数",
            modifier = Modifier.padding(top = 8.dp),
        )
        strengthSessions(state).forEach { time ->
            val weeks = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            InkText(
                "${weeks[time.dayOfWeek.value - 1]}  %02d:%02d".format(time.hour, time.minute),
                modifier = Modifier.padding(top = 8.dp),
                size = 13.sp,
                color = Ink.muted,
            )
        }
        BranchRail(Phase.Wood, state, store)
    }
}

@Composable
fun SleepPane(state: RitualState, store: RitualStore) {
    val actual = state.sleepByDay[todayKey()]
    val target = state.sleepHour * 60 + state.sleepMinute
    val status = when {
        actual == null -> "今夜还没有打卡"
        onTime(actual, target) -> "已于 ${clock(actual)} 入眠，算准点"
        else -> "已于 ${clock(actual)} 记下，偏离了约定"
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("睡眠", clock(state.sleepHour, state.sleepMinute), state.notice)
        Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp, 28.dp).offset(y = 70.dp)) {
                drawOval(Color(0x66000000))
            }
            SleepDial(
                targetMinute = target,
                actualMinute = actual,
                modifier = Modifier
                    .size(210.dp)
                    .graphicsLayer {
                        rotationX = 62f
                        cameraDistance = 9f * density
                    },
            )
        }
        InkText(status, size = 14.sp, color = Ink.muted)
        InkText("铜针是约定，青针是今夜", modifier = Modifier.padding(top = 4.dp), size = 12.sp, color = Ink.faint)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                InkText("时", size = 12.sp, color = Ink.muted)
                Stepper(
                    value = "%02d".format(state.sleepHour),
                    onMinus = { store.setSleep(state.sleepHour - 1, state.sleepMinute) },
                    onPlus = { store.setSleep(state.sleepHour + 1, state.sleepMinute) },
                    minusDesc = "入眠提前一小时",
                    plusDesc = "入眠推后一小时",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column {
                InkText("分", size = 12.sp, color = Ink.muted)
                Stepper(
                    value = "%02d".format(state.sleepMinute),
                    onMinus = { shiftMinute(store, state, -5) },
                    onPlus = { shiftMinute(store, state, 5) },
                    minusDesc = "入眠提前五分钟",
                    plusDesc = "入眠推后五分钟",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Seal(
            label = if (actual == null) "此刻打卡" else "改记此刻",
            desc = "此刻打卡睡觉",
            onClick = { store.checkSleep() },
            accent = true,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )
        BranchRail(Phase.Metal, state, store)
    }
}

private fun shiftMinute(store: RitualStore, state: RitualState, delta: Int) {
    var minute = state.sleepMinute + delta
    var hour = state.sleepHour
    while (minute >= 60) {
        minute -= 60
        hour += 1
    }
    while (minute < 0) {
        minute += 60
        hour -= 1
    }
    store.setSleep(hour, minute)
}

@Composable
private fun SleepDial(targetMinute: Int, actualMinute: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.46f
        drawCircle(Color(0xFF171410), r, c)
        drawCircle(Ink.bronze.copy(alpha = 0.9f), r, c, style = Stroke(1.4f))
        drawCircle(Ink.line, r * 0.72f, c, style = Stroke(1f))
        for (hour in 0 until 24) {
            val angle = hour / 24f * 6.2831855f - 1.5707963f
            val inner = if (hour % 6 == 0) r * 0.78f else r * 0.88f
            val cosA = cos(angle)
            val sinA = sin(angle)
            drawLine(
                if (hour % 6 == 0) Ink.bronze else Ink.faint,
                Offset(c.x + cosA * inner, c.y + sinA * inner),
                Offset(c.x + cosA * r * 0.96f, c.y + sinA * r * 0.96f),
                if (hour % 6 == 0) 2f else 1f,
            )
        }
        fun hand(minute: Int, color: Color, length: Float, width: Float) {
            val angle = minute / 1440f * 6.2831855f - 1.5707963f
            drawLine(
                color,
                c,
                Offset(c.x + cos(angle) * r * length, c.y + sin(angle) * r * length),
                width,
            )
        }
        hand(targetMinute, Ink.bronze, 0.78f, 2.4f)
        if (actualMinute != null) hand(actualMinute, Ink.celadon, 0.62f, 2f)
        drawCircle(Ink.text, r * 0.045f, c)
    }
}
