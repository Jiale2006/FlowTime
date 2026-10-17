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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.HourMinuteWheel
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.AmbientParticles
import com.example.flowtime.ui.components.CategoryBadge
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.tasks.TaskIcons
import com.example.flowtime.ui.components.RingTimer
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.components.rememberAmbientParticlesState
import com.example.flowtime.ui.components.rememberReducedMotion
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.records.BackfillDialog
import com.example.flowtime.ui.records.RecordUndoBar
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.CardOrange
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.ErrorRed
import com.example.flowtime.ui.theme.MutedSurface
import com.example.flowtime.ui.theme.OnChampagne
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
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
    val records by viewModel.records.collectAsState()
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
    var showBackfill by remember { mutableStateOf(false) }
    // 撤销条读 ViewModel：补录弹窗关掉后由本页接着弹撤销
    val lastAdded = viewModel.lastAddedRecord.collectAsState().value
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    // 时长弹层里的临时值：每次打开都从当前选中时长起步，确定后才写回
    var pickerMinutes by remember { mutableStateOf(0) }
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
        // 环境漂浮粒子：**铺满整页**（挂在外层 Box 的第一个子元素 = 绘制在 Column 之前，
        // 永远在 UI 之下）—— 所以任务卡/按钮会遮住一部分，是"背景浮尘"而不是"屏幕飞屑"，
        // 不会干扰读数。水彩主题有自己的画（芦苇河），粒子层跳过它。
        // 粒子系统主题同理跳过：它自带 ParticleNetBackdrop 粒子网，两套粒子同屏会互相抢戏
        //（提示词明令禁止 "patterns that compete with particle animation"）。
        if (!isIdle && !reducedMotion &&
            ActivePalette.mode != ThemeMode.WATERCOLOR &&
            ActivePalette.mode != ThemeMode.PARTICLES
        ) {
            AmbientParticles(
                state = ambient,
                active = timer.phase == AppViewModel.TimerPhase.RUNNING,
                modifier = Modifier.matchParentSize(),
                // 整页铺开后面积约 ×2.5，数量按可见比例补到这里（不必等比例）
                targetCount = 32,
                // 🔴 禁区圆心纵向比例：圆环区在内容区约 38% 高度处（任务卡之下、按钮之上）。
                //    不能沿用默认 0.5 —— 那是"画布中心"，全屏后已在屏幕正中，禁区会挖错位置。
                keepOutCenterYFraction = 0.38f,
                intensity = if (timer.mode != AppViewModel.TimerMode.COUNT_UP &&
                    timer.remainingMillis in 1..5_000L
                ) {
                    1.6f
                } else {
                    1f
                },
            )
        }
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
            // 水彩主题是竖幅画（芦苇河 934×1685，宽高比 0.55），
            // 若沿用 ringMaxSize 的横向口径卡高度，min(W/934, H/1685) 会以高度为瓶颈，
            // 画作只占画布宽 ~44%、两侧各留白 ~110dp —— 竖幅画必须吃满纵向空间。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (ActivePalette.mode == ThemeMode.WATERCOLOR) {
                            Modifier
                        } else {
                            Modifier.heightIn(max = ringMaxSize + 40.dp)
                        },
                    ),
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
                        // 颜料时间线 = 已用比例（倒计时/番茄：剩余→已用要取反；正计时本来就是
                        // 小时周期内的已用比例；待机显示起步色）。之前直接传剩余比例，
                        // 导致开场就是香槟金、结尾反而变蓝——"越紧迫越金"整个反了。
                        pigmentProgress = when {
                            isIdle -> 0f
                            timer.mode == AppViewModel.TimerMode.COUNT_UP -> timer.progress
                            else -> 1f - timer.progress
                        },
                        timeText = formatClock(displayMillis),
                    modifier = Modifier.fillMaxHeight(0.94f),
                    dimmed = timer.phase == AppViewModel.TimerPhase.PAUSED,
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
                            // 实心主色 + 白字（夜航香槟底配深墨字，白字压亮金对比只有 2.3:1）
                            .background(SolidColor(Primary))
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
                            color = when (ActivePalette.mode) {
                                ThemeMode.NOCTURNE -> OnChampagne
                                else -> Color.White
                            },
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
                                    // 弹层从当前已选时长起步：按钮上显示 30min，弹层里也该是 30min。
                                    // 之前固定回 45min，按钮和弹层对不上，看着像两套数据。
                                    pickerMinutes = selectedDuration
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

        // 补录撤销条：贴在底栏上方（补错了不用跑去记录页删）
        RecordUndoBar(
            visible = lastAdded != null,
            message = "已补录 ${formatMinutesHm(lastAdded?.minutes ?: 0)}",
            onUndo = {
                lastAdded?.let { viewModel.deleteRecord(it.id) }
                viewModel.clearLastAddedRecord()
            },
            onExpire = { viewModel.clearLastAddedRecord() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
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

    // 点「倒计时」弹出：选时长（纯小时/分钟滚轮），不占首页版面
    if (showDurationPicker) {
        // 关掉弹层自身的手势（sheetGesturesEnabled=false）：否则拨滚轮的竖向
        // 拖拽会被弹层抢走，整层跟着跑、标题被顶出屏幕（用户实测反馈）。
        // 内容短，直接全展开；关闭走点遮罩 / 返回键 / 两个按钮。
        ModalBottomSheet(
            onDismissRequest = { showDurationPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetGesturesEnabled = false,
            dragHandle = null,
            containerColor = PaperElevated,
        ) {
            BlurDialogBehind(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    // 拖拽把手去掉后它那块顶部空间也没了，标题不能顶在圆角上
                    .padding(top = 24.dp)
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.ListBottomGap),
            ) {
                Text("选择倒计时时长", fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                // 纯时/分滚轮（用户定稿：不要固定档快选，滚轮本身覆盖 0-8h 任意值）
                HourMinuteWheel(
                    hours = pickerMinutes / 60,
                    minutes = pickerMinutes % 60,
                    // 不在这里钳：钳了会让滚轮"拨了没反应"（拨到 24 小时后分钟永远回到 0）。
                    // 越界照常显示、按上限生效，并在下面写明。
                    onChange = { h, m -> pickerMinutes = h * 60 + m },
                    maxTotalMinutes = BackfillRules.MAX_MINUTES,
                    // 弹层底色就是 PaperElevated，遮罩按它叠才不穿帮
                    fadeColor = PaperElevated,
                )
                Spacer(Modifier.height(10.dp))
                // 结束时刻预览：市面计时软件必备，用户要的是"几点结束"这个信息。
                // 跨天时额外标出来——长倒计时常在夜里设，不说清楚会以为最多到今晚。
                val picked = pickerMinutes.coerceIn(1, BackfillRules.MAX_MINUTES)
                val validPick = pickerMinutes >= 1
                Text(
                    durationPreviewText(picked),
                    fontSize = 13.sp,
                    color = if (validPick && durationCrossesDay(picked)) WarningAmber else TextSecondary,
                )
                if (pickerMinutes > BackfillRules.MAX_MINUTES) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "最长 ${BackfillRules.MAX_MINUTES / 60} 小时，超出会按这个时长记录",
                        fontSize = 11.sp,
                        color = WarningAmber,
                    )
                }
                Spacer(Modifier.height(14.dp))
                // 主按钮 = 选完即起（市面软件做法：选25min → 直接进计时），
                // 次按钮 = 仅写回时长不开跑，留给"只想看看结束时刻"的场合。
                // 之前只有「确定」，点完弹层关掉什么都不会发生，还得再点一次「开始专注」。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = {
                            viewModel.selectDuration(picked)
                            showDurationPicker = false
                        },
                        enabled = validPick,
                        modifier = Modifier.height(Dimens.ButtonHeight),
                    ) {
                        Text("仅设置", color = if (validPick) Primary else DisabledGray, fontSize = 14.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.selectDuration(picked)
                            viewModel.startFocus()
                            showDurationPicker = false
                        },
                        enabled = validPick,
                        modifier = Modifier.weight(1f).height(Dimens.ButtonHeight),
                        shape = ActivePalette.buttonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(
                            "开始 ${durationLabel(picked)} 倒计时",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 首页补录入口：沿用当前选中的任务（自由专注也能补，记到未分类）
                    TextButton(onClick = {
                        showTaskPicker = false
                        showBackfill = true
                    }) {
                        Text("补录时间", color = Primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            showTaskPicker = false
                            // 直达编辑器：跳转任务页后由 newTaskRequest 触发打开新建面板
                            viewModel.requestNewTask()
                            onNavigateToTasks()
                        },
                    ) {
                        Text("＋ 新建任务", color = Primary)
                    }
                }
            }
        }
    }

    if (showBackfill) {
        BackfillDialog(
            tasks = tasks,
            initialTaskId = task?.id,
            records = records,
            onDismiss = { showBackfill = false },
            onConfirm = { picked, startMillis, endMillis ->
                viewModel.addManualRecord(picked, startMillis, endMillis)
                showBackfill = false
            },
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
/**
 * 时长文案：45min / 1h / 1.5h / 2h30m。
 * 上限提到 24h 后必须能正确写出整小时——否则按钮会显示"开始 1440min 倒计时"。
 */
private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}min"
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes % 30 == 0 -> "${minutes / 60}.5h"
    else -> "${minutes / 60}h${minutes % 60}m"
}

/** 这个时长从此刻起会不会跨过午夜（跨天时预览要标"次日"、警告要变色） */
private fun durationCrossesDay(minutes: Int): Boolean {
    if (minutes <= 0) return false
    val now = Instant.now()
    return now.atZone(zone()).plusMinutes(minutes.toLong()).toLocalDate() != now.atZone(zone()).toLocalDate()
}

private fun zone(): ZoneId = ZoneId.systemDefault()

/**
 * 时长预览：把"多久"翻译成用户真正关心的"几点结束"。
 * 0 分钟（还没选）时只回时长，避免出现 12:30 → 12:30 这种无意义文案。
 * 跨天时结束日要跟着变，否则深夜选2h 会显示成"23:10 → 01:10"看不出是第二天。
 */
private fun durationPreviewText(minutes: Int): String {
    if (minutes <= 0) return "请选择时长"
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val start = Instant.ofEpochMilli(now).atZone(zone)
    val end = start.plusMinutes(minutes.toLong())
    val endText = if (end.toLocalDate() == start.toLocalDate()) {
        "%02d:%02d".format(end.hour, end.minute)
    } else {
        "次日 %02d:%02d".format(end.hour, end.minute)
    }
    return "现在 %02d:%02d → %s 结束（%s）".format(
        start.hour, start.minute, endText, durationLabel(minutes),
    )
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
