package com.example.flowtime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.flowtime.data.FlowTimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 提醒到点：弹通知，并把下一次的闹钟排上（设置里关掉提醒时闹钟已被取消，不会再有回调） */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(FocusReminder.EXTRA_TIME) ?: return
        val goal = intent.getIntExtra(FocusReminder.EXTRA_GOAL, 0)
        FocusReminder.notifyNow(context, goal)
        FocusReminder.apply(context, enabled = true, time = time, goalMinutes = goal)
    }
}

/** 开机后重新排程：被动等应用启动不可靠，重启也要能按时提醒 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = FlowTimeRepository(appContext).settings.first()
                FocusReminder.apply(
                    appContext,
                    enabled = settings.reminderEnabled,
                    time = settings.reminderTime,
                    goalMinutes = settings.dailyGoalMinutes,
                )
            } catch (e: Exception) {
                Log.w("FlowTime", "开机重排提醒失败: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
