package com.fangchenmi.guard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var confirmDeactivate by remember { mutableStateOf(false) }
    val unlockedFor = (config.unlockUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val unlocksLeft = com.fangchenmi.guard.core.policy.UnlockPolicy.remainingUnlocks(config, usage)

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
            Spacer(Modifier.width(64.dp))
        }
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
                subtitle = if (deviceAdminActive) "已开启，孩子删不掉" else "未开启，孩子可以直接卸载",
            ) {
                OutlinedButton(onClick = onEnableDeviceAdmin) {
                    Text(if (deviceAdminActive) "已开启" else "去开启")
                }
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = unlocksLeft != 0,
                    onClick = onTempUnlock,
                ) {
                    Text(
                        when {
                            unlocksLeft == 0 -> "今天次数已用完"
                            else -> "解锁 ${config.unlockMinutes} 分钟"
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

        Spacer(Modifier.height(12.dp))
        ActionCard(
            title = "解除守护",
            detail = "关闭守护并取消卸载保护。仅家长使用。",
            actionText = "解除守护",
            onAction = { confirmDeactivate = true },
            danger = true,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = "小鲸守护 v${com.fangchenmi.guard.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(30.dp))
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
