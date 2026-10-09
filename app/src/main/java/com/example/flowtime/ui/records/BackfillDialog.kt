package com.example.flowtime.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.DayQuickChip
import com.example.flowtime.ui.components.DurationWheelBlock
import com.example.flowtime.ui.components.HourMinuteWheel
import com.example.flowtime.ui.components.PastDateWheel
import com.example.flowtime.ui.components.WheelHeight
import com.example.flowtime.ui.components.WheelSelectionBand
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pastDayLabel
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 补录时间（一步式底部弹层）——任务详情页和首页共用。
 *
 * 口径：**结束时刻是锚点**，日期 chip 选的是「结束日」，开始时刻 = 结束 − 时长。
 * 跨天溢出放行：开始时刻落到前一天不钳制、不警告。
 * 选「今天」时结束时刻不能拨到未来（滚轮上限 = 此刻，超出则结束钉在此刻、时长不变）。
 *
 * 重叠检测只在**同一任务内**比对，命中只警告不阻止——同一时段补两次是用户的权利，
 * 只是让他知道自己正在这么做。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackfillDialog(
    taskId: String?,
    taskName: String?,
    records: List<FocusRecord>,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val nowMillis = remember { System.currentTimeMillis() }

    // 初始值：按这个任务的历史习惯猜（高频结束小时 + 常用时长），无历史则「现在 + 30 分钟」
    val defaults = remember(taskId, records) {
        BackfillRules.defaultsFor(records, taskId, nowMillis, zone)
    }
    var daysAgo by remember { mutableStateOf(defaults.daysAgo) }
    var showDateWheel by remember { mutableStateOf(false) }
    var endHour by remember { mutableStateOf(defaults.endHour) }
    var endMinute by remember { mutableStateOf(defaults.endMinute) }
    var duration by remember { mutableStateOf(defaults.durationMinutes) }

    val date = today.minusDays(daysAgo.toLong())
    val capMinutes = BackfillRules.endCapMinutes(date, nowMillis, zone)

    // 切到「今天」时，把已经拨到未来的结束时刻拉回此刻（否则滚轮和落库值会对不上）
    LaunchedEffect(daysAgo) {
        val cap = capMinutes
        if (endHour * 60 + endMinute > cap) {
            endHour = cap / 60
            endMinute = cap % 60
        }
    }

    val span = BackfillRules.resolve(date, endHour, endMinute, duration, nowMillis, zone)
    val conflicts = BackfillRules.overlapping(records, taskId, span)
    val crosses = BackfillRules.crossesDay(span, zone)
    val valid = span.minutes >= 1

    val startZ = Instant.ofEpochMilli(span.startMillis).atZone(zone)
    val endZ = Instant.ofEpochMilli(span.endMillis).atZone(zone)
    val preview = "%02d-%02d %02d:%02d → %s%02d:%02d（%s）".format(
        startZ.monthValue, startZ.dayOfMonth, startZ.hour, startZ.minute,
        if (crosses) "次日 " else "",
        endZ.hour, endZ.minute,
        formatMinutesHm(span.minutes),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperElevated,
    ) {
        BlurDialogBehind(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ListBottomGap),
        ) {
            Text("补录时间", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (taskName != null) "记到「$taskName」" else "记到未分类（自由专注）",
                fontSize = 12.sp,
                color = TextSecondary,
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 1, 2).forEach { offset ->
                    DayQuickChip(
                        label = pastDayLabel(today, offset),
                        selected = daysAgo == offset,
                        onClick = {
                            daysAgo = offset
                            showDateWheel = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                DayQuickChip(
                    label = if (daysAgo > 2) pastDayLabel(today, daysAgo) else "更多",
                    selected = daysAgo > 2,
                    onClick = { showDateWheel = !showDateWheel },
                    modifier = Modifier.weight(1f),
                )
            }

            // 「更多」就地展开日期滚轮，不再套一层弹窗
            if (showDateWheel) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(WheelHeight), contentAlignment = Alignment.Center) {
                    WheelSelectionBand()
                    PastDateWheel(
                        daysAgo = daysAgo,
                        onDaysAgoChange = { daysAgo = it },
                        today = today,
                        modifier = Modifier.width(130.dp),
                        fadeColor = PaperElevated,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("结束时间", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            HourMinuteWheel(
                hours = endHour,
                minutes = endMinute,
                onChange = { h, m ->
                    endHour = h
                    endMinute = m
                },
                // 今天只能到此刻；其它日期 00:00-23:59 全开放
                maxTotalMinutes = capMinutes,
                columnWidth = 62.dp,
                fadeColor = PaperElevated,
            )
            if (daysAgo == 0) {
                Spacer(Modifier.height(6.dp))
                Text("今天不能补还没发生的时间", fontSize = 11.sp, color = TextSecondary)
            }

            Spacer(Modifier.height(16.dp))
            Text("时长", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            DurationWheelBlock(
                minutes = duration,
                onChange = { duration = it.coerceIn(0, BackfillRules.MAX_MINUTES) },
                maxTotalMinutes = BackfillRules.MAX_MINUTES,
                fadeColor = PaperElevated,
            )

            Spacer(Modifier.height(10.dp))
            Text(preview, fontSize = 12.sp, color = TextSecondary)

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val first = conflicts.first()
                val cs = Instant.ofEpochMilli(first.startEpochMillis).atZone(zone)
                val ce = Instant.ofEpochMilli(first.endEpochMillis).atZone(zone)
                Text(
                    "该时段与已有 ${conflicts.size} 条记录重叠" +
                        "（%02d:%02d-%02d:%02d，%s），确认后会重复计入".format(
                            cs.hour, cs.minute, ce.hour, ce.minute,
                            formatMinutesHm(first.minutes),
                        ),
                    fontSize = 12.sp,
                    color = WarningAmber,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(span.startMillis, span.endMillis) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight),
                shape = ActivePalette.buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(
                    "补录",
                    fontSize = 15.sp,
                    color = if (valid) Color.White else DisabledGray,
                )
            }
        }
    }
}
