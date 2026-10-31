package com.fangchenmi.guard

import android.app.Application
import com.fangchenmi.guard.service.GuardWatchdogReceiver

class GuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guard.install(this)
        // 进程每次起来都自检一次：守护开着、但无障碍没授权 → 立刻提醒，别让家长以为还护着。
        GuardWatchdogReceiver.checkAndWarn(this)
    }
}
