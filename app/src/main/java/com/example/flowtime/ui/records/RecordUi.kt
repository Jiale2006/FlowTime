package com.example.flowtime.ui.records

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.RecordSource
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.DurationWheelBlock
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
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
 * 记录行：左侧圆点 + 时间区间 + 时长；手动补录的记录带「补」标记。
 *
 * - **长按删除**（删除后可撤销，见 [RecordUndoBar]）
 * - **点按改时长**，仅对手动补录的记录开放；真实计时点一下会说明原因，不留"点了没反应"
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
    record: FocusRecord,
    showTaskName: Boolean,
    onEditDuration: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val editable = BackfillRules.canEditDuration(record)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (editable) {
                        onEditDuration()
                    } else {
                        Toast.makeText(
                            context,
                            "真实计时记录的时长不可修改，长按可以删除",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onLongClick = onDelete,
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (record.recordSource == RecordSource.MANUAL) Primary else AccentGreen),
        )
        Spacer(Modifier.width(10.dp))
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
                if (record.recordSource == RecordSource.MANUAL) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardGreenLight)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("补", fontSize = 10.sp, color = Primary)
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
            formatMinutesHm(record.minutes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
        )
    }
}

/** 记录之间的细分隔线（与列表行配套使用） */
@Composable
fun RecordDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(DividerColor))
}

/**
 * 删除 / 补录后的撤销条：贴在屏幕底部，[seconds] 秒内可反悔。
 * 超时自动消失（调用方通过 [onExpire] 清理状态）。
 */
@Composable
fun RecordUndoBar(
    visible: Boolean,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "记录已删除",
    seconds: Int = 6,
) {
    LaunchedEffect(visible) {
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
                .clip(RoundedCornerShape(12.dp))
                .background(TextPrimary.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onUndo) {
                Text("撤销", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CardGreenLight)
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
