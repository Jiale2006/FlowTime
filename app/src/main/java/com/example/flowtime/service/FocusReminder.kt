package com.example.flowtime.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.flowtime.MainActivity
import com.example.flowtime.R
import com.example.flowtime.ui.components.formatMinutesCompact
import java.util.Calendar

/**
 * 每日习惯提醒。
 *
 * 排程策略：每次只排「下一次」触发，触发后由 [ReminderReceiver] 再排下一天，避免重复闹钟堆积。
 * Android 12+ 若未授予「闹钟和提醒」权限，`setExactAndAllowWhileIdle` 会抛异常，
 * 因此退化为 `setWindow`（窗口 10 分钟内必达，可能晚几分钟）。
 */
object FocusReminder {

    /** 渠道 id 带版本号：渠道创建后配置不可改，换配置只能换 id（旧 id 会被清理） */
    const val CHANNEL_ID = "focus_reminder_v3"
    /** 历史渠道：只响铃→无震动，建新渠道时一并清掉，避免系统设置里堆一串同名渠道 */
    private val LEGACY_CHANNEL_IDS = listOf("focus_reminder", "focus_reminder_v2")
    private const val NOTIFICATION_ID = 2001
    private const val REQUEST_CODE = 2001
    const val EXTRA_TIME = "reminder_time"
    const val EXTRA_GOAL = "reminder_goal"
    /** 不精确排程的窗口（毫秒）：允许系统在这个窗口内找合适时机送达 */
    private const val INEXACT_WINDOW_MILLIS = 10 * 60_000L

    /** 按设置重新排程；enabled=false 时取消。可重复调用（幂等）。 */
    fun apply(context: Context, enabled: Boolean, time: String, goalMinutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, time, goalMinutes)
        if (!enabled) {
            alarmManager.cancel(pending)
            return
        }
        // 开启提醒时就把渠道建好，用户能在系统设置里看到并调整提示音
        ensureChannel(context)
        val triggerAt = nextTriggerMillis(time)
        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MILLIS, pending)
            }
        } catch (e: SecurityException) {
            // 少数 ROM 即使 canScheduleExactAlarms() 为 true 也会拦，兜底为窗口闹钟
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MILLIS, pending)
        }
    }

    /** 是否可以使用精确闹钟（Android 12+ 需用户在系统设置里允许「闹钟和提醒」） */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** 通知是否被系统关闭（Android 13+ 需运行时授权 POST_NOTIFICATIONS） */
    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * 提醒渠道：显式指定提示音 + 高优先级（横幅弹出），**不震动**。
     * 注意渠道一旦创建，声音/震动/优先级不能再改，改配置必须换 CHANNEL_ID。
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 旧渠道（曾带震动）清掉，避免系统设置里堆一串同名「习惯提醒」
        LEGACY_CHANNEL_IDS.forEach { legacy ->
            runCatching { manager.deleteNotificationChannel(legacy) }
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "习惯提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "到你设定的时间提醒开始专注（只响提示音，不震动）"
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (soundUri != null) {
                val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, attributes)
            }
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** 立即发一条提醒通知（到点触发时调用） */
    fun notifyNow(context: Context, goalMinutes: Int) {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (goalMinutes > 0) {
            "今天的目标 ${formatMinutesCompact(goalMinutes)}，来一段专注吧"
        } else {
            "来一段专注，把注意力收回当下"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task_time)
            .setContentTitle("该专注啦")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun pendingIntent(context: Context, time: String, goalMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_TIME, time)
            .putExtra(EXTRA_GOAL, goalMinutes)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 下一次触发时刻：今天的 HH:mm 还没到就用今天，已过则顺延到明天 */
    fun nextTriggerMillis(time: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 20
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
