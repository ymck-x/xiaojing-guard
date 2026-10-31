package com.fangchenmi.guard

import android.app.Application
import com.fangchenmi.guard.data.ConfigStore
import com.fangchenmi.guard.data.PackageFacts
import com.fangchenmi.guard.data.PinAttemptGuard
import com.fangchenmi.guard.data.UsageStore
import com.fangchenmi.guard.service.GuardEngine

/**
 * 极简依赖容器。界面、无障碍服务、悬浮层都从同一个进程内单例取依赖，
 * 保证全应用只有一份配置和一份计时状态。
 */
object Guard {

    lateinit var config: ConfigStore
        private set
    lateinit var usage: UsageStore
        private set
    lateinit var facts: PackageFacts
        private set
    lateinit var pinGuard: PinAttemptGuard
        private set
    lateinit var engine: GuardEngine
        private set

    @Volatile
    var installed: Boolean = false
        private set

    fun install(application: Application) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            config = ConfigStore(application)
            usage = UsageStore(application)
            facts = PackageFacts(application)
            pinGuard = PinAttemptGuard(application)
            engine = GuardEngine(application, config, usage, facts)
            installed = true
            engine.start()
        }
    }
}
