package com.fangchenmi.guard.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.StatusBarManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fangchenmi.guard.R
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.ui.LauncherIcon
import com.fangchenmi.guard.ui.MainActivity

/**
 * 桌面图标藏起来之后，家长靠这条常驻通知进应用。
 *
 * 两个安全阀：
 * 1. **没有通知权限就不藏图标**——否则家长就再也进不来了。
 * 2. 通知是 ongoing，删不掉；点一下直达家长验证。
 */
object GuardNotifier {

    private const val CHANNEL_ID = "guard_status"
    private const val NOTIFICATION_ID = 1000

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 按配置把「隐藏图标」和「常驻通知」调到一致的状态。 */
    fun sync(context: Context, config: GuardConfig) {
        // 守护开着、又有通知渠道（家长能进来），就藏图标。
        val wantHidden = config.enabled && !config.showLauncherIcon && canNotify(context)
        if (wantHidden) {
            LauncherIcon.setVisible(context, false)
            post(context)
            requestTileOnce(context)
        } else {
            LauncherIcon.setVisible(context, true)
            cancel(context)
        }
    }

    /**
     * 图标藏了之后，通知是主要入口；万一通知被系统清掉、设备又没被锁，
     * 家长还需要一个随时能点的东西——通知栏里的快捷磁贴。这里申请加一次。
     */
    private fun requestTileOnce(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = context.getSharedPreferences("guard_tile", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        runCatching {
            val manager = context.getSystemService(StatusBarManager::class.java) ?: return
            val icon = android.graphics.drawable.Icon.createWithResource(
                context,
                R.drawable.ic_stat_guard,
            )
            manager.requestAddTileService(
                android.content.ComponentName(context, GuardTileService::class.java),
                context.getString(R.string.app_name),
                icon,
                context.mainExecutor,
            ) { }
        }
    }

    /**
     * 通知被划掉时把它补回来。
     *
     * 注意：**绝不能**反过来——通知没了就把图标放回来。
     * 那等于给出一条「划掉通知 → 图标出现 → 长按卸载」的绕过路径。
     * 正确做法是让通知自己长回来，图标永远是隐藏的。
     */
    fun ensure(context: Context, config: GuardConfig) {
        if (!config.enabled || config.showLauncherIcon) return
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alive = runCatching {
            manager.activeNotifications.any { it.id == NOTIFICATION_ID }
        }.getOrDefault(false)
        if (!alive) post(context)
    }

    private fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "守护状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "守护运行时的常驻通知，点一下可以进入家长验证"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle("小鲸守护正在运行")
            .setContentText("点这里进入家长设置")
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}
