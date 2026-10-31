package com.fangchenmi.guard.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val pkg: String,
    val label: String,
    val icon: Bitmap? = null,
    val system: Boolean = false,
)

/** 列出可以启动的应用，供家长逐个分类。 */
class AppCatalog(private val context: Context) {

    private class Entry(
        val pkg: String,
        val label: String,
        val system: Boolean,
        val info: ApplicationInfo,
    )

    /**
     * 分两段回调：先给出「没有图标」的完整列表（毫秒级），再补上图标。
     * 一次性取 200+ 个应用的图标要好几秒，家长会以为界面卡死了。
     */
    suspend fun load(onProgress: suspend (List<InstalledApp>) -> Unit): List<InstalledApp> {
        val entries = queryEntries()
        onProgress(entries.map { InstalledApp(it.pkg, it.label, null, it.system) })
        val withIcons = entries.map {
            InstalledApp(it.pkg, it.label, loadIcon(it.info), it.system)
        }
        onProgress(withIcons)
        return withIcons
    }

    private fun queryEntries(): List<Entry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
        return resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                Entry(
                    pkg = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    info = info,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun loadIcon(info: ApplicationInfo): Bitmap? = runCatching {
        context.packageManager.getApplicationIcon(info).toBitmap(72, 72)
    }.getOrNull()
}
