package com.example.flowtime.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.example.flowtime.ui.components.FlowAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.CardCream
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.ZoneId

/** 任务名最大长度（卡片标题单行展示，过长会撑坏布局） */
private const val NAME_MAX = 16

/** 建任务时不再让用户选日期，内部自动记为今天（卡片不展示该字段） */
internal fun todayLabel(): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    return "今天 ${today.monthValue}/${today.dayOfMonth}"
}

/** 新建面板的产出：一个任务（parentId 为空 = 未分类顶层） */
internal data class NewTaskDraft(
    val name: String,
    val colorIndex: Int,
    val iconIndex: Int,
    val parentId: String?,
)

/**
 * 任务面板（底部弹层）：新建 / 编辑共用。
 *
 * - 新建 = 新建一个任务，**默认未分类**；
 * - 选择「分类」（= 某个顶层任务）→ 归档成它下面的小任务；
 * - [fixedParent] 非空 = 从某个任务页进入，归属固定，不再显示分类选择；
 * - [editing] 非空 = 编辑既有任务（改名 / 换图标配色）。**改归属不在范围内**，
 *   所以归属同样固定不显示选择器（改了要连带处理子任务层级，另说）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskEditorSheet(
    /** 归属固定时（在某任务页里添加小任务）传入该任务 */
    fixedParent: FocusTask? = null,
    /** 编辑既有任务时传入它 */
    editing: FocusTask? = null,
    /** 可选的分类：顶层任务列表 */
    parentOptions: List<FocusTask> = emptyList(),
    defaultColorIndex: Int,
    defaultIconIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (NewTaskDraft) -> Unit,
) {
    // 按 id 而不是按对象认：Room 的 tasks Flow 每次失效都会重建任务实例，
    // 按对象认的话在编辑过程中打字会把输入框重置回原值
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var colorIndex by remember(editing?.id) {
        mutableIntStateOf((editing?.colorIndex ?: defaultColorIndex) % TaskCardPalette.size)
    }
    var iconIndex by remember(editing?.id) {
        mutableIntStateOf((editing?.iconIndex ?: defaultIconIndex) % TaskIcons.size)
    }
    var pickedParentId by remember(editing?.id) {
        mutableStateOf(fixedParent?.id ?: editing?.parentId)
    }
    var parentMenuOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val showParentPicker = fixedParent == null && editing == null
    val parentName = fixedParent?.name
        ?: parentOptions.firstOrNull { it.id == pickedParentId }?.name
    val valid = name.isNotBlank()
    // 编辑态下名字一开始就是非空的，所以"有没有改过"得跟原值比，不能只看空不空
    val editingName = editing?.name ?: ""
    // 🔴 M3 1.4.0 把 confirmValueChange 当作 rememberSaveable 的 **key**（SheetDefaults.kt:519-521），
    //    闭包只要捕获"值"（dirty 这种 val），值一变就换新闭包 → key 变 → SheetState 被重建
    //    （新状态从 Hidden 起步），随后 ModalBottomSheet 的 LaunchedEffect(sheetState) show()
    //    又把弹层顶回来 —— 观感就是"打字打进去、弹窗消失又浮上来"。
    //    所以闭包里只读 name 这个 State（对象恒定），并把闭包本身 remember 住。
    val confirmValueChange = remember(editingName) {
        { value: SheetValue ->
            // 改了名字却没保存就用手势/返回键关掉时先拦一次，避免修改被静默丢弃
            if (value == SheetValue.Hidden && name.trim() != editingName) {
                confirmDiscard = true
                false
            } else {
                true
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmValueChange,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperElevated,
    ) {
        com.example.flowtime.ui.components.BlurDialogBehind(26.dp)
        // 语音输入时键盘会换成一块接近全屏的面板，不钳制的话 M3 弹层会被压成 0 高、
        // 先消失再弹回来（见 KeepSheetAboveIme 的注释）
        com.example.flowtime.ui.components.KeepSheetAboveIme()
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding)) {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (editing != null) "编辑任务" else "新建任务",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= NAME_MAX) name = it },
                    label = { Text("任务名称") },
                    singleLine = true,
                    supportingText = { Text("${name.length}/$NAME_MAX") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                // 分类：默认「不分类」；点开是下拉框，选一个任务即归档为它的小任务
                if (showParentPicker) {
                    Text("分类", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardCream)
                                .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                                .clickable { parentMenuOpen = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = parentName ?: "不分类",
                                fontSize = 15.sp,
                                color = if (parentName == null) TextSecondary else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "选择分类",
                                tint = TextSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = parentMenuOpen,
                            onDismissRequest = { parentMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("不分类") },
                                onClick = {
                                    pickedParentId = null
                                    parentMenuOpen = false
                                },
                                trailingIcon = {
                                    if (pickedParentId == null) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
                                    }
                                },
                            )
                            parentOptions.forEach { parent ->
                                DropdownMenuItem(
                                    text = { Text(parent.name) },
                                    onClick = {
                                        pickedParentId = parent.id
                                        parentMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (pickedParentId == parent.id) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardCream)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("归属", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = parentName ?: "不分类",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Primary,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Text("预览", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                TaskPreviewCard(
                    name = name.ifBlank { "任务名称" },
                    subtitle = parentName ?: "未分类",
                    colorIndex = colorIndex,
                    iconIndex = iconIndex,
                )
                Spacer(Modifier.height(16.dp))

                Text("图标", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(TaskIcons.size) { index ->
                        val selected = iconIndex == index
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) CardGreenLight else CardCream)
                                .then(
                                    if (selected) {
                                        Modifier.border(1.5.dp, Primary, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { iconIndex = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            // 保留图标自带配色；选中态由外框底色 + 描边表示
                            TaskIcons.Glyph(index = index, size = 26.dp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("背景色", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TaskCardPalette.forEachIndexed { index, (swatch, swatchText) ->
                        val selected = colorIndex == index
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, Primary, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, BorderColor, CircleShape)
                                    },
                                )
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (swatchText == Color.White) Color.White else Primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 操作区固定在弹层底部，不随内容滚动
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(Dimens.ButtonHeight),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("取消", color = TextSecondary, fontSize = 15.sp)
                }
                Button(
                    onClick = {
                        onConfirm(
                            NewTaskDraft(
                                name = name.trim(),
                                colorIndex = colorIndex,
                                iconIndex = iconIndex,
                                parentId = fixedParent?.id ?: pickedParentId,
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.ButtonHeight),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(
                        if (editing != null) "保存" else "创建",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmDiscard) {
        FlowAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃这次编辑？", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "任务名还没保存，关掉就没了。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}

/** 即时预览：与任务卡片同一套配色/图标，选什么立刻看到什么 */
@Composable
private fun TaskPreviewCard(
    name: String,
    subtitle: String,
    colorIndex: Int,
    iconIndex: Int,
) {
    val (bg, fg) = TaskCardPalette[colorIndex % TaskCardPalette.size]
    Card(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                TaskIcons.Glyph(index = iconIndex, size = 24.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
