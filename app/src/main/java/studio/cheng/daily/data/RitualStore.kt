package studio.cheng.daily.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class MealReading(
    val score: Int,
    val title: String,
    val commentary: String,
    val produce: Int,
    val variety: Int,
    val fresh: Int,
    val balance: Int,
    val foods: List<String>,
    val usedModel: Boolean,
)

data class MealEntry(
    val id: String,
    val epoch: Long,
    val imagePath: String,
    val score: Int,
    val title: String,
    val commentary: String,
    val produce: Int,
    val variety: Int,
    val fresh: Int,
    val balance: Int,
    val foods: List<String>,
    val usedModel: Boolean,
    val kcal: Int = 0,
)

data class MoveEntry(
    val id: String,
    val epoch: Long,
    val kind: String,
    val amount: Int,
    val kcal: Int,
)

data class NightLog(
    val hoursTenth: Int,
    val wakes: Int,
)

data class Vow(
    val id: String,
    val title: String,
    val spanDays: Int,
    val anchor: String,
    val marks: Map<String, Boolean>,
)

data class Branch(
    val id: String,
    val phase: String,
    val title: String,
    val weekly: Int,
)

data class RitualState(
    val heightCm: Int = 170,
    val weightTenth: Int = 650,
    val fatTenth: Int = 0,
    val age: Int = 28,
    val male: Boolean = true,
    val bmrManual: Int = 0,
    val season: String = "keep",
    val stepGoal: Int = 8000,
    val sleepGoalTenth: Int = 70,
    val noteWhenThink: Boolean = true,
    val waterGoalMl: Int = 2000,
    val mealGoal: Int = 3,
    val mondayWeek: Boolean = true,
    val name: String = "",
    val waterByDay: Map<String, Int> = emptyMap(),
    val mealsByDay: Map<String, List<MealEntry>> = emptyMap(),
    val movesByDay: Map<String, List<MoveEntry>> = emptyMap(),
    val nightsByDay: Map<String, NightLog> = emptyMap(),
    val moodsByDay: Map<String, List<Int>> = emptyMap(),
    val fireNotes: Map<String, String> = emptyMap(),
    val fireTended: Set<String> = emptySet(),
    val vows: List<Vow> = defaultVows(),
    val branches: List<Branch> = emptyList(),
    val branchEpochs: Map<String, List<Long>> = emptyMap(),
    val notice: String = "",
)

fun defaultVows(day: String = todayKey()): List<Vow> = listOf(
    Vow("lust", "色欲", 21, day, emptyMap()),
    Vow("greed", "贪财", 21, day, emptyMap()),
)

class RitualStore(context: Context) {
    private val prefs = context.getSharedPreferences("cheng.rituals", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RitualState> = _state.asStateFlow()

    fun setHeight(cm: Int) {
        val value = cm.coerceIn(140, 220)
        update(_state.value.copy(heightCm = value, notice = "身高改为 $value cm"))
    }

    fun setWeight(tenth: Int) {
        val value = tenth.coerceIn(350, 2000)
        update(_state.value.copy(weightTenth = value, notice = "体重改为 ${tenthText(value)} kg"))
    }

    fun setFat(tenth: Int) {
        val value = when {
            tenth <= 0 -> 0
            else -> tenth.coerceIn(50, 600)
        }
        val note = if (value == 0) "体脂改为未填" else "体脂改为 ${tenthText(value)}%"
        update(_state.value.copy(fatTenth = value, notice = note))
    }

    fun setAge(years: Int) {
        val value = years.coerceIn(14, 80)
        update(_state.value.copy(age = value, notice = "年龄改为 $value"))
    }

    fun setMale(male: Boolean) {
        update(_state.value.copy(male = male, notice = if (male) "按男性估算代谢" else "按女性估算代谢"))
    }

    fun setBmrManual(kcal: Int) {
        val value = if (kcal <= 0) 0 else kcal.coerceIn(900, 4000)
        val note = if (value == 0) "基础代谢改回估算" else "基础代谢改为 $value kcal"
        update(_state.value.copy(bmrManual = value, notice = note))
    }

    fun setSeason(key: String) {
        val season = if (key == "cut" || key == "gain") key else "keep"
        update(_state.value.copy(season = season, notice = "饮食阶段改为${seasonLabel(season)}"))
    }

    fun setStepGoal(steps: Int) {
        val goal = steps.coerceIn(2000, 30000)
        update(_state.value.copy(stepGoal = goal, notice = "每日步数改为 $goal"))
    }

    fun setSleepGoal(tenth: Int) {
        val goal = tenth.coerceIn(60, 100)
        update(_state.value.copy(sleepGoalTenth = goal, notice = "睡眠改为 ${tenthText(goal)} 小时"))
    }

    fun setNoteWhenThink(on: Boolean) {
        update(_state.value.copy(noteWhenThink = on, notice = if (on) "思虑时写下那一句" else "思虑时不必写"))
    }

    fun addWater(delta: Int) {
        val day = todayKey()
        val cur = _state.value
        val value = ((cur.waterByDay[day] ?: 0) + delta).coerceIn(0, 8000)
        val verb = if (delta >= 0) "记下" else "退回"
        update(cur.copy(waterByDay = cur.waterByDay + (day to value), notice = "$verb $value ml"))
    }

    fun setWaterGoal(ml: Int) {
        val goal = ml.coerceIn(500, 5000)
        update(_state.value.copy(waterGoalMl = goal, notice = "饮水目标改为 $goal ml"))
    }

    fun setMealGoal(count: Int) {
        val goal = count.coerceIn(1, 5)
        update(_state.value.copy(mealGoal = goal, notice = "每日餐数改为 $goal"))
    }

    fun logMove(kind: String, amount: Int) {
        val cur = _state.value
        val size = amount.coerceAtLeast(1)
        val kcal = moveKcal(kind, size, weightKg(cur))
        val entry = MoveEntry(
            id = System.currentTimeMillis().toString(),
            epoch = System.currentTimeMillis(),
            kind = kind,
            amount = size,
            kcal = kcal,
        )
        val day = todayKey()
        val prior = cur.movesByDay[day].orEmpty()
        val kept = if (kind == "steps") prior.filterNot { it.kind == "steps" } + entry else (prior + entry).takeLast(12)
        update(cur.copy(movesByDay = cur.movesByDay + (day to kept), notice = "记下${moveLabel(kind)} · $kcal kcal"))
    }

    fun undoMove(id: String) {
        val cur = _state.value
        val day = todayKey()
        val list = cur.movesByDay[day].orEmpty()
        if (list.none { it.id == id }) return
        update(cur.copy(movesByDay = cur.movesByDay + (day to list.filterNot { it.id == id }), notice = "已撤回这项运动"))
    }

    fun logNight(hoursTenth: Int, wakes: Int) {
        val cur = _state.value
        val hours = hoursTenth.coerceIn(0, 140)
        val count = wakes.coerceIn(0, 12)
        val note = when {
            hours + 2 < cur.sleepGoalTenth -> "不够 ${tenthText(cur.sleepGoalTenth)} 小时"
            count >= 3 -> "时长够，起来了 $count 次"
            else -> "昨夜收住了"
        }
        val night = NightLog(hours, count)
        update(cur.copy(nightsByDay = cur.nightsByDay + (todayKey() to night), notice = note))
    }

    fun markMood(code: Int) {
        val day = todayKey()
        val cur = _state.value
        val list = (cur.moodsByDay[day].orEmpty() + code.coerceIn(0, fireMarks.lastIndex)).takeLast(6)
        val name = fireMarks[list.last()]
        update(cur.copy(moodsByDay = cur.moodsByDay + (day to list), notice = "观火 · $name"))
    }

    fun undoMood() {
        val day = todayKey()
        val cur = _state.value
        val list = cur.moodsByDay[day].orEmpty()
        if (list.isEmpty()) return
        update(cur.copy(moodsByDay = cur.moodsByDay + (day to list.dropLast(1)), notice = "已撤回最近一次观火"))
    }

    fun setFireNote(value: String) {
        val day = todayKey()
        val cur = _state.value
        update(cur.copy(fireNotes = cur.fireNotes + (day to value.trim().take(40))))
    }

    fun tendFire() {
        val day = todayKey()
        val cur = _state.value
        update(cur.copy(fireTended = cur.fireTended + day, notice = "照做过了"))
    }

    fun markVow(id: String, kept: Boolean) {
        val day = todayKey()
        val cur = _state.value
        val vow = cur.vows.find { it.id == id } ?: return
        val next = if (kept) {
            val anchor = if (vow.anchor.isBlank() || vow.anchor > day) day else vow.anchor
            vow.copy(anchor = anchor, marks = vow.marks + (day to true))
        } else {
            vow.copy(
                anchor = LocalDate.now().plusDays(1).toString(),
                marks = vow.marks + (day to false),
            )
        }
        val note = if (kept) "守住${vow.title}" else "${vow.title}破了，明日起重新计"
        update(cur.copy(vows = cur.vows.map { if (it.id == id) next else it }, notice = note))
    }

    fun setVowSpan(id: String, days: Int) {
        val span = days.coerceIn(1, 180)
        val cur = _state.value
        val vow = cur.vows.find { it.id == id } ?: return
        update(
            cur.copy(
                vows = cur.vows.map { if (it.id == id) it.copy(spanDays = span) else it },
                notice = "${vow.title}的期限改为 $span 天",
            ),
        )
    }

    fun addMeal(path: String, reading: MealReading): String {
        val id = System.currentTimeMillis().toString()
        val entry = MealEntry(
            id = id,
            epoch = System.currentTimeMillis(),
            imagePath = path,
            score = reading.score,
            title = reading.title,
            commentary = reading.commentary,
            produce = reading.produce,
            variety = reading.variety,
            fresh = reading.fresh,
            balance = reading.balance,
            foods = reading.foods,
            usedModel = reading.usedModel,
        )
        val cur = _state.value
        val day = todayKey()
        val kept = ((cur.mealsByDay[day] ?: emptyList()) + entry).takeLast(20)
        update(cur.copy(mealsByDay = cur.mealsByDay + (day to kept), notice = "这一餐 ${reading.score} · ${reading.title}"))
        return id
    }

    fun addIntake(kcal: Int) {
        val amount = kcal.coerceIn(50, 2500)
        val id = System.currentTimeMillis().toString()
        val entry = MealEntry(
            id = id,
            epoch = System.currentTimeMillis(),
            imagePath = "",
            score = 0,
            title = "补记",
            commentary = "热量先记在这里。模型接上之后，可以从照片估。",
            produce = 0,
            variety = 0,
            fresh = 0,
            balance = 0,
            foods = emptyList(),
            usedModel = false,
            kcal = amount,
        )
        val cur = _state.value
        val day = todayKey()
        val kept = ((cur.mealsByDay[day] ?: emptyList()) + entry).takeLast(20)
        update(cur.copy(mealsByDay = cur.mealsByDay + (day to kept), notice = "补记 $amount kcal"))
    }

    fun setMealKcal(id: String, kcal: Int) {
        val cur = _state.value
        val day = todayKey()
        val list = cur.mealsByDay[day].orEmpty()
        if (list.none { it.id == id }) return
        val amount = kcal.coerceIn(0, 4000)
        val next = list.map { if (it.id == id) it.copy(kcal = amount) else it }
        update(cur.copy(mealsByDay = cur.mealsByDay + (day to next), notice = "这餐热量改为 $amount kcal"))
    }

    fun removeMeal(id: String) {
        val cur = _state.value
        val day = todayKey()
        val list = cur.mealsByDay[day].orEmpty()
        val target = list.find { it.id == id } ?: return
        update(
            cur.copy(
                mealsByDay = cur.mealsByDay + (day to list.filterNot { it.id == id }),
                notice = "已撤下这餐",
            ),
        )
        if (target.imagePath.isNotBlank()) File(target.imagePath).delete()
    }

    fun setName(value: String) {
        update(_state.value.copy(name = value.trim().take(12)))
    }

    fun setMondayWeek(monday: Boolean) {
        val note = if (monday) "一周从周一开始" else "一周从周日开始"
        update(_state.value.copy(mondayWeek = monday, notice = note))
    }

    fun addBranch(phase: Phase, title: String, weekly: Int) {
        val name = title.trim().take(16)
        if (name.isEmpty()) return
        val branch = Branch(
            id = System.currentTimeMillis().toString(),
            phase = phase.name,
            title = name,
            weekly = weekly.coerceIn(1, 7),
        )
        val cur = _state.value
        update(cur.copy(branches = cur.branches + branch, notice = "接入${phase.glyph}的分支 · $name"))
    }

    fun removeBranch(id: String) {
        val cur = _state.value
        val branch = cur.branches.find { it.id == id } ?: return
        update(
            cur.copy(
                branches = cur.branches.filterNot { it.id == id },
                branchEpochs = cur.branchEpochs - id,
                notice = "已撤下分支 · ${branch.title}",
            ),
        )
    }

    fun checkBranch(id: String) {
        val cur = _state.value
        val branch = cur.branches.find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val horizon = now - 90L * 24 * 3600 * 1000
        val kept = cur.branchEpochs[id].orEmpty().filter { it >= horizon } + now
        update(cur.copy(branchEpochs = cur.branchEpochs + (id to kept), notice = "记下分支 · ${branch.title}"))
    }

    fun undoBranch(id: String) {
        val cur = _state.value
        val branch = cur.branches.find { it.id == id } ?: return
        val last = cur.branchEpochs[id].orEmpty().filter { inThisWeek(it, cur.mondayWeek) }.maxOrNull() ?: return
        val kept = cur.branchEpochs[id].orEmpty() - last
        update(cur.copy(branchEpochs = cur.branchEpochs + (id to kept), notice = "已撤回分支 · ${branch.title}"))
    }

    private fun update(next: RitualState) {
        _state.value = next
        prefs.edit().putString(KEY, next.toJson()).apply()
    }

    private fun load(): RitualState {
        val raw = prefs.getString(KEY, null) ?: return RitualState()
        return try {
            parse(raw)
        } catch (_: Exception) {
            RitualState()
        }
    }

    private fun RitualState.toJson(): String {
        val root = JSONObject()
        root.put("schema", 2)
        root.put("height", heightCm)
        root.put("weight", weightTenth)
        root.put("fat", fatTenth)
        root.put("age", age)
        root.put("male", male)
        root.put("bmr", bmrManual)
        root.put("season", season)
        root.put("stepGoal", stepGoal)
        root.put("sleepGoal", sleepGoalTenth)
        root.put("thinkNote", noteWhenThink)
        root.put("waterGoal", waterGoalMl)
        root.put("mealGoal", mealGoal)
        root.put("weekMonday", mondayWeek)
        root.put("name", name)
        root.put("water", waterByDay.toJsonInts())
        root.put("meals", mealsByDay.toJsonMeals())
        root.put("moves", movesByDay.toJsonMoves())
        root.put("nights", nightsByDay.toJsonNights())
        root.put("moods", moodsByDay.toJsonMoods())
        root.put("fireNotes", fireNotes.toJsonStrings())
        root.put("fireTended", JSONArray(fireTended.toList()))
        root.put("vows", vows.toVowJson())
        root.put("branches", branches.toBranchJson())
        val marks = JSONObject()
        branchEpochs.forEach { (id, list) -> marks.put(id, JSONArray(list)) }
        root.put("branchMarks", marks)
        return root.toString()
    }

    private fun parse(raw: String): RitualState {
        val root = JSONObject(raw)
        val schema = root.optInt("schema", 1)
        return RitualState(
            heightCm = root.optInt("height", 170).coerceIn(140, 220),
            weightTenth = root.optInt("weight", 650).coerceIn(350, 2000),
            fatTenth = root.optInt("fat", 0).let { if (it in 1..49) 50 else it.coerceIn(0, 600) },
            age = root.optInt("age", 28).coerceIn(14, 80),
            male = root.optBoolean("male", true),
            bmrManual = root.optInt("bmr", 0).let { if (it <= 0) 0 else it.coerceIn(900, 4000) },
            season = root.optString("season", "keep").let { if (it == "cut" || it == "gain") it else "keep" },
            stepGoal = root.optInt("stepGoal", 8000).coerceIn(2000, 30000),
            sleepGoalTenth = root.optInt("sleepGoal", 70).coerceIn(60, 100),
            noteWhenThink = root.optBoolean("thinkNote", true),
            waterGoalMl = root.optInt("waterGoal", 2000).coerceIn(500, 5000),
            mealGoal = root.optInt("mealGoal", 3).coerceIn(1, 5),
            mondayWeek = root.optBoolean("weekMonday", true),
            name = root.optString("name").take(12),
            waterByDay = root.optJSONObject("water").toIntMap(),
            mealsByDay = parseMeals(root.optJSONObject("meals")),
            movesByDay = parseMoves(root.optJSONObject("moves")),
            nightsByDay = parseNights(root.optJSONObject("nights")),
            moodsByDay = parseMoods(root.optJSONObject("moods"), schema),
            fireNotes = root.optJSONObject("fireNotes").toStringMap(),
            fireTended = parseStrings(root.optJSONArray("fireTended")).toSet(),
            vows = ensureVows(parseVows(root.optJSONArray("vows"))),
            branches = parseBranches(root.optJSONArray("branches")),
            branchEpochs = parseBranchMarks(root.optJSONObject("branchMarks")),
        )
    }

    private fun ensureVows(parsed: List<Vow>): List<Vow> {
        val known = parsed.associateBy { it.id }
        val builtins = defaultVows().map { seed -> known[seed.id] ?: seed }
        val extra = parsed.filter { it.id != "lust" && it.id != "greed" }
        return builtins + extra
    }

    private fun Map<String, Int>.toJsonInts(): JSONObject {
        val obj = JSONObject()
        forEach { (key, value) -> obj.put(key, value) }
        return obj
    }

    private fun Map<String, String>.toJsonStrings(): JSONObject {
        val obj = JSONObject()
        forEach { (key, value) -> obj.put(key, value) }
        return obj
    }

    private fun Map<String, List<Int>>.toJsonMoods(): JSONObject {
        val obj = JSONObject()
        forEach { (day, list) -> obj.put(day, JSONArray(list)) }
        return obj
    }

    private fun Map<String, List<MealEntry>>.toJsonMeals(): JSONObject {
        val meals = JSONObject()
        forEach { (day, list) ->
            val arr = JSONArray()
            list.forEach { meal ->
                arr.put(
                    JSONObject().apply {
                        put("id", meal.id)
                        put("epoch", meal.epoch)
                        put("path", meal.imagePath)
                        put("score", meal.score)
                        put("title", meal.title)
                        put("note", meal.commentary)
                        put("produce", meal.produce)
                        put("variety", meal.variety)
                        put("fresh", meal.fresh)
                        put("balance", meal.balance)
                        put("model", meal.usedModel)
                        put("kcal", meal.kcal)
                        put("foods", JSONArray(meal.foods))
                    },
                )
            }
            meals.put(day, arr)
        }
        return meals
    }

    private fun Map<String, List<MoveEntry>>.toJsonMoves(): JSONObject {
        val obj = JSONObject()
        forEach { (day, list) ->
            val arr = JSONArray()
            list.forEach { move ->
                arr.put(
                    JSONObject().apply {
                        put("id", move.id)
                        put("epoch", move.epoch)
                        put("kind", move.kind)
                        put("amount", move.amount)
                        put("kcal", move.kcal)
                    },
                )
            }
            obj.put(day, arr)
        }
        return obj
    }

    private fun Map<String, NightLog>.toJsonNights(): JSONObject {
        val obj = JSONObject()
        forEach { (day, night) ->
            obj.put(
                day,
                JSONObject().apply {
                    put("hours", night.hoursTenth)
                    put("wakes", night.wakes)
                },
            )
        }
        return obj
    }

    private fun List<Vow>.toVowJson(): JSONArray {
        val arr = JSONArray()
        forEach { vow ->
            val marks = JSONObject()
            vow.marks.forEach { (day, kept) -> marks.put(day, kept) }
            arr.put(
                JSONObject().apply {
                    put("id", vow.id)
                    put("title", vow.title)
                    put("span", vow.spanDays)
                    put("anchor", vow.anchor)
                    put("marks", marks)
                },
            )
        }
        return arr
    }

    private fun List<Branch>.toBranchJson(): JSONArray {
        val arr = JSONArray()
        forEach { branch ->
            arr.put(
                JSONObject().apply {
                    put("id", branch.id)
                    put("phase", branch.phase)
                    put("title", branch.title)
                    put("weekly", branch.weekly)
                },
            )
        }
        return arr
    }

    private fun parseMeals(obj: JSONObject?): Map<String, List<MealEntry>> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, List<MealEntry>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val arr = obj.optJSONArray(day) ?: continue
            out[day] = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val foodsJson = item.optJSONArray("foods")
                    val foods = buildList {
                        if (foodsJson != null) {
                            for (j in 0 until foodsJson.length()) add(foodsJson.optString(j))
                        }
                    }.filter { it.isNotBlank() }
                    add(
                        MealEntry(
                            id = item.optString("id", "$day-$i"),
                            epoch = item.optLong("epoch"),
                            imagePath = item.optString("path"),
                            score = item.optInt("score"),
                            title = item.optString("title", "未读"),
                            commentary = item.optString("note"),
                            produce = item.optInt("produce"),
                            variety = item.optInt("variety"),
                            fresh = item.optInt("fresh"),
                            balance = item.optInt("balance"),
                            foods = foods,
                            usedModel = item.optBoolean("model"),
                            kcal = item.optInt("kcal"),
                        ),
                    )
                }
            }
        }
        return out
    }

    private fun parseMoves(obj: JSONObject?): Map<String, List<MoveEntry>> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, List<MoveEntry>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val arr = obj.optJSONArray(day) ?: continue
            out[day] = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val kind = item.optString("kind")
                    if (kind != "lift" && kind != "cardio" && kind != "steps") continue
                    add(
                        MoveEntry(
                            id = item.optString("id", "$day-$i"),
                            epoch = item.optLong("epoch"),
                            kind = kind,
                            amount = item.optInt("amount"),
                            kcal = item.optInt("kcal"),
                        ),
                    )
                }
            }
        }
        return out
    }

    private fun parseNights(obj: JSONObject?): Map<String, NightLog> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, NightLog>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val item = obj.optJSONObject(day) ?: continue
            out[day] = NightLog(
                hoursTenth = item.optInt("hours").coerceIn(0, 140),
                wakes = item.optInt("wakes").coerceIn(0, 12),
            )
        }
        return out
    }

    private fun parseMoods(obj: JSONObject?, schema: Int): Map<String, List<Int>> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, List<Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val arr = obj.optJSONArray(day) ?: continue
            out[day] = buildList {
                for (i in 0 until arr.length()) {
                    val raw = arr.optInt(i)
                    val code = if (schema < 2 && raw == 0) 1 else raw
                    add(code.coerceIn(0, fireMarks.lastIndex))
                }
            }
        }
        return out
    }

    private fun parseVows(arr: JSONArray?): List<Vow> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isEmpty()) continue
                add(
                    Vow(
                        id = item.optString("id", "v$i"),
                        title = title.take(8),
                        spanDays = item.optInt("span", 21).coerceIn(1, 180),
                        anchor = item.optString("anchor"),
                        marks = item.optJSONObject("marks").toBoolMap(),
                    ),
                )
            }
        }
    }

    private fun parseBranches(arr: JSONArray?): List<Branch> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isEmpty() || phaseOf(item.optString("phase")) == null) continue
                add(
                    Branch(
                        id = item.optString("id", "b$i"),
                        phase = item.optString("phase"),
                        title = title.take(16),
                        weekly = item.optInt("weekly", 3).coerceIn(1, 7),
                    ),
                )
            }
        }
    }

    private fun parseBranchMarks(obj: JSONObject?): Map<String, List<Long>> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, List<Long>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            out[id] = parseLongs(obj.optJSONArray(id))
        }
        return out
    }

    private fun parseLongs(arr: JSONArray?): List<Long> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(arr.optLong(i))
        }
    }

    private fun parseStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(arr.optString(i))
        }.filter { it.isNotBlank() }
    }

    private fun JSONObject?.toIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<String, Int>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = optInt(key)
        }
        return out
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = optString(key).take(40)
        }
        return out
    }

    private fun JSONObject?.toBoolMap(): Map<String, Boolean> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<String, Boolean>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = optBoolean(key)
        }
        return out
    }

    private companion object {
        const val KEY = "json"
    }
}
