package studio.cheng.daily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cheng.daily.data.RitualState
import studio.cheng.daily.data.dayEnergy
import studio.cheng.daily.data.dayScore
import studio.cheng.daily.data.energyLine
import studio.cheng.daily.data.gapMark
import studio.cheng.daily.data.monthCells
import studio.cheng.daily.data.monthDates
import studio.cheng.daily.data.phaseNow
import studio.cheng.daily.data.scoreWord
import studio.cheng.daily.data.spanScore
import studio.cheng.daily.data.weekdayLabels
import java.time.LocalDate

@Composable
fun LedgerPane(state: RitualState) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var picked by remember { mutableStateOf(today) }
    val cells = monthCells(month, state.mondayWeek)
    val monthScore = spanScore(state, monthDates(month), today)
    val shown = if (picked.month == month.month && picked.year == month.year) picked else month
    val futureMonth = !month.isBefore(today.withDayOfMonth(1))
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                InkText("${month.year}年${month.monthValue}月", size = 28.sp, display = true)
                InkText(
                    "月均 ${monthScore.average} · ${scoreWord(monthScore.average)}",
                    modifier = Modifier.padding(top = 2.dp),
                    size = 13.sp,
                    color = Ink.bronze,
                )
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seal("上一月", "看上一月", {
                val next = month.minusMonths(1)
                month = next
                picked = next
            })
            Seal(
                "下一月",
                "看下一月",
                {
                    val next = month.plusMonths(1)
                    month = next
                    picked = if (next.month == today.month && next.year == today.year) today else next
                },
                enabled = !futureMonth,
            )
        }
        InkText(
            "在轨 20，偏弱 10，过头 6，空着 0。一天五行合计一百分。",
            modifier = Modifier.padding(top = 12.dp),
            size = 12.sp,
            color = Ink.faint,
            line = 18.sp,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            weekdayLabels(state.mondayWeek).forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    InkText(label, size = 12.sp, color = Ink.muted)
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        today = today,
                        score = day?.let { dayScore(state, it.toString()) } ?: 0,
                        gap = day?.let { gapMark(dayEnergy(state, it.toString())) }.orEmpty(),
                        picked = day != null && day == picked,
                        onPick = { picked = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
        DaySlip(state, shown, Modifier.padding(top = 18.dp, bottom = 18.dp))
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    today: LocalDate,
    score: Int,
    gap: String,
    picked: Boolean,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Box(modifier.padding(vertical = 8.dp))
        return
    }
    val ahead = day.isAfter(today)
    Box(
        modifier
            .padding(2.dp)
            .border(1.dp, if (day == today || picked) Ink.bronze else Ink.line, CircleShape)
            .background(if (picked) Ink.bronze.copy(alpha = 0.16f) else Color.Transparent, CircleShape)
            .clickable(
                remember { MutableInteractionSource() },
                null,
                enabled = !ahead,
                role = Role.Button,
                onClick = { onPick(day) },
            )
            .semantics {
                contentDescription = "${day.monthValue}月${day.dayOfMonth}日 $score 分 ${if (gap.isBlank()) "缺口未记" else gap}"
            }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InkText(
                "${day.dayOfMonth}",
                size = 13.sp,
                color = if (ahead) Ink.faint else Ink.text,
            )
            if (!ahead && gap.isNotBlank()) {
                InkText(gap, modifier = Modifier.padding(top = 1.dp), size = 9.sp, color = Ink.bronze)
            }
        }
    }
}

@Composable
private fun DaySlip(state: RitualState, day: LocalDate, modifier: Modifier = Modifier) {
    val ahead = day.isAfter(LocalDate.now())
    val score = if (ahead) 0 else dayScore(state, day.toString())
    val rows = phaseNow(state, day.toString())
    Column(modifier.fillMaxWidth()) {
        InkText("${day.monthValue}月${day.dayOfMonth}日", size = 12.sp, color = Ink.muted, spacing = 2.sp)
        InkText(if (ahead) "还没到" else "$score", size = 40.sp, display = true)
        if (!ahead) {
            InkText(scoreWord(score), modifier = Modifier.padding(top = 2.dp), size = 13.sp, color = Ink.bronze)
            InkText(
                energyLine(state, day.toString()),
                modifier = Modifier.padding(top = 8.dp),
                size = 13.sp,
                color = Ink.bronze,
                line = 20.sp,
            )
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkText(row.phase.glyph, size = 18.sp, color = row.phase.tint(), display = true)
                    InkText(row.phase.practice, modifier = Modifier.padding(start = 10.dp).weight(1f), size = 14.sp)
                    InkText(row.value, size = 14.sp, color = Ink.muted)
                }
            }
        }
    }
}
