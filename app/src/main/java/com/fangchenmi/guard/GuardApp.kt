package com.fangchenmi.guard

import android.app.Application
import com.fangchenmi.guard.admin.GuardDeviceAdminReceiver
import com.fangchenmi.guard.service.GuardNotifier
import com.fangchenmi.guard.service.GuardWatchdogReceiver

class GuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guard.install(this)
        // 图标隐藏 / 常驻通知按配置对齐一次。
        GuardNotifier.sync(this, Guard.config.current())
        // 设备所有者模式下，把「禁止卸载」重新钉一遍（系统升级等场景可能被复位）。
        GuardDeviceAdminReceiver.applyUninstallBlock(this)
        // 进程每次起来都自检一次：守护开着、但无障碍没授权 → 立刻提醒，别让家长以为还护着。
        GuardWatchdogReceiver.checkAndWarn(this)
    }
}
