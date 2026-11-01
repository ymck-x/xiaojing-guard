package com.fangchenmi.guard.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标的显示与隐藏。
 *
 * 图标挂在 `LauncherEntry` 这个 activity-alias 上，所以隐藏它不影响应用本身：
 * 无障碍服务照跑、通知照发，只是桌面和搜索结果里都找不到这个应用——
 * 也就没有「长按图标 → 卸载」这个入口了。
 *
 * 家长靠常驻通知进应用。
 */
object LauncherIcon {

    private const val ALIAS = "com.fangchenmi.guard.ui.LauncherEntry"

    private fun alias(context: Context) = ComponentName(context.packageName, ALIAS)

    fun isVisible(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        pm.getComponentEnabledSetting(alias(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }.getOrDefault(true)

    /**
     * 返回是否真的隐藏成功。失败时调用方应当保持「图标显示」，
     * 否则家长就再也打不开这个应用了。
     */
    fun setVisible(context: Context, visible: Boolean): Boolean = runCatching {
        val state = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            state,
            PackageManager.DONT_KILL_APP,
        )
        true
    }.getOrDefault(false)
}
