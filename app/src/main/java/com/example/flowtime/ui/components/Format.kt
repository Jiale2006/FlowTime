package com.example.flowtime.ui.components

/** 分钟数统一显示：≥60 → "x h xx min"（整小时省略 min），<60 → "xx min" */
fun formatMinutesHm(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} h"
    minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}
