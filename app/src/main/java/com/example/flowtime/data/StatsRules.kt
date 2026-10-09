package com.example.flowtime.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 统计范围：本周（周一起）/ 本月（1 号起）/ 全部。
 *
 * 放在 data 层而不是 ViewModel 里——[StatsRules] 需要用它，data 层不该反向依赖 UI 层。
 */
enum class StatsRange(val label: String) {
    WEEK("本周"),
    MONTH("本月"),
    ALL("全部"),
}

/**
 * 统计口径的纯规则：全是「输入 → 输出」的纯函数，不碰 Android API，可直接 JVM 单测。
 *
 * 统计页口径原则（2026-10-09 批次4 确立）：
 * - **成就 / 里程碑类 → 全量**（连续天数、历史最强一天）
 * - **复盘 / 分析类 → 跟范围**（累计、日均、趋势、时段分布、分布、对比、导出）
 */
object StatsRules {

    /** 日均至少要经过这么多天才有意义（周一当天算出来的日均就等于当日总量，很误导） */
    const val MIN_DAYS_FOR_AVERAGE = 2

    /**
     * 把一条记录**按分钟摊到它跨越的每一个小时**（时段分布的算法）。
     *
     * 例：20:30 → 22:00 的 90 分钟 → {20:30, 21:60}。
     * 旧实现是把整段分钟数挂在**结束那一小时**上，一条 8 小时记录会全堆在一根柱子里，
     * 跨天时更离谱（深夜那段全算凌晨结束点）。
     *
     * 时区/夏令时通过 `truncatedTo(HOURS)` 逐段推进处理；两端都按整分钟取值。
     */
    fun distributeByHour(startMillis: Long, endMillis: Long, zone: ZoneId): Map<Int, Int> {
        if (endMillis <= startMillis) return emptyMap()
        val out = HashMap<Int, Int>()
        var cursor = startMillis
        while (cursor < endMillis) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zoned.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val segmentEnd = minOf(nextHour, endMillis)
            val minutes = ((segmentEnd - cursor) / 60_000L).toInt()
            if (minutes > 0) out[zoned.hour] = (out[zoned.hour] ?: 0) + minutes
            cursor = segmentEnd
        }
        return out
    }

    /** 一批记录 → 24 小时时长分布 */
    fun hourMinutesOf(records: List<FocusRecord>, zone: ZoneId): List<Int> {
        val hours = IntArray(24)
        records.forEach { record ->
            distributeByHour(record.startEpochMillis, record.endEpochMillis, zone)
                .forEach { (hour, minutes) ->
                    if (hour in 0..23) hours[hour] = hours[hour] + minutes
                }
        }
        return hours.toList()
    }

    /**
     * 日均：分母是**范围内已过的自然日**。
     * 天数不足 [MIN_DAYS_FOR_AVERAGE] 时返回 null（界面显示「—」），
     * 否则周一那天算出来的"日均"就是当天总量，读起来像个假数字。
     */
    fun dailyAverage(totalMinutes: Int, elapsedDays: Int): Int? =
        if (elapsedDays < MIN_DAYS_FOR_AVERAGE) null else totalMinutes / elapsedDays

    /** 单次时长分布的一档 */
    data class DurationBucket(val label: String, val count: Int, val minutes: Int)

    /** 单次时长分桶：<15 / 15-30 / 30-60 / 60-120 / ≥120 分钟 */
    fun durationBuckets(records: List<FocusRecord>): List<DurationBucket> = listOf(
        "<15分" to { m: Int -> m < 15 },
        "15-30分" to { m: Int -> m in 15..29 },
        "30-60分" to { m: Int -> m in 30..59 },
        "1-2小时" to { m: Int -> m in 60..119 },
        "≥2小时" to { m: Int -> m >= 120 },
    ).map { (label, test) ->
        val hit = records.filter { test(it.minutes) }
        DurationBucket(label, hit.size, hit.sumOf { it.minutes })
    }

    /** 最强一天：按当日分钟数降序取前 [n]（同分钟时取较早的日期，结果稳定可测） */
    data class TopDay(val date: LocalDate, val minutes: Int)

    fun topDays(byDay: Map<LocalDate, Int>, n: Int = 3): List<TopDay> = byDay.entries
        .filter { it.value > 0 }
        .sortedWith(compareByDescending<Map.Entry<LocalDate, Int>> { it.value }.thenBy { it.key })
        .take(n)
        .map { TopDay(it.key, it.value) }

    /**
     * 与上一区间对比的涨跌百分比（正 = 增长）。
     * 上一区间为 0 时返回 null——"从 0 涨到 100%"没有意义，界面该显示「新增」而不是 ∞。
     */
    fun changePercent(current: Int, previous: Int): Int? =
        if (previous <= 0) null else Math.round((current - previous) * 100f / previous)

    /**
     * 对比用的「上一区间」日期范围（闭区间）；[StatsRange.ALL] 没有上一区间，返回 null。
     * - 本周 → 上周一 ~ 上周日
     * - 本月 → 上月 1 号 ~ 上月末
     */
    fun previousWindow(range: StatsRange, today: LocalDate): ClosedRange<LocalDate>? = when (range) {
        StatsRange.WEEK -> {
            val thisMonday = today.with(java.time.DayOfWeek.MONDAY)
            thisMonday.minusWeeks(1)..thisMonday.minusDays(1)
        }

        StatsRange.MONTH -> {
            val firstOfThisMonth = today.withDayOfMonth(1)
            firstOfThisMonth.minusMonths(1)..firstOfThisMonth.minusDays(1)
        }

        StatsRange.ALL -> null
    }
}
