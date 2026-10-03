package com.example.flowtime.ui.tasks

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flowtime.R

/**
 * 任务图标注册表：index → 图标资源（矢量 iconfont + 彩图 PNG 混用）。
 * 索引顺序兼顾旧 emoji iconIndex 语义（0=书/英语 1=数学 2=笔记 3=目标）；
 * **新图标一律追加在末尾**，改动中间顺序会让已有任务的图标错位。
 */
object TaskIcons {
    private val icons = listOf(
        R.drawable.ic_task_english,   // 0 英语（彩图）
        R.drawable.ic_task_math,      // 1 数学（彩图）
        R.drawable.ic_task_notes,     // 2 笔记
        R.drawable.ic_task_goal,      // 3 目标（彩图）
        R.drawable.ic_task_time,      // 4 时间
        R.drawable.ic_task_code,      // 5 代码
        R.drawable.ic_task_computer,  // 6 计算机工程
        R.drawable.ic_task_game,      // 7 游戏
        R.drawable.ic_task_politics,  // 8 政治（彩图）
        R.drawable.ic_task_barchart,  // 9 柱状图
        R.drawable.ic_task_award,     // 10 奖
        R.drawable.ic_task_empty,     // 11 empty
        R.drawable.ic_task_review,    // 12 复盘（彩图）
        R.drawable.ic_task_run,       // 13 跑步（彩图）
    )

    /** 自由专注标识图标（时间） */
    const val FREE_FOCUS_INDEX = 4

    val size: Int get() = icons.size

    fun resAt(index: Int): Int = icons[index % icons.size]

    /**
     * 画一个任务图标。
     * [tint] 默认 [Color.Unspecified] = 保留图标自带配色（这套 iconfont 是多色图，染成单色会整幅变色）；
     * 需要放在深/彩色底上保证对比度时再显式传 tint。
     */
    @Composable
    fun Glyph(
        index: Int,
        tint: Color = Color.Unspecified,
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
    ) {
        Icon(
            painter = painterResource(resAt(index)),
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(size),
        )
    }
}
