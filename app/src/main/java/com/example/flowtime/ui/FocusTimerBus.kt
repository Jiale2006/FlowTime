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
    )

    val snapshot = MutableStateFlow(Snapshot())

    enum class Command { PAUSE, RESUME, STOP }

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 4)
}
