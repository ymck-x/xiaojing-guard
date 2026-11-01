package com.fangchenmi.guard.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.fangchenmi.guard.BuildConfig
import com.fangchenmi.guard.Guard

/**
 * 整个守护的眼睛：它只做一件事——把「现在前台是哪个应用」告诉执行中枢。
 * 不读取屏幕内容（canRetrieveWindowContent=false），不记录任何文字。
 */
class GuardAccessibilityService : AccessibilityService() {

    private var overlay: LockOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "service connected")
        val lockOverlay = LockOverlay(
            service = this,
            pinVerifier = { pin -> Guard.pinGuard.verify(pin, Guard.config.current().pin) },
            pinLength = { Guard.config.current().pin.length },
            onUnlockRequest = { Guard.engine.requestUnlock() },
            onSecretEntry = { openParentApp() },
        )
        overlay = lockOverlay
        Guard.engine.attach(this, lockOverlay)
    }

    /**
     * 锁屏右下角连点 5 次的入口。
     * 场景：家长把通知划掉了、图标又是隐藏的——没有这个暗门就再也进不去应用。
     */
    private fun openParentApp() {
        runCatching {
            startActivity(
                Intent(this, com.fangchenmi.guard.ui.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "event type=${event.eventType} pkg=${event.packageName}")
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Guard.engine.onForeground(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Guard.engine.onConfigurationChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Guard.engine.detach(this)
        overlay = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Guard.engine.detach(this)
        overlay = null
        super.onDestroy()
    }


    companion object {
        private const val TAG = "FangChenMi"


        fun componentName(context: Context): ComponentName =
            ComponentName(context, GuardAccessibilityService::class.java)

        /** 无障碍服务是否已被家长开启。 */
        fun isEnabled(context: Context): Boolean {
            val expected = componentName(context)
            val raw = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull() ?: return false
            return raw.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == expected
            }
        }
    }
}
