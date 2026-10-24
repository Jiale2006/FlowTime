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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.flowtime.MainActivity
import com.example.flowtime.R
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_COMMAND)) {
            "PAUSE" -> scope.launch { FocusTimerBus.commands.emit(FocusTimerBus.Command.PAUSE) }
            "RESUME" -> scope.launch { FocusTimerBus.commands.emit(FocusTimerBus.Command.RESUME) }
            "STOP" -> scope.launch { FocusTimerBus.commands.emit(FocusTimerBus.Command.STOP) }
        }
        createChannel()
        startForegroundWithType(buildNotification(FocusTimerBus.snapshot.value))
        if (!collecting) {
            collecting = true
            scope.launch {
                FocusTimerBus.snapshot.collect { snapshot ->
                    if (!snapshot.active) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        runCatching {
                            NotificationManagerCompat.from(this@FocusTimerService)
                                .notify(NOTIFICATION_ID, buildNotification(snapshot))
                        }
                    }
                }
            }
        }
        return START_STICKY
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
        val title = if (snapshot.paused) {
            "已暂停 · ${snapshot.taskName}"
        } else {
            "${snapshot.modeLabel} · ${snapshot.taskName}"
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
            .addAction(action("停止", "STOP"))

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
