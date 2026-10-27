package com.example.flowtime.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时间文本口径（2026-10-25 定）：
 * 不到一小时只显示「分:秒」，满一小时才加小时且补零。
 * 他反馈 "0:34:00" 难看、要的是 "34:00"；超过一小时后用 "01:05:00"。
 */
class ClockFormatTest {

    @Test
    fun underOneHourShowsMinutesAndSeconds() {
        assertEquals("00:00", formatClockHms(0))
        assertEquals("00:59", formatClockHms(59))
        assertEquals("01:00", formatClockHms(60))
        assertEquals("34:00", formatClockHms(34 * 60L))
        assertEquals("45:00", formatClockHms(45 * 60L))
        assertEquals("59:59", formatClockHms(59 * 60L + 59))
    }

    @Test
    fun oneHourAndAbovePadsHours() {
        assertEquals("01:00:00", formatClockHms(3600))
        assertEquals("01:05:00", formatClockHms(3600 + 5 * 60L))
        assertEquals("02:30:00", formatClockHms(2 * 3600L + 30 * 60L))
        assertEquals("12:00:00", formatClockHms(12 * 3600L))
        assertEquals("24:00:00", formatClockHms(24 * 3600L))
    }

    @Test
    fun negativeClampsToZero() {
        assertEquals("00:00", formatClockHms(-5))
    }
}
