package com.example.flowtime.ui.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.RecordSource
import com.example.flowtime.data.TrashStore
import com.example.flowtime.data.TrashedRecord
import com.example.flowtime.data.TrashedTask
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.FlowAlertDialog
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.tasks.TaskIcons
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.ErrorRed
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary

/**
 * 回收站：删掉的记录在这里待 [TrashStore.RETENTION_DAYS] 天，随时可以恢复。
 *
 * 放设置的「其他」组里而不是记录页：记录页是二级页，唯一入口在任务详情里，
 * 挂在那儿等于谁也找不到（见 MEMORY 里的导航结构记录）。
 *
 * 数据存在私有目录的 JSON 里，不碰 `records` 表 —— 好处是零 migration 风险，
 * 代价是这里的记录不会出现在统计/热力图/CSV 里（本来也不该出现）。
 */
@Composable
fun TrashScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val trashTasks by viewModel.trashTasks.collectAsStateWithLifecycle()
    var confirmPurge by remember { mutableStateOf(false) }
    /** 当前展开的任务（最多展开一个，避免整页铺满记录） */
    var expandedRootId by remember { mutableStateOf<String?>(null) }
    /** 长按某条 → 等确认永久删除它（长按只负责"选中"，真删要过一道确认） */
    var pendingPurgeRecord by remember { mutableStateOf<TrashedRecord?>(null) }
    var pendingPurgeTask by remember { mutableStateOf<TrashedTask?>(null) }

    /** 多选模式（右上角「编辑」）：勾选后批量恢复 / 批量永久删除 */
    var selectionMode by remember { mutableStateOf(false) }
    val selRecords = remember { mutableStateMapOf<Long, Boolean>() }
    val selTasks = remember { mutableStateMapOf<String, Boolean>() }
    var confirmPurgeSelected by remember { mutableStateOf(false) }
    val selectedCount = selRecords.size + selTasks.size
    val totalCount = trash.size + trashTasks.size
    val allSelected = totalCount > 0 && selectedCount == totalCount

    // 进页面重读一次：可能在别处（计时页/记录页）刚删过
    LaunchedEffect(Unit) { viewModel.refreshTrash() }

    // 恢复/删除后条目会从列表消失，选中集合要跟着瘦身，否则计数虚高
    LaunchedEffect(trash, trashTasks) {
        val liveRecordIds = trash.map { it.record.id }.toSet()
        val liveTaskIds = trashTasks.map { it.root.id }.toSet()
        selRecords.keys.filter { it !in liveRecordIds }.forEach { selRecords.remove(it) }
        selTasks.keys.filter { it !in liveTaskIds }.forEach { selTasks.remove(it) }
    }

    fun toggleRecord(entry: TrashedRecord) {
        val id = entry.record.id
        if (selRecords.remove(id) == null) selRecords[id] = true
    }

    fun toggleTask(entry: TrashedTask) {
        val id = entry.root.id
        if (selTasks.remove(id) == null) selTasks[id] = true
    }

    fun restoreSelected() {
        val records = trash.filter { selRecords.containsKey(it.record.id) }.map { it.record }
        val tasks = trashTasks.filter { selTasks.containsKey(it.root.id) }.map { it.root.id }
        viewModel.restoreTrashItems(records, tasks)
        selectionMode = false
        selRecords.clear()
        selTasks.clear()
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
                IconButton(
                    onClick = {
                        // 多选时先退选择，不直接离开页面（避免误触把勾选丢了）
                        if (selectionMode) {
                            selectionMode = false
                            selRecords.clear()
                            selTasks.clear()
                        } else {
                            onBack()
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text(
                    "回收站",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (totalCount > 0) {
                    TextButton(
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) {
                                selRecords.clear()
                                selTasks.clear()
                            }
                        },
                    ) {
                        Text(
                            if (selectionMode) "完成" else "编辑",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (selectionMode) {
                    "已选 $selectedCount 项"
                } else {
                    "保留 ${TrashStore.RETENTION_DAYS} 天，过期自动清除 · 长按可永久删除"
                },
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 12.dp),
            )

            Spacer(Modifier.height(12.dp))
            val empty = trashTasks.isEmpty() && trash.isEmpty()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (empty) "0 项" else "${trashTasks.size} 个任务 · ${trash.size} 条记录",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                if (!empty) {
                    TextButton(onClick = { confirmPurge = true }) {
                        Text("清空", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ErrorRed)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (empty) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(Dimens.CardPadding)) {
                        Text(
                            "回收站是空的。\n删除的任务和记录会在这里先放 ${TrashStore.RETENTION_DAYS} 天，随时恢复。",
                            fontSize = 13.sp,
                            color = TextPlaceholder,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = Dimens.ListBottomGap),
                ) {
                    if (trashTasks.isNotEmpty()) {
                        item(key = "header-task") { TrashGroupHeader("任务") }
                        items(trashTasks, key = { "task-" + it.root.id }) { entry ->
                            // 记录是删任务那一刻一起摘下来的快照，直接跟着条目走
                            val own = remember(entry) {
                                entry.records.sortedByDescending { it.endEpochMillis }
                            }
                            Box(Modifier.animateItem()) {
                                Column {
                                    TrashTaskRow(
                                        entry = entry,
                                        recordCount = own.size,
                                        recordMinutes = own.sumOf { it.minutes },
                                        expanded = expandedRootId == entry.root.id && !selectionMode,
                                        selectionMode = selectionMode,
                                        selected = selTasks.containsKey(entry.root.id),
                                        onToggle = {
                                            expandedRootId =
                                                if (expandedRootId == entry.root.id) null else entry.root.id
                                        },
                                        onToggleSelect = { toggleTask(entry) },
                                        onRestore = { viewModel.restoreTask(entry.root.id) },
                                        onLongPress = { pendingPurgeTask = entry },
                                    )
                                    if (expandedRootId == entry.root.id && !selectionMode) {
                                        TrashTaskRecords(entry = entry, own = own)
                                    }
                                    RecordDivider()
                                }
                            }
                        }
                    }
                    if (trash.isNotEmpty()) {
                        if (trashTasks.isNotEmpty()) item(key = "header-record") { TrashGroupHeader("记录") }
                        items(trash, key = { "rec-" + it.record.id }) { entry ->
                            Box(Modifier.animateItem()) {
                                Column {
                                    TrashRow(
                                        entry = entry,
                                        selectionMode = selectionMode,
                                        selected = selRecords.containsKey(entry.record.id),
                                        onRestore = { viewModel.restoreRecord(entry.record) },
                                        onLongPress = { pendingPurgeRecord = entry },
                                        onToggleSelect = { toggleRecord(entry) },
                                    )
                                    RecordDivider()
                                }
                            }
                        }
                    }
                }

                // 多选操作栏：只在编辑模式出现，粘在底部
                if (selectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                if (allSelected) {
                                    selRecords.clear()
                                    selTasks.clear()
                                } else {
                                    trash.forEach { selRecords[it.record.id] = true }
                                    trashTasks.forEach { selTasks[it.root.id] = true }
                                }
                            },
                        ) {
                            Text(
                                if (allSelected) "全不选" else "全选",
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { restoreSelected() }, enabled = selectedCount > 0) {
                            Text(
                                "恢复${
                                    if (selectedCount > 0) " ($selectedCount)" else ""
                                }",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedCount > 0) Primary else TextPlaceholder,
                            )
                        }
                        TextButton(
                            onClick = { confirmPurgeSelected = true },
                            enabled = selectedCount > 0,
                        ) {
                            Text(
                                "永久删除${
                                    if (selectedCount > 0) " ($selectedCount)" else ""
                                }",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedCount > 0) ErrorRed else TextPlaceholder,
                            )
                        }
                    }
                }
            }
        }
    }

    // 清空是**真不可逆**的，这里才该用确认框（撤销条场景相反，见 NN/g 的结论）
    if (confirmPurge) {
        FlowAlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text("清空回收站", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    buildString {
                        append("这 ")
                        append(
                            when {
                                trashTasks.isNotEmpty() && trash.isNotEmpty() ->
                                    "${trashTasks.size} 个任务和 ${trash.size} 条记录"
                                trashTasks.isNotEmpty() -> "${trashTasks.size} 个任务"
                                else -> "${trash.size} 条记录"
                            },
                        )
                        append("会从回收站永久清掉，删了就找不回来了。")
                        if (trashTasks.isNotEmpty()) {
                            append("\n\n任务名下已有的记录不会跟着消失，会回到「未分类」，时长照旧。")
                        }
                        append("\n\n不着急的话也可以不清 —— 满 ${TrashStore.RETENTION_DAYS} 天会自动清除。")
                    },
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeTrash()
                        confirmPurge = false
                    },
                ) {
                    Text("永久删除", color = ErrorRed, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }

    // 长按单条 → 永久删除。真不可逆，所以照旧过确认框（不可逆的动作一律不"长按即删"）
    pendingPurgeRecord?.let { entry ->
        val record = entry.record
        FlowAlertDialog(
            onDismissRequest = { pendingPurgeRecord = null },
            title = { Text("永久删除记录", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "「${record.taskName}」这条记录（${formatMinutesHm(record.minutes)}，" +
                        "${recordRangeText(record)}）会被彻底删掉，找不回来了。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeRecord(record.id)
                        pendingPurgeRecord = null
                    },
                ) {
                    Text("永久删除", color = ErrorRed, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurgeRecord = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }

    pendingPurgeTask?.let { entry ->
        val own = entry.records
        FlowAlertDialog(
            onDismissRequest = { pendingPurgeTask = null },
            title = { Text("永久删除任务", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    buildString {
                        append("「${entry.root.name}」会从回收站彻底删掉，找不回来了。")
                        if (own.isNotEmpty()) {
                            append(
                                "\n\n它名下的 ${own.size} 条记录会回到「未分类」（累计 " +
                                    "${formatMinutesHm(own.sumOf { it.minutes })}），" +
                                    "时长不会丢，只是不再挂在任何任务上。",
                            )
                        }
                    },
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeTask(entry.root.id)
                        pendingPurgeTask = null
                    },
                ) {
                    Text("永久删除", color = ErrorRed, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurgeTask = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }

    // 多选批量永久删除：一次确认，逐项清（不可逆，所以照旧要过确认框）
    if (confirmPurgeSelected) {
        val taskCount = selTasks.size
        FlowAlertDialog(
            onDismissRequest = { confirmPurgeSelected = false },
            title = { Text("永久删除 $selectedCount 项", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    buildString {
                        append("选中项会从回收站永久清掉，找不回来了。")
                        if (taskCount > 0) {
                            append("\n\n其中 $taskCount 个任务名下的记录不会销毁，会回到「未分类」，时长照旧。")
                        }
                    },
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeTrashItems(
                            recordIds = trash
                                .filter { selRecords.containsKey(it.record.id) }
                                .map { it.record.id },
                            taskRootIds = trashTasks
                                .filter { selTasks.containsKey(it.root.id) }
                                .map { it.root.id },
                        )
                        confirmPurgeSelected = false
                        selectionMode = false
                        selRecords.clear()
                        selTasks.clear()
                    },
                ) {
                    Text("永久删除", color = ErrorRed, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurgeSelected = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }
}

/** 回收站行：右侧「恢复」，**长按永久删除**（不可逆，长按只负责选中，真删还要过确认框） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    entry: TrashedRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val record = entry.record
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                // 多选时整行只负责勾选，不再响应长按删除
                onClick = { if (selectionMode) onToggleSelect() else Unit },
                onLongClick = { if (!selectionMode) onLongPress() },
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectMark(selected)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.taskName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.recordSource == RecordSource.MANUAL) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardGreenLight)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("补", fontSize = 10.sp, color = Primary)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(recordRangeText(record), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatMinutesHm(record.minutes)} · ${deletedAgoText(entry.deletedAt)}删除",
                fontSize = 11.sp,
                color = TextPlaceholder,
            )
        }
        // 多选时把单条「恢复」藏起来：操作统一走底部批量栏，行内再放一个容易手滑
        if (!selectionMode) {
            TextButton(onClick = onRestore) {
                Text("恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
            }
        }
    }
}

/** 「刚刚 / 12 分钟前 / 3 小时前 / 5 天前」 */
private fun deletedAgoText(deletedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - deletedAt) / 60_000L
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}

/** 多选勾选标记：空心圆 → 实心 + 白勾 */
@Composable
private fun SelectMark(selected: Boolean) {
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

/** 分组标题：任务 / 记录 */
@Composable
private fun TrashGroupHeader(text: String) {    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = TextPlaceholder,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

/**
 * 任务行：**整行点开/收起**名下记录，右侧「恢复」把任务连同子任务放回去。
 *
 * 记录不搬进回收站 —— 任务的 `taskId` 在墓碑期间照旧有效，所以恢复那一刻
 * 记录就自动归位，不用一条条捡回来。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashTaskRow(
    entry: TrashedTask,
    recordCount: Int,
    recordMinutes: Int,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
) {
    val task = entry.root
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                // 多选时整行只负责勾选，点开的记录列表也收起来（expanded 在调用处已按 selectionMode 关掉）
                onClick = { if (selectionMode) onToggleSelect() else onToggle() },
                onLongClick = { if (!selectionMode) onLongPress() },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectMark(selected)
            Spacer(Modifier.width(10.dp))
        }
        TaskIcons.Glyph(index = task.iconIndex, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.childCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("含 ${entry.childCount} 个小任务", fontSize = 11.sp, color = TextPlaceholder)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (recordCount == 0) {
                        append("暂无记录")
                    } else {
                        append("$recordCount 条记录 · ${formatMinutesHm(recordMinutes)}")
                    }
                    append(" · ${deletedAgoText(entry.deletedAt)}删除")
                    if (recordCount > 0) append(if (expanded) " · 收起" else " · 查看")
                },
                fontSize = 11.sp,
                color = TextPlaceholder,
            )
        }
        // 多选时操作统一走底部批量栏
        if (!selectionMode) {
            TextButton(onClick = onRestore) {
                Text("恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
            }
        }
    }
}

/** 展开后：这个任务（含子任务）留下的记录，只读 */
@Composable
private fun TrashTaskRecords(entry: TrashedTask, own: List<FocusRecord>) {
    val childNames = remember(entry) { entry.tasks.drop(1).associate { it.id to it.name } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
    ) {
        if (own.isEmpty()) {
            Text("这个任务还没有专注记录", fontSize = 12.sp, color = TextPlaceholder)
            return@Column
        }
        own.forEach { record ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // 属于子任务的记录带上子任务名，免得一堆时间分不清是哪一条
                    (childNames[record.taskId]?.let { "$it · " } ?: "") + recordRangeText(record),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatMinutesHm(record.minutes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
            }
        }
    }
}
