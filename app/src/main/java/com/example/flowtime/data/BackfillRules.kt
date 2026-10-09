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
 * - 选「今天」时结束时刻不能超过此刻（超出则把结束钉在此刻，**时长保持不变**，开始时刻自动回退）；
 * - 弹层初始值固定落在**昨天**（主场景是补昨天/前天忘掉的那一整段），见 [defaultsFor]；
 * - 猜出的初始值撞上已有记录时**往后自动避让**，护栏见 [avoidOverlap]。
 */
object BackfillRules {

    const val MIN_MINUTES = 1
    /**
     * 单次记录 / 补录的时长上限：24 小时。
     * 2026-10-09 由 480（8h）提到 1440——"补一整天"这种段原来根本选不出来，
     * 表现就是"小时列最多只能拨到 8"。三处（补录 / 倒计时 / 设置页默认值）共用这一个上限。
     */
    const val MAX_MINUTES = 24 * 60
    /** 任务无历史时的默认时长 */
    const val DEFAULT_MINUTES = 30
    /** 无历史时兜底的结束小时 */
    const val FALLBACK_END_HOUR = 21
    /** 有效样本少于这个条数时不用"众数"，改用最近一条记录 */
    const val MIN_SAMPLES_FOR_MODE = 3
    /** 高频分钟的量化粒度：补一整段不需要分钟级精度 */
    const val MINUTE_GRAIN = 15
    /** 自动避让最多往后挪多少分钟（护栏，防止把时段默默挪到凌晨） */
    const val MAX_AVOID_SHIFT_MINUTES = 120
    /** 自动避让落位后对齐到几分钟 */
    private const val AVOID_STEP_MINUTES = 5
    /** 自动避让最多迭代几轮（理论上 1 轮就够） */
    private const val MAX_AVOID_STEPS = 4

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
     * 撞上已有记录时往后**自动避让**：接在所有重叠记录之后。
     * 返回 null 表示**放弃挪动**——调用方应保持原值并显示重叠警告。
     *
     * 护栏（2026-10-09 定）：挪动不超过 [MAX_AVOID_SHIFT_MINUTES]，
     * 且结束时刻不跨出当天。静默把时段挪到凌晨比弹个警告更糟。
     *
     * 只用于**猜出来的初始值**：用户手动拨过滚轮之后不应再自动挪，否则值会在手指下自己跳。
     */
    fun avoidOverlap(
        span: Span,
        records: List<FocusRecord>,
        taskId: String?,
        zone: ZoneId,
    ): Span? {
        val minutes = span.minutes
        if (minutes < MIN_MINUTES) return null
        val endDay = Instant.ofEpochMilli(span.endMillis).atZone(zone).toLocalDate()
        var candidate = span
        repeat(MAX_AVOID_STEPS) {
            val hits = overlapping(records, taskId, candidate)
            if (hits.isEmpty()) return candidate
            // 要不重叠，新起点必须 ≥ 所有重叠记录的结束时刻；
            // 新起点 = 新结束 − 时长，所以新结束 = 最大结束 + 时长（半开区间，首尾相接不算重叠）
            val nextEnd = hits.maxOf { it.endEpochMillis } + minutes * 60_000L
            val landed = ceilToStep(nextEnd)
            if (landed <= candidate.endMillis) return null
            if (landed - span.endMillis > MAX_AVOID_SHIFT_MINUTES * 60_000L) return null
            if (Instant.ofEpochMilli(landed).atZone(zone).toLocalDate() != endDay) return null
            candidate = Span(landed - minutes * 60_000L, landed)
        }
        return null
    }

    /**
     * 弹层初始值。主场景是「补昨天/前天忘掉的那一整段」，所以：
     *
     * - **日期固定昨天**——原来按"高频小时是否晚于此刻"在 今天/昨天 之间飘，
     *   同一个任务一天里不同时刻打开会给出不同日期，用户没法形成预期；
     * - 结束时刻 = 该任务高频**结束小时** + 该小时内众数分钟（量化到 [MINUTE_GRAIN]）；
     * - 时长**优先取补录记录**（MANUAL）的众数——真实计时多是 25/45 分钟，
     *   拿它们猜"补一整段"会永远猜偏（2–4 小时）；
     * - 有效样本少于 [MIN_SAMPLES_FOR_MODE] 条时不用众数，改用**最近一条记录**；
     * - 该任务一条记录都没有时回退 昨天 + 21:00 + 30 分钟。
     */
    fun defaultsFor(records: List<FocusRecord>, taskId: String?, zone: ZoneId): Defaults {
        val mine = records.filter { it.taskId == taskId }
        if (mine.isEmpty()) {
            return Defaults(
                daysAgo = 1,
                endHour = FALLBACK_END_HOUR,
                endMinute = 0,
                durationMinutes = DEFAULT_MINUTES,
            )
        }
        if (mine.size < MIN_SAMPLES_FOR_MODE) {
            val latest = mine.maxBy { it.endEpochMillis }
            val end = Instant.ofEpochMilli(latest.endEpochMillis).atZone(zone)
            return Defaults(
                daysAgo = 1,
                endHour = end.hour,
                endMinute = quantizeMinute(end.minute),
                durationMinutes = latest.minutes.coerceIn(MIN_MINUTES, MAX_MINUTES),
            )
        }
        // 同频时取较晚的小时 / 较晚的分钟 / 较长的时长
        val endHour = mine
            .groupingBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).hour }
            .eachCount()
            .entries
            .maxWith(compareBy({ it.value }, { it.key }))
            .key
        val endMinute = mine
            .filter { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).hour == endHour }
            .groupingBy { quantizeMinute(Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).minute) }
            .eachCount()
            .entries
            .maxWith(compareBy({ it.value }, { it.key }))
            .key
        val duration = mine
            .filter { it.recordSource == RecordSource.MANUAL }
            .ifEmpty { mine }
            .groupingBy { it.minutes }
            .eachCount()
            .entries
            .maxWith(compareBy({ it.value }, { it.key }))
            .key
            .coerceIn(MIN_MINUTES, MAX_MINUTES)
        return Defaults(daysAgo = 1, endHour = endHour, endMinute = endMinute, durationMinutes = duration)
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

    /** 分钟向下量化到 [MINUTE_GRAIN] 的整数倍（0 / 15 / 30 / 45） */
    private fun quantizeMinute(minute: Int): Int = (minute / MINUTE_GRAIN) * MINUTE_GRAIN

    /** 向上对齐到 [AVOID_STEP_MINUTES] 的整数倍；已在边界上则不动 */
    private fun ceilToStep(millis: Long): Long {
        val step = AVOID_STEP_MINUTES * 60_000L
        val rem = millis.mod(step)
        return if (rem == 0L) millis else millis + (step - rem)
    }
}
