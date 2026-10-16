package studio.cheng.daily.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

fun todayKey(): String = LocalDate.now().toString()

fun weekStart(mondayStart: Boolean = true, now: LocalDate = LocalDate.now()): LocalDate {
    val day = if (mondayStart) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    return now.with(TemporalAdjusters.previousOrSame(day))
}

fun weekDates(mondayStart: Boolean = true, now: LocalDate = LocalDate.now()): List<LocalDate> {
    val start = weekStart(mondayStart, now)
    return (0..6).map { start.plusDays(it.toLong()) }
}

fun weekBounds(now: LocalDate = LocalDate.now(), mondayStart: Boolean = true): Pair<Long, Long> {
    val zone = java.time.ZoneId.systemDefault()
    val startDay = weekStart(mondayStart, now)
    val start = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = startDay.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

fun inThisWeek(epoch: Long, mondayStart: Boolean = true, now: LocalDate = LocalDate.now()): Boolean {
    val (start, end) = weekBounds(now, mondayStart)
    return epoch >= start && epoch < end
}

fun clock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun formatToday(date: LocalDate = LocalDate.now()): String {
    val months = arrayOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
    val weeks = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return "${months[date.monthValue - 1]}${date.dayOfMonth}日 · ${weeks[date.dayOfWeek.value - 1]}"
}

fun tenthText(tenth: Int): String {
    val value = kotlin.math.abs(tenth)
    return "${value / 10}.${value % 10}"
}

fun weightKg(state: RitualState): Double = state.weightTenth / 10.0

fun bmr(state: RitualState): Int {
    if (state.bmrManual > 0) return state.bmrManual
    val kg = weightKg(state)
    if (state.fatTenth >= 50) {
        val lean = kg * (1.0 - state.fatTenth / 1000.0)
        return (370 + 21.6 * lean).roundToInt().coerceIn(900, 4000)
    }
    val base = 10 * kg + 6.25 * state.heightCm - 5.0 * state.age
    val sex = if (state.male) 5 else -161
    return (base + sex).roundToInt().coerceIn(900, 4000)
}

fun intakeTarget(state: RitualState): Int {
    val basal = bmr(state)
    val raw = when (state.season) {
        "cut" -> basal * 1.15 - 300
        "gain" -> basal * 1.25 + 250
        else -> basal * 1.2
    }
    return raw.roundToInt().coerceIn(1200, 4500)
}

fun moveKcal(kind: String, amount: Int, kg: Double): Int {
    val weight = kg.coerceAtLeast(35.0)
    val kcal = when (kind) {
        "steps" -> amount * 0.0005 * weight
        "cardio" -> 7.0 * weight * (amount / 60.0)
        "lift" -> 6.0 * weight * (amount / 60.0)
        else -> 0.0
    }
    return kcal.roundToInt().coerceAtLeast(0)
}

fun dayMoves(state: RitualState, day: String = todayKey()): List<MoveEntry> = state.movesByDay[day].orEmpty()

fun dayBurn(state: RitualState, day: String = todayKey()): Int = dayMoves(state, day).sumOf { it.kcal }

fun dayIntake(state: RitualState, day: String = todayKey()): Int =
    state.mealsByDay[day].orEmpty().sumOf { it.kcal }

fun daySteps(state: RitualState, day: String = todayKey()): Int =
    dayMoves(state, day).filter { it.kind == "steps" }.sumOf { it.amount }

data class PhasePulse(
    val phase: Phase,
    val value: String,
    val progress: Float,
    val tide: Tide,
)

fun phaseNow(state: RitualState, day: String = todayKey()): List<PhasePulse> {
    return Phase.entries.map { phase ->
        when (phase) {
            Phase.Wood -> woodPulse(state, day)
            Phase.Fire -> firePulse(state, day)
            Phase.Earth -> earthPulse(state, day)
            Phase.Metal -> metalPulse(state, day)
            Phase.Water -> waterPulse(state, day)
        }
    }
}

private fun woodPulse(state: RitualState, day: String): PhasePulse {
    val moves = dayMoves(state, day)
    val steps = moves.filter { it.kind == "steps" }.sumOf { it.amount }
    val minutes = moves.filter { it.kind != "steps" }.sumOf { it.amount }
    val kcal = moves.sumOf { it.kcal }
    val tide = when {
        moves.isEmpty() -> Tide.Empty
        minutes >= 150 || kcal >= 900 -> Tide.High
        steps >= state.stepGoal || minutes >= 20 || kcal >= 120 -> Tide.Even
        else -> Tide.Low
    }
    val progress = ((steps / state.stepGoal.toFloat().coerceAtLeast(1f)) * 0.45f + (kcal / 280f) * 0.55f)
        .coerceIn(0f, 1.2f)
    val value = if (moves.isEmpty()) "未动" else "$kcal kcal"
    return PhasePulse(Phase.Wood, value, progress, tide)
}

private fun firePulse(state: RitualState, day: String): PhasePulse {
    val moods = state.moodsByDay[day].orEmpty()
    if (moods.isEmpty()) return PhasePulse(Phase.Fire, "未观", 0f, Tide.Empty)
    val code = moods.last().coerceIn(0, fireMarks.lastIndex)
    val tide = when (code) {
        0 -> Tide.Low
        1 -> Tide.Even
        else -> Tide.High
    }
    val progress = when (tide) {
        Tide.Even -> 1f
        Tide.Low -> 0.4f
        Tide.High -> 1f
        Tide.Empty -> 0f
    }
    return PhasePulse(Phase.Fire, fireMarks[code], progress, tide)
}

private fun earthPulse(state: RitualState, day: String): PhasePulse {
    val meals = state.mealsByDay[day].orEmpty()
    val water = state.waterByDay[day] ?: 0
    val intake = meals.sumOf { it.kcal }
    val target = intakeTarget(state)
    val tide = when {
        meals.isEmpty() && water == 0 -> Tide.Empty
        (intake > 0 && intake > target * 1.15) || meals.size >= state.mealGoal + 2 -> Tide.High
        meals.size >= state.mealGoal && water >= state.waterGoalMl -> Tide.Even
        else -> Tide.Low
    }
    val progress = (
        meals.size / state.mealGoal.toFloat().coerceAtLeast(1f) * 0.55f +
            water / state.waterGoalMl.toFloat().coerceAtLeast(1f) * 0.45f
        ).coerceIn(0f, 1.2f)
    val value = when {
        intake > 0 -> "$intake kcal"
        meals.isEmpty() && water == 0 -> "未记"
        else -> "${meals.size}/${state.mealGoal} 餐"
    }
    return PhasePulse(Phase.Earth, value, progress, tide)
}

private fun metalPulse(state: RitualState, day: String): PhasePulse {
    val night = state.nightsByDay[day]
    if (night == null) return PhasePulse(Phase.Metal, "未记", 0f, Tide.Empty)
    val goal = state.sleepGoalTenth.coerceAtLeast(10)
    val tide = when {
        night.hoursTenth + 2 < goal -> Tide.Low
        night.wakes >= 3 -> Tide.Low
        else -> Tide.Even
    }
    val progress = (night.hoursTenth / goal.toFloat()).coerceIn(0f, 1.2f)
    return PhasePulse(Phase.Metal, "${tenthText(night.hoursTenth)} h", progress, tide)
}

private fun waterPulse(state: RitualState, day: String): PhasePulse {
    val vows = state.vows
    if (vows.isEmpty()) return PhasePulse(Phase.Water, "未观", 0f, Tide.Empty)
    val marks = vows.map { it.marks[day] }
    val tide = when {
        marks.any { it == false } -> Tide.High
        marks.all { it == true } -> Tide.Even
        marks.all { it == null } -> Tide.Empty
        else -> Tide.Low
    }
    val kept = marks.count { it == true }
    val value = when (tide) {
        Tide.High -> "破了"
        Tide.Even -> "守住"
        Tide.Low -> "未全"
        Tide.Empty -> "未观"
    }
    return PhasePulse(Phase.Water, value, kept / vows.size.toFloat(), tide)
}

data class PhaseWeek(val phase: Phase, val done: Int, val goal: Int)

fun phaseWeek(state: RitualState): List<PhaseWeek> {
    val dates = weekDates(state.mondayWeek)
    return Phase.entries.map { phase ->
        val done = dates.count { day -> met(state, phase, day.toString()) }
        PhaseWeek(phase, done, 7)
    }
}

private fun met(state: RitualState, phase: Phase, day: String): Boolean {
    return phaseNow(state, day).first { it.phase == phase }.tide == Tide.Even
}

fun branchDone(state: RitualState, branch: Branch): Int {
    return state.branchEpochs[branch.id].orEmpty().count { inThisWeek(it, state.mondayWeek) }
}

fun cycleReading(state: RitualState): String {
    val pulses = phaseNow(state)
    fun tide(phase: Phase) = pulses.first { it.phase == phase }.tide
    if (pulses.all { it.tide == Tide.Empty }) {
        return "五行都还空着。先记一项，环才转得起来。"
    }
    val wood = tide(Phase.Wood)
    val fire = tide(Phase.Fire)
    val earth = tide(Phase.Earth)
    val metal = tide(Phase.Metal)
    val water = tide(Phase.Water)
    return when {
        water == Tide.High -> "欲过了。水一耗，木会跟着没力气，火也容易躁。"
        fire == Tide.High -> "火压着金。心神不停，今夜很难收，饮食也容易乱。"
        earth == Tide.High -> "土偏满。吃喝过头，金今晚不容易收敛，欲也会被抬起来。"
        wood == Tide.High -> "木过头了。动得太狠，脾胃会被一起耗掉。"
        metal == Tide.Low -> "金没收住。睡不够，精气托不住欲，明天也难把木展开。"
        wood == Tide.Low -> "木还弱。气机郁着，火容易往烦躁走。"
        fire == Tide.Low -> "火偏弱。情绪提不起来，胃口和行动都会慢。"
        earth == Tide.Low -> "土还虚。吃喝不够，夜里收不住，白天也生不出火。"
        pulses.all { it.tide == Tide.Even } -> "今日五行都在环上。生的接得住，克的也没有过头。"
        else -> "有的行还空着。空一环，旁边两环都会跟着晃。"
    }
}

fun vowStart(vow: Vow, today: LocalDate = LocalDate.now()): LocalDate {
    return runCatching { LocalDate.parse(vow.anchor) }.getOrElse { today }
}

fun vowDay(vow: Vow, today: LocalDate = LocalDate.now()): Int {
    val between = ChronoUnit.DAYS.between(vowStart(vow, today), today).toInt()
    if (between < 0) return 0
    return between + 1
}

fun vowStreak(vow: Vow, today: LocalDate = LocalDate.now()): Int {
    var cursor = today
    if (vow.marks[cursor.toString()] != true) cursor = cursor.minusDays(1)
    var count = 0
    while (vow.marks[cursor.toString()] == true && count < 400) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

fun energyLine(state: RitualState, day: String = todayKey()): String {
    val intake = dayIntake(state, day)
    val burn = dayBurn(state, day)
    val gap = intake - bmr(state) - burn
    val word = when {
        gap <= -80 -> "缺口 ${-gap}"
        gap >= 80 -> "盈余 $gap"
        else -> "大致持平"
    }
    return "截至目前 $word kcal"
}
