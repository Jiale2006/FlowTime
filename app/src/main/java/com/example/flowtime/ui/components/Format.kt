package com.example.flowtime.ui.components

/**
 * 时长文案：**全站唯一的写法**（2026-10-25 定：英文单位 `h` / `min`）。
 *
 * `45min` / `2h` / `6h 33min` —— 单一单位不加空格，只有「时 + 分」拼接处空一格。
 * 不再出现 `45 分钟` / `1.5h` / `1h45m` / `6 分 19 秒` 这几种混写。
 *
 * 🔴 为什么单一单位不加空格：卡片里 `45 min` 会被读成两个词，数字也被拉开，
 *    而 `6h 33min` 不加空格就是 `6h33min`，两个单位糊成一团。只给拼接处空一格。
 *
 * 🔴 为什么收成一个函数：此前统计页写 `30 min`、设置页写 `45 分钟`、
 *    时长按钮写 `1h45m`、补录弹窗写 `3h30m`、删除文案写 `2 小时前` ——
 *    同一件事五套写法，读者要在脑子里换算，界面也就永远像半成品。
 *    **要紧凑就全都紧凑、要英文就全都英文，不许各写各的。**
 *
 * 🔴 为什么最后选了紧凑而非中文：并排三张卡、圆环中心、最强一天的右列、设置页的
 *    chip 都是"宽度被卡死"的位置，`1 小时 45 分钟` 在那里不是换行就是被 Ellipsis
 *    吃掉，数字反而不突出。`1h 45min` 在同样的宽度里能完整显示，扫一眼就能比大小。
 *
 * 秒级读数不走这里：[formatClockHms] 负责 `01:34:00` 这种计时读数。
 */
fun formatMinutesCompact(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return when {
        m < 60 -> "${m}min"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}min"
    }
}

/**
 * 分 + 秒的短文案：`12min 30s` / `45s`（取消计时弹窗的"已计时 X"用它）。
 *
 * 单独一个函数而不是塞进 [formatMinutesCompact]：那边收的是整分钟数，
 * 这里要保留秒，混在一起会让调用方传一个 `seconds/60` 的半截值。
 */
fun formatMinSecCompact(minutes: Int, seconds: Int): String {
    val m = minutes.coerceAtLeast(0)
    val s = seconds.coerceIn(0, 59)
    return when {
        m <= 0 -> "${s}s"
        s == 0 -> formatMinutesCompact(m)
        else -> "${m}min ${s}s"
    }
}

/**
 * 计时读数：**恒定的** `时:分:秒`（2026-10-25 他定：所有主题统一）。
 *
 * 🔴 为什么必须只有一个实现：原来计时页和通知里各写了一份 `mm:ss`，
 *    结果玻璃主题（翻牌钟自动进 5 位）显示 `1:59:45`、涂鸦主题显示 `119:45`
 *    —— 同一场计时两套格式，分钟还会涨到三位数。抽到这里后两边不可能再分叉。
 *
 * 小时不补零、分秒补零：`0:45:00` / `1:44:38` / `12:00:00`，
 * 与翻牌钟的卡片数（<10 小时只占一张小时卡）对齐。
 */
fun formatClockHms(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    // 🔴 2026-10-25 改回「不到一小时不显示小时」：
    // 原来恒定三段（"0:34:00"），单个 0 挂在最前面很难看，他要的是
    //   < 1 小时 → "34:00"（只显示分:秒）
    //   ≥ 1 小时 → "01:34:00"（小时补零，视觉和分秒一样宽）
    return if (h > 0) {
        "%02d:%02d:%02d".format(h, m, sec)
    } else {
        "%02d:%02d".format(m, sec)
    }
}
