package com.fangchenmi.guard.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 卸载保护。启用后系统不允许直接卸载本应用，孩子必须先进设置停用设备管理器，
 * 而「设置」本身被守护挡着——这就是「孩子删不掉」的实现。
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "关闭后孩子可以直接卸载小鲸守护。如果是家长操作，请先确认当前时间。"

    override fun onDisabled(context: Context, intent: Intent) = Unit

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, GuardDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching { dpm.isAdminActive(componentName(context)) }.getOrDefault(false)
        }

        /** 拉起系统的「激活设备管理器」确认页。 */
        fun activationIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "开启后，孩子无法直接卸载小鲸守护。家长随时可以在应用内关闭。",
                )
            }

        /** 家长验证通过后关闭卸载保护。 */
        fun deactivate(context: Context) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
            runCatching { dpm.removeActiveAdmin(componentName(context)) }
        }
    }
}
