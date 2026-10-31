package com.fangchenmi.guard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fangchenmi.guard.Guard
import com.fangchenmi.guard.R
import com.fangchenmi.guard.ui.MainActivity

/**
 * 守护的「看门狗」。
 *
 * 无障碍授权不是一个稳定的东西：应用被覆盖安装、系统升级、部分 ROM 的安全策略，
 * 都会把 `enabled_accessibility_services` 清空。一旦被清空，守护就完全失效，
 * 而界面上那张「守护中」的牌子只有家长主动打开 App 才会刷新——等于没有提示。
 *
 * 所以这里监听「开机」和「本应用被更新」两个时机，发现授权没了就发通知。
 * 这是整个应用里唯一一个不依赖无障碍、也不依赖进程存活的检查点。
 */
class GuardWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_CHECK_GUARD,
            -> checkAndWarn(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_CHECK_GUARD = "com.fangchenmi.guard.action.CHECK_GUARD"
        private const val CHANNEL_ID = "guard_alerts"
        private const val NOTIFICATION_ID = 1001

        fun checkAndWarn(context: Context) {
            val config = runCatching { Guard.config.current() }.getOrNull() ?: return
            if (!config.enabled) return
            if (GuardAccessibilityService.isEnabled(context)) return
            notifyBroken(context)
        }

        fun notifyBroken(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "守护状态提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "无障碍权限被系统关掉时提醒家长"
                }
                manager.createNotificationChannel(channel)
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_guard)
                .setContentTitle("小鲸守护已失效，请重新开启")
                .setContentText("系统把无障碍权限关掉了，现在孩子做什么都不会被拦。点这里重新开启。")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "系统把无障碍权限关掉了，现在孩子做什么都不会被拦。点这里重新开启。",
                    ),
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }
}
