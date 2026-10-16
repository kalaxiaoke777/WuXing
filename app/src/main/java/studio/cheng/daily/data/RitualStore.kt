package studio.cheng.daily.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
)

data class RitualState(
    val waterGoalMl: Int = 2000,
    val strengthGoal: Int = 4,
    val sleepHour: Int = 23,
    val sleepMinute: Int = 30,
    val mealGoal: Int = 3,
    val moodGoal: Int = 1,
    val mondayWeek: Boolean = true,
    val name: String = "",
    val waterByDay: Map<String, Int> = emptyMap(),
    val sleepByDay: Map<String, Int> = emptyMap(),
    val moodsByDay: Map<String, List<Int>> = emptyMap(),
    val mealsByDay: Map<String, List<MealEntry>> = emptyMap(),
    val strengthEpochs: List<Long> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val branchEpochs: Map<String, List<Long>> = emptyMap(),
    val notice: String = "",
)

class RitualStore(context: Context) {
    private val prefs = context.getSharedPreferences("cheng.rituals", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RitualState> = _state.asStateFlow()

    fun addWater(delta: Int) {
        val day = todayKey()
        val cur = _state.value
        val value = ((cur.waterByDay[day] ?: 0) + delta).coerceIn(0, 8000)
        val verb = if (delta >= 0) "记下" else "退回"
        update(cur.copy(waterByDay = cur.waterByDay + (day to value), notice = "$verb $value ml"))
    }

    fun setWaterGoal(ml: Int) {
        update(_state.value.copy(waterGoalMl = ml.coerceIn(500, 5000), notice = "饮水目标改为 $ml ml"))
    }

    fun addStrength() {
        val now = System.currentTimeMillis()
        val cur = _state.value
        val horizon = now - 90L * 24 * 3600 * 1000
        val pruned = cur.strengthEpochs.filter { it >= horizon } + now
        update(cur.copy(strengthEpochs = pruned, notice = "已记下一次运动"))
    }

    fun undoStrength() {
        val cur = _state.value
        val last = cur.strengthEpochs.filter { inThisWeek(it, cur.mondayWeek) }.maxOrNull() ?: return
        update(cur.copy(strengthEpochs = cur.strengthEpochs - last, notice = "已撤回最近一次运动"))
    }

    fun setStrengthGoal(count: Int) {
        val goal = count.coerceIn(1, 7)
        update(_state.value.copy(strengthGoal = goal, notice = "每周运动改为 $goal 次"))
    }

    fun setSleep(hour: Int, minute: Int) {
        update(
            _state.value.copy(
                sleepHour = hour.mod(24),
                sleepMinute = minute.coerceIn(0, 59),
                notice = "入眠约定改为 ${clock(hour.mod(24), minute.coerceIn(0, 59))}",
            ),
        )
    }

    fun checkSleep(minuteOfDay: Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }) {
        val cur = _state.value
        val target = cur.sleepHour * 60 + cur.sleepMinute
        val note = if (onTime(minuteOfDay, target)) "准点入眠" else "已记下，偏离约定"
        update(cur.copy(sleepByDay = cur.sleepByDay + (todayKey() to minuteOfDay), notice = note))
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
        File(target.imagePath).delete()
    }

    fun setMealGoal(count: Int) {
        val goal = count.coerceIn(1, 5)
        update(_state.value.copy(mealGoal = goal, notice = "每日饮食改为 $goal 餐"))
    }

    fun setMoodGoal(count: Int) {
        val goal = count.coerceIn(1, 3)
        update(_state.value.copy(moodGoal = goal, notice = "每日情绪改为 $goal 次"))
    }

    fun markMood(code: Int) {
        val day = todayKey()
        val cur = _state.value
        val list = (cur.moodsByDay[day].orEmpty() + code.coerceIn(0, moodMarks.lastIndex)).takeLast(6)
        val name = moodMarks[list.last()]
        update(cur.copy(moodsByDay = cur.moodsByDay + (day to list), notice = "记下此刻的火 · $name"))
    }

    fun undoMood() {
        val day = todayKey()
        val cur = _state.value
        val list = cur.moodsByDay[day].orEmpty()
        if (list.isEmpty()) return
        update(cur.copy(moodsByDay = cur.moodsByDay + (day to list.dropLast(1)), notice = "已撤回最近一次情绪"))
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
        root.put("waterGoal", waterGoalMl)
        root.put("strengthGoal", strengthGoal)
        root.put("sleepH", sleepHour)
        root.put("sleepM", sleepMinute)
        root.put("mealGoal", mealGoal)
        root.put("moodGoal", moodGoal)
        root.put("weekMonday", mondayWeek)
        root.put("name", name)
        val water = JSONObject()
        waterByDay.forEach { (k, v) -> water.put(k, v) }
        root.put("water", water)
        val sleep = JSONObject()
        sleepByDay.forEach { (k, v) -> sleep.put(k, v) }
        root.put("sleep", sleep)
        val moods = JSONObject()
        moodsByDay.forEach { (day, list) -> moods.put(day, JSONArray(list)) }
        root.put("moods", moods)
        val meals = JSONObject()
        mealsByDay.forEach { (day, list) ->
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
                        put("foods", JSONArray(meal.foods))
                    },
                )
            }
            meals.put(day, arr)
        }
        root.put("meals", meals)
        root.put("strength", JSONArray(strengthEpochs))
        val branchesJson = JSONArray()
        branches.forEach { branch ->
            branchesJson.put(
                JSONObject().apply {
                    put("id", branch.id)
                    put("phase", branch.phase)
                    put("title", branch.title)
                    put("weekly", branch.weekly)
                },
            )
        }
        root.put("branches", branchesJson)
        val marks = JSONObject()
        branchEpochs.forEach { (id, list) -> marks.put(id, JSONArray(list)) }
        root.put("branchMarks", marks)
        return root.toString()
    }

    private fun parse(raw: String): RitualState {
        val root = JSONObject(raw)
        return RitualState(
            waterGoalMl = root.optInt("waterGoal", 2000).coerceIn(500, 5000),
            strengthGoal = root.optInt("strengthGoal", 4).coerceIn(1, 7),
            sleepHour = root.optInt("sleepH", 23).mod(24),
            sleepMinute = root.optInt("sleepM", 30).coerceIn(0, 59),
            mealGoal = root.optInt("mealGoal", 3).coerceIn(1, 5),
            moodGoal = root.optInt("moodGoal", 1).coerceIn(1, 3),
            mondayWeek = root.optBoolean("weekMonday", true),
            name = root.optString("name").take(12),
            waterByDay = root.optJSONObject("water").toIntMap(),
            sleepByDay = root.optJSONObject("sleep").toIntMap(),
            moodsByDay = parseMoods(root.optJSONObject("moods")),
            mealsByDay = parseMeals(root.optJSONObject("meals")),
            strengthEpochs = parseLongs(root.optJSONArray("strength")),
            branches = parseBranches(root.optJSONArray("branches")),
            branchEpochs = parseBranchMarks(root.optJSONObject("branchMarks")),
        )
    }

    private fun parseMeals(obj: JSONObject?): Map<String, List<MealEntry>> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, List<MealEntry>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val arr = obj.optJSONArray(day) ?: continue
            val list = buildList {
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
                        ),
                    )
                }
            }
            out[day] = list
        }
        return out
    }

    private fun parseMoods(obj: JSONObject?): Map<String, List<Int>> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, List<Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val arr = obj.optJSONArray(day) ?: continue
            out[day] = buildList {
                for (i in 0 until arr.length()) add(arr.optInt(i).coerceIn(0, moodMarks.lastIndex))
            }
        }
        return out
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

    private companion object {
        const val KEY = "json"
    }
}
