package com.example.flowtime.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import android.animation.ValueAnimator
import com.example.flowtime.ui.theme.ThemeReveal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import com.example.flowtime.ui.components.FlowAlertDialog
import com.example.flowtime.ui.components.HourMinuteWheel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.flowtime.data.AppSettings
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.data.FlowTimeRepository
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.components.sketchStroke
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.AllPalettes
import com.example.flowtime.ui.theme.AppPalette
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.CardCream
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryDark
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.ThemeMode
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsDialog { PROFILE, THEME, DURATION, POMODORO, RINGTONE, REMINDER, BACKUP, ABOUT }

/**
 * 设置页：个人资料（头像 + 昵称）/ 专注时长 / 番茄计时参数 / 番茄铃声 / 习惯提醒（真实本地通知）/ 关于。
 */
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    // 从系统设置页返回时刷新「通知 / 精确闹钟」授权状态
    var systemStateTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) systemStateTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsOk = remember(systemStateTick) { viewModel.notificationsEnabled() }
    val exactAlarmOk = remember(systemStateTick) { viewModel.exactAlarmAllowed() }
    val version = remember { appVersion(context) }

    // 备份 / 恢复：走系统文件选择器，无需存储权限
    val scope = rememberCoroutineScope()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    // 本机自动回滚点：打开备份弹窗时刷新一次列表
    var rollbackPoints by remember { mutableStateOf<List<FlowTimeRepository.Rollback>>(emptyList()) }
    var pendingRollback by remember { mutableStateOf<FlowTimeRepository.Rollback?>(null) }
    LaunchedEffect(dialog) {
        if (dialog == SettingsDialog.BACKUP) rollbackPoints = viewModel.rollbackPoints()
    }
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = viewModel.exportBackupTo(uri)
                Toast.makeText(
                    context,
                    if (saved) "备份已导出" else "导出失败，换个位置再试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    // Android 13+ 通知是运行时权限：开启提醒时先要权限，拿到才真正打开
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.updateSettings(viewModel.settings.value.copy(reminderEnabled = true))
        } else {
            Toast.makeText(context, "没给通知权限，提醒到点也弹不出来", Toast.LENGTH_LONG).show()
        }
    }

    val onReminderToggle: (Boolean) -> Unit = { wanted ->
        when {
            !wanted -> viewModel.updateSettings(viewModel.settings.value.copy(reminderEnabled = false))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsOk ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

            else -> viewModel.updateSettings(viewModel.settings.value.copy(reminderEnabled = true))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.ContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(Dimens.ScreenTopGap))
            Text("设置", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(20.dp))

            // ---- 个人资料：头像 + 昵称 ----
            SettingsCard(onClick = { dialog = SettingsDialog.PROFILE }) {
                AvatarCircle(viewModel, settings.avatarPath, Dimens.Avatar)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text("个人资料", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        if (settings.avatarPath.isBlank()) {
                            "${settings.nickname} · 还没设置头像"
                        } else {
                            settings.nickname
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                Chevron()
            }
            Spacer(Modifier.height(Dimens.SectionGap))

            SectionTitle("外观")

            // 主题：点进去的选择界面（弹层列表式，加新主题只要往 AllPalettes 里放）。
            // 点击选项立即生效并持久化，全 app 的 token 是快照状态，下一帧整体换装。
            SettingsCard(onClick = { dialog = SettingsDialog.THEME }) {
                IconChip(Icons.Default.Palette)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text("主题", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("${ActivePalette.label} · ${ActivePalette.tagline}", fontSize = 13.sp, color = TextSecondary)
                }
                Chevron()
            }
            Spacer(Modifier.height(Dimens.CardGap))

            SectionTitle("专注")

            // 默认专注时长（主题横幅高亮卡：笔刷随主题走——水彩/夜航渐变，涂鸦扁平墨色）
            Surface(
                modifier = Modifier.fillMaxWidth().pressScale().clickable { dialog = SettingsDialog.DURATION },
                shape = ActivePalette.cardShape,
                color = Color.Transparent,
                shadowElevation = 0.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            ActivePalette.bannerBrush,
                            ActivePalette.cardShape,
                        )
                        .padding(Dimens.RowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.IconChip)
                            .clip(ActivePalette.badgeShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(Dimens.IconChipGlyph),
                        )
                    }
                    Spacer(Modifier.width(Dimens.IconTextGap))
                    Column(Modifier.weight(1f)) {
                        Text("专注设置", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(
                            "倒计时默认时长",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Text(
                        "${settings.defaultDurationMinutes} 分钟",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(Dimens.CardGap))

            SettingsCard(onClick = { dialog = SettingsDialog.POMODORO }) {
                IconChip(Icons.Default.Timer)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text("番茄计时", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        "${settings.pomodoroFocusMinutes} 分钟专注 · " +
                            "${settings.pomodoroBreakMinutes} 分钟休息 · " +
                            "每 ${settings.pomodoroRoundsBeforeLongBreak} 轮长休息",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                Chevron()
            }
            Spacer(Modifier.height(Dimens.CardGap))

            SettingsCard(onClick = { dialog = SettingsDialog.RINGTONE }) {
                IconChip(Icons.Default.VolumeUp)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text("番茄铃声", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        "完成提示音 ${if (settings.ringtoneEnabled) "开启" else "关闭"} · " +
                            "自动下一轮 ${if (settings.autoStart) "开启" else "关闭"}",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                Chevron()
            }
            Spacer(Modifier.height(Dimens.SectionGap))

            SectionTitle("提醒")

            ProCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale()
                    .clickable { dialog = SettingsDialog.REMINDER },
            ) {
                Column(Modifier.padding(Dimens.CardPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Default.NotificationsActive)
                        Spacer(Modifier.width(Dimens.IconTextGap))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "习惯提醒",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                            )
                            Text(
                                if (settings.reminderEnabled) {
                                    "每天 ${settings.reminderTime} · 目标 ${goalLabel(settings.dailyGoalMinutes)} · 响一声"
                                } else {
                                    "已关闭 · 点这里设时间与目标"
                                },
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                        Switch(
                            checked = settings.reminderEnabled,
                            onCheckedChange = onReminderToggle,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = AccentGreen,
                                checkedThumbColor = Primary,
                            ),
                        )
                    }
                    if (settings.reminderEnabled && !notificationsOk) {
                        StatusHint(
                            text = "通知权限未开启，到点弹不出来",
                            action = "去开启",
                        ) { openNotificationSettings(context) }
                    }
                    if (settings.reminderEnabled && !exactAlarmOk) {
                        StatusHint(
                            text = "系统未允许「闹钟和提醒」，可能晚几分钟",
                            action = "去设置",
                        ) { openExactAlarmSettings(context) }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.SectionGap))

            SectionTitle("其他")

            SettingsCard(onClick = { dialog = SettingsDialog.BACKUP }) {
                IconChip(Icons.Default.Save)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text("数据备份", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("导出全部任务与记录 · 可换机恢复", fontSize = 13.sp, color = TextSecondary)
                }
                Chevron()
            }
            Spacer(Modifier.height(Dimens.CardGap))

            SettingsCard(onClick = { dialog = SettingsDialog.ABOUT }) {
                IconChip(Icons.Default.Info)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text("关于", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("版本信息 · 意见反馈", fontSize = 13.sp, color = TextSecondary)
                }
                Text("v$version", fontSize = 13.sp, color = TextSecondary)
                Chevron()
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        SettingsDialog.PROFILE -> ProfileDialog(viewModel) { dialog = null }
        SettingsDialog.THEME -> ThemePickerDialog(themeMode, viewModel) { dialog = null }
        SettingsDialog.DURATION -> DurationDialog(settings, viewModel) { dialog = null }
        SettingsDialog.POMODORO -> PomodoroDialog(settings, viewModel) { dialog = null }
        SettingsDialog.RINGTONE -> RingtoneDialog(settings, viewModel) { dialog = null }
        SettingsDialog.REMINDER -> ReminderDialog(settings, viewModel, notificationsOk, exactAlarmOk) { dialog = null }
        SettingsDialog.BACKUP -> BackupDialog(
            rollbacks = rollbackPoints,
            onExport = {
                exportBackup.launch("flowtime_backup.json")
                dialog = null
            },
            onRestore = {
                pickBackup.launch(arrayOf("*/*"))
                dialog = null
            },
            onRestoreRollback = { rollback ->
                pendingRollback = rollback
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ABOUT -> AboutDialog(version) { dialog = null }
        null -> Unit
    }

    // 恢复前二次确认：覆盖不可撤销，但会先自动留一份回滚点
    pendingRestoreUri?.let { uri ->
        FlowAlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复备份", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "将用这份备份整体覆盖当前的全部任务、记录与设置。\n\n" +
                        "覆盖前会自动在本机留一份「回滚点」，恢复完发现不对可以在「数据备份」里退回来。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        val result = viewModel.restoreBackupFrom(uri)
                        val message = when (result) {
                            AppViewModel.RestoreResult.OK -> "已从备份恢复"
                            AppViewModel.RestoreResult.NOT_A_BACKUP -> "恢复失败：这不是本应用的备份文件"
                            AppViewModel.RestoreResult.TOO_NEW -> "恢复失败：备份来自更新的版本，请先更新 App"
                            AppViewModel.RestoreResult.FAILED -> "恢复失败：文件读不出来"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        rollbackPoints = viewModel.rollbackPoints()
                    }
                }) { Text("覆盖恢复", color = Gold, fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") }
            },
        )
    }

    // 用回滚点恢复：也是覆盖操作，同样二次确认
    pendingRollback?.let { rollback ->
        FlowAlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text("回滚到这一份", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "会把当前的任务、记录与设置整体换回「${rollbackLabel(rollback.savedAt)}」那一份。\n\n" +
                        "当前数据会先自动留一份新的回滚点，所以这一步本身也能再退回来。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = rollback
                    pendingRollback = null
                    scope.launch {
                        val ok = viewModel.restoreFromRollback(target)
                        Toast.makeText(
                            context,
                            if (ok) "已回滚到 ${rollbackLabel(target.savedAt)}" else "回滚失败",
                            Toast.LENGTH_LONG,
                        ).show()
                        rollbackPoints = viewModel.rollbackPoints()
                    }
                }) { Text("回滚", color = Gold, fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) { Text("取消") }
            },
        )
    }
}

// ---- 通用小组件 ----

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    ProCard(modifier = Modifier.fillMaxWidth().pressScale().clickable(onClick = onClick)) {
        Row(Modifier.padding(Dimens.RowPadding), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(Dimens.IconChip)
            .clip(ActivePalette.badgeShape)
            .background(CardGreenLight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(Dimens.IconChipGlyph))
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.Default.KeyboardArrowRight,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/**
 * 主题选择弹层：列出全部主题（AllPalettes 顺序），色点预览 + 名称，点选即换。
 * 换装走径向揭示：先截旧主题画面 → 以触点为圆心把新主题圆形扩出（ThemeReveal）；
 * 系统"减弱动画"（动画时长缩放=0）时跳过揭示，直接普通切换。
 */
@Composable
private fun ThemePickerDialog(
    current: ThemeMode,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题", fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AllPalettes.forEach { palette ->
                    ThemeOptionRow(
                        palette = palette,
                        selected = palette.mode == current,
                        onClick = { centerPx ->
                            // 点的就是当前主题 → 什么都不发生：不截旧画面、不起揭示、
                            // 不写重复的偏好、也不关弹层（用户要的是"这一下是空操作"）
                            if (palette.mode == current) return@ThemeOptionRow
                            if (ValueAnimator.getDurationScale() > 0f) {
                                val snap = ThemeReveal.captureRoot()
                                ThemeReveal.start(centerPx ?: Offset.Zero, snap)
                            }
                            viewModel.setThemeMode(palette.mode)
                            onDismiss()
                        },
                    )
                }
                Text(
                    "切换立即生效，重启应用后保持",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {},
    )
}

/**
 * 弹层里的单个主题选项：色点 + 名称 + 一句话风格，选中 = 淡彩底 + 勾。
 * 记录按下触点（换算为主窗口屏幕坐标；键盘触发时退化为行中心）交给 onClick。
 */
@Composable
private fun ThemeOptionRow(
    palette: AppPalette,
    selected: Boolean,
    onClick: (Offset?) -> Unit,
) {
    val composeView = LocalView.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var downPos by remember { mutableStateOf<Offset?>(null) }

    fun fire() {
        val c = coords
        val p = downPos ?: c?.size?.let { Offset(it.width / 2f, it.height / 2f) }
        val screen = if (c != null && p != null) {
            val loc = IntArray(2)
            composeView.getLocationOnScreen(loc)
            val root = c.localToRoot(p)
            Offset(loc[0] + root.x, loc[1] + root.y)
        } else {
            null
        }
        onClick(screen)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    downPos = awaitFirstDown(requireUnconsumed = false).position
                }
            }
            .onGloballyPositioned { coords = it }
            .clip(ActivePalette.tileShape)
            .background(if (selected) CardGreenLight else Color.Transparent)
            .sketchStroke(
                ActivePalette.tileShape,
                if (selected) Primary else DividerColor,
                1.dp,
            )
            .clickable(onClick = ::fire)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            palette.previewDots.forEachIndexed { index, dot ->
                if (index > 0) Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(dot)
                        .border(1.dp, DividerColor, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                palette.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) Primary else TextPrimary,
            )
            Text(palette.tagline, fontSize = 11.sp, color = TextSecondary)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "已选择", tint = Primary)
        }
    }
}

/** 圆形头像：有图用图，没图用默认人像图标；换头像后按文件名重新解码 */
@Composable
private fun AvatarCircle(viewModel: AppViewModel, avatarPath: String, size: Dp) {
    val bitmap by produceState<Bitmap?>(initialValue = null, avatarPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                viewModel.avatarFile()?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CardGreenLight),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** 授权缺失提示行（柔和水彩色，非刺眼警示） */
@Composable
private fun StatusHint(text: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardCream)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text(action, fontSize = 12.sp, color = Gold, fontWeight = FontWeight.Medium)
        }
    }
}

// ---- 弹窗 ----

/** 个人资料：换头像（系统照片选择器，无需存储权限）+ 改昵称 */
@Composable
private fun ProfileDialog(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val live by viewModel.settings.collectAsState()
    // 昵称只取打开弹窗时的初值，后续设置变化不覆盖用户正在输入的内容
    var nickname by remember { mutableStateOf(viewModel.settings.value.nickname) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateAvatar(uri)
    }
    val openGallery = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("个人资料", fontWeight = FontWeight.Medium) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = openGallery),
                    contentAlignment = Alignment.Center,
                ) {
                    AvatarCircle(viewModel, live.avatarPath, 84.dp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = openGallery) { Text("从相册选择", color = Primary) }
                    if (live.avatarPath.isNotBlank()) {
                        TextButton(onClick = { viewModel.clearAvatar() }) {
                            Text("移除头像", color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(12) },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 用当前最新设置回写，避免把弹窗打开后新选的头像覆盖掉
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(
                            nickname = nickname.trim().ifEmpty { "心流用户" },
                        ),
                    )
                    onDismiss()
                },
            ) { Text("保存", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationDialog(settings: AppSettings, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val options = listOf(15, 25, 30, 45, 60)
    // 上限与补录/倒计时统一到 BackfillRules.MAX_MINUTES（24h）。
    // 这里曾单独卡 240，后来统一成 480，2026-10-09 又一起提到 1440——
    // 三处共用一个常量，就不会再出现"这里能选 8h、那里只能选 4h"的错位。
    var minutes by remember { mutableStateOf(settings.defaultDurationMinutes.coerceIn(1, BackfillRules.MAX_MINUTES)) }
    // 上限在滚轮里也拨得到，所以这里**不压回**（压回去会让滚轮"拨了没反应"）：
    // 超出部分按上限生效，并在下面写明
    val effective = minutes.coerceIn(1, BackfillRules.MAX_MINUTES)
    val overLimit = minutes > BackfillRules.MAX_MINUTES
    val valid = minutes >= 1
    // 长时长会跨夜，选到跨天时把结束时刻说清楚（否则用户以为最多到今晚）
    val startNow = remember { LocalDateTime.now() }
    val endAt = startNow.plusMinutes(effective.toLong())
    val crossesDay = endAt.toLocalDate() != startNow.toLocalDate()

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认专注时长", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text("倒计时用这个时长，正计时与番茄不受影响", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text("$m 分钟") },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HourMinuteWheel(
                    hours = minutes / 60,
                    minutes = minutes % 60,
                    onChange = { h, m -> minutes = h * 60 + m },
                    maxTotalMinutes = BackfillRules.MAX_MINUTES,
                )
                if (overLimit) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "最长 ${BackfillRules.MAX_MINUTES / 60} 小时，超出会按这个时长记录",
                        fontSize = 12.sp,
                        color = WarningAmber,
                    )
                }
                if (crossesDay) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这个时长会跨夜：%02d:%02d 开始，次日 %02d:%02d 结束".format(
                            startNow.hour, startNow.minute, endAt.hour, endAt.minute,
                        ),
                        fontSize = 12.sp,
                        color = WarningAmber,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.updateSettings(viewModel.settings.value.copy(defaultDurationMinutes = effective))
                    onDismiss()
                },
                enabled = valid,
            ) { Text("保存", color = if (valid) Primary else DisabledGray) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 番茄计时参数：专注 / 短休息 / 长休息 / 每几轮长休息 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PomodoroDialog(settings: AppSettings, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var focus by remember { mutableStateOf(settings.pomodoroFocusMinutes) }
    var shortBreak by remember { mutableStateOf(settings.pomodoroBreakMinutes) }
    var longBreak by remember { mutableStateOf(settings.pomodoroLongBreakMinutes) }
    var rounds by remember { mutableStateOf(settings.pomodoroRoundsBeforeLongBreak) }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("番茄计时", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text("专注时长", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 20, 25, 30, 45, 60).forEach { m ->
                        FilterChip(selected = focus == m, onClick = { focus = m }, label = { Text("$m 分钟") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("休息时长", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { m ->
                        FilterChip(
                            selected = shortBreak == m,
                            onClick = { shortBreak = m },
                            label = { Text("$m 分钟") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("长休息时长", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30).forEach { m ->
                        FilterChip(
                            selected = longBreak == m,
                            onClick = { longBreak = m },
                            label = { Text("$m 分钟") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("每几轮进入长休息", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5).forEach { r ->
                        FilterChip(selected = rounds == r, onClick = { rounds = r }, label = { Text("$r 轮") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "当前节奏：$focus 分钟专注 → $shortBreak 分钟休息，每 $rounds 轮换成 $longBreak 分钟长休息。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(
                            pomodoroFocusMinutes = focus,
                            pomodoroBreakMinutes = shortBreak,
                            pomodoroLongBreakMinutes = longBreak,
                            pomodoroRoundsBeforeLongBreak = rounds,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("保存", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RingtoneDialog(settings: AppSettings, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var ringtone by remember { mutableStateOf(settings.ringtoneEnabled) }
    var autoStart by remember { mutableStateOf(settings.autoStart) }
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("番茄铃声", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                SwitchRow("完成提示音", "专注段结束响一声", ringtone) { ringtone = it }
                Spacer(Modifier.height(8.dp))
                SwitchRow("自动开始下一轮", "休息结束后自动进入下一轮专注", autoStart) { autoStart = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(
                            ringtoneEnabled = ringtone,
                            autoStart = autoStart,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("保存", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen, checkedThumbColor = Primary),
        )
    }
}

/** 习惯提醒：时间 + 每日目标；开关在设置页卡片上，这里只改参数 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(
    settings: AppSettings,
    viewModel: AppViewModel,
    notificationsOk: Boolean,
    exactAlarmOk: Boolean,
    onDismiss: () -> Unit,
) {
    val goalOptions = listOf(60, 90, 120, 180, 240)
    var goal by remember { mutableStateOf(settings.dailyGoalMinutes) }
    var time by remember { mutableStateOf(settings.reminderTime) }
    val timeValid = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time)
    val valid = timeValid

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("习惯提醒", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text("每日目标", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goalOptions.forEach { minutes ->
                        FilterChip(
                            selected = goal == minutes,
                            onClick = { goal = minutes },
                            label = { Text(goalLabel(minutes)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it.take(5) },
                    label = { Text("提醒时间（HH:mm）") },
                    singleLine = true,
                    isError = !timeValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "到点会弹通知并响一声（不震动）；想换提示音，可在系统「应用通知 → 习惯提醒」里调。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                if (!notificationsOk) {
                    Spacer(Modifier.height(10.dp))
                    Text("· 通知权限未开启，到点弹不出来", fontSize = 12.sp, color = Gold)
                }
                if (!exactAlarmOk) {
                    Spacer(Modifier.height(6.dp))
                    Text("· 系统未允许「闹钟和提醒」，触发可能晚几分钟", fontSize = 12.sp, color = Gold)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.updateSettings(
                        viewModel.settings.value.copy(
                            dailyGoalMinutes = goal,
                            reminderTime = time,
                        ),
                    )
                    onDismiss()
                },
                enabled = valid,
            ) { Text("保存", color = if (valid) Primary else DisabledGray) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 数据备份：导出 / 恢复 + 本机自动回滚点 */
@Composable
private fun BackupDialog(
    rollbacks: List<FlowTimeRepository.Rollback>,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onRestoreRollback: (FlowTimeRepository.Rollback) -> Unit,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据备份", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text(
                    "备份文件是 JSON，含全部任务、专注记录与设置，可存到「下载」或网盘。" +
                        "头像文件不包含在内（换机后需要重新设置头像）。",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "恢复会整体覆盖当前数据；覆盖前会自动留一份回滚点（就是下面这些）。",
                    fontSize = 13.sp,
                    color = Gold,
                )
                if (rollbacks.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("本机回滚点", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        rollbacks.forEach { rollback ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    rollbackLabel(rollback.savedAt),
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onRestoreRollback(rollback) }) {
                                    Text("回滚", fontSize = 13.sp, color = Primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) {
                Text("导出备份", color = Primary, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onRestore) { Text("从备份恢复", color = Primary) }
        },
    )
}

/** 回滚点时间文案：今天 16:28 / 10-08 20:15 */
private fun rollbackLabel(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) {
        "今天 %02d:%02d".format(t.hour, t.minute)
    } else {
        "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
    }
}

@Composable
private fun AboutDialog(version: String, onDismiss: () -> Unit) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于心流时间", fontWeight = FontWeight.Medium) },
        text = {
            Text(
                "心流时间 v$version\n\n番茄工作法 + 任务管理的专注效率应用，让注意力回到当下。\n\n意见反馈：1823666798@qq.com",
                fontSize = 14.sp,
                color = TextSecondary,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定", color = Primary) } },
    )
}

// ---- 小工具 ----

/** 目标时长文案：90 分钟显示为 1.5 小时 */
private fun goalLabel(minutes: Int): String = if (minutes % 60 == 0) {
    "${minutes / 60} 小时"
} else {
    "%.1f 小时".format(minutes / 60f)
}

/** 应用版本号（不依赖 BuildConfig，构建未开 buildConfig 也能用） */
private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "1.0"

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }
}
