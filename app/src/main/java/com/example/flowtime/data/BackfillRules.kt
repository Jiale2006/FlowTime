package com.example.flowtime.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 补录时间的纯规则：全部是「输入 → 输出」的纯函数，不碰 Android API，可直接 JVM 单测。
 *
 * 口径（2026-10-09 grill 定稿）：
 * - 锚点是**结束时刻**，开始时刻 = 结束 − 时长；
 * - **跨天溢出放行**：开始时刻落到前一天不钳制、不警告；
 * - 选「今天」时结束时刻不能超过此刻（超出则把结束钉在此刻，**时长保持不变**，开始时刻自动回退）。
 */
object BackfillRules {

    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 480
    /** 任务无历史时的默认时长 */
    const val DEFAULT_MINUTES = 30

    /** 一次补录/编辑算出的时间区间（半开区间：[start, end)） */
    data class Span(val startMillis: Long, val endMillis: Long) {
        /** 由区间反算的分钟数（整分钟） */
        val minutes: Int get() = ((endMillis - startMillis) / 60_000L).toInt()
    }

    /** 补录弹层的初始值 */
    data class Defaults(
        /** 距今天数：0 = 今天，1 = 昨天 */
        val daysAgo: Int,
        val endHour: Int,
        val endMinute: Int,
        val durationMinutes: Int,
    )

    /** 把「结束日 + 结束时刻 + 时长」拼成区间；今日超出此刻则结束钉在此刻（时长不变） */
    fun resolve(
        date: LocalDate,
        endHour: Int,
        endMinute: Int,
        durationMinutes: Int,
        nowMillis: Long,
        zone: ZoneId,
    ): Span {
        val minutes = durationMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val endMillis = composedEndMillis(date, endHour, endMinute, zone)
        val isToday = date == Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // 钉到整分钟：否则落库的结束时刻会带秒，区间两端不在分钟边界上
        val effectiveEnd = if (isToday && endMillis > nowMillis) floorToMinute(nowMillis) else endMillis
        return Span(effectiveEnd - minutes * 60_000L, effectiveEnd)
    }

    /**
     * 结束时刻滚轮的分钟上限（相对当天 00:00）：
     * 今天 = 此刻已过的分钟数（拨不到未来）；其它日期 = 23:59。
     */
    fun endCapMinutes(date: LocalDate, nowMillis: Long, zone: ZoneId): Int {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return if (date == now.toLocalDate()) now.hour * 60 + now.minute else 23 * 60 + 59
    }

    /** 该区间是否跨过午夜（开始时刻落在结束日之前） */
    fun crossesDay(span: Span, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(span.startMillis).atZone(zone).toLocalDate() !=
            Instant.ofEpochMilli(span.endMillis).atZone(zone).toLocalDate()

    /** 半开区间相交判断：首尾相接**不算**重叠（20:00 结束、20:00 开始是两段独立专注） */
    fun overlaps(startA: Long, endA: Long, startB: Long, endB: Long): Boolean =
        startA < endB && startB < endA

    /**
     * 与 [span] 时段重叠的已有记录。
     * **只在同一任务内比对**（不同任务本来就可能并行，不应误报）；
     * [excludeId] 用于编辑场景排除记录自己。
     */
    fun overlapping(
        records: List<FocusRecord>,
        taskId: String?,
        span: Span,
        excludeId: Long = 0L,
    ): List<FocusRecord> = records.filter {
        it.id != excludeId &&
            it.taskId == taskId &&
            overlaps(span.startMillis, span.endMillis, it.startEpochMillis, it.endEpochMillis)
    }

    /**
     * 弹层初始值：猜任务的历史习惯。
     * - 结束时刻取该任务**最常出现的结束小时**（分钟固定 00；同频时取较晚的小时）；
     * - 时长取**最常出现的时长**（同频时取较长的）；
     * - 猜出的结束时刻若晚于此刻，日期退到昨天（保留"习惯"信息，又不会落在未来）；
     * - 该任务没有任何记录时，回退「现在 + 30 分钟」。
     */
    fun defaultsFor(
        records: List<FocusRecord>,
        taskId: String?,
        nowMillis: Long,
        zone: ZoneId,
    ): Defaults {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val mine = records.filter { it.taskId == taskId }
        if (mine.isEmpty()) {
            return Defaults(daysAgo = 0, endHour = now.hour, endMinute = now.minute, durationMinutes = DEFAULT_MINUTES)
        }
        val endHour = mine
            .groupingBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).hour }
            .eachCount()
            .entries
            .maxWithOrNull(compareBy({ it.value }, { it.key }))
            ?.key ?: now.hour
        val duration = mine
            .groupingBy { it.minutes }
            .eachCount()
            .entries
            .maxWithOrNull(compareBy({ it.value }, { it.key }))
            ?.key?.coerceIn(MIN_MINUTES, MAX_MINUTES) ?: DEFAULT_MINUTES
        val daysAgo = if (endHour * 60 > now.hour * 60 + now.minute) 1 else 0
        return Defaults(daysAgo = daysAgo, endHour = endHour, endMinute = 0, durationMinutes = duration)
    }

    /** 只有手动补录的记录允许改时长；真实计时的时长是客观发生的，不给改 */
    fun canEditDuration(record: FocusRecord): Boolean =
        record.recordSource == RecordSource.MANUAL

    /**
     * 改时长：**结束时刻不变**（锚点），开始时刻按新时长回退。
     * 返回 null 表示这条记录不允许改（见 [canEditDuration]）。
     */
    fun withDuration(record: FocusRecord, minutes: Int): FocusRecord? {
        if (!canEditDuration(record)) return null
        val safe = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        return record.copy(
            minutes = safe,
            startEpochMillis = record.endEpochMillis - safe * 60_000L,
        )
    }

    /** 结束日 + 结束时刻 → 毫秒时间戳 */
    private fun composedEndMillis(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone)
            .plusHours(hour.toLong().coerceIn(0, 23))
            .plusMinutes(minute.toLong().coerceIn(0, 59))
            .toInstant()
            .toEpochMilli()

    /** 抹掉秒与毫秒（epoch 毫秒对 60000 取模即分钟对齐，时区偏移都是整分钟） */
    private fun floorToMinute(millis: Long): Long = millis - millis.mod(60_000L)
}
