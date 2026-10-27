package com.example.flowtime.ui.tasks

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import com.example.flowtime.ui.components.FlowAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.CategoryBadge
import com.example.flowtime.ui.components.formatMinutesCompact
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.ErrorRed
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPlaceholder
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
    /** 多选：长按任意任务进入；空集合 = 普通浏览模式 */
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** 待确认删除的任务集合（多选时是一组） */
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    /** 清单 / 已归档视图：点标题切换，点 ⌄ 从下拉里选 */
    var showArchived by remember { mutableStateOf(false) }
    /** 标题旁 ⌄ 的下拉是否展开 */
    var viewMenuOpen by remember { mutableStateOf(false) }
    /** 编辑模式：右上角「编辑」进入，点行勾选，归档/删除走底部条 */
    var editMode by remember { mutableStateOf(false) }
    /**
     * 长按弹出的下拉菜单（归档 / 删除）指向哪一行。
     * 菜单画在根 Box 的浮层上，不挂在列表行里面 —— 行被回收、重排、滚出屏幕都带不走它。
     */
    var menuTaskId by remember { mutableStateOf<String?>(null) }
    /** 菜单锚点：被长按那一行在窗口里的位置 */
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    /** 菜单自己量出来的尺寸：宽决定贴哪条边，高决定要不要翻到行下面 */
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    /** 手指按在菜单上时暂停自动收起 */
    var menuHeld by remember { mutableStateOf(false) }
    /** 每次交互 +1：把「0.9 秒没操作就收起」的倒计时重新计时 */
    var menuTick by remember { mutableIntStateOf(0) }
    /** 根容器尺寸：菜单贴边时要用 */
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    /** 列表在窗口里的顶端：菜单不能越过它，否则会压住标题栏的「新建」 */
    var listTopInWindow by remember { mutableStateOf(0f) }
    /** 刚被长按的那一行：抬手时的「点击」要拦掉，否则会长按完直接跳进详情页 */
    var suppressClickId by remember { mutableStateOf<String?>(null) }
    /** 菜单在窗口里的位置：点它外面就收起（Popup 会抢触摸，所以只能自己判） */
    val menuBounds = remember { mutableStateOf(Rect.Zero) }
    val rootPosInWindow = remember { mutableStateOf(Offset.Zero) }
    /** 编辑模式 = 点行勾选；空集合时底部条禁用 */
    val selectionMode = editMode
    // 乐观删除：确认那一刻先把条目（连同小任务）从界面摘掉，数据库删完
    // 由 Flow 追平；否则列表要等 Room 失效回调，比弹窗关闭晚一帧以上，观感不同步。
    var goneIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tasks) {
        if (goneIds.isNotEmpty()) {
            goneIds = goneIds.filter { id -> tasks.any { it.id == id } }.toSet()
        }
    }
    val liveTasks = tasks.filter { it.id !in goneIds }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 删除兜底：1.5 秒后库里还有这些 id，说明没删成 —— 把条目放回列表并说一声，
    // 而不是让它悄悄消失、下次进来又自己冒出来
    LaunchedEffect(goneIds) {
        val pending = goneIds
        if (pending.isEmpty()) return@LaunchedEffect
        delay(1500)
        val stillThere = pending.filter { id -> tasks.any { it.id == id } }
        if (stillThere.isNotEmpty()) {
            goneIds = goneIds - stillThere.toSet()
            Toast.makeText(context, "删除没成功，任务已恢复", Toast.LENGTH_SHORT).show()
        }
    }

    /** 收起长按菜单（尺寸留着复用，下次弹出不用重新量一帧） */
    fun closeMenu() {
        menuTaskId = null
        menuHeld = false
    }

    // 菜单弹出后 2 秒没有操作就收起（他定的时长）；手指按在菜单上时暂停计时
    LaunchedEffect(menuTaskId, menuTick, menuHeld) {
        if (menuTaskId == null || menuHeld) return@LaunchedEffect
        delay(2000)
        menuTaskId = null
        menuHeld = false
    }

    // 返回键先收菜单：菜单开着的时候不该顺手退出页面
    BackHandler(enabled = menuTaskId != null) { closeMenu() }

    // 切换清单/归档视图时清空多选，避免“选中的项在新视图里看不见”
    LaunchedEffect(showArchived) {
        selectedIds = emptySet()
        editMode = false
        closeMenu()
    }

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

    // 归档视图 = 同一张清单，只把数据源换成已归档任务
    val scopedTasks = liveTasks.filter { it.archived == showArchived }

    // 整列就按手动排序展示：父子关系只表现为"这一行带个分类标签"，
    // 不再把顶层任务和小任务分成两段 —— 拖动只改顺序，绝不改归属。
    // 「自由专注」也是真任务行，跟别人一样按 sortOrder 排。
    val rows: List<TaskRowModel> = scopedTasks.map { task ->
        TaskRowModel(
            task = task,
            parentName = task.parentId?.let { nameById[it] },
            childCount = childrenOf(task.id).size,
            minutes = minutesOf(task),
        )
    }

    fun toggleSelect(task: FocusTask) {
        menuTaskId = null
        selectedIds = if (task.id in selectedIds) selectedIds - task.id else selectedIds + task.id
    }

    /** 选中的根任务：父任务被选时跳过它的子任务（父任务会级联带走子任务） */
    fun selectedRoots(): List<FocusTask> = selectedIds
        .mapNotNull { id -> liveTasks.firstOrNull { it.id == id } }
        .filter { it.parentId == null || it.parentId !in selectedIds }

    // ---- 拖动排序：只在列表视图；只换同一层内的位置，不会改变归属 ----
    /**
     * 手势闭包（pointerInput）不随重组重启，捕获到的 rows 会一直是最初那份。
     * 拖动要用最新顺序，所以统一从这里取。
     */
    val rowsState = rememberUpdatedState(rows)
    /** 正在拖的行；null = 没在拖 */
    var dragId by remember { mutableStateOf<String?>(null) }
    /** 拖动开始时这一屏的顺序快照（拖动中按它渲染，松手才落库） */
    var dragOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 被拖行相对它"槽位"的位移：换位一次扣掉一行，松手回 0 */
    var dragOffset by remember { mutableStateOf(0f) }
    /** 网格视图用的横向位移（列表恒为 0） */
    var dragOffsetX by remember { mutableStateOf(0f) }
    /** 位移超过这个距离才算「真的在排序」，否则当成长按后手抖 */
    val dragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
    /** 震动节流：快速拖过好几行时不要连成一片嗡嗡声 */
    var lastHapticNanos by remember { mutableStateOf(0L) }
    /** 其他行让位：跟落位一个节奏（~80ms），别让人盯着看它慢慢挪 */
    val placeSpec = remember {
        spring<IntOffset>(
            dampingRatio = 1f,
            stiffness = 2600f,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
    }
    /** 落位动画的 Job：下一次拖动开始前先取消，避免两次落位互相打架 */
    var settleJob by remember { mutableStateOf<Job?>(null) }

    /** 每行实测高度：拖动时越过半行才换位，手指停在哪行就跟哪行换 */
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    /** 每行在窗口里的位置：长按要弹菜单时直接用，不必等一次布局回调（非 state，写入不触发重组） */
    val rowBounds = remember { mutableMapOf<String, Rect>() }
    val gapPx = with(LocalDensity.current) { Dimens.CardGap.toPx() }
    /** 拖动位移只能在自己这一层的范围里走（撞到别的层级就停住，不会飞出屏幕） */
    var dragLimit by remember { mutableStateOf(-1e9f..1e9f) }

    /** 把拖动相关的状态全部归零 —— 只写这一处，避免哪条分支漏清留下"卡片一直抬着" */
    fun resetDrag() {
        dragId = null
        dragOrder = emptyList()
        dragOffset = 0f
        dragOffsetX = 0f
        dragLimit = -1e9f..1e9f
    }

    /** 弹出某一行的长按菜单（归档 / 删除）：位置取这行此刻在窗口里的坐标 */
    fun showRowMenu(taskId: String) {
        menuAnchor = rowBounds[taskId] ?: Rect.Zero
        menuTaskId = taskId
        // 每次弹出都先把「按住暂停」状态清掉，免得上一次没收尾卡住 1 秒倒计时
        menuHeld = false
        menuTick++
    }

    fun hapticTick() {
        val now = System.nanoTime()
        if (now - lastHapticNanos < 60_000_000L) return
        lastHapticNanos = now
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }


    /** 松手后库里的顺序还没发回来之前，先沿用拖动得到的顺序，避免列表先回弹再飞过去 */
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }

    /**
     * 拖动中按当前顺序渲染（松手前不写库，否则每帧一次写入）。
     * 松手后也一样，直到 rows 的真实顺序等于 pendingOrder 才切回去。
     */
    val orderToRender = if (dragId != null) dragOrder else pendingOrder
    val displayRows = if (orderToRender.isNullOrEmpty()) rows else orderToRender.mapNotNull { id ->
        rows.firstOrNull { it.task.id == id }
    }

    // 库里顺序到位 → 解除暂挂；万一没到位（例如别处改动），1.2 秒后也解除
    LaunchedEffect(pendingOrder, rows) {
        val p = pendingOrder ?: return@LaunchedEffect
        if (rows.map { it.task.id } == p) pendingOrder = null
    }
    // 进/出编辑模式或开始选择时，残留的挂起顺序必须丢掉，否则后面的重组会拿它渲染成"换序"
    LaunchedEffect(selectionMode, selectedIds) {
        if (pendingOrder != null && dragId == null) {
            val p = pendingOrder ?: return@LaunchedEffect
            if (rows.map { it.task.id } != p) pendingOrder = null
        }
    }
    LaunchedEffect(pendingOrder) {
        val p = pendingOrder ?: return@LaunchedEffect
        delay(1200)
        if (pendingOrder == p) pendingOrder = null
    }

    fun applyDrag(deltaY: Float) {
        val draggedId = dragId ?: return
        val list = dragOrder.toMutableList()
        var index = list.indexOf(draggedId)
        if (index < 0) return
        var offset = dragOffset + deltaY

        fun step(direction: Int): Boolean {
            val otherId = list.getOrNull(index + direction) ?: return false
            val height = (rowHeights[otherId] ?: return false) + gapPx
            if (offset * direction <= height / 2f) return false
            list[index] = list[index + direction]
            list[index + direction] = draggedId
            index += direction
            offset -= direction * height
            // 每跨过一行震一下：拖到哪儿了不用看屏幕也心里有数
            hapticTick()
            return true
        }
        // 先朝手指移动的方向换位，再反向补，避免"先下去再被拉回来"的抖动
        if (deltaY >= 0f) {
            while (step(1)) Unit
            while (step(-1)) Unit
        } else {
            while (step(-1)) Unit
            while (step(1)) Unit
        }
        // 整列都能拖：上限 = 到列表顶端，下限 = 到列表末端（不限层级）
        var above = 0f
        for (i in index - 1 downTo 0) above += (rowHeights[list[i]] ?: 0) + gapPx
        var below = 0f
        for (i in index + 1 until list.size) below += (rowHeights[list[i]] ?: 0) + gapPx
        dragLimit = -above..below
        dragOrder = list
        dragOffset = offset.coerceIn(-above, below)
    }

    // 网格视图的拖动：数据本身还是一维顺序，落点按「被拖卡片中心压住哪个格子」算
    val gridState = rememberLazyGridState()

    fun applyGridDrag(deltaX: Float, deltaY: Float) {
        val draggedId = dragId ?: return
        val list = dragOrder.toMutableList()
        val index = list.indexOf(draggedId)
        if (index < 0) return
        var offX = dragOffsetX + deltaX
        var offY = dragOffset + deltaY
        val visible = gridState.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == draggedId } ?: return
        val cx = me.offset.x + me.size.width / 2f + offX
        val cy = me.offset.y + me.size.height / 2f + offY
        val target = visible.firstOrNull { other ->
            other.key != draggedId &&
                cx >= other.offset.x && cx <= other.offset.x + other.size.width &&
                cy >= other.offset.y && cy <= other.offset.y + other.size.height
        }
        if (target != null) {
            val targetId = target.key as? String
            val targetIndex = targetId?.let { list.indexOf(it) } ?: -1
            if (targetIndex >= 0 && targetIndex != index) {
                list.removeAt(index)
                list.add(targetIndex, draggedId)
                // 扣掉两个格子之间的位置差，卡片才不会在换位瞬间跳一下
                offX -= (target.offset.x - me.offset.x).toFloat()
                offY -= (target.offset.y - me.offset.y).toFloat()
                dragOrder = list
                hapticTick()
            }
        }
        dragOffsetX = offX
        dragOffset = offY
    }

    // 拖到上下边缘自动滚动：滚动后把位移补回拖尾，再补一次换位判定
    val listState = rememberLazyListState()
    val edgeScrollPx = with(LocalDensity.current) { 96.dp.toPx() }
    val minScrollPx = with(LocalDensity.current) { 3.dp.toPx() }
    val maxScrollPx = with(LocalDensity.current) { 15.dp.toPx() }
    LaunchedEffect(dragId) {
        if (dragId == null) return@LaunchedEffect
        while (true) {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == dragId }
            if (info != null) {
                val top = info.offset + dragOffset
                val vp = listState.layoutInfo
                val topEdge = vp.viewportStartOffset + edgeScrollPx
                val bottomEdge = vp.viewportEndOffset - edgeScrollPx
                // 越界越深滚得越快：3dp → 15dp。原来是固定 10dp，长列表拖到底要干等
                fun ramp(depth: Float): Float =
                    minScrollPx + (maxScrollPx - minScrollPx) *
                        (depth / edgeScrollPx).coerceIn(0f, 1f)
                val step = when {
                    top < topEdge &&
                        dragOffset > dragLimit.start + 1f -> -ramp(topEdge - top)
                    top + info.size > bottomEdge &&
                        dragOffset < dragLimit.endInclusive - 1f ->
                        ramp(top + info.size - bottomEdge)
                    else -> 0f
                }
                // 列表没得滚（内容一屏装得下）时不能给卡片加位移，否则它会自己一路飞上去
                if (step != 0f) {
                    val consumed = listState.scrollBy(step)
                    if (consumed != 0f) {
                        dragOffset += consumed
                        applyDrag(0f)
                    }
                }
            }
            withFrameNanos { }
        }
    }

    /**
     * 松手落位：把拖出来的整列顺序写回 sortOrder。
     * **只动顺序，不动父子归属** —— 小任务拖到哪儿都还是原来那个任务的子任务。
     */
    fun commitDrag(draggedId: String) {
        val newOrder = dragOrder
        if (newOrder.size > 1) {
            viewModel.reorderTasks(newOrder)
            pendingOrder = newOrder
        }
        // 落位：90ms 直线收回 0，拖尾一收就解除拖动态。够快，不会让人等
        val from = dragOffset
        settleJob?.cancel()
        settleJob = scope.launch {
            if (from != 0f) {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 90),
                ) { v, _ ->
                    dragOffset = v
                }
            }
            // 只收自己这一次的尾：期间要是又拖了别的行，别把新的拖动清掉
            if (dragId == draggedId) resetDrag()
        }
    }

    /** 手势收尾：动过就落库落位；没动过（长按后直接松手）就弹归档/删除 */
    fun finishDrag(taskId: String, moved: Boolean) {
        if (moved && dragId == taskId) {
            commitDrag(taskId)
        } else {
            resetDrag()
            showRowMenu(taskId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .onGloballyPositioned { rootPosInWindow.value = it.positionInWindow() }
            .pointerInput(menuTaskId) {
                if (menuTaskId == null) return@pointerInput
                awaitEachGesture {
                    // 落在菜单外：只用来收起菜单，这一整串事件吃掉（不然顺手就把别的任务点开了）
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (menuBounds.value.contains(down.position + rootPosInWindow.value)) return@awaitEachGesture
                    closeMenu()
                    down.consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
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
                // 标题只是标题，点了不动：切清单统一走旁边那个 ⌄ 下拉
                Text(
                    if (showArchived) "已归档任务" else "任务清单",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.width(2.dp))
                // ⌄ 是下拉：直接选「任务清单 / 已归档任务」
                Box {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "选择清单",
                        tint = TextPlaceholder,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewMenuOpen = true }
                            .padding(4.dp),
                    )
                    DropdownMenu(
                        expanded = viewMenuOpen,
                        onDismissRequest = { viewMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "任务清单",
                                    color = if (!showArchived) Primary else TextPrimary,
                                )
                            },
                            onClick = {
                                viewMenuOpen = false
                                showArchived = false
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "已归档任务",
                                    color = if (showArchived) Primary else TextPrimary,
                                )
                            },
                            onClick = {
                                viewMenuOpen = false
                                showArchived = true
                            },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
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
                // 编辑模式：点行勾选，底部条归档/删除
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    onClick = {
                        editMode = !editMode
                        selectedIds = emptySet()
                        menuTaskId = null
                    },
                ) {
                    Text(if (editMode) "完成" else "编辑", color = TextSecondary, fontSize = 14.sp)
                }
                // 归档视图里不新建（新建出来的都是未归档任务，放这里会“凭空消失”）
                if (!showArchived) {
                    OutlinedButton(
                        onClick = { showEditor = true },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Primary),
                    ) {
                        Text("新建", color = Primary)
                    }
                }
            }
            Spacer(Modifier.height(Dimens.SectionGap))

            // 归档视图刚清空（把最后一个取消归档）时给一句回话，别只剩一片空白
            if (showArchived && rows.isEmpty()) {
                Text(
                    "还没有归档的任务 · 长按任务可以归档",
                    fontSize = 13.sp,
                    color = TextPlaceholder,
                )
            }

            if (gridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    contentPadding = PaddingValues(bottom = Dimens.ListBottomGap),
                    modifier = Modifier.onGloballyPositioned {
                        listTopInWindow = it.boundsInWindow().top
                    },
                ) {
                    gridItems(displayRows, key = { it.task.id }) { row ->
                        val dragging = row.task.id == dragId
                        // 网格也把长按门槛降到 250ms，和列表一致
                        val baseConfig = LocalViewConfiguration.current
                        val quick = remember(baseConfig) {
                            object : ViewConfiguration by baseConfig {
                                override val longPressTimeoutMillis: Long = 250
                            }
                        }
                        CompositionLocalProvider(LocalViewConfiguration provides quick) {
                        Box(
                            Modifier
                                .then(if (dragging) Modifier else Modifier.animateItem(placementSpec = placeSpec))
                                .onGloballyPositioned {
                                    rowBounds[row.task.id] = it.boundsInWindow()
                                }
                                .then(
                                    if (selectionMode) {
                                        Modifier
                                    } else {
                                        // 网格拖动：长按拿起 → 松手不动弹菜单 → 移动了按"压住哪个格子"换位
                                        Modifier.pointerInput(row.task.id) {
                                            var moved = false
                                            var accumX = 0f
                                            var accumY = 0f
                                            var finished = false
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    suppressClickId = row.task.id
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    closeMenu()
                                                    settleJob?.cancel()
                                                    resetDrag()
                                                    dragOrder = rowsState.value.map { it.task.id }
                                                    moved = false
                                                    accumX = 0f
                                                    accumY = 0f
                                                    finished = false
                                                },
                                                onDragEnd = {
                                                    suppressClickId = null
                                                    if (!finished) {
                                                        finished = true
                                                        finishDrag(row.task.id, moved)
                                                    }
                                                },
                                                onDragCancel = {
                                                    suppressClickId = null
                                                    if (!finished) {
                                                        finished = true
                                                        finishDrag(row.task.id, moved)
                                                    }
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    if (!moved) {
                                                        accumX += amount.x
                                                        accumY += amount.y
                                                        if (abs(accumX) >= dragSlopPx || abs(accumY) >= dragSlopPx) {
                                                            moved = true
                                                            dragId = row.task.id
                                                            dragOffset = 0f
                                                            dragOffsetX = 0f
                                                            hapticTick()
                                                        }
                                                    }
                                                    if (moved) applyGridDrag(amount.x, amount.y)
                                                },
                                            )
                                        }
                                    },
                                )
                                .then(
                                    if (dragging) {
                                        Modifier
                                            .zIndex(1f)
                                            .graphicsLayer {
                                                translationX = dragOffsetX
                                                translationY = dragOffset
                                                shadowElevation = 12.dp.toPx()
                                                // 阴影跟着卡片圆角走，别是直角的
                                                shape = ActivePalette.tileShape
                                                clip = false
                                            }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            TaskCard(
                                task = row.task,
                                parentName = row.parentName,
                                subtitle = rowSubtitle(row),
                                selected = row.task.id in selectedIds,
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelect(row.task)
                                    } else if (suppressClickId == row.task.id) {
                                        suppressClickId = null
                                    } else {
                                        closeMenu()
                                        onOpenTask(row.task.id)
                                    }
                                },
                                // 长按由拖动手势独占，抬手那一下用 suppressClickId 拦掉
                                onLongClick = if (selectionMode) ({ toggleSelect(row.task) }) else null,
                            )
                        }
                        }
                    }
                }
            } else {
                // 长按门槛 500ms → 250ms：手指一按住就能开始拖，不用等半秒
                val rowViewConfiguration = LocalViewConfiguration.current
                val quickLongPress = remember(rowViewConfiguration) {
                    object : ViewConfiguration by rowViewConfiguration {
                        override val longPressTimeoutMillis: Long = 250
                    }
                }
                CompositionLocalProvider(LocalViewConfiguration provides quickLongPress) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    contentPadding = PaddingValues(bottom = Dimens.ListBottomGap),
                    modifier = Modifier.onGloballyPositioned {
                        listTopInWindow = it.boundsInWindow().top
                    },
                ) {
                    items(displayRows, key = { it.task.id }) { row ->
                        val dragging = row.task.id == dragId
                        // 只有真的拖起来才「抬起」：长按不动时不能有任何尺寸/阴影变化
                        val liftScale by animateFloatAsState(
                            targetValue = if (dragging) 1.02f else 1f,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 1100f),
                            label = "liftScale",
                        )
                        val liftShadow by animateFloatAsState(
                            targetValue = if (dragging) 14f else 0f,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 1100f),
                            label = "liftShadow",
                        )
                        // 普通模式：整行长按即可拖动排序；编辑模式不拖（手势留给勾选）
                        val draggable = !selectionMode
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(if (dragging) Modifier else Modifier.animateItem(placementSpec = placeSpec))
                                .onGloballyPositioned {
                                    rowHeights[row.task.id] = it.size.height
                                    rowBounds[row.task.id] = it.boundsInWindow()
                                }
                                .then(
                                    if (draggable) {
                                        // 长按 = 拿起；松手没动 = 弹归档/删除；动过 = 排序。
                                        // moved/accum/finished 放在 pointerInput 闭包里：手势自己的状态，
                                        // 重组不会丢，也不会串到下一根手指去。
                                        Modifier.pointerInput(row.task.id) {
                                            var moved = false
                                            var accum = 0f
                                            var finished = false
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    suppressClickId = row.task.id
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    closeMenu()
                                                    settleJob?.cancel()
                                                    resetDrag()
                                                    dragOrder = rowsState.value.map { it.task.id }
                                                    moved = false
                                                    accum = 0f
                                                    finished = false
                                                },
                                                onDragEnd = {
                                                    suppressClickId = null
                                                    if (!finished) {
                                                        finished = true
                                                        finishDrag(row.task.id, moved)
                                                    }
                                                },
                                                onDragCancel = {
                                                    suppressClickId = null
                                                    if (!finished) {
                                                        finished = true
                                                        finishDrag(row.task.id, moved)
                                                    }
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    if (!moved) {
                                                        // 攒够 8dp 才算排序：手指轻抖一下不该把列表搅乱
                                                        accum += amount.y
                                                        if (abs(accum) >= dragSlopPx) {
                                                            moved = true
                                                            dragId = row.task.id
                                                            dragOffset = 0f
                                                            hapticTick()
                                                        }
                                                    }
                                                    if (moved) applyDrag(amount.y)
                                                },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    Modifier
                                        .zIndex(if (dragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (dragging) dragOffset else 0f
                                            scaleX = liftScale
                                            scaleY = liftScale
                                            shadowElevation = liftShadow.dp.toPx()
                                            // 阴影按卡片的圆角画：不给 shape 的话图层轮廓是矩形，投影是直角的
                                            shape = RoundedCornerShape(16.dp)
                                            clip = false
                                        },
                                ),
                        ) {
                            TaskListRow(
                                task = row.task,
                                parentName = row.parentName,
                                subtitle = rowSubtitle(row),
                                selected = row.task.id in selectedIds,
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelect(row.task)
                                    } else if (suppressClickId == row.task.id) {
                                        // 刚才这一下是长按（已经在拖/已露出菜单），抬手不算点击
                                        suppressClickId = null
                                    } else {
                                        closeMenu()
                                        onOpenTask(row.task.id)
                                    }
                                },
                                // 长按由拖动手势独占（拿起 / 排序 / 露菜单）；抬手那一下用 suppressClickId 拦掉
                                onLongClick = if (selectionMode) ({ toggleSelect(row.task) }) else null,
                            )
                        }
                    }
                }
                }
            }
        }

        // 编辑模式操作条：归档/删除都在这里
        if (selectionMode) {
            SelectionBar(
                count = selectedIds.size,
                archivedView = showArchived,
                onCancel = {
                    selectedIds = emptySet()
                    editMode = false
                },
                onArchive = {
                    viewModel.archiveTasks(selectedRoots().map { it.id }, !showArchived)
                    selectedIds = emptySet()
                },
                onDelete = { pendingDeleteIds = selectedIds },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            )
        }

        // 长按菜单浮层：画在根 Box 上，不属于任何列表行 —— 行被回收、重排、滚出屏幕都带不走它
        val anchorId = menuTaskId
        if (anchorId != null && menuAnchor != Rect.Zero) {
            TaskRowMenu(
                archivedView = showArchived,
                onArchive = {
                    closeMenu()
                    viewModel.archiveTasks(listOf(anchorId), !showArchived)
                },
                onDelete = {
                    closeMenu()
                    pendingDeleteIds = setOf(anchorId)
                },
                onHoldChange = { held ->
                    menuHeld = held
                    if (!held) menuTick++
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { menuSize = it }
                    .offset {
                        val w = if (menuSize.width > 0) menuSize.width else 92.dp.roundToPx()
                        val h = if (menuSize.height > 0) menuSize.height else 84.dp.roundToPx()
                        val root = rootPosInWindow.value
                        val margin = 8.dp.toPx()
                        // 上限：菜单不能进标题栏；下限：也不能出屏幕底
                        val minY = (listTopInWindow - root.y + margin).coerceAtLeast(margin)
                        var x = (menuAnchor.right - root.x - w).toInt()
                        var y = (menuAnchor.top - root.y - 46.dp.toPx()).toInt()
                        // 顶到标题栏就夹住（不翻到行下方，那样会盖住下一行）
                        if (y < minY) y = minY.toInt()
                        // 下面顶到边 → 贴底
                        if (rootSize.height > 0 && y + h > rootSize.height - margin) {
                            y = (rootSize.height - h - margin).toInt()
                        }
                        if (x < margin) x = margin.toInt()
                        IntOffset(x, y)
                    }
                    .zIndex(9f)
                    .onGloballyPositioned { menuBounds.value = it.boundsInWindow() },
            )
        }
    }

    if (showEditor) {
        TaskEditorSheet(
            // 归档的大类不再作为可选父任务（不然新建的任务会挂到一个看不见的类下）
            parentOptions = liveTasks.filter { it.parentId == null && !it.archived },
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

    pendingDeleteIds?.let { ids ->
        val targets = ids.mapNotNull { id -> liveTasks.firstOrNull { it.id == id } }
        val roots = targets.filter { it.parentId == null || it.parentId !in ids }
        if (roots.isEmpty()) {
            // 目标已经不在了（刚被别处删掉 / id 对不上）：直接收摊，别让弹窗卡住也不崩
            LaunchedEffect(ids) { pendingDeleteIds = null }
        } else {
        // 真正会被移入回收站的任务（父任务会带走子任务）
        val removedIds = roots.flatMap { root -> listOf(root.id) + childrenOf(root.id).map { it.id } }.toSet()
        val kept = records.filter { it.taskId != null && it.taskId in removedIds }
        val keptMinutes = kept.sumOf { it.minutes }
        FlowAlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text(if (roots.size > 1) "删除 ${roots.size} 个任务" else "删除任务") },
            text = {
                Text(
                    buildString {
                        if (roots.size == 1) {
                            append("确定删除「${roots.first().name}」吗？")
                        } else {
                            append("确定删除选中的 ${roots.size} 个任务吗？")
                        }
                        val childCount = removedIds.size - roots.size
                        if (childCount > 0) append("\n\n它们的 $childCount 个小任务会一并删除。")
                        if (kept.isNotEmpty()) {
                            append(
                                "\n\n这些任务的 ${kept.size} 条专注记录（累计 " +
                                    "${formatMinutesCompact(keptMinutes)}）会一起移入回收站，随时能连同任务一起恢复。",
                            )
                        } else {
                            append("\n\n这些任务还没有专注记录。")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 乐观摘除：确认那一刻先把条目从界面拿掉，数据库删完由 Flow 追平
                    goneIds = goneIds + removedIds
                    // 清掉拖动残留：挂着的旧顺序快照会把刚删的条目又渲染出来
                    pendingOrder = pendingOrder?.filter { it !in removedIds }
                    if (dragId in removedIds) {
                        settleJob?.cancel()
                        resetDrag()
                    }
                    viewModel.deleteTasks(roots.map { it.id })
                    pendingDeleteIds = null
                    selectedIds = emptySet()
                }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) { Text("取消") }
            },
        )
        }
    }
}

private fun rowSubtitle(row: TaskRowModel): String = when {
    row.childCount > 0 ->
        if (row.minutes > 0) {
            "${row.childCount} 个小任务 · 累计 ${formatMinutesCompact(row.minutes)}"
        } else {
            "${row.childCount} 个小任务"
        }

    row.minutes > 0 -> "累计 ${formatMinutesCompact(row.minutes)}"
    else -> "暂无记录"
}

/** 多选底部操作条：归档 / 删除；归档视图里换成「取消归档」 */
@Composable
private fun SelectionBar(
    count: Int,
    archivedView: Boolean,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (count == 0) "选择任务" else "已选 $count 项",
                fontSize = 13.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("取消", color = TextSecondary) }
            TextButton(onClick = onArchive, enabled = count > 0) {
                Text(
                    if (archivedView) "取消归档" else "归档",
                    color = if (count > 0) Primary else TextPlaceholder,
                )
            }
            TextButton(onClick = onDelete, enabled = count > 0) {
                Text("删除", color = if (count > 0) ErrorRed else TextPlaceholder)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: FocusTask,
    parentName: String?,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val (bg, fg) = TaskCardPalette[task.colorIndex % TaskCardPalette.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.GridCardHeight)
            .pressScale()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = ActivePalette.tileShape,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Primary else BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(Dimens.CardPaddingTight)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Dimens.IconChipSmall)
                        .clip(ActivePalette.badgeShape)
                        .background(Color.White.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    TaskIcons.Glyph(index = task.iconIndex, size = Dimens.IconChipGlyph)
                }
                Spacer(Modifier.weight(1f))
                // 多选时右上角换成勾：一眼看出哪些被选中
                Icon(
                    if (selected) Icons.Default.Check else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (selected) Primary else fg.copy(alpha = 0.6f),
                )
            }
            // 图标在上、文字在下流式排：字体放大时两者只会互相顶开，不会叠在一起
            Spacer(Modifier.weight(1f))
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
    }
}

/**
 * 长按单行的浮起菜单：归档（归档视图里是取消归档）/ 删除。
 * 必须画在同一个 Compose 里，不能用 DropdownMenu/Popup —— Popup 是独立窗口，
 * 一弹出就把后续触摸截走，长按之后就没法接着拖动排序了。
 * 只在点菜单外、按返回、或选项被点时收起，不自己消失。
 */
@Composable
private fun TaskRowMenu(
    archivedView: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onHoldChange: (Boolean) -> Unit = {},
) {
    // 入场：以右上角为原点从 0.94 弹到 1 并淡入，像从那一行长出来
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 700f))
    }
    // 回调走 rememberUpdatedState：pointerInput 的 key 保持 Unit，不会因重组把手势打断
    val hold by rememberUpdatedState(onHoldChange)
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 手指按在菜单上：暂停「0.9 秒自动收起」，抬手再重新计时
                    awaitFirstDown(requireUnconsumed = false)
                    hold(true)
                    waitForUpOrCancellation()
                    hold(false)
                }
            }
            .graphicsLayer {
                alpha = appear.value
                val s = 0.94f + 0.06f * appear.value
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(1f, 0f)
            },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, BorderColor),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            MenuRowText(if (archivedView) "取消归档" else "归档", Primary, onArchive)
            MenuRowText("删除", ErrorRed, onDelete)
        }
    }
}

@Composable
private fun MenuRowText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** 列表视图行：图标 + 名称 + 分类标注 + 时长/小任务数 + 箭头 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskListRow(
    task: FocusTask,
    parentName: String?,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val (bg, fg) = TaskCardPalette[task.colorIndex % TaskCardPalette.size]
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScale()
            // 关掉 M3 的按下叠加层：长按时整行会变灰，看着像"被选中了"。缩放反馈已经够
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Primary else BorderColor),
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
                if (selected) Icons.Default.Check else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = if (selected) Primary else TextSecondary,
            )
        }
    }
}
