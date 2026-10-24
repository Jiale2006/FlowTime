package com.example.flowtime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 计时快照编解码的单元测试。
 * 覆盖：往返一致 / 自由专注 / 损坏或空数据 / 非法阶段 / 倒计时缺总时长 / 正计时零总时长。
 */
class TimerSnapshotTest {

    private val snapshot = TimerSnapshot(
        phase = "RUNNING",
        mode = "COUNT_DOWN",
        inBreak = false,
        round = 2,
        selectedTaskId = "task-1",
        totalMillis = 45 * 60_000L,
        elapsedMillis = 12_345L,
        selectedDurationMinutes = 45,
    )

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(snapshot, TimerSnapshot.decode(snapshot.encode()))
    }

    @Test
    fun freeFocusKeepsNullTask() {
        val freeFocus = snapshot.copy(selectedTaskId = null)
        assertEquals(freeFocus, TimerSnapshot.decode(freeFocus.encode()))
    }

    @Test
    fun brokenPayloadIsIgnored() {
        assertNull(TimerSnapshot.decode(null))
        assertNull(TimerSnapshot.decode(""))
        assertNull(TimerSnapshot.decode("not json"))
        assertNull(TimerSnapshot.decode("""{"phase":"RUNNING"}"""))
    }

    @Test
    fun idlePhaseIsNotRestorable() {
        assertNull(TimerSnapshot.decode(snapshot.copy(phase = "IDLE").encode()))
    }

    @Test
    fun countdownWithoutTotalIsIgnored() {
        assertNull(TimerSnapshot.decode(snapshot.copy(totalMillis = 0L).encode()))
    }

    @Test
    fun countUpWithZeroTotalIsValid() {
        val countUp = snapshot.copy(mode = "COUNT_UP", totalMillis = 0L)
        assertEquals(countUp, TimerSnapshot.decode(countUp.encode()))
    }
}
