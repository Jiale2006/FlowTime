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
 * 任务图标注册表：index → 图标资源（彩色矢量，**不再随主题 tint**）。
 *
 * 索引 0..13 沿用旧 emoji iconIndex 语义，保证老任务的图标不会错位：
 * 0=英语 1=数学 2=学习/笔记 3=目标 4=时间 5=代码 6=计算机 7=游戏
 * 8=政治 9=柱状图 10=奖 11=空 12=复盘 13=运动
 * 14 以后是本轮新增的细项（听力/阅读/听课/做题/网络/组成/OS/数据结构/反省/阅读…）。
 * **新图标一律追加在末尾**，改动中间顺序会让已有任务的图标错位。
 */
object TaskIcons {
    private val icons = listOf(
        R.drawable.ic_task_english,   // 0  英语（记单词）
        R.drawable.ic_task_lecture,   // 1  数学（听课）
        R.drawable.ic_task_read,      // 2  学习 / 笔记
        R.drawable.ic_task_goal,      // 3  目标
        R.drawable.ic_task_time,      // 4  时间（自由专注）
        R.drawable.ic_task_code,      // 5  代码
        R.drawable.ic_task_computer,  // 6  计算机工程
        R.drawable.ic_task_game,      // 7  游戏
        R.drawable.ic_task_politics,  // 8  政治
        R.drawable.ic_task_barchart,  // 9  柱状图
        R.drawable.ic_task_award,     // 10 奖
        R.drawable.ic_task_empty,     // 11 空
        R.drawable.ic_task_review,    // 12 复盘
        R.drawable.ic_task_run,       // 13 跑步
        R.drawable.ic_task_listening, // 14 英语听力
        R.drawable.ic_task_reading,   // 15 英语阅读
        R.drawable.ic_task_practice,  // 16 数学做题
        R.drawable.ic_task_network,   // 17 计算机网络
        R.drawable.ic_task_arch,      // 18 计算机组成原理
        R.drawable.ic_task_os,        // 19 操作系统
        R.drawable.ic_task_ds,        // 20 数据结构
        R.drawable.ic_task_reflect,   // 21 反省
        R.drawable.ic_task_sport,     // 22 运动
    )

    /** 自由专注标识图标（时间） */
    const val FREE_FOCUS_INDEX = 4

    val size: Int get() = icons.size

    fun resAt(index: Int): Int = icons[index % icons.size]

    /**
     * 画一个任务图标。
     * [tint] 默认 [Color.Unspecified] = 保留图标自带配色（彩色矢量）；
     * 需要压成单色时再显式传 tint。
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
