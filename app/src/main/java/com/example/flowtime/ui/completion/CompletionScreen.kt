package com.example.flowtime.ui.completion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.FireworksOverlay
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.formatMinutesCompact
import com.example.flowtime.ui.components.rememberFireworksState
import com.example.flowtime.ui.theme.BgPrimary
import com.example.flowtime.ui.theme.CardOrange
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** 专注完成页（教程 §3.3）：奖杯 + 烟花 + 成就卡 + 继续专注 */
@Composable
fun CompletionScreen(
    viewModel: AppViewModel,
    onContinue: () -> Unit,
) {
    val record by viewModel.completedRecord.collectAsStateWithLifecycle()
    val fireworks = rememberFireworksState()
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fireworks.celebrate()
        delay(150)
        contentVisible = true
    }

    // 退出完成页必须消费完成事件，否则回计时页会重复触发跳转
    BackHandler {
        viewModel.consumeCompleted()
        onContinue()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = {
                        viewModel.consumeCompleted()
                        onContinue()
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text(
                    "专注完成",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
            }
            // 奖杯 + 时长 + 鼓励语：整组在标题栏与底部任务卡之间垂直居中后**再上抬 36dp**
            // （他要"放屏幕中央上方一点"；单独挪文字会破坏组内间距，所以整组一起抬）
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // BoxScope 里要显式用顶层 AnimatedVisibility，避免解析到 ColumnScope 扩展
                androidx.compose.animation.AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 6 },
                ) {
                    // 🔴 上移用 padding，不能用 offset：offset 只改绘制不改测量，
                    // 内容会画出容器边界，而 AnimatedVisibility 默认 clip=true →
                    // 奖杯上半截被裁掉、动画走完才"补"出来（他报的"上一半延迟显示"）。
                    // 底部留 72dp 让居中位置等效上抬 36dp。
                    Column(
                        modifier = Modifier.padding(bottom = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "奖杯",
                            tint = Gold,
                            modifier = Modifier.size(88.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "+${formatMinutesCompact(record?.minutes ?: 0)}",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Medium,
                            color = Primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("干得好，继续保持", fontSize = 16.sp, color = TextSecondary)
                    }
                }
            }
            ProCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.CardPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardOrange),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "本次任务：${record?.taskName ?: "-"}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                            )
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        FireworksOverlay(fireworks)
    }
}
