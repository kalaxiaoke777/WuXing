package studio.cheng.daily.data

enum class Phase(
    val glyph: String,
    val practice: String,
    val quote: String,
    val verse: String,
) {
    Water(
        "水",
        "饮水",
        "上善若水",
        "水善利万物。杯中有度，一日可以慢慢满上。",
    ),
    Fire(
        "火",
        "情绪",
        "知焰不焚",
        "火曰炎上。先看见这簇火，再决定要不要添柴。",
    ),
    Metal(
        "金",
        "睡眠",
        "入夜收金",
        "金曰从革。到了约定的时辰，把白日收成一块。",
    ),
    Earth(
        "土",
        "饮食",
        "稼穑在盘",
        "土爰稼穑。这一餐落在纸上，才算种进今天。",
    ),
    Wood(
        "木",
        "运动",
        "曲直复伸",
        "木曰曲直。屈一次，伸一次，枝干才立得住。",
    ),
}

data class Branch(
    val id: String,
    val phase: String,
    val title: String,
    val weekly: Int,
)

fun phaseOf(key: String): Phase? = Phase.entries.find { it.name == key }

val moodMarks = listOf("静", "温", "旺", "燥")
