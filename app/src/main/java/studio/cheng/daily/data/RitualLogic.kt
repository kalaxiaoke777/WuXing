package studio.cheng.daily.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

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
    val zone = ZoneId.systemDefault()
    val startDay = weekStart(mondayStart, now)
    val start = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = startDay.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

fun inThisWeek(epoch: Long, mondayStart: Boolean = true, now: LocalDate = LocalDate.now()): Boolean {
    val (start, end) = weekBounds(now, mondayStart)
    return epoch >= start && epoch < end
}

fun onTime(actualMinute: Int, targetMinute: Int): Boolean {
    var diff = actualMinute - targetMinute
    if (diff > 720) diff -= 1440
    if (diff < -720) diff += 1440
    return diff in -45..90
}

fun clock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

fun formatToday(date: LocalDate = LocalDate.now()): String {
    val months = arrayOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
    val weeks = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return "${months[date.monthValue - 1]}${date.dayOfMonth}日 · ${weeks[date.dayOfWeek.value - 1]}"
}

data class OrbitMarks(
    val water: Float,
    val fire: Float,
    val metal: Float,
    val earth: Float,
    val wood: Float,
)

data class PhaseReading(
    val phase: Phase,
    val value: String,
    val progress: Float,
)

fun phaseNow(state: RitualState, day: String = todayKey()): List<PhaseReading> {
    val waterMl = state.waterByDay[day] ?: 0
    val moods = state.moodsByDay[day].orEmpty()
    val sleepMin = state.sleepByDay[day]
    val target = state.sleepHour * 60 + state.sleepMinute
    val meals = state.mealsByDay[day].orEmpty()
    val trained = state.strengthEpochs.count { inThisWeek(it, state.mondayWeek) }
    val sleepProgress = when {
        sleepMin == null -> 0f
        onTime(sleepMin, target) -> 1f
        else -> 0.62f
    }
    return listOf(
        PhaseReading(
            Phase.Water,
            "$waterMl ml",
            waterMl / state.waterGoalMl.toFloat().coerceAtLeast(1f),
        ),
        PhaseReading(
            Phase.Fire,
            if (moods.isEmpty()) "未记" else moods.joinToString("") { moodMarks.getOrElse(it) { "·" } },
            moods.size / state.moodGoal.toFloat().coerceAtLeast(1f),
        ),
        PhaseReading(
            Phase.Metal,
            if (sleepMin == null) "未至" else clock(sleepMin),
            sleepProgress,
        ),
        PhaseReading(
            Phase.Earth,
            if (meals.isEmpty()) "未记" else "${meals.size}/${state.mealGoal}",
            meals.size / state.mealGoal.toFloat().coerceAtLeast(1f),
        ),
        PhaseReading(
            Phase.Wood,
            "$trained / ${state.strengthGoal}",
            trained / state.strengthGoal.toFloat().coerceAtLeast(1f),
        ),
    )
}

fun orbitMarks(state: RitualState): OrbitMarks {
    val rows = phaseNow(state)
    fun at(phase: Phase) = rows.first { it.phase == phase }.progress.coerceIn(0f, 1f)
    return OrbitMarks(at(Phase.Water), at(Phase.Fire), at(Phase.Metal), at(Phase.Earth), at(Phase.Wood))
}

data class PhaseWeek(val phase: Phase, val done: Int, val goal: Int)

fun phaseWeek(state: RitualState): List<PhaseWeek> {
    val dates = weekDates(state.mondayWeek)
    val target = state.sleepHour * 60 + state.sleepMinute
    val waterDays = dates.count { (state.waterByDay[it.toString()] ?: 0) >= state.waterGoalMl }
    val fireDays = dates.count { state.moodsByDay[it.toString()].orEmpty().size >= state.moodGoal }
    val metalDays = dates.count { day ->
        val minute = state.sleepByDay[day.toString()] ?: return@count false
        onTime(minute, target)
    }
    val earthDays = dates.count { state.mealsByDay[it.toString()].orEmpty().size >= state.mealGoal }
    val wood = state.strengthEpochs.count { inThisWeek(it, state.mondayWeek) }
    return listOf(
        PhaseWeek(Phase.Water, waterDays, 7),
        PhaseWeek(Phase.Fire, fireDays, 7),
        PhaseWeek(Phase.Metal, metalDays, 7),
        PhaseWeek(Phase.Earth, earthDays, 7),
        PhaseWeek(Phase.Wood, wood, state.strengthGoal),
    )
}

fun branchDone(state: RitualState, branch: Branch): Int {
    return state.branchEpochs[branch.id].orEmpty().count { inThisWeek(it, state.mondayWeek) }
}

fun todayReading(state: RitualState): String {
    val rows = phaseNow(state)
    val water = rows.first { it.phase == Phase.Water }
    val fire = rows.first { it.phase == Phase.Fire }
    val metal = rows.first { it.phase == Phase.Metal }
    val earth = rows.first { it.phase == Phase.Earth }
    val wood = rows.first { it.phase == Phase.Wood }
    val day = todayKey()
    val waterMl = state.waterByDay[day] ?: 0
    return when {
        water.progress <= 0f && fire.progress <= 0f && metal.progress <= 0f && earth.progress <= 0f && wood.progress <= 0f ->
            "五行都还空着。先饮一口，或记下此刻的火。"
        water.progress < 0.5f -> "水还没过半，今天还差 ${(state.waterGoalMl - waterMl).coerceAtLeast(0)} ml。"
        fire.progress < 1f -> "火还没记满，今天还差 ${(state.moodGoal - state.moodsByDay[day].orEmpty().size).coerceAtLeast(0)} 次。"
        metal.progress <= 0f -> "金还没入夜，约定是 ${clock(state.sleepHour, state.sleepMinute)}。"
        earth.progress < 1f -> "土还没种满，今天 ${state.mealsByDay[day].orEmpty().size} / ${state.mealGoal} 餐。"
        wood.progress < 1f -> "木还差 ${(state.strengthGoal - state.strengthEpochs.count { inThisWeek(it, state.mondayWeek) }).coerceAtLeast(0)} 次。"
        else -> "今日五行都在轨上。"
    }
}

fun trainedDaysThisWeek(state: RitualState): Set<LocalDate> {
    val zone = ZoneId.systemDefault()
    return state.strengthEpochs
        .filter { inThisWeek(it, state.mondayWeek) }
        .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        .toSet()
}

fun strengthSessions(state: RitualState): List<ZonedDateTime> {
    val zone = ZoneId.systemDefault()
    return state.strengthEpochs
        .filter { inThisWeek(it, state.mondayWeek) }
        .sortedDescending()
        .map { Instant.ofEpochMilli(it).atZone(zone) }
}
