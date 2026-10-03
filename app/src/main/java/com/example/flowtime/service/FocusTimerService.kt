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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task_time)
            .setContentTitle("${snapshot.modeLabel} · ${snapshot.taskName}")
            .setContentText(snapshot.displayText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(pauseResume)
            .addAction(action("停止", "STOP"))
            .build()
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

    override fun onDestroy() {
        collecting = false
        scope.cancel()
        super.onDestroy()
    }
}
