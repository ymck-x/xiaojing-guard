package com.fangchenmi.guard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.Features
import kotlinx.coroutines.delay
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.pin.PinHasher
import com.fangchenmi.guard.ui.components.MinuteStepper
import com.fangchenmi.guard.ui.components.SectionCard
import com.fangchenmi.guard.ui.components.SettingRow
import com.fangchenmi.guard.ui.components.StatusGood
import com.fangchenmi.guard.ui.components.StatusWarn
import com.fangchenmi.guard.ui.components.ToggleRow

@Composable
fun SettingsScreen(
    config: GuardConfig,
    accessibilityEnabled: Boolean,
    deviceAdminActive: Boolean,
    deviceOwner: Boolean,
    onConfigChange: (GuardConfig) -> Unit,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onDeactivate: () -> Unit,
    onTempUnlock: () -> Unit,
    onChangePin: () -> Unit,
    usage: com.fangchenmi.guard.core.usage.MeterState,
) {
    val context = LocalContext.current
    var confirmDeactivate by remember { mutableStateOf(false) }
    var showDeviceOwnerGuide by remember { mutableStateOf(false) }
    val unlockedFor = (config.unlockUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val unlocksLeft = com.fangchenmi.guard.core.policy.UnlockPolicy.remainingUnlocks(config, usage, com.fangchenmi.guard.Features.limits)

    // 改动即时生效、不需要点保存——但家长看不到这件事就会怀疑没存上。
    // 每次配置变化闪一下「已保存」，比放一个保存按钮更符合这个应用的使用节奏。
    var changedOnce by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(config) {
        if (!changedOnce) {
            changedOnce = true
            return@LaunchedEffect
        }
        savedFlash = true
        delay(1600)
        savedFlash = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
            Text("家长设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                if (savedFlash) {
                    Text(
                        "已保存 ✓",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusGood,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "改动即时生效；设置完点底部的「保存并返回」即可。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        SectionCard(title = "守护开关") {
            ToggleRow(
                title = "启用防沉迷",
                subtitle = if (config.enabled) "正在按下面的规则计时和拦截" else "关闭时不做任何拦截",
                checked = config.enabled,
                onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
            )
            if (config.enabled && !accessibilityEnabled) {
                Text(
                    "无障碍权限未开启，开关虽然打开但拦不住任何东西。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusWarn,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        RulesSection(config = config, onChange = onConfigChange)

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "权限") {
            SettingRow(
                title = "无障碍守护",
                subtitle = if (accessibilityEnabled) "已开启" else "必须开启，否则无法拦截",
            ) {
                OutlinedButton(onClick = onOpenAccessibility) {
                    Text(if (accessibilityEnabled) "设置" else "去开启")
                }
            }
            SettingRow(
                title = "卸载保护",
                subtitle = when {
                    deviceOwner -> "已开启（设备所有者）：系统层面禁止卸载，孩子卸不掉"
                    deviceAdminActive -> "已开启（设备管理员）：能挡住不懂的人；会玩设置的人可以先停用管理员再卸载"
                    else -> "未开启，孩子长按图标就能直接卸载"
                },
            ) {
                OutlinedButton(onClick = onEnableDeviceAdmin) {
                    Text(if (deviceAdminActive) "已开启" else "去开启")
                }
            }
            if (!deviceOwner) {
                SettingRow(
                    title = "更强的卸载保护",
                    subtitle = "把本应用设成「设备所有者」后，系统会禁止卸载。需要电脑跑一次命令，只需一次。",
                ) {
                    TextButton(onClick = { showDeviceOwnerGuide = true }) { Text("怎么做") }
                }
            }
            SettingRow(
                title = "图标藏起来了，怎么进来？",
                subtitle = "① 点通知栏那条「小鲸守护正在运行」（这条通知删不掉）；② 被锁屏时在屏幕右下角连点 5 下（第三下会冒出小圆点）。实在进不去就重装一次，配置不会丢。",
            ) {
                Text("")
            }
            val canNotify = com.fangchenmi.guard.service.GuardNotifier.canNotify(context)
            ToggleRow(
                title = "在桌面显示图标",
                subtitle = when {
                    // 界面必须说实话：没通知权限时图标其实没藏，别让家长以为藏了。
                    !config.showLauncherIcon && !canNotify ->
                        "通知权限没开，为避免你进不来，图标暂时保留。去系统设置允许通知后才会隐藏。"
                    config.showLauncherIcon ->
                        "图标显示在桌面上——孩子长按图标就能找到卸载入口，不建议打开。"
                    else ->
                        "图标已隐藏，桌面和搜索里都找不到本应用。请从通知栏「小鲸守护正在运行」进入家长设置。"
                },
                checked = config.showLauncherIcon,
                onCheckedChange = { onConfigChange(config.copy(showLauncherIcon = it)) },
            )
            ToggleRow(
                title = "禁止进入设置与管理工具",
                subtitle = "设置、手机管家、权限管理、应用商店、文件管理：这些入口都能用来关掉守护或装应用",
                checked = config.blockSettings,
                onCheckedChange = { onConfigChange(config.copy(blockSettings = it)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "应用规则",
            subtitle = "哪些算学习、哪些算娱乐、哪些完全不许。不单独设置的一律按娱乐计时。",
        ) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenApps) {
                Text("选择应用")
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "临时解锁",
            subtitle = if (unlockedFor > 0) {
                "已解锁，还剩 ${formatMinutesText(unlockedFor / 60_000)}"
            } else {
                "家长临时用一下平板时，解锁后一段时间内不做任何拦截。"
            },
        ) {
            if (Features.isPro) {
                SettingRow(
                    title = "每次解锁时长",
                    subtitle = "解锁期间不计时、不拦截",
                ) {
                    MinuteStepper(
                        value = config.unlockMinutes,
                        onChange = { onConfigChange(config.copy(unlockMinutes = it)) },
                        step = 5,
                        min = 5,
                        max = 60,
                    )
                }
                SettingRow(
                    title = "每天最多解锁次数",
                    subtitle = if (config.maxUnlocksPerDay <= 0) {
                        "不限（不建议：密码一旦泄露就等于没有上限）"
                    } else {
                        "今天已用 ${usage.unlocksToday} 次，共 ${usage.unlockMinutesToday} 分钟"
                    },
                ) {
                    MinuteStepper(
                        value = config.maxUnlocksPerDay,
                        onChange = { onConfigChange(config.copy(maxUnlocksPerDay = it)) },
                        step = 1,
                        min = 0,
                        max = 10,
                        unit = "次",
                    )
                }
            } else {
                SettingRow(
                    title = "免费版额度",
                    subtitle = "每天 1 次、每次 10 分钟。付费版可以自己调时长和次数。",
                ) {
                    Text(
                        "🔒",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = unlocksLeft != 0,
                    onClick = onTempUnlock,
                ) {
                    Text(
                        when {
                            unlocksLeft == 0 -> "今天次数已用完"
                            else -> "解锁 ${com.fangchenmi.guard.core.policy.UnlockPolicy.minutesPerUnlock(config, com.fangchenmi.guard.Features.limits)} 分钟"
                        },
                    )
                }
                if (unlockedFor > 0) {
                    OutlinedButton(
                        onClick = { onConfigChange(config.copy(unlockUntilMillis = 0L)) },
                    ) { Text("提前结束") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "家长密码") {
            SettingRow(
                title = "修改家长密码",
                subtitle = if (config.pin.length >= PinHasher.DEFAULT_LENGTH) {
                    "当前是 ${config.pin.length} 位数字"
                } else {
                    "当前是 ${config.pin.length} 位，建议改成 ${PinHasher.DEFAULT_LENGTH} 位"
                },
            ) {
                OutlinedButton(onClick = onChangePin) { Text("修改") }
            }
        }

        if (!Features.isPro) {
            Spacer(Modifier.height(12.dp))
            SectionCard(
                title = "升级到付费版",
                subtitle = "一次性 19.9 元，以后版本都免费更新。",
            ) {
                Text(
                    "解锁：每个应用单独限额、护眼强制休息、禁止名单不限数量、" +
                        "每天 3 次临时解锁（时长可调）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { openPurchasePage(context) }) {
                    Text("打开购买页")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "付款后回到这里，或在爱发电给作者发私信，说「已付款」，作者会发你一条专属下载链接（7 天有效）。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "装的时候直接装上去覆盖免费版，不要卸载——已经设好的规则和累计时长都会保留。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        // 设置本来就是改一下存一下，但家长要的不是技术真相，是「我点过了、它确实存下来了」。
        // 这个按钮负责给这个确定感，并顺手返回上一页。
        Button(
            onClick = {
                Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("保存并返回", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(20.dp))
        ActionCard(
            title = "解除守护",
            detail = "关闭守护并取消卸载保护。仅家长使用。",
            actionText = "解除守护",
            onAction = { confirmDeactivate = true },
            danger = true,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = buildString {
                append("小鲸守护 v${com.fangchenmi.guard.BuildConfig.VERSION_NAME}")
                if (Features.licensee.isNotEmpty()) append(" · 授权给 ${Features.licensee}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(30.dp))
    }

    if (showDeviceOwnerGuide) {
        AlertDialog(
            onDismissRequest = { showDeviceOwnerGuide = false },
            title = { Text("怎么做到「卸不掉」") },
            text = {
                Column {
                    Text(
                        "现在的「设备管理员」只能让卸载多一步：得先停用管理员。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "把本应用设成「设备所有者」之后，系统层面会直接禁止卸载——长按、设置里、第三方管家都不行。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "1. 平板用数据线连电脑，打开 USB 调试\\n" +
                            "2. 电脑上执行：",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        com.fangchenmi.guard.admin.GuardDeviceAdminReceiver.DEVICE_OWNER_COMMAND,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "只需做一次。前提是设备上没有登录任何账号（有账号系统会拒绝）；" +
                            "恢复出厂设置同样能把本应用清掉，这一点没有应用能防住。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceOwnerGuide = false }) { Text("知道了") }
            },
        )
    }

    if (confirmDeactivate) {
        AlertDialog(
            onDismissRequest = { confirmDeactivate = false },
            title = { Text("确定解除守护？") },
            text = {
                Text(
                    "解除后：计时和拦截立即停止，卸载保护被取消，孩子可以直接卸载本应用。\n" +
                        "以后想恢复，重新打开守护即可（需要再次开启无障碍）。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeactivate = false
                    onDeactivate()
                }) { Text("确定解除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeactivate = false }) { Text("算了") }
            },
        )
    }
}

/** 打开购买页（免费版专用）。链接改 [Features.PURCHASE_URL] 一处即可。 */
private fun openPurchasePage(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(Features.PURCHASE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}