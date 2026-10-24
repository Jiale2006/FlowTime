package com.example.flowtime.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import com.example.flowtime.ui.components.FlowAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.CategoryBadge
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.ErrorRed
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary

/** 清单里的一行：任务（可能带小任务）+ 分类标注 + 时长/小任务数 */
private data class TaskRowModel(
    val task: FocusTask,
    val parentName: String?,
    val childCount: Int,
    val minutes: Int,
)

/**
 * 任务清单页：所有条目都是任务（同一个列表、同一套样式）。
 * 顶层任务排在前、小任务在后；挂了小任务的任务会显示「N 个小任务」——它就成了分类/大类。
 */
@Composable
fun TasksScreen(
    viewModel: AppViewModel,
    onOpenTask: (String) -> Unit,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val newTaskRequest by viewModel.newTaskRequest.collectAsStateWithLifecycle()
    // 视图模式持久化到设置里：退出应用再进来保持上次的选择
    val gridView = !settings.taskListView
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FocusTask?>(null) }
    // 乐观删除：确认那一刻先把条目（连同小任务）从界面摘掉，数据库删完
    // 由 Flow 追平；否则列表要等 Room 失效回调，比弹窗关闭晚一帧以上，观感不同步。
    var goneIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tasks) {
        if (goneIds.isNotEmpty()) {
            goneIds = goneIds.filter { id -> tasks.any { it.id == id } }.toSet()
        }
    }
    val liveTasks = tasks.filter { it.id !in goneIds }

    // 首页「＋新建任务」跳转过来时直接打开编辑器
    LaunchedEffect(newTaskRequest) {
        if (newTaskRequest) {
            showEditor = true
            viewModel.consumeNewTaskRequest()
        }
    }

    val nameById = liveTasks.associate { it.id to it.name }

    fun childrenOf(taskId: String): List<FocusTask> = liveTasks.filter { it.parentId == taskId }

    /** 累计时长：按 taskId 归属（含改名前的旧记录）；大类 = 自身 + 小任务 */
    fun minutesOf(task: FocusTask): Int =
        RecordLink.minutesOf(task, records) +
            childrenOf(task.id).sumOf { RecordLink.minutesOf(it, records) }

    // 顶层任务在前，小任务在后；大类（也就是"挂了小任务的任务"）自然排在顶层里
    val rows: List<TaskRowModel> = buildList {
        liveTasks.filter { it.parentId == null }.forEach { task ->
            add(
                TaskRowModel(
                    task = task,
                    parentName = null,
                    childCount = childrenOf(task.id).size,
                    minutes = minutesOf(task),
                ),
            )
        }
        liveTasks.filter { it.parentId != null }.forEach { task ->
            add(
                TaskRowModel(
                    task = task,
                    parentName = task.parentId?.let { nameById[it] },
                    childCount = 0,
                    minutes = RecordLink.minutesOf(task, records),
                ),
            )
        }
        // 自由专注排最后：没挂任务的记录（自由计时 + 任务被彻底删后放生的）都归这里。
        // 一条都没有就不占位，免得列表里凭空多一行空的。
        if (records.any { it.taskId == null }) {
            add(
                TaskRowModel(
                    task = FocusTask.freeFocus(),
                    parentName = null,
                    childCount = 0,
                    minutes = records.filter { it.taskId == null }.sumOf { it.minutes },
                ),
            )
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
                .padding(horizontal = Dimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(Dimens.ScreenTopGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "任务清单",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                // 网格/列表视图切换（写入设置，下次进入保持）
                IconButton(onClick = {
                    viewModel.updateSettings(settings.copy(taskListView = gridView))
                }) {
                    Icon(
                        imageVector = if (gridView) Icons.Default.List else Icons.Default.Apps,
                        contentDescription = if (gridView) "切换列表视图" else "切换网格视图",
                        tint = Primary,
                    )
                }
                OutlinedButton(
                    onClick = { showEditor = true },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Primary),
                ) {
                    Text("新建", color = Primary)
                }
            }
            Spacer(Modifier.height(Dimens.SectionGap))

            if (gridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    contentPadding = PaddingValues(bottom = Dimens.ListBottomGap),
                ) {
                    gridItems(rows, key = { it.task.id }) { row ->
                        TaskCard(
                            task = row.task,
                            parentName = row.parentName,
                            subtitle = rowSubtitle(row),
                            onClick = { onOpenTask(row.task.id) },
                            // 虚拟「自由专注」不落库，长按删除没有意义（也删不掉任何东西）
                            onLongClick = { if (!row.task.isFreeFocus) pendingDelete = row.task },
                            // 增删任务时平滑让位/淡入，而不是整列突然跳动
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    contentPadding = PaddingValues(bottom = Dimens.ListBottomGap),
                ) {
                    items(rows, key = { it.task.id }) { row ->
                        TaskListRow(
                            task = row.task,
                            parentName = row.parentName,
                            subtitle = rowSubtitle(row),
                            onClick = { onOpenTask(row.task.id) },
                            onLongClick = { if (!row.task.isFreeFocus) pendingDelete = row.task },
                            // 增删任务时平滑让位/淡入，而不是整列突然跳动
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorSheet(
            parentOptions = liveTasks.filter { it.parentId == null },
            defaultColorIndex = tasks.size % TaskCardPalette.size,
            defaultIconIndex = tasks.size % TaskIcons.size,
            onDismiss = { showEditor = false },
            onConfirm = { draft ->
                viewModel.addTask(
                    name = draft.name,
                    dateLabel = todayLabel(),
                    colorIndex = draft.colorIndex,
                    iconIndex = draft.iconIndex,
                    parentId = draft.parentId,
                )
                showEditor = false
            },
        )
    }

    pendingDelete?.let { target ->
        val childIds = childrenOf(target.id).map { it.id }
        val childCount = childIds.size
        // 这个任务（含子任务）名下会被保留的记录：条数要说清，否则用户不知道删完还剩什么
        val kept = records.filter { it.taskId != null && it.taskId in (childIds + target.id) }
        val keptMinutes = kept.sumOf { it.minutes }
        FlowAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除任务") },
            text = {
                Text(
                    buildString {
                        append("确定删除「${target.name}」吗？")
                        if (childCount > 0) append("\n\n它的 $childCount 个小任务会一并删除。")
                        if (kept.isNotEmpty()) {
                            append(
                                "\n\n它的 ${kept.size} 条专注记录（累计 ${formatMinutesHm(keptMinutes)}）" +
                                    "会一起移入回收站，随时能连同任务一起恢复。",
                            )
                        } else {
                            append("\n\n这个任务还没有专注记录。")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 级联条目一并即时摘除（父任务删除会带走小任务）
                    goneIds = goneIds + target.id + childrenOf(target.id).map { it.id }
                    viewModel.deleteTask(target.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

private fun rowSubtitle(row: TaskRowModel): String = when {
    row.childCount > 0 ->
        if (row.minutes > 0) {
            "${row.childCount} 个小任务 · 累计 ${formatMinutesHm(row.minutes)}"
        } else {
            "${row.childCount} 个小任务"
        }

    row.minutes > 0 -> "累计 ${formatMinutesHm(row.minutes)}"
    else -> "暂无记录"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: FocusTask,
    parentName: String?,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = TaskCardPalette[task.colorIndex % TaskCardPalette.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.GridCardHeight)
            .pressScale()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = ActivePalette.tileShape,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(Dimens.CardPaddingTight)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(Dimens.IconChipSmall)
                    .clip(ActivePalette.badgeShape)
                    .background(Color.White.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                TaskIcons.Glyph(index = task.iconIndex, size = Dimens.IconChipGlyph)
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = fg.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(
                    task.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (parentName != null) {
                        CategoryBadge(categoryName = parentName, onCardColor = fg)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = fg.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(width = 22.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fg.copy(alpha = 0.5f)),
            )
        }
    }
}

/** 列表视图行：图标 + 名称 + 分类标注 + 时长/小任务数 + 箭头 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskListRow(
    task: FocusTask,
    parentName: String?,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = TaskCardPalette[task.colorIndex % TaskCardPalette.size]
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScale()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(Dimens.RowPadding), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Dimens.IconChip)
                    .clip(ActivePalette.badgeShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                TaskIcons.Glyph(index = task.iconIndex, size = Dimens.IconChipGlyph)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (parentName != null) {
                        CategoryBadge(categoryName = parentName)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
            )
        }
    }
}
