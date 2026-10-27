package com.example.flowtime.data

import android.content.Context
import org.json.JSONObject

/**
 * 被划掉后台 / 进程被杀时的计时快照。
 *
 * 语义：离开期间的时间**不补**，恢复后从 [elapsedMillis] 接着走。
 * phase / mode 存枚举名而不是类型，避免 data 层依赖 UI 包。
 */
data class TimerSnapshot(
    val phase: String,
    val mode: String,
    val inBreak: Boolean,
    val round: Int,
    val selectedTaskId: String?,
    val totalMillis: Long,
    val elapsedMillis: Long,
    val selectedDurationMinutes: Int,
) {

    fun encode(): String = JSONObject()
        .put("phase", phase)
        .put("mode", mode)
        .put("inBreak", inBreak)
        .put("round", round)
        .put("selectedTaskId", selectedTaskId ?: JSONObject.NULL)
        .put("totalMillis", totalMillis)
        .put("elapsedMillis", elapsedMillis)
        .put("selectedDurationMinutes", selectedDurationMinutes)
        .toString()

    companion object {
        private val PHASES = setOf("RUNNING", "PAUSED")
        private val MODES = setOf("COUNT_UP", "COUNT_DOWN", "POMODORO")

        /**
         * 容错解析：空、缺字段、枚举不认识、倒计时没有总时长都视为没有快照。
         * 宁可回到待机，也不要恢复出一个坏状态。
         */
        fun decode(raw: String?): TimerSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                val phase = json.optString("phase")
                val mode = json.optString("mode")
                val totalMillis = json.optLong("totalMillis", 0L)
                if (phase !in PHASES || mode !in MODES) return null
                if (mode != "COUNT_UP" && totalMillis <= 0L) return null
                TimerSnapshot(
                    phase = phase,
                    mode = mode,
                    inBreak = json.optBoolean("inBreak", false),
                    round = json.optInt("round", 1).coerceAtLeast(1),
                    selectedTaskId = if (json.isNull("selectedTaskId")) {
                        null
                    } else {
                        json.optString("selectedTaskId").takeIf { it.isNotBlank() }
                    },
                    totalMillis = totalMillis.coerceAtLeast(0L),
                    elapsedMillis = json.optLong("elapsedMillis", 0L).coerceAtLeast(0L),
                    selectedDurationMinutes = json.optInt("selectedDurationMinutes", 0)
                        .coerceAtLeast(0),
                )
            }.getOrNull()
        }
    }
}

/**
 * 计时快照落盘。
 *
 * 这里刻意用 SharedPreferences 而不是 DataStore：启动时要在第一帧之前**同步**读出
 * （否则会先闪一下待机页），运行中还要每秒写一次；DataStore 的异步首读和整文件事务
 * 都不适合这种高频小状态。
 */
class TimerSnapshotStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("timer_state", Context.MODE_PRIVATE)

    fun load(): TimerSnapshot? = TimerSnapshot.decode(prefs.getString(KEY, null))

    fun save(snapshot: TimerSnapshot) {
        prefs.edit().putString(KEY, snapshot.encode()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    /**
     * 通知栏按钮命令的落盘副本。
     *
     * 🔴 为什么必须落盘：命令原来只走进程内总线，App 在后台被系统回收后
     * 服务重建（START_STICKY）而 ViewModel 还没回来，命令无处投递就被丢弃——
     * 用户点「停止」后那段计时彻底丢失（他报的问题，2026-10-26）。
     * 现在服务先落盘再广播，VM 处理完清除；VM 晚一步恢复也能补做。
     */
    fun savePendingCommand(command: String) {
        prefs.edit()
            .putString(PENDING_KEY, command)
            .putLong(PENDING_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    fun loadPendingCommand(): String? = prefs.getString(PENDING_KEY, null)?.takeIf { it.isNotBlank() }

    /** 太旧的命令不再补做（避免几天后打开 App 突然结算一段计时） */
    fun isPendingFresh(maxAgeMillis: Long = PENDING_MAX_AGE_MILLIS): Boolean =
        System.currentTimeMillis() - prefs.getLong(PENDING_AT_KEY, 0L) <= maxAgeMillis

    fun clearPendingCommand() {
        prefs.edit().remove(PENDING_KEY).remove(PENDING_AT_KEY).apply()
    }

    private companion object {
        const val KEY = "snapshot"
        const val PENDING_KEY = "pending_command"
        const val PENDING_AT_KEY = "pending_command_at"

        /** 通知栏命令的有效期：2 小时。超过就算陈旧，丢弃。 */
        const val PENDING_MAX_AGE_MILLIS = 2 * 60 * 60 * 1000L
    }
}
