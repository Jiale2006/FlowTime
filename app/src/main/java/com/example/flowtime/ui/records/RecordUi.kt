package com.example.flowtime.ui.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.DurationWheelBlock
import com.example.flowtime.ui.components.formatMinutesCompact
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.components.softShadow
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.SuccessGreen
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/** 记录的时间区间文案：「10-08 20:00 → 21:30」，跨天标「次日」 */
fun recordRangeText(record: FocusRecord, zone: ZoneId = ZoneId.systemDefault()): String {
    val s = Instant.ofEpochMilli(record.startEpochMillis).atZone(zone)
    val e = Instant.ofEpochMilli(record.endEpochMillis).atZone(zone)
    return if (s.toLocalDate() == e.toLocalDate()) {
        "%02d-%02d %02d:%02d → %02d:%02d".format(
            s.monthValue, s.dayOfMonth, s.hour, s.minute, e.hour, e.minute,
        )
    } else {
        "%02d-%02d %02d:%02d → 次日 %02d:%02d".format(
            s.monthValue, s.dayOfMonth, s.hour, s.minute, e.hour, e.minute,
        )
    }
}

/**
 * 记录行：左侧圆点 + 时间区间 + 时长。
 *
 * - **手动补录与真实计时一视同仁**（2026-10-25 他定）：不再带「补」标签，
 *   两者在列表里长得完全一样。补录只是另一种记法，不是需要被标记的异常。
 * - **点按改时长**（真实计时也开放 —— 误触的倒计时跑完一趟只能靠这个救回来）
 * - **长按删除**（删除后可撤销，见 [RecordUndoBar]）
 * - [selected] 非 null = 多选模式：行首换成勾选圈，点按 = 切换勾选，长按失效
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
    record: FocusRecord,
    showTaskName: Boolean,
    onEditDuration: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** 该记录的任务已被删除（回收站里待恢复 / 已彻底删）——列表上要看得出来 */
    taskDeleted: Boolean = false,
    /** 多选模式（记录页右上角「编辑」）：null = 普通模式 */
    selected: Boolean? = null,
    onToggleSelect: () -> Unit = {},
) {
    val picking = selected != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { if (picking) onToggleSelect() else onEditDuration() },
                onLongClick = if (picking) null else onDelete,
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (picking) {
            SelectMark(selected == true)
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    // 圆点不再按来源变色：补录与真实计时在列表里长得一样，也不再加「补」标签；
                    // 用户要的就是"一视同仁"，来源只留在数据里（改时长等逻辑仍需区分）。
                    .background(AccentGreen),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showTaskName) {
                    Text(
                        record.taskName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (taskDeleted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("已删", fontSize = 10.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    recordRangeText(record),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatMinutesCompact(record.minutes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
        )
    }
}

/** 多选勾选标记：空心圆 → 实心 + 白勾（记录页 / 回收站共用） */
@Composable
fun SelectMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) Primary else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (selected) Color.Transparent else DividerColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 记录之间的细分隔线（与列表行配套使用） */
@Composable
fun RecordDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(DividerColor))
}

/**
 * 删除 / 补录后的撤销条：贴在屏幕底部，[seconds] 秒内可反悔。
 *
 * 材质走主题 token（卡面 + 描边 + 柔和阴影 + 主题胶囊形状）——
 * 之前写死「深墨底 + 白字」，夜航/涂鸦主题下 textPrimary 是近白，
 * 整条变成一块大白板、白字直接看不见。
 * 左侧语义点随 [accent] 变色：补录/恢复绿、删除琥珀。
 * 超时自动消失（调用方通过 [onExpire] 清理状态）。
 */
@Composable
fun RecordUndoBar(
    visible: Boolean,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "已移入回收站",
    seconds: Int = 1,
    accent: Color = SuccessGreen,
) {
    // 退出动画期间调用方已清空状态，message/accent 会被重算成另一种语义：
    // 任务详情页删除与补录共用一条，"已补录"淡出时会闪成"已移入回收站"。
    // 缓存最后一次可见时的文案，淡出过程文字与色点保持不动。
    val shown = remember { mutableStateOf(message to accent) }
    SideEffect { if (visible) shown.value = message to accent }

    // key 带 seconds：任务详情页那条删除/补录共用，两态时长不同，
    // 只绑 visible 的话「先删后补」不会重开计时，补录条会只剩删除条的余秒
    LaunchedEffect(visible, seconds) {
        if (visible) {
            delay(seconds * 1000L)
            onExpire()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp)
                .softShadow(ActivePalette.pillShape, elevation = 10.dp)
                .clip(ActivePalette.pillShape)
                .background(ActivePalette.surfaceCard)
                .border(1.dp, ActivePalette.borderColor, ActivePalette.pillShape)
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(shown.value.second),
            )
            Spacer(Modifier.width(10.dp))
            Text(shown.value.first, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onUndo) {
                Text("撤销", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
            }
        }
    }
}

/**
 * 改时长（仅手动补录记录）：**结束时刻不变**，开始时刻按新时长回退。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationEditDialog(
    record: FocusRecord,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var minutes by remember { mutableStateOf(record.minutes) }
    val preview = remember(minutes) {
        val s = Instant.ofEpochMilli(record.endEpochMillis - minutes * 60_000L).atZone(zone)
        val e = Instant.ofEpochMilli(record.endEpochMillis).atZone(zone)
        val cross = s.toLocalDate() != e.toLocalDate()
        "%02d-%02d %02d:%02d → %s%02d:%02d".format(
            s.monthValue, s.dayOfMonth, s.hour, s.minute,
            if (cross) "次日 " else "",
            e.hour, e.minute,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PaperElevated) {
        BlurDialogBehind(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ListBottomGap),
        ) {
            Text("修改时长", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("结束时刻不变，开始时刻随之回退", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            DurationWheelBlock(
                minutes = minutes,
                onChange = { minutes = it.coerceIn(0, BackfillRules.MAX_MINUTES) },
                maxTotalMinutes = BackfillRules.MAX_MINUTES,
                fadeColor = PaperElevated,
            )
            Spacer(Modifier.height(10.dp))
            Text(preview, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(minutes.coerceIn(BackfillRules.MIN_MINUTES, BackfillRules.MAX_MINUTES)) },
                enabled = minutes >= BackfillRules.MIN_MINUTES,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight).pressScale(),
                shape = ActivePalette.buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("保存", fontSize = 15.sp)
            }
        }
    }
}
