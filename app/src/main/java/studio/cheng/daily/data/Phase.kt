package studio.cheng.daily.data

enum class Phase(
    val glyph: String,
    val practice: String,
    val office: String,
    val quote: String,
    val verse: String,
    val feeds: String,
    val reins: String,
) {
    Wood(
        "木",
        "运动",
        "升发、舒展",
        "曲直复伸",
        "身体的活动与气血。适度则流通，不足则郁，过量则透支。",
        "木生火：动一动，情绪才有处去。",
        "木克土：运动帮运化，过度则伤脾胃。",
    ),
    Fire(
        "火",
        "情绪",
        "神明、感知",
        "知焰不焚",
        "心神与喜怒。平和则安，躁动是旺，低落是弱。",
        "火生土：心神安，脾胃才吸收得好。",
        "火克金：心火不停，夜里就收不住。",
    ),
    Earth(
        "土",
        "饮食",
        "承载、运化",
        "稼穑在盘",
        "吃喝与津液都归土。有度才能运化，过满就会阻滞。",
        "土生金：吃喝有度，夜里才收得成觉。",
        "土克水：饮食清淡，虚浮的欲才压得住。",
    ),
    Metal(
        "金",
        "睡眠",
        "收敛、肃降",
        "入夜收金",
        "把白天散出去的气血心神收回来。不够七小时，收敛就无力。",
        "金生水：睡够了，精气才守得住欲。",
        "金克木：觉不够还硬动，气机会被耗伤。",
    ),
    Water(
        "水",
        "欲望",
        "潜藏、蓄藏",
        "潜藏有源",
        "色欲与贪欲都属水。适度则精气内敛，过盛则持续耗损。",
        "水生木：精气蓄得住，才有力气去运动。",
        "水克火：欲不过度，才压得住心烦。",
    ),
}

fun Phase.bears(): Phase = Phase.entries[(ordinal + 1) % Phase.entries.size]

fun Phase.restrains(): Phase = Phase.entries[(ordinal + 2) % Phase.entries.size]

fun phaseOf(key: String): Phase? = Phase.entries.find { it.name == key }

val fireMarks = listOf("弱", "平", "旺", "思")

fun fireHint(code: Int): String = when (code) {
    0 -> "火弱。先见一点光，或走出两分钟。"
    1 -> "火是平的。不用添柴，也不用压。"
    2 -> "火旺。先停一次，不处理眼前的事。"
    else -> "思虑不停。写成一句，今晚才收得住。"
}

enum class Tide { Empty, Low, Even, High }

fun seasonLabel(key: String): String = when (key) {
    "cut" -> "减脂期"
    "gain" -> "增肌期"
    else -> "维持期"
}

fun moveLabel(kind: String): String = when (kind) {
    "lift" -> "无氧"
    "cardio" -> "有氧"
    "steps" -> "步数"
    else -> "活动"
}
