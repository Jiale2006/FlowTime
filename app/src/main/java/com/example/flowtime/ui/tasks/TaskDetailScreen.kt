package com.example.flowtime.ui.tasks
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.DonutChart
import com.example.flowtime.ui.components.DonutSlice
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.TrendScrollStrip
import com.example.flowtime.ui.components.formatMinutesCompact
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.records.BackfillDialog
import com.example.flowtime.ui.records.DurationEditDialog
import com.example.flowtime.ui.records.RecordDivider
import com.example.flowtime.ui.records.RecordRow
import com.example.flowtime.ui.records.RecordUndoBar
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.SuccessGreen
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId

/**
 * 「专注趋势」图表区固定高度（他 2026-10-26 要"这个窗口定高"）。
 *
 * 高度 = 表头行(≈20) + 间隔(10) + 柱区固定槽(130) + 间隔(6) + 日期(≈15) ≈ 181dp，留 3dp 余量。
 * 🔴 定高的意义：没有记录时只显示「暂无记录」一行，卡片会突然矮一大截；
 *    数据小（4min）时柱子矮，卡片也跟着缩。现在无数据也占同样的位置，卡片高度恒定。
 * 🔴 2026-10-27：窗口高度**不变**，被裁的是柱区占位太死。柱区改成吃剩余高度（见 WeeklyBarsPager），
 *    表头/日期行先占，字体调大时柱区自己让位 —— 「一二三四五六日」不会再被从底部切掉。
 */
private val TrendChartHeight = 184.dp


/**
 * 任务详情页：信息卡 + 小任务区（任何任务都能加小任务）+ 全部记录按周分页柱状图
 * + 记录列表 + 补录 + 所属分类入口。
 */
@Composable
fun TaskDetailScreen(
    viewModel: AppViewModel,
    taskId: String,
    onBack: () -> Unit,
    onStartFocus: () -> Unit,
    onOpenTask: (String) -> Unit = {},
    onOpenAllRecords: (String) -> Unit = {},
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    // 虚拟「自由专注」不落库，进这个路由时现场造一个（它只是聚合入口，见 FocusTask.freeFocus）
    val task = tasks.firstOrNull { it.id == taskId }
        ?: if (taskId == FocusTask.FREE_FOCUS_ID) FocusTask.freeFocus() else null
    val parentTask = task?.parentId?.let { parentId -> tasks.firstOrNull { it.id == parentId } }
    val children = tasks.filter { it.parentId == taskId }
    // 统计含**直接子任务**：与统计页「分类分布」口径一致（同一个「数学」两页数字终于对得上）
    val belongsToTask = remember(task?.id, tasks) {
        task?.let { RecordLink.treePredicate(it, tasks) } ?: { _: FocusRecord -> false }
    }
    val stats = remember(records, task?.id, tasks) {
        task?.let { viewModel.taskStats(it, records, tasks) }
    }
    val dayTrend = remember(records, task?.id, tasks) {
        viewModel.buildDayTrend(records.filter(belongsToTask))
    }
    val childMinutes = remember(records, children) {
        children.associate { child -> child.name to RecordLink.minutesOf(child, records) }
    }
    var showBackfill by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showEditTask by remember { mutableStateOf(false) }
    // 正在改时长的记录（仅手动补录） / 刚被删掉的记录
    var editingRecord by remember { mutableStateOf<FocusRecord?>(null) }
    var deletedRecord by remember { mutableStateOf<FocusRecord?>(null) }
    // 刚补录进来、还在撤销窗口里的记录（状态在 ViewModel，补录页返回后这里才读得到）
    val lastAdded = viewModel.lastAddedRecord.collectAsStateWithLifecycle().value
    val (cardColor, cardFg) = TaskCardPalette[(task?.colorIndex ?: 0) % TaskCardPalette.size]

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding),
            ) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        TaskIcons.Glyph(index = task?.iconIndex ?: 0, size = 24.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            task?.name ?: "任务不存在",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (task != null) {
                            Text(
                                when {
                                    // 虚拟任务没有 dateLabel（空串），照原样拼会留个「未分类 · 」的尾巴
                                    task.isFreeFocus -> "没有挂到任务的专注记录都归这里"
                                    children.isEmpty() -> "${parentTask?.name ?: "未分类"} · ${task.dateLabel}"
                                    else -> "${parentTask?.name ?: "未分类"} · ${children.size} 个小任务"
                                },
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                    // 改名 / 换图标配色。虚拟「自由专注」不落库，没有可编辑的东西
                    if (task != null && !task.isFreeFocus) {
                        IconButton(onClick = { showEditTask = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑任务",
                                tint = TextSecondary,
                            )
                        }
                    }
                }

                // 所属分类入口（小任务 → 它的父任务）
                if (parentTask != null) {
                    Spacer(Modifier.height(14.dp))
                    ProCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTask(parentTask.id) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "所属分类：${parentTask.name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 16.dp)) {
                        DetailStat("总时长", formatMinutesCompact(stats?.totalMinutes ?: 0), Modifier.weight(1f))
                        DetailStat("专注次数", "${stats?.sessionCount ?: 0} 次", Modifier.weight(1f))
                        DetailStat(
                            "最近专注",
                            stats?.lastFocusMillis?.let { formatDay(it) } ?: "-",
                            Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 小任务：任何任务都可以加，加了它就成为分类/大类。
                // 虚拟的「自由专注」没有子任务（它只是个聚合桶），整块不显示 ——
                // 否则能给它加出 parentId=__free_focus__ 的孤儿任务。
                if (task?.isFreeFocus != true) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "小任务",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${children.size} 个", fontSize = 13.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.height(12.dp))

                        if (children.isNotEmpty()) {
                            val totalChildMinutes = childMinutes.values.sum()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (totalChildMinutes > 0) {
                                    DonutChart(
                                        slices = children.mapIndexed { index, child ->
                                            DonutSlice(
                                                color = CategoryColors[index % CategoryColors.size],
                                                fraction = (childMinutes[child.name] ?: 0).toFloat() /
                                                    totalChildMinutes,
                                            )
                                        },
                                        centerText = formatMinutesCompact(totalChildMinutes),
                                        centerSubtext = "小任务",
                                        modifier = Modifier.weight(0.42f),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                }
                                Column(Modifier.weight(0.58f)) {
                                    children.forEach { child ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onOpenTask(child.id) }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(AccentGreen),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                child.name,
                                                fontSize = 13.sp,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                formatMinutesCompact(childMinutes[child.name] ?: 0),
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        } else {
                            Text(
                                "还没有小任务。加上「做练习」「听网课」这类小项，就能分别统计它们的时间。",
                                fontSize = 13.sp,
                                color = TextPlaceholder,
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        Button(
                            onClick = { showEditor = true },
                            enabled = task != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .pressScale(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加小任务", fontSize = 15.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                }
                Spacer(Modifier.height(14.dp))

                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Text("专注趋势", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        // 定高：无记录时「暂无记录」也占满同样的位置，卡片不会忽高忽低。
                        // 内容顶部对齐（不居中）：多出的几 dp 落在日期行下方，
                        // 柱子基线、日期行的位置在任何数据下都一样。
                        // 高度是**死高**：柱区在里面吃剩余空间，字体调大时柱区自己变矮，别让 Pager 裁掉日期行。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TrendChartHeight),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if ((stats?.sessionCount ?: 0) == 0) {
                                Text("暂无记录", fontSize = 13.sp, color = TextPlaceholder)
                            } else {
                                TrendScrollStrip(
                                    days = dayTrend,
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "专注记录",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { task?.let { onOpenAllRecords(it.id) } }) {
                                Text("查看更多记录 ›", fontSize = 12.sp, color = Primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val recent = stats?.recent.orEmpty()
                        if (recent.isEmpty()) {
                            Text(
                                "还没有专注记录，点击下方按钮开始第一次专注",
                                fontSize = 13.sp,
                                color = TextPlaceholder,
                            )
                        } else {
                            Text(
                                // 写清"只有 10 条"，免得以为这个任务一共就记了这么点
                                "最近 ${AppViewModel.RECENT_RECORDS_PREVIEW} 条 · 点按改时长 · 长按删除",
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                            Spacer(Modifier.height(4.dp))
                            recent.forEachIndexed { index, record ->
                                RecordRow(
                                    record = record,
                                    // 有子任务时记录会混在一起，必须显示任务名才分得清是哪条
                                    showTaskName = children.isNotEmpty(),
                                    onEditDuration = { editingRecord = record },
                                    onDelete = {
                                        deletedRecord = record
                                        viewModel.deleteRecord(record)
                                    },
                                )
                                if (index < recent.lastIndex) RecordDivider()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(Dimens.CardPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showBackfill = true },
                    enabled = task != null,
                    modifier = Modifier.weight(1f).height(Dimens.ButtonHeightHero).pressScale(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Primary),
                ) {
                    Text("补录时间", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        task?.let {
                            // 直接把任务带进 startFocus：selectTask 在计时未结束时会静默拒绝，
                            // 那样会沿用上一个任务开始计时（通知栏和记录都错在旧任务上）
                            viewModel.startFocus(if (it.isFreeFocus) null else it.id)
                            onStartFocus()
                        }
                    },
                    enabled = task != null,
                    modifier = Modifier.weight(1f).height(Dimens.ButtonHeightHero).pressScale(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("开始专注", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 撤销条：删除与补录共用（补录写错了不用再去"全部记录"里长按删）
        RecordUndoBar(
            visible = deletedRecord != null || lastAdded != null,
            message = when {
                lastAdded != null -> "已补录 ${formatMinutesCompact(lastAdded.minutes)}"
                else -> "已移入回收站"
            },
            accent = if (lastAdded != null) SuccessGreen else WarningAmber,
            // 两种提示都只停留 1 秒（真正兜底的是回收站）
            seconds = 1,
            onUndo = {
                deletedRecord?.let { viewModel.restoreRecord(it) }
                lastAdded?.let { viewModel.deleteRecord(it) }
                deletedRecord = null
                viewModel.clearLastAddedRecord()
            },
            onExpire = {
                deletedRecord = null
                viewModel.clearLastAddedRecord()
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
        )
    }

    if (showBackfill) {
        BackfillDialog(
            tasks = tasks,
            initialTaskId = task?.takeUnless { it.isFreeFocus }?.id,
            records = records,
            onDismiss = { showBackfill = false },
            onConfirm = { picked, startMillis, endMillis ->
                viewModel.addManualRecord(picked, startMillis, endMillis)
                showBackfill = false
            },
        )
    }

    editingRecord?.let { record ->
        DurationEditDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onConfirm = { minutes ->
                viewModel.updateRecordDuration(record, minutes)
                editingRecord = null
            },
        )
    }

    // 编辑当前任务：改名 / 换图标配色（归属不变）
    if (showEditTask && task != null) {
        TaskEditorSheet(
            editing = task,
            // 编辑态不显示分类选择器，只拿它把「归属」那行的父任务名查出来
            parentOptions = listOfNotNull(parentTask),
            defaultColorIndex = task.colorIndex,
            defaultIconIndex = task.iconIndex,
            onDismiss = { showEditTask = false },
            onConfirm = { draft ->
                viewModel.updateTask(
                    task.copy(
                        name = draft.name,
                        colorIndex = draft.colorIndex,
                        iconIndex = draft.iconIndex,
                    ),
                )
                showEditTask = false
            },
        )
    }

    // 在这个任务下添加小任务（归属固定为当前任务）
    if (showEditor && task != null) {
        TaskEditorSheet(
            fixedParent = task,
            defaultColorIndex = children.size % TaskCardPalette.size,
            defaultIconIndex = children.size % TaskIcons.size,
            onDismiss = { showEditor = false },
            onConfirm = { draft ->
                viewModel.addTask(
                    name = draft.name,
                    dateLabel = todayLabel(),
                    colorIndex = draft.colorIndex,
                    iconIndex = draft.iconIndex,
                    parentId = task.id,
                )
                showEditor = false
            },
        )
    }
}

/** 小任务分布配色（与统计页同一套） */
private val CategoryColors = listOf(Primary, AccentGreen, WarningAmber, Gold, PrimaryLight, SuccessGreen)

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

private fun formatDay(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d-%02d".format(dt.monthValue, dt.dayOfMonth)
}
