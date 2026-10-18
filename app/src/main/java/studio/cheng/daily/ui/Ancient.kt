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

enum class Ancient(
    val courtesy: String,
    val person: String,
    val method: String,
    val origin: String,
    val line: String,
) {
    Wang(
        "伯安",
        "王阳明",
        "心法",
        "余姚 · 明",
        "圣人之道，吾性自足",
    ),
    Di(
        "怀英",
        "狄仁杰",
        "清正",
        "并州 · 唐",
        "断狱无冤，举贤为国",
    ),
    Sima(
        "君实",
        "司马光",
        "勤俭",
        "夏县 · 宋",
        "平生所为，未尝有不可对人言者",
    ),
    Anshi(
        "介甫",
        "王安石",
        "变法",
        "临川 · 宋",
        "自奉甚俭，心思在公事上",
    ),
    Zeng(
        "伯涵",
        "曾国藩",
        "日课",
        "湘乡 · 清",
        "不为圣贤，便为禽兽",
    ),
}

private data class Passage(val title: String, val body: String)

private val wangPassages = listOf(
    Passage(
        "格竹",
        "年轻时听朱子讲，一草一木都有理。他便坐到竹子前头格物，格了七天，人病倒了。竹子还是竹子。理不在外头的物上。",
    ),
    Passage(
        "龙场",
        "后来得罪贬到贵州龙场，瘴疠、生死都放在眼前。一夜忽然明白：圣人之道，吾性自足，不必向外头寻。这就是心即理。道在自己心里，不在书堆里等着人搬回来。",
    ),
    Passage(
        "知行合一",
        "在贵阳讲学，他把这句话定死。知是行的主意，行是知的功夫。知而不行，只是未知。嘴里说得出孝，身上做不到，那还不算知道孝。",
    ),
    Passage(
        "致良知",
        "平宁王之后，晚年只提这一句。良知是自己分得清善恶的那一点，不是从外面借来的。致，是把它带到今天的事上。放在心里供着，不算致。",
    ),
    Passage(
        "四句教",
        "对学生说：无善无恶心之体，有善有恶意之动，知善知恶是良知，为善去恶是格物。心本来静，意一动就有了善恶。能照见这一动的，是良知。照见了还要去做，才是格物。",
    ),
)

private val zengPassages = listOf(
    Passage(
        "日课",
        "在北京时，唐鉴对他说，诗词好，可这不是正事。他便立了十二课：主敬、静坐、早起、读书不二、读史、谨言、养气、保身、日知其所亡、月无忘所能、作字、夜不出门。一天过完，用这十二条对着查。",
    ),
    Passage(
        "日记",
        "课是死的，日记是活的。求阙斋日记里，当天的过失当天写：晏起、多言、好名，一句不饶。知己之过失，即自为承认之地。瞒自己，功课就断了。这是他的不自欺。",
    ),
    Passage(
        "早起",
        "十二条里他最常犯的是早起。日记里反复写晏起，第二天还是把早起立回去。破了再来，不算把课废掉。",
    ),
    Passage(
        "军中",
        "后来带湘军，胜败都在身上，日记没有停。不是打完仗再回去修养，是仗还在打，课还在做。事上磨练，他用日记做成了每天的格式。",
    ),
    Passage(
        "不自欺",
        "他读过王阳明，把致良知收成一句更笨的话：不自欺。良知是分得清，不自欺是当天就承认。承认了还要改，跟知行合一是同一刀。",
    ),
)

private val diPassages = listOf(
    Passage(
        "断狱",
        "他做大理丞的那一年，清理积压的案子一万七千人，没有人喊冤。好的一面在这里：刀用在案卷上，不冤枉人。这是清，不是每天写日记骂自己。",
    ),
    Passage(
        "止造像",
        "武则天要铸大佛，花费摊到百姓身上。他站出来说，工役一起，民何以堪。好的一面是肯为还没说话的人挡一道。",
    ),
    Passage(
        "举贤",
        "他荐张柬之、姚崇、桓彦范、敬晖这一班人，后来办成了恢复唐朝的事。自己的位子不是终点，把能办事的人推上去，才是他留给后面的东西。",
    ),
)

private val simaPassages = listOf(
    Passage(
        "警枕",
        "他用圆木做枕头。睡沉了，枕头一滚，人就醒，起来接着读。后人叫它警枕。好的一面是把时间看得紧，不拿睡眠把白天的功课混过去。",
    ),
    Passage(
        "通鉴",
        "《资治通鉴》写了十九年，从战国写到五代。一天的进度有时只有几百字，他不肯把没核过的句子交出去。好的一面是久，一件事肯做完。",
    ),
    Passage(
        "训俭",
        "他写《训俭示康》给儿子，说由俭入奢易，由奢入俭难。自己做官，家里不摆排场。好的一面是俭能传下去，不只自己装一回清淡。",
    ),
    Passage(
        "可对人言",
        "他说：吾无过人者，但平生所为，未尝有不可对人言者。好的一面是行事经得起打开。跟不自欺相近，不过他查的是能不能对人说，不是当天的日记。",
    ),
)

private val anshiPassages = listOf(
    Passage(
        "不置产",
        "做到宰相，仍不给家里置产业，衣食随手放着，不讲究。好的一面是俸禄没有变成家私。这是自奉俭，不是一套每天自省的功课。",
    ),
    Passage(
        "新法",
        "青苗、募役，用意是不让农户再去借那种吃人的利，也不让差役只压在没背景的人身上。好的一面是他把书上的想法拿去碰真的赋税和户口。后来有的法办走了样，那是后话，起意在宽民力。",
    ),
    Passage(
        "拗",
        "别人叫他拗相公。认定的公事，人情求不动。好的一面是不拿国法送人情。坏处也在这同一个地方：听不进不同的话。这一篇只取他肯把自己押在政策上的那一面。",
    ),
)

private fun Ancient.passages(): List<Passage> = when (this) {
    Ancient.Di -> diPassages
    Ancient.Sima -> simaPassages
    Ancient.Anshi -> anshiPassages
    Ancient.Wang -> wangPassages
    Ancient.Zeng -> zengPassages
}

@Composable
fun AncientIndex(onOpen: (Ancient) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PaneHeader("觉古", "古往今来", "")
        InkText(
            "一篇一个人。各取他好的那一面，不把不同的人写成同一种。",
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            size = 13.sp,
            color = Ink.faint,
            line = 20.sp,
        )
        Ancient.entries.forEach { person ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clickable(
                        remember { MutableInteractionSource() },
                        null,
                        role = Role.Button,
                        onClick = { onOpen(person) },
                    )
                    .semantics { contentDescription = "打开${person.courtesy}${person.person}" },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkText(person.courtesy, size = 22.sp, color = Ink.bronze, display = true)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        InkText(person.person, size = 18.sp)
                        InkText("${person.origin} · ${person.method}", size = 12.sp, color = Ink.muted)
                    }
                }
                InkText(
                    person.line,
                    modifier = Modifier.padding(top = 8.dp),
                    size = 14.sp,
                    color = Ink.bronze,
                    line = 22.sp,
                )
            }
        }
    }
}

@Composable
fun AncientBody(person: Ancient, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Seal("收回", "回到觉古", onBack)
            Column(Modifier.padding(start = 14.dp)) {
                InkText("${person.courtesy} · ${person.person}", size = 16.sp, display = true)
                InkText(person.method, modifier = Modifier.padding(top = 2.dp), size = 12.sp, color = Ink.muted)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            InkText(person.line, size = 22.sp, color = Ink.bronze, display = true, line = 32.sp)
            InkText(
                person.origin,
                modifier = Modifier.padding(top = 6.dp),
                size = 13.sp,
                color = Ink.faint,
            )
            person.passages().forEach { passage ->
                InkText(passage.title, modifier = Modifier.padding(top = 22.dp), size = 12.sp, color = Ink.muted, spacing = 3.sp)
                InkText(
                    passage.body,
                    modifier = Modifier.padding(top = 8.dp),
                    size = 15.sp,
                    line = 26.sp,
                )
            }
            InkText(
                "取他这一面，用到自己今天的事上。其余的，不要硬套。",
                modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
                size = 13.sp,
                color = Ink.faint,
                line = 22.sp,
            )
        }
    }
}
