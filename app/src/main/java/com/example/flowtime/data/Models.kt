package com.example.flowtime.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「分类」= 顶层且已挂有子任务的任务（派生视图，只用于统计/导出展示）。
 * 不再单独存储：任何任务只要挂了小任务，它就成为分类/大类。
 */
data class Category(
    val id: String,
    val name: String,
    val colorIndex: Int,
)

/**
 * 专注任务（Room 实体：tasks 表）。
 * parentId 为空 = 顶层任务（默认未分类）；非空 = 归属该父任务（分类）下的小任务。
 */
@Entity(tableName = "tasks")
data class FocusTask(
    @PrimaryKey val id: String,
    val name: String,
    val durationMinutes: Int,
    val dateLabel: String,
    val colorIndex: Int,
    val iconIndex: Int,
    val parentId: String? = null,
)

/**
 * 一次完成的专注记录（Room 实体：records 表）。
 * 用 [taskId] 关联任务，任务改名不会断链；[taskName] 只作展示快照。
 */
@Entity(tableName = "records")
data class FocusRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 归属任务 id；null = 自由专注，或任务已被删掉（此时只能靠 taskName 显示） */
    val taskId: String? = null,
    /** 记录当时的任务名快照 */
    val taskName: String,
    val minutes: Int,
    /** 本次专注开始时间（旧数据/补录按 end - minutes 推算） */
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

/** 设置页全部配置 */
data class AppSettings(
    val nickname: String = "心流用户",
    /** 头像文件名（应用私有 filesDir 内）；空 = 未设置头像 */
    val avatarPath: String = "",
    val defaultDurationMinutes: Int = 45,
    /** 任务清单视图：true = 列表视图，false = 网格视图（持久化，退出应用后保持） */
    val taskListView: Boolean = false,
    val ringtoneEnabled: Boolean = true,
    val autoStart: Boolean = true,
    val dailyGoalMinutes: Int = 120,
    val reminderTime: String = "20:00",
    /** 每日习惯提醒开关（开启后按 reminderTime 弹本地通知） */
    val reminderEnabled: Boolean = false,
    /** 番茄计时：专注段时长（分钟） */
    val pomodoroFocusMinutes: Int = 25,
    /** 番茄计时：短休息时长（分钟） */
    val pomodoroBreakMinutes: Int = 5,
    /** 番茄计时：长休息时长（分钟） */
    val pomodoroLongBreakMinutes: Int = 15,
    /** 番茄计时：每几轮进入一次长休息 */
    val pomodoroRoundsBeforeLongBreak: Int = 4,
    /** 番茄计时结构说明是否已提示过：安装后只自动弹一次，之后点「番茄计时」直接切换模式 */
    val pomodoroTipShown: Boolean = false,
    /** 统计页范围：WEEK / MONTH / ALL（持久化，退出应用后保持上次选择） */
    val statsRange: String = "WEEK",
    /** 界面主题：WATERCOLOR（水彩画风）/ NOCTURNE（玻璃拟态），存 ThemeMode 枚举名 */
    val themeMode: String = "NOCTURNE",
)
