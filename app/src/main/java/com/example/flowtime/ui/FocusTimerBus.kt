package com.example.flowtime.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 计时状态总线：ViewModel 为唯一状态源，服务只读快照渲染通知；
 * 通知按钮命令经 SharedFlow 回传 VM 执行（避免双状态源）。
 */
object FocusTimerBus {

    data class Snapshot(
        val active: Boolean = false,
        val paused: Boolean = false,
        val taskName: String = "",
        val displayText: String = "",
        val modeLabel: String = "",
        /** 0..100；-1 = 不显示进度条（正计时没有终点） */
        val progressPercent: Int = -1,
        /** 番茄轮次文案；非番茄模式为空 */
        val roundText: String = "",
        /**
         * 系统 Chronometer 的基准时刻（wall clock 毫秒），0 = 不启用。
         *
         * 🔴 通知栏的时间必须交给系统走：以前是把格式化好的 `displayText` 每次
         * notify 推过去，App 切到后台后更新被系统节流（Doze 下更低），
         * 通知里的秒数就"冻"住了，点回前台才对（他报的问题，2026-10-24）。
         */
        val chronometerBase: Long = 0L,
        /** true = 倒计时（从 [chronometerBase] 往下数）；false = 正计时 */
        val chronometerCountDown: Boolean = false,
    )

    val snapshot = MutableStateFlow(Snapshot())

    enum class Command { PAUSE, RESUME, STOP }

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 4)
}
