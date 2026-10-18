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

fun heightCm(state: RitualState): Double = state.heightTenth / 10.0

fun ageYears(state: RitualState): Double = state.ageTenth / 10.0

fun MealEntry.heatTenth(): Int = if (heatTenth > 0) heatTenth else kcal * 10

/**
 * 没填体脂时用 Mifflin–St Jeor（1990）：体重、身高、年龄、性别。
 * 填了体脂时用 Müller 等（2004）：瘦体重、脂肪量、年龄、性别。
 * 身高已经体现在称出来的体重和体脂里，所以后一条不再单独乘身高。
 */
fun bmrTenth(state: RitualState): Int {
    if (state.bmrTenth > 0) return state.bmrTenth
    val kcal = if (state.fatTenth >= 50) mullerKcal(state) else mifflinKcal(state)
    return (kcal * 10.0).roundToInt().coerceIn(9000, 40000)
}

fun bmr(state: RitualState): Int = (bmrTenth(state) + 5) / 10

fun bmrFormula(state: RitualState): String {
    if (state.bmrTenth > 0) return "正在用你填的数"
    return if (state.fatTenth >= 50) {
        "Müller：瘦体重、脂肪、年龄、性别"
    } else {
        "Mifflin–St Jeor：体重、身高、年龄、性别"
    }
}

private fun mifflinKcal(state: RitualState): Double {
    val sex = if (state.male) 5.0 else -161.0
    return 10.0 * weightKg(state) + 6.25 * heightCm(state) - 5.0 * ageYears(state) + sex
}

private fun mullerKcal(state: RitualState): Double {
    val kg = weightKg(state)
    val fat = state.fatTenth / 1000.0
    val lean = kg * (1.0 - fat)
    val mass = kg - lean
    val sex = if (state.male) 1.0 else 0.0
    val mj = 0.05192 * lean + 0.04036 * mass + 0.869 * sex - 0.01181 * ageYears(state) + 2.992
    return mj * 1000.0 / 4.184
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
    (state.mealsByDay[day].orEmpty().sumOf { it.heatTenth() } + 5) / 10

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
    val intake = (meals.sumOf { it.heatTenth() } + 5) / 10
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

fun tideMark(tide: Tide): Int = when (tide) {
    Tide.Even -> 20
    Tide.Low -> 10
    Tide.High -> 6
    Tide.Empty -> 0
}

fun dayScore(state: RitualState, day: String = todayKey()): Int {
    return phaseNow(state, day).sumOf { tideMark(it.tide) }
}

data class SpanScore(val average: Int, val counted: Int)

fun spanScore(state: RitualState, dates: List<LocalDate>, today: LocalDate = LocalDate.now()): SpanScore {
    val elapsed = dates.filter { !it.isAfter(today) }
    if (elapsed.isEmpty()) return SpanScore(0, 0)
    val sum = elapsed.sumOf { dayScore(state, it.toString()) }
    return SpanScore(sum / elapsed.size, elapsed.size)
}

fun monthDates(month: LocalDate): List<LocalDate> {
    val first = month.withDayOfMonth(1)
    return (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }
}

fun digestBrief(state: RitualState, span: String, today: LocalDate = LocalDate.now()): String {
    val dates = when (span) {
        "week" -> weekDates(state.mondayWeek, today).filter { !it.isAfter(today) }
        "month" -> monthDates(today).filter { !it.isAfter(today) }
        else -> listOf(today)
    }
    val lines = ArrayList<String>()
    lines += "跨度 ${dates.firstOrNull() ?: today} 至 ${dates.lastOrNull() ?: today}，共 ${dates.size} 天。"
    lines += "评分 ${spanScore(state, dates, today).average}。"
    var intake = 0
    var burn = 0
    var sleep = 0
    var slept = 0
    var water = 0
    dates.forEach { date ->
        val key = date.toString()
        intake += dayIntake(state, key)
        burn += dayBurn(state, key)
        water += state.waterByDay[key] ?: 0
        val night = state.nightsByDay[key]
        if (night != null) {
            sleep += night.hoursTenth
            slept += 1
        }
    }
    lines += "饮食 $intake kcal，运动 $burn kcal，基础代谢每天约 ${bmr(state)} kcal，饮水 $water ml。"
    if (slept > 0) lines += "记下的睡眠平均 ${tenthText(sleep / slept)} 小时。"
    val vows = state.vows.joinToString("，") { vow ->
        val broken = dates.count { vow.marks[it.toString()] == false }
        val held = dates.count { vow.marks[it.toString()] == true }
        "${vow.title} 守住 $held 破了 $broken"
    }
    if (vows.isNotBlank()) lines += vows
    return lines.joinToString("\n").take(1800)
}

fun monthCells(month: LocalDate, mondayStart: Boolean): List<LocalDate?> {
    val first = month.withDayOfMonth(1)
    val lead = if (mondayStart) first.dayOfWeek.value - 1 else first.dayOfWeek.value % 7
    return List(lead) { null } + monthDates(first)
}

fun weekdayLabels(mondayStart: Boolean): List<String> {
    return if (mondayStart) {
        listOf("一", "二", "三", "四", "五", "六", "日")
    } else {
        listOf("日", "一", "二", "三", "四", "五", "六")
    }
}

fun scoreWord(score: Int): String = when {
    score >= 85 -> "五行在环上"
    score >= 60 -> "大体还稳"
    score >= 30 -> "有几环空着"
    score > 0 -> "才起了个头"
    else -> "尚未落笔"
}

data class DayEnergy(
    val intake: Int,
    val burn: Int,
    val basal: Int,
    val recorded: Boolean,
) {
    val gap: Int get() = intake - basal - burn
}

fun dayEnergy(state: RitualState, day: String = todayKey()): DayEnergy {
    val intake = dayIntake(state, day)
    val burn = dayBurn(state, day)
    return DayEnergy(intake, burn, bmr(state), intake > 0 || burn > 0)
}

fun gapText(energy: DayEnergy): String {
    if (!energy.recorded) return "未记"
    return when {
        energy.gap <= -80 -> "缺口 ${-energy.gap}"
        energy.gap >= 80 -> "盈余 ${energy.gap}"
        else -> "大致持平"
    }
}

fun gapMark(energy: DayEnergy): String {
    if (!energy.recorded) return ""
    return when {
        energy.gap <= -80 -> "缺${-energy.gap}"
        energy.gap >= 80 -> "盈${energy.gap}"
        else -> "平"
    }
}

fun energyLine(state: RitualState, day: String = todayKey()): String {
    val energy = dayEnergy(state, day)
    if (!energy.recorded) return "饮食和运动都还没记，缺口还算不出来。"
    return "饮食 ${energy.intake} − 代谢 ${energy.basal} − 运动 ${energy.burn}，${gapText(energy)} kcal"
}
