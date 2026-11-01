package com.fangchenmi.guard.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 卸载保护。
 *
 * 这里要区分两种强度，不能混为一谈：
 *
 * - **设备管理员**：只是让系统在卸载前要求「先停用设备管理员」多一步。
 *   挡住不懂的人，挡不住会点设置的人。任何应用都能拿到这个强度。
 *
 * - **设备所有者（Device Owner）**：可以调用 setUninstallBlocked，
 *   系统层面直接禁止卸载——长按图标、设置里、第三方管家，全都卸不掉。
 *   只能在设备上通过一次 adb 命令授予（`dpm set-device-owner`），
 *   因为这是企业设备管理的权限，普通应用安装时拿不到。
 *
 * 界面上的文案必须如实反映当前是哪种强度。
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "关闭后孩子可以直接卸载小鲸守护。如果是家长操作，请先确认当前时间。"

    override fun onDisabled(context: Context, intent: Intent) = Unit

    override fun onEnabled(context: Context, intent: Intent) {
        applyUninstallBlock(context)
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, GuardDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching { dpm.isAdminActive(componentName(context)) }.getOrDefault(false)
        }

        /** 是否已被授予「设备所有者」。这是真正能禁止卸载的前提。 */
        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
        }

        /**
         * 如果是设备所有者，直接禁止卸载。不是则什么都不做（也没法做）。
         * 每次启动、每次开启守护都调一次，保证状态一致。
         */
        fun applyUninstallBlock(context: Context): Boolean {
            if (!isActive(context)) return false
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching {
                if (!isDeviceOwner(context)) return false
                dpm.setUninstallBlocked(componentName(context), context.packageName, true)
                true
            }.getOrDefault(false)
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
            runCatching {
                if (isDeviceOwner(context)) {
                    dpm.setUninstallBlocked(componentName(context), context.packageName, false)
                }
                dpm.removeActiveAdmin(componentName(context))
            }
        }

        /** 把这台机器变成「设备所有者」的一次性命令，给家长照着敲。 */
        const val DEVICE_OWNER_COMMAND =
            "adb shell dpm set-device-owner com.fangchenmi.guard/.admin.GuardDeviceAdminReceiver"
    }
}
