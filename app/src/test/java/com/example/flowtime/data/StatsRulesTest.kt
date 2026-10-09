package com.example.flowtime.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统计口径规则的单元测试。
 * 覆盖：时段按分钟拆分（含跨天/整点边界）、日均分母、时长分桶、最强一天、区间对比、上一区间范围。
 */
class StatsRulesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun record(startMs: Long, endMs: Long, taskId: String? = "t1") = FocusRecord(
        taskId = taskId,
        taskName = "测试",
        minutes = ((endMs - startMs) / 60_000L).toInt(),
        startEpochMillis = startMs,
        endEpochMillis = endMs,
    )

    // ---- 时段分布：按分钟拆分 ----

    @Test
    fun `同一小时内的记录全部落在那一个小时`() {
        val got = StatsRules.distributeByHour(ms(2026, 10, 9, 20, 10), ms(2026, 10, 9, 20, 40), zone)
        assertEquals(mapOf(20 to 30), got)
    }

    @Test
    fun `跨小时按分钟摊到两根柱子上`() {
        // 20:30 → 22:00 = 90 分钟
        val got = StatsRules.distributeByHour(ms(2026, 10, 9, 20, 30), ms(2026, 10, 9, 22, 0), zone)
        assertEquals(mapOf(20 to 30, 21 to 60), got)
    }

    @Test
    fun `跨午夜分别落到 23 点和 0 点`() {
        val got = StatsRules.distributeByHour(ms(2026, 10, 9, 23, 30), ms(2026, 10, 10, 1, 0), zone)
        assertEquals(mapOf(23 to 30, 0 to 60), got)
    }

    @Test
    fun `整点边界不重复计入`() {
        val got = StatsRules.distributeByHour(ms(2026, 10, 9, 20, 0), ms(2026, 10, 9, 21, 0), zone)
        assertEquals(mapOf(20 to 60), got)
    }

    @Test
    fun `非法区间返回空`() {
        assertTrue(StatsRules.distributeByHour(100L, 100L, zone).isEmpty())
        assertTrue(StatsRules.distributeByHour(200L, 100L, zone).isEmpty())
    }

    @Test
    fun `长记录会摊到跨越的每一个小时`() {
        // 8 小时：20:00 → 次日 04:00
        val got = StatsRules.distributeByHour(ms(2026, 10, 9, 20, 0), ms(2026, 10, 10, 4, 0), zone)
        assertEquals(8, got.size)
        assertEquals(60, got[20])
        assertEquals(60, got[23])
        assertEquals(60, got[0])
        assertEquals(60, got[3])
        assertNull(got[4])
    }

    @Test
    fun `一批记录聚合成 24 小时分布且总量守恒`() {
        val list = listOf(
            record(ms(2026, 10, 9, 9, 0), ms(2026, 10, 9, 10, 0)),   // 9 点 60
            record(ms(2026, 10, 9, 9, 30), ms(2026, 10, 9, 10, 30)), // 9 点 30 + 10 点 30
        )
        val hours = StatsRules.hourMinutesOf(list, zone)
        assertEquals(24, hours.size)
        assertEquals(90, hours[9])
        assertEquals(30, hours[10])
        assertEquals(120, hours.sum())
    }

    // ---- 日均 ----

    @Test
    fun `天数不足时不显示日均`() {
        assertNull(StatsRules.dailyAverage(120, 0))
        assertNull(StatsRules.dailyAverage(120, 1))
    }

    @Test
    fun `天数足够时按已过自然日平均`() {
        assertEquals(40, StatsRules.dailyAverage(120, 3))
        assertEquals(0, StatsRules.dailyAverage(0, 5))
    }

    // ---- 单次时长分桶 ----

    @Test
    fun `时长分桶边界正确`() {
        val list = listOf(
            record(0, 14 * 60_000L),    // <15
            record(0, 15 * 60_000L),    // 15-30
            record(0, 29 * 60_000L),    // 15-30
            record(0, 30 * 60_000L),    // 30-60
            record(0, 59 * 60_000L),    // 30-60
            record(0, 60 * 60_000L),    // 1-2 小时
            record(0, 119 * 60_000L),   // 1-2 小时
            record(0, 120 * 60_000L),   // ≥2 小时
        )
        val buckets = StatsRules.durationBuckets(list)
        assertEquals(listOf(1, 2, 2, 2, 1), buckets.map { it.count })
        assertEquals(listOf("<15分", "15-30分", "30-60分", "1-2小时", "≥2小时"), buckets.map { it.label })
        assertEquals(8, buckets.sumOf { it.count })
    }

    @Test
    fun `空记录时分桶全为 0`() {
        val buckets = StatsRules.durationBuckets(emptyList())
        assertEquals(5, buckets.size)
        assertEquals(0, buckets.sumOf { it.count })
    }

    // ---- 最强一天 ----

    @Test
    fun `最强一天按分钟降序 同分取更早的日期`() {
        val byDay = mapOf(
            LocalDate.of(2026, 10, 1) to 100,
            LocalDate.of(2026, 10, 5) to 200,
            LocalDate.of(2026, 10, 3) to 200,
            LocalDate.of(2026, 10, 7) to 0,
        )
        val top = StatsRules.topDays(byDay, 3)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1)), top.map { it.date })
        assertEquals(listOf(200, 200, 100), top.map { it.minutes })
    }

    @Test
    fun `最强一天跳过 0 分钟的日子并受 n 限制`() {
        val byDay = mapOf(
            LocalDate.of(2026, 10, 1) to 0,
            LocalDate.of(2026, 10, 2) to 30,
            LocalDate.of(2026, 10, 3) to 40,
        )
        assertEquals(2, StatsRules.topDays(byDay, 5).size)
        assertEquals(1, StatsRules.topDays(byDay, 1).size)
        assertTrue(StatsRules.topDays(emptyMap(), 3).isEmpty())
    }

    // ---- 对比 ----

    @Test
    fun `上一区间为 0 时不给百分比`() {
        assertNull(StatsRules.changePercent(120, 0))
    }

    @Test
    fun `涨跌百分比`() {
        assertEquals(50, StatsRules.changePercent(150, 100)!!)
        assertEquals(-25, StatsRules.changePercent(75, 100)!!)
        assertEquals(0, StatsRules.changePercent(100, 100)!!)
    }

    @Test
    fun `本周的上一区间是上周一到上周日`() {
        // 2026-10-09 是周五，本周一是 10-05
        val window = StatsRules.previousWindow(StatsRange.WEEK, LocalDate.of(2026, 10, 9))!!
        assertEquals(LocalDate.of(2026, 9, 28), window.start)
        assertEquals(LocalDate.of(2026, 10, 4), window.endInclusive)
    }

    @Test
    fun `本月的上一区间是上一个整月`() {
        val window = StatsRules.previousWindow(StatsRange.MONTH, LocalDate.of(2026, 10, 9))!!
        assertEquals(LocalDate.of(2026, 9, 1), window.start)
        assertEquals(LocalDate.of(2026, 9, 30), window.endInclusive)
    }

    @Test
    fun `全部档没有上一区间`() {
        assertNull(StatsRules.previousWindow(StatsRange.ALL, LocalDate.of(2026, 10, 9)))
    }
}
