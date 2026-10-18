package studio.cheng.daily.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cheng.daily.data.Phase
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.RitualStore
import studio.cheng.daily.data.bmr
import studio.cheng.daily.data.bmrFormula
import studio.cheng.daily.data.intakeTarget
import studio.cheng.daily.data.phaseNow
import studio.cheng.daily.data.seasonLabel
import studio.cheng.daily.data.tenthText

@Composable
fun TuneIndex(state: RitualState, onOpen: (Phase) -> Unit) {
    val rows = phaseNow(state)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("五行设置", "分而行", state.notice)
        InkText(
            "每一行单独成页。以后要加的功能，都长在自己的页上。",
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        Phase.entries.forEach { phase ->
            val reading = rows.first { it.phase == phase }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable(
                        remember { MutableInteractionSource() },
                        null,
                        role = Role.Button,
                        onClick = { onOpen(phase) },
                    )
                    .semantics { contentDescription = "设置${phase.practice}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkText(phase.glyph, size = 28.sp, color = phase.tint(), display = true)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    InkText(phase.practice, size = 16.sp)
                    InkText(phase.office, size = 12.sp, color = Ink.muted)
                }
                InkText(reading.value, size = 14.sp, color = phase.tint(), display = true)
            }
        }
    }
}

@Composable
fun PhaseTune(phase: Phase, state: RitualState, store: RitualStore) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        InkText(phase.feeds, size = 13.sp, color = Ink.bronze, line = 20.sp)
        InkText(phase.reins, modifier = Modifier.padding(top = 4.dp), size = 13.sp, color = Ink.faint, line = 20.sp)
        when (phase) {
            Phase.Wood -> WoodTune(state, store)
            Phase.Fire -> FireTune(state, store)
            Phase.Earth -> EarthTune(state, store)
            Phase.Metal -> MetalTune(state, store)
            Phase.Water -> WaterTune(state, store)
        }
        BranchDesk(phase, state, store)
        androidx.compose.foundation.layout.Box(Modifier.padding(bottom = 18.dp))
    }
}

@Composable
private fun WoodTune(state: RitualState, store: RitualStore) {
    InkText("日常步数", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
    MeasureStepper(
        label = "目标",
        tenth = state.stepGoal,
        suffix = "步",
        step = 500,
        min = 2000,
        max = 30000,
        onCommit = store::setStepGoal,
        minusDesc = "降低步数目标",
        plusDesc = "提高步数目标",
        modifier = Modifier.padding(top = 8.dp),
        decimal = false,
    )
    InkText(
        "无氧、有氧在运动页手记。步数优先读手机上今天已经走的，读不到再手记。热量按个人页的体重估算。",
        modifier = Modifier.padding(top = 10.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
}

@Composable
private fun FireTune(state: RitualState, store: RitualStore) {
    InkText(
        "观火固定四种：弱、平、旺、思。旺和思会压住睡眠，弱会拖慢胃口。记下之后只做一个很小的动作。",
        modifier = Modifier.padding(top = 16.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
    RowChoice(
        label = "思虑时写下那一句",
        on = state.noteWhenThink,
        onYes = { store.setNoteWhenThink(true) },
        onNo = { store.setNoteWhenThink(false) },
        yes = "要写",
        no = "不写",
    )
}

@Composable
private fun EarthTune(state: RitualState, store: RitualStore) {
    val target = intakeTarget(state)
    InkText("阶段", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
    androidx.compose.foundation.layout.Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Seal("减脂", "改为减脂期", { store.setSeason("cut") }, accent = state.season == "cut")
        Seal("维持", "改为维持期", { store.setSeason("keep") }, accent = state.season == "keep")
        Seal("增肌", "改为增肌期", { store.setSeason("gain") }, accent = state.season == "gain")
    }
    InkText(
        "${seasonLabel(state.season)}建议摄入约 $target kcal。${bmrFormula(state)}，约 ${bmr(state)} kcal。",
        modifier = Modifier.padding(top = 10.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
    InkText("进食次数", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted)
    MeasureStepper(
        label = "每日",
        tenth = state.mealGoal,
        suffix = "餐",
        step = 1,
        min = 1,
        max = 8,
        onCommit = store::setMealGoal,
        minusDesc = "降低每日餐数",
        plusDesc = "提高每日餐数",
        modifier = Modifier.padding(top = 8.dp),
        decimal = false,
    )
    InkText("饮水", modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted)
    MeasureStepper(
        label = "目标",
        tenth = state.waterGoalMl,
        suffix = "ml",
        step = 100,
        min = 500,
        max = 5000,
        onCommit = store::setWaterGoal,
        minusDesc = "降低饮水目标",
        plusDesc = "提高饮水目标",
        modifier = Modifier.padding(top = 8.dp),
        decimal = false,
    )
    InkText(
        "进食和饮水都记在土里。餐数不固定，点数字可以手填。",
        modifier = Modifier.padding(top = 8.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
}

@Composable
private fun MetalTune(state: RitualState, store: RitualStore) {
    InkText("每晚时长", modifier = Modifier.padding(top = 18.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
    MeasureStepper(
        label = "时长",
        tenth = state.sleepGoalTenth,
        suffix = "小时",
        step = 5,
        min = 40,
        max = 120,
        onCommit = store::setSleepGoal,
        minusDesc = "降低睡眠时长",
        plusDesc = "提高睡眠时长",
        modifier = Modifier.padding(top = 8.dp),
    )
    InkText(
        "次日补记昨夜睡了多久，以及夜里起来几次。时长不够，或起来太多次，都算没收住。",
        modifier = Modifier.padding(top = 10.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
}

@Composable
private fun WaterTune(state: RitualState, store: RitualStore) {
    InkText(
        "色欲和贪财各有一段禁欲期。守住就往下数，破了从明天重新计。",
        modifier = Modifier.padding(top = 16.dp),
        size = 13.sp,
        color = Ink.faint,
        line = 20.sp,
    )
    state.vows.filter { it.id == "lust" || it.id == "greed" }.forEach { vow ->
        InkText(vow.title, modifier = Modifier.padding(top = 16.dp), size = 15.sp, color = Ink.celadon)
        MeasureStepper(
            label = "期限",
            tenth = vow.spanDays,
            suffix = "天",
            step = 1,
            min = 1,
            max = 365,
            onCommit = { store.setVowSpan(vow.id, it) },
            minusDesc = "缩短${vow.title}期限",
            plusDesc = "延长${vow.title}期限",
            modifier = Modifier.padding(top = 8.dp),
            decimal = false,
        )
    }
}

@Composable
private fun RowChoice(
    label: String,
    on: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    yes: String,
    no: String,
) {
    InkText(label, modifier = Modifier.padding(top = 16.dp), size = 12.sp, color = Ink.muted)
    androidx.compose.foundation.layout.Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Seal(yes, yes, onYes, accent = on)
        Seal(no, no, onNo, accent = !on)
    }
}
