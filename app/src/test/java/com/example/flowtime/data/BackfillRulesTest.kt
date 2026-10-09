package com.example.flowtime.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 补录时间规则的单元测试。
 * 覆盖：结束锚定 / 今天钳制到此刻（时长不变）/ 跨天放行 / 半开区间重叠 /
 * 默认值推导（固定昨天、分钟众数、补录历史优先、样本不足回退）/ 自动避让护栏 / 改时长权限。
 */
class BackfillRulesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int, sec: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min, sec).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        id: Long = 0L,
        taskId: String? = "t1",
        minutes: Int = 30,
        startMs: Long,
        endMs: Long,
        source: RecordSource = RecordSource.TIMER,
    ) = FocusRecord(
        id = id,
        taskId = taskId,
        taskName = "测试任务",
        minutes = minutes,
        startEpochMillis = startMs,
        endEpochMillis = endMs,
        source = source.name,
    )

    private val today = LocalDate.of(2026, 10, 9)
    private val yesterday = LocalDate.of(2026, 10, 8)

    // ---- resolve：结束锚定 ----

    @Test
    fun `用结束时刻减去时长得到开始时刻`() {
        val span = BackfillRules.resolve(today, 20, 0, 30, ms(2026, 10, 9, 22, 0), zone)
        assertEquals(ms(2026, 10, 9, 19, 30), span.startMillis)
        assertEquals(ms(2026, 10, 9, 20, 0), span.endMillis)
        assertEquals(30, span.minutes)
    }

    @Test
    fun `今天选未来结束时刻会钉在此刻且时长不变`() {
        // 现在 14:00:37，用户选了今天 15:00 结束、时长 30 分钟
        val now = ms(2026, 10, 9, 14, 0, 37)
        val span = BackfillRules.resolve(today, 15, 0, 30, now, zone)
        assertEquals(ms(2026, 10, 9, 14, 0), span.endMillis) // 秒被抹掉
        assertEquals(ms(2026, 10, 9, 13, 30), span.startMillis)
        assertEquals(30, span.minutes) // 关键是时长没被砍
        assertTrue(span.endMillis <= now)
    }

    @Test
    fun `今天选过去的时刻不钳制`() {
        val span = BackfillRules.resolve(today, 13, 0, 30, ms(2026, 10, 9, 20, 0), zone)
        assertEquals(ms(2026, 10, 9, 13, 0), span.endMillis)
        assertEquals(ms(2026, 10, 9, 12, 30), span.startMillis)
    }

    @Test
    fun `跨天放行——今天 00-30 结束 2 小时 开始落到昨天 22-30`() {
        val span = BackfillRules.resolve(today, 0, 30, 120, ms(2026, 10, 9, 12, 0), zone)
        assertEquals(ms(2026, 10, 9, 0, 30), span.endMillis)
        assertEquals(ms(2026, 10, 8, 22, 30), span.startMillis)
        assertTrue(BackfillRules.crossesDay(span, zone))
    }

    @Test
    fun `非今天不钳制——昨天 23 点结束 3 小时`() {
        val span = BackfillRules.resolve(yesterday, 23, 0, 180, ms(2026, 10, 9, 12, 0), zone)
        assertEquals(ms(2026, 10, 8, 23, 0), span.endMillis)
        assertEquals(ms(2026, 10, 8, 20, 0), span.startMillis)
        assertFalse(BackfillRules.crossesDay(span, zone))
    }

    @Test
    fun `时长越界被夹到上限`() {
        assertEquals(1, BackfillRules.resolve(today, 20, 0, 0, ms(2026, 10, 9, 22, 0), zone).minutes)
        assertEquals(1, BackfillRules.resolve(today, 20, 0, -5, ms(2026, 10, 9, 22, 0), zone).minutes)
        assertEquals(
            BackfillRules.MAX_MINUTES,
            BackfillRules.resolve(today, 20, 0, 9999, ms(2026, 10, 9, 22, 0), zone).minutes,
        )
    }

    @Test
    fun `时长上限是 24 小时，能补一整段`() {
        assertEquals(24 * 60, BackfillRules.MAX_MINUTES)
        // 补"上午 8 点到晚上 8 点"这种 12 小时的段：上限内必须原样保留
        assertEquals(720, BackfillRules.resolve(today, 20, 0, 720, ms(2026, 10, 9, 22, 0), zone).minutes)
    }

    // ---- endCapMinutes ----

    @Test
    fun `结束时刻上限 今天等于此刻的分钟数`() {
        val now = ms(2026, 10, 9, 13, 5, 40)
        assertEquals(13 * 60 + 5, BackfillRules.endCapMinutes(today, now, zone))
    }

    @Test
    fun `结束时刻上限 非今天就是 23-59`() {
        val now = ms(2026, 10, 9, 13, 5)
        assertEquals(23 * 60 + 59, BackfillRules.endCapMinutes(yesterday, now, zone))
        assertEquals(23 * 60 + 59, BackfillRules.endCapMinutes(LocalDate.of(2026, 10, 10), now, zone))
    }

    // ---- overlaps / overlapping ----

    @Test
    fun `半开区间 首尾相接不算重叠`() {
        assertFalse(BackfillRules.overlaps(100L, 200L, 200L, 300L))
        assertFalse(BackfillRules.overlaps(100L, 200L, 50L, 100L))
        assertTrue(BackfillRules.overlaps(100L, 200L, 199L, 300L))
        assertTrue(BackfillRules.overlaps(100L, 200L, 100L, 200L))
        assertTrue(BackfillRules.overlaps(100L, 200L, 150L, 160L))
    }

    @Test
    fun `重叠只在同一任务内比对`() {
        val a = record(id = 1, taskId = "t1", startMs = ms(2026, 10, 9, 10, 0), endMs = ms(2026, 10, 9, 11, 0))
        val b = record(id = 2, taskId = "t2", startMs = ms(2026, 10, 9, 10, 0), endMs = ms(2026, 10, 9, 11, 0))
        val span = BackfillRules.Span(ms(2026, 10, 9, 10, 30), ms(2026, 10, 9, 11, 30))

        assertEquals(listOf(1L), BackfillRules.overlapping(listOf(a, b), "t1", span).map { it.id })
        assertEquals(listOf(2L), BackfillRules.overlapping(listOf(a, b), "t2", span).map { it.id })
        assertTrue(BackfillRules.overlapping(listOf(a, b), "t3", span).isEmpty())
        assertTrue(BackfillRules.overlapping(listOf(a, b), null, span).isEmpty())
    }

    @Test
    fun `重叠比对可排除记录自己`() {
        val a = record(id = 7, taskId = "t1", startMs = ms(2026, 10, 9, 10, 0), endMs = ms(2026, 10, 9, 11, 0))
        val span = BackfillRules.Span(ms(2026, 10, 9, 10, 0), ms(2026, 10, 9, 11, 0))
        assertEquals(1, BackfillRules.overlapping(listOf(a), "t1", span).size)
        assertTrue(BackfillRules.overlapping(listOf(a), "t1", span, excludeId = 7L).isEmpty())
    }

    // ---- defaultsFor ----

    @Test
    fun `无历史时回退 昨天 21 点 30 分钟`() {
        val d = BackfillRules.defaultsFor(emptyList(), "t1", zone)
        assertEquals(1, d.daysAgo)
        assertEquals(21, d.endHour)
        assertEquals(0, d.endMinute)
        assertEquals(30, d.durationMinutes)
    }

    @Test
    fun `日期固定昨天——不再随打开时刻漂移`() {
        // 原来按"高频小时是否晚于此刻"在 今天/昨天 之间飘，同一任务不同时刻打开结果不同
        val list = listOf(
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 6, 19, 15), endMs = ms(2026, 10, 6, 20, 0)),
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 7, 19, 15), endMs = ms(2026, 10, 7, 20, 0)),
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 8, 19, 15), endMs = ms(2026, 10, 8, 20, 0)),
        )
        assertEquals(1, BackfillRules.defaultsFor(list, "t1", zone).daysAgo)
        assertEquals(1, BackfillRules.defaultsFor(emptyList(), "t1", zone).daysAgo)
    }

    @Test
    fun `取任务最常出现的结束小时`() {
        val list = listOf(
            record(taskId = "t1", minutes = 30, startMs = ms(2026, 10, 6, 19, 30), endMs = ms(2026, 10, 6, 20, 0)),
            record(taskId = "t1", minutes = 30, startMs = ms(2026, 10, 7, 19, 30), endMs = ms(2026, 10, 7, 20, 0)),
            record(taskId = "t1", minutes = 60, startMs = ms(2026, 10, 8, 20, 0), endMs = ms(2026, 10, 8, 21, 0)),
        )
        val d = BackfillRules.defaultsFor(list, "t1", zone)
        assertEquals(20, d.endHour) // 20 点两次、21 点一次
        assertEquals(0, d.endMinute)
        assertEquals(30, d.durationMinutes)
    }

    @Test
    fun `结束分钟取该小时内的众数并量化到 15 分钟`() {
        // 三次都在 21 点段结束：21:47 / 21:52 / 21:49 → 量化后全是 45
        val list = listOf(
            record(taskId = "t1", minutes = 60, startMs = ms(2026, 10, 6, 20, 47), endMs = ms(2026, 10, 6, 21, 47)),
            record(taskId = "t1", minutes = 60, startMs = ms(2026, 10, 7, 20, 52), endMs = ms(2026, 10, 7, 21, 52)),
            record(taskId = "t1", minutes = 60, startMs = ms(2026, 10, 8, 20, 49), endMs = ms(2026, 10, 8, 21, 49)),
        )
        val d = BackfillRules.defaultsFor(list, "t1", zone)
        assertEquals(21, d.endHour)
        assertEquals(45, d.endMinute) // 原来是硬编码的 0
    }

    @Test
    fun `时长优先取补录记录——不被短计时记录带偏`() {
        val list = listOf(
            record(taskId = "t1", minutes = 25, startMs = ms(2026, 10, 4, 20, 35), endMs = ms(2026, 10, 4, 21, 0)),
            record(taskId = "t1", minutes = 25, startMs = ms(2026, 10, 5, 20, 35), endMs = ms(2026, 10, 5, 21, 0)),
            record(taskId = "t1", minutes = 25, startMs = ms(2026, 10, 6, 20, 35), endMs = ms(2026, 10, 6, 21, 0)),
            record(
                taskId = "t1", minutes = 180,
                startMs = ms(2026, 10, 7, 18, 0), endMs = ms(2026, 10, 7, 21, 0),
                source = RecordSource.MANUAL,
            ),
            record(
                taskId = "t1", minutes = 180,
                startMs = ms(2026, 10, 8, 18, 0), endMs = ms(2026, 10, 8, 21, 0),
                source = RecordSource.MANUAL,
            ),
        )
        val d = BackfillRules.defaultsFor(list, "t1", zone)
        // 全部记录里 25 分钟出现 3 次更多，但补录历史优先 → 180
        assertEquals(180, d.durationMinutes)
        assertEquals(21, d.endHour)
    }

    @Test
    fun `没有补录历史时退回全部记录的众数`() {
        val list = listOf(
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 6, 20, 15), endMs = ms(2026, 10, 6, 21, 0)),
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 7, 20, 15), endMs = ms(2026, 10, 7, 21, 0)),
            record(taskId = "t1", minutes = 30, startMs = ms(2026, 10, 8, 20, 30), endMs = ms(2026, 10, 8, 21, 0)),
        )
        assertEquals(45, BackfillRules.defaultsFor(list, "t1", zone).durationMinutes)
    }

    @Test
    fun `样本不足 3 条时改用最近一条记录`() {
        val list = listOf(
            record(taskId = "t1", minutes = 30, startMs = ms(2026, 10, 6, 19, 30), endMs = ms(2026, 10, 6, 20, 0)),
            record(taskId = "t1", minutes = 200, startMs = ms(2026, 10, 8, 17, 47), endMs = ms(2026, 10, 8, 21, 7)),
        )
        val d = BackfillRules.defaultsFor(list, "t1", zone)
        assertEquals(1, d.daysAgo)
        assertEquals(21, d.endHour) // 取最近一条（10-08 21:07）
        assertEquals(0, d.endMinute) // 7 分钟量化到 0
        assertEquals(200, d.durationMinutes)
    }

    @Test
    fun `同频时取较晚的小时与较长的时长`() {
        val list = listOf(
            record(taskId = "t1", minutes = 30, startMs = ms(2026, 10, 6, 18, 30), endMs = ms(2026, 10, 6, 19, 0)),
            record(taskId = "t1", minutes = 30, startMs = ms(2026, 10, 7, 18, 30), endMs = ms(2026, 10, 7, 19, 0)),
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 7, 19, 30), endMs = ms(2026, 10, 7, 20, 0)),
            record(taskId = "t1", minutes = 45, startMs = ms(2026, 10, 8, 19, 30), endMs = ms(2026, 10, 8, 20, 0)),
        )
        val d = BackfillRules.defaultsFor(list, "t1", zone)
        assertEquals(20, d.endHour) // 19 点两次、20 点两次 → 取较晚
        assertEquals(45, d.durationMinutes) // 30 两次、45 两次 → 取较长
    }

    @Test
    fun `只看同任务的记录`() {
        val other = record(taskId = "t2", minutes = 120, startMs = ms(2026, 10, 6, 9, 0), endMs = ms(2026, 10, 6, 11, 0))
        val d = BackfillRules.defaultsFor(listOf(other), "t1", zone)
        assertEquals(21, d.endHour) // 该任务无记录 → 走兜底
        assertEquals(30, d.durationMinutes)
    }

    // ---- avoidOverlap ----

    @Test
    fun `不重叠时原样返回`() {
        val existing = record(id = 1, startMs = ms(2026, 10, 8, 9, 0), endMs = ms(2026, 10, 8, 10, 0))
        val span = BackfillRules.Span(ms(2026, 10, 8, 20, 0), ms(2026, 10, 8, 21, 0))
        assertEquals(span, BackfillRules.avoidOverlap(span, listOf(existing), "t1", zone))
    }

    @Test
    fun `只跟同任务的记录避让`() {
        val other = record(id = 1, taskId = "t2", startMs = ms(2026, 10, 8, 20, 0), endMs = ms(2026, 10, 8, 22, 0))
        val span = BackfillRules.Span(ms(2026, 10, 8, 20, 30), ms(2026, 10, 8, 21, 30))
        assertEquals(span, BackfillRules.avoidOverlap(span, listOf(other), "t1", zone))
    }

    @Test
    fun `撞上记录就接在它后面 时长不变`() {
        // 想补 20:00-21:00（1 小时），昨天已有 19:30-20:30
        val existing = record(id = 1, startMs = ms(2026, 10, 8, 19, 30), endMs = ms(2026, 10, 8, 20, 30))
        val span = BackfillRules.Span(ms(2026, 10, 8, 20, 0), ms(2026, 10, 8, 21, 0))

        val moved = BackfillRules.avoidOverlap(span, listOf(existing), "t1", zone)!!
        assertEquals(ms(2026, 10, 8, 20, 30), moved.startMillis) // 新起点落在已有记录的结束时刻
        assertEquals(ms(2026, 10, 8, 21, 30), moved.endMillis)
        assertEquals(60, moved.minutes) // 时长不变
        assertTrue(BackfillRules.overlapping(listOf(existing), "t1", moved).isEmpty())
    }

    @Test
    fun `连续撞上多条记录时一路往后让 恰好 2 小时仍接受`() {
        val list = listOf(
            record(id = 1, startMs = ms(2026, 10, 8, 19, 0), endMs = ms(2026, 10, 8, 20, 0)),
            record(id = 2, startMs = ms(2026, 10, 8, 20, 30), endMs = ms(2026, 10, 8, 21, 30)),
        )
        val span = BackfillRules.Span(ms(2026, 10, 8, 19, 30), ms(2026, 10, 8, 20, 30))
        val moved = BackfillRules.avoidOverlap(span, list, "t1", zone)!!
        assertEquals(ms(2026, 10, 8, 21, 30), moved.startMillis)
        assertEquals(ms(2026, 10, 8, 22, 30), moved.endMillis)
        // 挪动幅度正好 2 小时（上限内）
        assertEquals(
            BackfillRules.MAX_AVOID_SHIFT_MINUTES * 60_000L,
            moved.endMillis - span.endMillis,
        )
    }

    @Test
    fun `挪动超过 2 小时就放弃挪动`() {
        val existing = record(id = 1, startMs = ms(2026, 10, 8, 20, 0), endMs = ms(2026, 10, 8, 22, 30))
        val span = BackfillRules.Span(ms(2026, 10, 8, 20, 0), ms(2026, 10, 8, 21, 0))
        // 新结束会是 23:30，挪了 2.5 小时 → 超护栏，放弃（调用方改为显示警告）
        assertNull(BackfillRules.avoidOverlap(span, listOf(existing), "t1", zone))
    }

    @Test
    fun `挪出当天就放弃挪动`() {
        val existing = record(id = 1, startMs = ms(2026, 10, 8, 22, 0), endMs = ms(2026, 10, 8, 23, 30))
        val span = BackfillRules.Span(ms(2026, 10, 8, 22, 30), ms(2026, 10, 8, 23, 30))
        // 新结束会落到次日 00:30 → 跨天，放弃
        assertNull(BackfillRules.avoidOverlap(span, listOf(existing), "t1", zone))
    }

    @Test
    fun `时长非法时不做避让`() {
        val span = BackfillRules.Span(ms(2026, 10, 8, 21, 0), ms(2026, 10, 8, 21, 0))
        assertNull(BackfillRules.avoidOverlap(span, emptyList(), "t1", zone))
    }

    // ---- 改时长权限 ----

    @Test
    fun `只有手动补录的记录可以改时长`() {
        val manual = record(startMs = 0L, endMs = 0L, source = RecordSource.MANUAL)
        val timer = record(startMs = 0L, endMs = 0L, source = RecordSource.TIMER)
        assertTrue(BackfillRules.canEditDuration(manual))
        assertFalse(BackfillRules.canEditDuration(timer))
    }

    @Test
    fun `改时长时结束时刻不变 开始时刻回退`() {
        val end = ms(2026, 10, 9, 15, 0)
        val manual = record(
            id = 3,
            minutes = 30,
            startMs = end - 30 * 60_000L,
            endMs = end,
            source = RecordSource.MANUAL,
        )
        val edited = BackfillRules.withDuration(manual, 90)
        assertEquals(90, edited!!.minutes)
        assertEquals(end, edited.endEpochMillis)
        assertEquals(end - 90 * 60_000L, edited.startEpochMillis)
    }

    @Test
    fun `真实计时的记录不给改时长`() {
        val timer = record(minutes = 30, startMs = 0L, endMs = 30 * 60_000L, source = RecordSource.TIMER)
        assertNull(BackfillRules.withDuration(timer, 90))
    }

    @Test
    fun `改时长同样受上下限约束`() {
        val end = ms(2026, 10, 9, 15, 0)
        val manual = record(minutes = 30, startMs = end - 30 * 60_000L, endMs = end, source = RecordSource.MANUAL)
        assertEquals(1, BackfillRules.withDuration(manual, 0)!!.minutes)
        assertEquals(BackfillRules.MAX_MINUTES, BackfillRules.withDuration(manual, 99999)!!.minutes)
    }

    // ---- RecordSource 宽容解析 ----

    @Test
    fun `来源解析 未知或空值按真实计时处理`() {
        assertEquals(RecordSource.TIMER, RecordSource.of(null))
        assertEquals(RecordSource.TIMER, RecordSource.of(""))
        assertEquals(RecordSource.TIMER, RecordSource.of("GARBAGE"))
        assertEquals(RecordSource.MANUAL, RecordSource.of("MANUAL"))
        assertEquals(RecordSource.MANUAL, RecordSource.of("manual"))
        assertEquals(RecordSource.TIMER, RecordSource.of("TIMER"))
    }
}
