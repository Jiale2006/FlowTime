package com.example.flowtime.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.flowtime.MainActivity
import com.example.flowtime.R
import com.example.flowtime.ui.components.formatMinutesCompact

/**
 * 专注自然结束的完成通知。
 *
 * 渠道保持静默：应用内已经按「完成提示音」开关播放铃声，并统一轻震；
 * 渠道若再出声会变成双重提醒（而且通知权限被拒时铃声仍要能用，不能把提醒交给渠道）。
 */
object FocusCompleteNotifier {

    private const val CHANNEL_ID = "focus_complete_v1"
    private const val NOTIFICATION_ID = 2002

    fun notifyCountdownComplete(context: Context, taskName: String, minutes: Int) {
        post(
            context = context,
            title = "专注完成",
            text = "「$taskName」 · ${formatMinutesCompact(minutes)}",
            bigText = "「$taskName」 · ${formatMinutesCompact(minutes)}，休息一下吧",
        )
    }

    fun notifyPomodoroComplete(
        context: Context,
        taskName: String,
        focusMinutes: Int,
        round: Int,
        totalRounds: Int,
        breakMinutes: Int,
        longBreak: Boolean,
    ) {
        val breakLabel = if (longBreak) {
            "长休息 ${formatMinutesCompact(breakMinutes)}"
        } else {
            "休息 ${formatMinutesCompact(breakMinutes)}"
        }
        post(
            context = context,
            title = "番茄专注完成",
            text = "「$taskName」 · 第 $round/$totalRounds 轮 · ${formatMinutesCompact(focusMinutes)}",
            bigText = "「$taskName」 · 第 $round/$totalRounds 轮结束，$breakLabel",
        )
    }

    /** 开始新一轮专注时清掉上一条完成通知，避免通知栏里留着过期战报 */
    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun post(context: Context, title: String, text: String, bigText: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task_time)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "专注完成",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "计时自然结束时的完成提醒（静默，应用内铃声与轻震负责提示）"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
