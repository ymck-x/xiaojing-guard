package com.fangchenmi.guard.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * 从系统里查「哪些包不该算进使用时长 / 哪些是设置 / 哪些是我们自己」。
 * 这些集合在进程生命周期内不变，算一次缓存起来。
 */
class PackageFacts(private val context: Context) {

    val selfPackages: Set<String> by lazy {
        setOf(context.packageName)
    }

    /** 桌面、系统界面、输入法、权限弹窗：都不算"在玩"。 */
    val ignoredPackages: Set<String> by lazy {
        buildSet {
            addAll(selfPackages)
            add("com.android.systemui")
            add("android")
            add("com.android.permissioncontroller")
            add("com.android.intentresolver")
            // 有些 ROM 把 Settings$FallbackHome 注册成桌面活动，必须把「设置」从桌面名单里剔掉，
            // 否则设置会被当作桌面忽略，孩子也就能自己去关无障碍。
            addAll(homePackages() - settingsPackages)
            addAll(imePackages())
            // 没有桌面入口的系统组件（系统弹窗、厂商功能页，比如「智能保持亮屏」）
            // 不是孩子在玩的东西，不该算进时长。
            addAll(systemPackagesWithoutLauncher() - settingsPackages)
        }
    }

    /**
     * 孩子能用来关掉守护、或者装/卸应用的入口，一律按「要家长密码」处理。
     *
     * 光挡「设置」不够：国产 ROM 的管家、安全中心、权限管理、应用商店、文件管理
     * 都能进「应用信息」强行停止，或者直接装一个 APK 覆盖回来。
     * 名单按已安装过滤，所以换机型也不会有多余条目。
     */
    val settingsPackages: Set<String> by lazy {
        buildSet {
            listOf(
                Intent(Settings.ACTION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_SETTINGS),
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            ).forEach { intent ->
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.let(::add)
            }
            add("com.android.settings")
            MANAGER_PACKAGES.forEach { pkg ->
                if (isInstalled(pkg)) add(pkg)
            }
        }
    }

    /** 有桌面入口的应用；其余系统组件一律视为「系统界面」。 */
    private val launchablePackages: Set<String> by lazy {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun systemPackagesWithoutLauncher(): Set<String> = runCatching {
        context.packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .map { it.packageName }
            .filter { it !in launchablePackages }
            .toSet()
    }.getOrDefault(emptySet())

    private fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0)
    }.isSuccess

    fun isSelf(pkg: String?): Boolean = pkg != null && pkg in selfPackages

    fun isSettings(pkg: String?): Boolean = pkg != null && pkg in settingsPackages

    private fun homePackages(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val result = mutableSetOf<String>()
        // 注意：这里不能用 MATCH_ALL。MATCH_ALL 会放宽过滤，实测会把
        // com.android.settings 这类应用也一起返回，导致「设置」被当桌面忽略掉、拦不住。
        pm.queryIntentActivities(intent, 0).forEach { info ->
            info.activityInfo?.packageName?.let(result::add)
        }
        pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.let(result::add)
        result
    }.getOrDefault(emptySet())

    private fun imePackages(): Set<String> = runCatching {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    fun labelOf(pkg: String): CharSequence = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
    }.getOrElse { pkg }
    private companion object {
        /** 各家 ROM 的「管家 / 商店 / 文件管理」入口，能强行停止或重装应用。 */
        val MANAGER_PACKAGES = listOf(
            // vivo / iQOO
            "com.vivo.safecenter",
            "com.iqoo.secure",
            "com.vivo.permissionmanager",
            "com.bbk.appstore",
            "com.android.filemanager",
            "com.vivo.easyshare",
            "com.vivo.ai.copilot",
            // OPPO / 一加 / realme
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.coloros.phonemanager",
            "com.heytap.market",
            "com.coloros.filemanager",
            // 小米 / 红米
            "com.miui.securitycenter",
            "com.xiaomi.market",
            "com.mi.android.globalFileexplorer",
            // 华为 / 荣耀
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager",
            "com.huawei.appmarket",
            "com.huawei.hidisk",
            // 三星
            "com.samsung.android.lool",
            // 通用
        )
    }
}