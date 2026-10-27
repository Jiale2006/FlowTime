package com.example.flowtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.flowtime.MainActivity
import com.example.flowtime.R
import com.example.flowtime.data.TimerSnapshotStore
import com.example.flowtime.ui.FocusTimerBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 专注计时前台服务：常驻通知显示进度，支持暂停/继续/停止，保证熄屏计时可靠 */
class FocusTimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_COMMAND = "command"
        private const val TAG = "FocusTimerService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false
    private val store by lazy { TimerSnapshotStore(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_COMMAND)?.let { dispatch(it) }
        createChannel()
        startForegroundWithType(buildNotification(currentSnapshot()))
        startCollectingIfNeeded()
        return START_STICKY
    }

    /**
     * 通知栏按钮 → 命令。
     *
     * 🔴 先落盘再广播（2026-10-26）：命令只走进程内总线时，App 在后台被系统回收、
     * 服务被 START_STICKY 重建而 ViewModel 还没回来，命令就无处投递、直接消失。
     * 用户点「停止」后这段计时随之丢失。落盘后 VM 晚一步恢复也能补做。
     */
    private fun dispatch(raw: String) {
        val command = runCatching { FocusTimerBus.Command.valueOf(raw) }.getOrNull() ?: return
        store.savePendingCommand(command.name)
        FocusTimerBus.post(command)
    }

    /**
     * 总线里的实时快照优先；为空（进程刚被重建、VM 还没恢复）时用落盘快照兜底，
     * 否则通知会直接消失 —— 用户看着像是计时被清掉了，也点不到停止。
     */
    private fun currentSnapshot(): FocusTimerBus.Snapshot {
        val live = FocusTimerBus.snapshot.value
        if (live.active) return live
        val saved = store.load() ?: return live
        return FocusTimerBus.Snapshot(
            active = true,
            paused = true,
            // 任务名要靠 VM 的任务列表查，这里查不到；交给 VM 回来后的快照覆盖
            taskName = "",
            displayText = "已暂停，点回应用继续",
            modeLabel = if (saved.mode == "COUNT_UP") "正计时" else "倒计时",
            progressPercent = if (saved.mode == "COUNT_UP" || saved.totalMillis <= 0L) {
                -1
            } else {
                ((saved.elapsedMillis * 100) / saved.totalMillis).toInt().coerceIn(0, 100)
            },
        ).also { FocusTimerBus.snapshot.value = it }
    }

    private fun startCollectingIfNeeded() {
        if (collecting) return
        collecting = true
        scope.launch {
            FocusTimerBus.snapshot.collect { snapshot ->
                if (!snapshot.active) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    // 前台服务的通知优先用 startForeground 更新（官方推荐的那条路）：
                    // notify() 更新在部分 ROM 上会被忽略，通知就永远停在启动那一刻的内容
                    // —— 任务名不跟着变。快照现在只在语义变化时发射，频率很低，可以放心走这里。
                    runCatching {
                        startForegroundWithType(buildNotification(snapshot))
                    }.recoverCatching {
                        NotificationManagerCompat.from(this@FocusTimerService)
                            .notify(NOTIFICATION_ID, buildNotification(snapshot))
                    }.onFailure {
                        // 以前静默吞掉：通知权限被关时这里一直失败，
                        // 界面上就是「通知永远停在旧任务名」，查都没法查
                        Log.w(TAG, "update notification failed: ${it.message}")
                    }
                }
            }
        }
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "专注计时",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "专注计时进行中的常驻通知"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(snapshot: FocusTimerBus.Snapshot): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseResume = if (snapshot.paused) {
            action("继续", "RESUME")
        } else {
            action("暂停", "PAUSE")
        }
        val name = snapshot.taskName.ifBlank { "专注" }
        val title = if (snapshot.paused) {
            "已暂停 · $name"
        } else {
            "${snapshot.modeLabel} · $name"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task_time)
            .setContentTitle(title)
            .setSubText(snapshot.roundText.ifBlank { null })
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(pauseResume)
            // 「停止」= 结束并记录（与计时页的「结束并记录」同语义）。
            // 🔴 以前这条映射到 cancelFocus（丢弃数据、不写记录），
            //    用户点了停止，40 分钟直接蒸发（2026-10-26 他报的问题）。
            .addAction(action("停止并记录", "STOP"))

        // 时间交给系统 Chronometer：由 SystemUI 自己走秒，App 在后台被节流/进 Doze
        // 都不影响读数（以前推静态文本，切出去就"冻"住，点回前台才跳正）。
        if (snapshot.chronometerBase > 0L) {
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(snapshot.chronometerCountDown)
                .setWhen(snapshot.chronometerBase)
                .setShowWhen(true)
                .setContentText(null)
        } else {
            // 暂停 / 未开始：静态时间，别让系统按墙钟继续数
            builder
                .setUsesChronometer(false)
                .setShowWhen(false)
                .setContentText(snapshot.displayText)
        }

        // 正计时 progressPercent = -1，不画进度条
        if (snapshot.progressPercent >= 0) {
            builder.setProgress(100, snapshot.progressPercent, false)
        }
        return builder.build()
    }

    private fun action(title: String, command: String): NotificationCompat.Action {
        val intent = Intent(this, FocusTimerService::class.java)
            .putExtra(EXTRA_COMMAND, command)
        val pendingIntent = PendingIntent.getService(
            this,
            command.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(null, title, pendingIntent).build()
    }

    /**
     * 划掉最近任务：快照已由 AppViewModel 每秒落盘，这里只收掉常驻通知并停服，
     * 否则会留下一条不再更新的旧计时通知（且前台服务会把进程一直吊着）。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        collecting = false
        scope.cancel()
        super.onDestroy()
    }
}
