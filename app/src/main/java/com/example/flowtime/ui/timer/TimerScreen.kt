package com.example.flowtime.ui.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.FlowAlertDialog
import com.example.flowtime.ui.components.ReedPaintingTimer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.AmbientParticles
import com.example.flowtime.ui.components.CategoryBadge
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.tasks.TaskIcons
import com.example.flowtime.ui.components.RingTimer
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.components.rememberAmbientParticlesState
import com.example.flowtime.ui.components.rememberReducedMotion
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.CardOrange
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.ErrorRed
import com.example.flowtime.ui.theme.MutedSurface
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.ThemeMode

private const val COUNT_UP_PAINT_TOTAL_MS = 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    viewModel: AppViewModel,
    onNavigateToTasks: () -> Unit,
    onCompleted: () -> Unit,
) {
    val timer by viewModel.timer.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val completed by viewModel.completedRecord.collectAsState()
    val selectedDuration by viewModel.selectedDurationMinutes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val task = tasks.firstOrNull { it.id == timer.selectedTaskId }
    val isIdle = timer.phase == AppViewModel.TimerPhase.IDLE
    val reducedMotion = rememberReducedMotion()
    val ambient = rememberAmbientParticlesState()
    // 「芦苇河」显影进度在番茄休息段冻结（休息不算专注，画不生长）
    val frozenPaintingP = remember { mutableFloatStateOf(0f) }
    // 环形计时器按屏高自适应，尽量让待机页一屏放下（小屏才需要轻微滚动）
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 以屏高为主、同时不让它在窄屏上把整行占满
    val ringMaxSize = minOf(screenHeightDp * 0.33f, screenWidthDp * 0.80f)
        .dp.coerceIn(180.dp, 320.dp)
    var showTaskPicker by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showCustomDuration by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showPomodoroSheet by remember { mutableStateOf(false) }

    LaunchedEffect(completed) {
        if (completed != null) onCompleted()
    }

    // 专注中保持亮屏
    val localView = LocalView.current
    DisposableEffect(isIdle) {
        localView.keepScreenOn = !isIdle
        onDispose { localView.keepScreenOn = false }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.ContentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            if (isIdle) {
                Text("心流时间", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("让注意力回到当下", fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.height(14.dp))

                // 任务卡置于首页最上方（需求②③）：点开选择任务 / 自由专注
                ProCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale()
                        .clickable { showTaskPicker = true },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(ActivePalette.badgeShape)
                                .background(
                                    task?.let { TaskCardPalette[it.colorIndex % TaskCardPalette.size].first }
                                        ?: MutedSurface,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            TaskIcons.Glyph(
                                index = task?.iconIndex ?: TaskIcons.FREE_FOCUS_INDEX,
                                size = 24.dp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = task?.name ?: "自由专注",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                            )
                            Text(
                                text = if (task != null) "点击更换任务" else "不计入任务（未分类）",
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 计时模式：切换模式不再弹窗（时长统一走右下角按钮）；
                // 番茄计时只在首次切换时自动说明一次结构，之后直接切换
                ModeSelector(
                    selected = timer.mode,
                    onSelect = { mode ->
                        viewModel.selectMode(mode)
                        if (mode == AppViewModel.TimerMode.POMODORO && !settings.pomodoroTipShown) {
                            showPomodoroSheet = true
                            viewModel.updateSettings(settings.copy(pomodoroTipShown = true))
                        }
                    },
                )
            } else {
                // 运行态：任务条放到最上方（与待机页位置一致），半透明弱化，
                // 并且不再显示「已专注 X」——时长看圆环就够，两处重复反而吵。
                ProCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard.copy(alpha = 0.62f),
                ) {
                    Row(
                        modifier = Modifier.padding(Dimens.CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.IconChip)
                                .clip(ActivePalette.badgeShape)
                                .background(CardGreenLight.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            TaskIcons.Glyph(
                                index = task?.iconIndex ?: TaskIcons.FREE_FOCUS_INDEX,
                                size = Dimens.IconChipGlyph,
                            )
                        }
                        Spacer(Modifier.width(Dimens.IconTextGap))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = task?.name ?: "自由专注",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    timer.inBreak -> "休息中 · 第 ${timer.round} 轮已完成"
                                    timer.mode == AppViewModel.TimerMode.POMODORO ->
                                        "第 ${timer.round} 轮专注中"
                                    timer.mode == AppViewModel.TimerMode.COUNT_UP -> "正计时中"
                                    else -> "倒计时中"
                                },
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (isIdle) 12.dp else 16.dp))

            // 圆环区吃掉剩余高度：小屏自动缩小，按钮始终在底部可见（无需滚动）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(max = ringMaxSize + 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val displayMillis = when {
                    timer.mode == AppViewModel.TimerMode.COUNT_UP -> timer.elapsedMillis
                    isIdle -> timer.totalMillis
                    else -> timer.remainingMillis
                }
                // 「芦苇河」画作：前半程显影（到 50% 时遮挡光斑散尽、整画全显），
                // 后半程芦苇逐支随风摆动。时间线 focusFrac = 真实进度：
                // 倒计时/番茄专注段 = elapsed/total；正计时 = elapsed/虚拟1小时（封顶，走满触发钤印）；
                // 显影进度 paintingP：倒计时 = focusFrac×2 封顶 1；正计时 = 开四次方前紧后松曲线；
                // 番茄休息段冻结；待机显示整画幻影（低透明度预告"专注会画出什么"）。
                var focusFrac = when {
                    timer.mode == AppViewModel.TimerMode.COUNT_UP ->
                        (timer.elapsedMillis / COUNT_UP_PAINT_TOTAL_MS.toFloat()).coerceIn(0f, 1f)
                    isIdle -> 1f
                    else -> 1f - timer.progress
                }
                var paintingP = if (timer.mode == AppViewModel.TimerMode.COUNT_UP) {
                    // 正计时显影走"前紧后松"曲线（四次方根，虚拟总长 1 小时）：
                    // 15min≈7成、30min≈8成5、45min≈9成5、1h 收满——短会话也有"画出大半"的满足感
                    Math.sqrt(Math.sqrt(focusFrac.toDouble())).toFloat()
                } else {
                    (focusFrac * 2f).coerceAtMost(1f)
                }
                if (timer.inBreak) paintingP = frozenPaintingP.floatValue
                if (!isIdle && !timer.inBreak) frozenPaintingP.floatValue = paintingP
                // 钤印只认走满（与显影解耦，显影在半程就到 1 了）
                val paintingCompleted = focusFrac >= 1f && !isIdle
                val modeLabel = when {
                    timer.inBreak -> "番茄计时 · 休息中 · 第 ${timer.round} 轮已完成"
                    timer.mode == AppViewModel.TimerMode.COUNT_UP -> "正计时"
                    timer.mode == AppViewModel.TimerMode.POMODORO ->
                        "番茄计时 · 第 ${timer.round} 轮专注"
                    else -> "倒计时"
                }
                if (!isIdle && !reducedMotion && ActivePalette.mode != ThemeMode.WATERCOLOR) {
                    // 环境漂浮粒子：运行中上飘，最后 5 秒加速冲刺，暂停自然消散（圆环专属）
                    AmbientParticles(
                        state = ambient,
                        active = timer.phase == AppViewModel.TimerPhase.RUNNING,
                        modifier = Modifier.matchParentSize(),
                        intensity = if (timer.mode != AppViewModel.TimerMode.COUNT_UP &&
                            timer.remainingMillis in 1..5_000L
                        ) {
                            1.6f
                        } else {
                            1f
                        },
                    )
                }
                if (ActivePalette.mode == ThemeMode.WATERCOLOR) {
                    // 水彩主题：「芦苇河」画作显影（替代圆环）
                    // 风：显影走完（半程）后起风，吹的是全显的画
                    val windActive = !isIdle && !timer.inBreak &&
                        focusFrac >= 0.5f && timer.phase != AppViewModel.TimerPhase.PAUSED
                    Box {
                        ReedPaintingTimer(
                            progress = paintingP,
                            timeText = formatClock(displayMillis),
                            modeLabel = modeLabel,
                            modifier = Modifier
                                .fillMaxHeight(0.94f)
                                .fillMaxWidth(),
                            completed = paintingCompleted,
                            windActive = windActive,
                            ghost = isIdle,
                            dimmed = timer.phase == AppViewModel.TimerPhase.PAUSED,
                            reducedMotion = reducedMotion,
                        )
                    }
                } else {
                    RingTimer(
                        progress = if (isIdle) 1f else timer.progress,
                        pigmentProgress = timer.progress,
                        timeText = formatClock(displayMillis),
                    modifier = Modifier.fillMaxHeight(0.94f),
                    breathing = timer.phase == AppViewModel.TimerPhase.RUNNING && !reducedMotion,
                    dimmed = timer.phase == AppViewModel.TimerPhase.PAUSED,
                    rippleFraction = if (timer.phase == AppViewModel.TimerPhase.RUNNING && !reducedMotion) {
                        (displayMillis % 1_000L) / 1_000f
                    } else {
                        null
                    },
                    centerContent = if (!isIdle) {
                        {
                            Text(
                                text = when {
                                    timer.inBreak -> "番茄计时 · 休息中 · 第 ${timer.round} 轮已完成"
                                    timer.mode == AppViewModel.TimerMode.COUNT_UP -> "正计时"
                                    timer.mode == AppViewModel.TimerMode.POMODORO ->
                                        "番茄计时 · 第 ${timer.round} 轮专注"
                                    else -> "倒计时"
                                },
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    } else {
                        null
                    },
                )
                }
                if (!isIdle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 28.dp)
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(AccentGreen, Primary)))
                            .pressScale()
                            .clickable {
                                if (timer.phase == AppViewModel.TimerPhase.RUNNING) {
                                    viewModel.pauseFocus()
                                } else {
                                    viewModel.resumeFocus()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (timer.phase == AppViewModel.TimerPhase.RUNNING) "暂停" else "继续",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (isIdle) 18.dp else 36.dp))

            if (isIdle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.startFocus() },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.ButtonHeightHero)
                            .pressScale(),
                        shape = ActivePalette.buttonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text("开始专注", fontSize = 16.sp, fontWeight = FontWeight.Light)
                    }
                    // 时长按钮：待机时点它即可选倒计时时长（番茄则看结构说明）
                    if (timer.mode != AppViewModel.TimerMode.COUNT_UP) {
                        OutlinedButton(
                            onClick = {
                                if (timer.mode == AppViewModel.TimerMode.POMODORO) {
                                    showPomodoroSheet = true
                                } else {
                                    showDurationPicker = true
                                }
                            },
                            modifier = Modifier
                                .height(Dimens.ButtonHeightHero)
                                // 描边按钮不投阴影：Material 3 里只有填充/悬浮按钮带高度；
                                // 而且 softShadow 投在透明底的元素上时，阴影轮廓会退化成矩形，
                                // 隔着透明底看就是按钮里的一块方角色斑（已实测复现）。
                                .pressScale(),
                            shape = ActivePalette.buttonShape,
                            border = BorderStroke(1.dp, BorderColor),
                        ) {
                            Text(
                                text = if (timer.mode == AppViewModel.TimerMode.POMODORO) {
                                    "${settings.pomodoroFocusMinutes}+${settings.pomodoroBreakMinutes}min"
                                } else {
                                    durationLabel(selectedDuration)
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                color = Primary,
                            )
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.weight(1f).height(Dimens.ButtonHeight).pressScale(),
                        shape = ActivePalette.buttonShape,
                        border = BorderStroke(1.dp, ErrorRed),
                    ) {
                        Text("取消", color = ErrorRed, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { viewModel.finishEarly() },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.ButtonHeight)
                            .pressScale(),
                        shape = ActivePalette.buttonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(
                            text = when (timer.mode) {
                                AppViewModel.TimerMode.COUNT_UP -> "完成"
                                AppViewModel.TimerMode.POMODORO -> "结束本轮"
                                AppViewModel.TimerMode.COUNT_DOWN -> "结束并记录"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.ListBottomGap))
        }
    }

    if (showCancelConfirm) {
        FlowAlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("取消计时", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "已计时 ${formatElapsed(timer.elapsedMillis)}，取消后将被丢弃，不会计入专注记录。确定取消吗？",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        viewModel.cancelFocus()
                    },
                ) {
                    Text("确认取消", color = ErrorRed, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("继续计时", color = Primary)
                }
            },
        )
    }

    if (showCustomDuration) {
        var customMinutes by remember { mutableStateOf(selectedDuration.toString()) }
        val customValid = (customMinutes.toIntOrNull() ?: 0) in 1..480
        FlowAlertDialog(
            onDismissRequest = { showCustomDuration = false },
            title = { Text("自定义时长", fontWeight = FontWeight.Medium) },
            text = {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { customMinutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("分钟（1-480）") },
                    singleLine = true,
                    isError = !customValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.selectDuration(customMinutes.toInt())
                        showCustomDuration = false
                    },
                    enabled = customValid,
                ) {
                    Text("确定", color = if (customValid) Primary else DisabledGray)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDuration = false }) { Text("取消") }
            },
        )
    }

    // 点「倒计时」弹出：选时长（预设 + 自定义），不占首页版面
    if (showDurationPicker) {
        ModalBottomSheet(
            onDismissRequest = { showDurationPicker = false },
            containerColor = PaperElevated,
        ) {
            BlurDialogBehind(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.ListBottomGap),
            ) {
                Text("选择倒计时时长", fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(14.dp))
                DurationSelector(
                    selectedMinutes = selectedDuration,
                    onSelect = {
                        viewModel.selectDuration(it)
                        showDurationPicker = false
                    },
                    onCustom = {
                        showDurationPicker = false
                        showCustomDuration = true
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "当前：${durationLabel(selectedDuration)}",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
            }
        }
    }

    // 点「番茄计时」弹出：结构说明（固定 25+5，不参与时长选择）
    if (showPomodoroSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPomodoroSheet = false },
            containerColor = PaperElevated,
        ) {
            BlurDialogBehind(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.ListBottomGap),
            ) {
                Text("番茄计时", fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    "${settings.pomodoroFocusMinutes} 分钟专注 → ${settings.pomodoroBreakMinutes} 分钟休息，" +
                        "每 ${settings.pomodoroRoundsBeforeLongBreak} 轮进入 ${settings.pomodoroLongBreakMinutes} 分钟长休息，" +
                        "自动循环，专注段结束自动记录并响铃。",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "时长与是否自动开始下一轮，都可在「设置」里调整。",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showPomodoroSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.ButtonHeight),
                    shape = ActivePalette.buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("知道了", fontSize = 15.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }

    if (showTaskPicker) {
        ModalBottomSheet(onDismissRequest = { showTaskPicker = false }) {
            BlurDialogBehind(26.dp)
            Column(Modifier.padding(horizontal = Dimens.ScreenPadding).padding(bottom = Dimens.ListBottomGap)) {
                Text("选择专注任务", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                // 自由专注：不绑定任务，记录归未分类（选中态=整行底色加深，不打勾）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ActivePalette.buttonShape)
                        .background(if (task == null) CardGreenLight else Color.Transparent)
                        .clickable {
                            viewModel.selectFreeFocus()
                            showTaskPicker = false
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(ActivePalette.badgeShape)
                            .background(MutedSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        TaskIcons.Glyph(TaskIcons.FREE_FOCUS_INDEX, size = 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "自由专注",
                        fontSize = 16.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                tasks.forEach { item ->
                    val (cardColor, _) = TaskCardPalette[item.colorIndex % TaskCardPalette.size]
                    val selected = item.id == task?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ActivePalette.buttonShape)
                            .background(if (selected) CardGreenLight else Color.Transparent)
                            .clickable {
                                viewModel.selectTask(item.id)
                                showTaskPicker = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(ActivePalette.badgeShape)
                                .background(cardColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            TaskIcons.Glyph(index = item.iconIndex, size = 22.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.name,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // 与任务清单一致：只有归属大类的任务才挂标签，未分类不再显示
                        val parentName = tasks.firstOrNull { it.id == item.parentId }?.name
                        if (parentName != null) {
                            CategoryBadge(categoryName = parentName)
                        }
                    }
                }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        showTaskPicker = false
                        // 直达编辑器：跳转任务页后由 newTaskRequest 触发打开新建面板
                        viewModel.requestNewTask()
                        onNavigateToTasks()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("＋ 新建任务", color = Primary)
                }
            }
        }
    }
}


@Composable
private fun DurationSelector(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    val presets = listOf(1, 30, 45, 60, 90, 120, 150)
    val hourLabels = mapOf(60 to "1h", 90 to "1.5h", 120 to "2h", 150 to "2.5h")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presets.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = { Text(hourLabels[minutes] ?: "${minutes}min", fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CardGreenLight,
                    selectedLabelColor = Primary,
                ),
            )
        }
        FilterChip(
            selected = selectedMinutes !in presets,
            onClick = onCustom,
            label = { Text("自定义", fontSize = 13.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CardGreenLight,
                selectedLabelColor = Primary,
            ),
        )
    }
}


@Composable
private fun ModeSelector(
    selected: AppViewModel.TimerMode,
    onSelect: (AppViewModel.TimerMode) -> Unit,
) {
    val modes = listOf(
        AppViewModel.TimerMode.COUNT_UP to "正计时",
        AppViewModel.TimerMode.COUNT_DOWN to "倒计时",
        AppViewModel.TimerMode.POMODORO to "番茄计时",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        modes.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(label, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CardGreenLight,
                    selectedLabelColor = Primary,
                ),
            )
        }
    }
}

/** 时长标签：与需求一致的 30min / 45min / 1h / 1.5h / 2h / 2.5h 写法 */
private fun durationLabel(minutes: Int): String = when (minutes) {
    60 -> "1h"
    90 -> "1.5h"
    120 -> "2h"
    150 -> "2.5h"
    else -> "${minutes}min"
}

private fun formatClock(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m 分 ${s.toString().padStart(2, '0')} 秒"
}
