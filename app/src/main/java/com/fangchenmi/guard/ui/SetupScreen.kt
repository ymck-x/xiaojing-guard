package com.fangchenmi.guard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.ui.components.WhaleImage
import com.fangchenmi.guard.ui.components.StatusGood
import com.fangchenmi.guard.ui.components.StatusWarn

/**
 * 首次使用引导。目标只有一个：让家长在同一个页面里把「密码 + 权限 + 规则 + 开守护」全部做完，
 * 不需要在应用和系统设置之间反复找。
 */
@Composable
fun SetupScreen(
    config: GuardConfig,
    accessibilityEnabled: Boolean,
    deviceAdminActive: Boolean,
    onConfigChange: (GuardConfig) -> Unit,
    onSetPin: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onFinish: () -> Unit,
) {
    val pinReady = config.pin.configured
    val ready = pinReady && accessibilityEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WhaleImage(res = com.fangchenmi.guard.R.drawable.whale_wave, height = 96.dp)
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("开始守护", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "我是小鲸。三步做完，这平板就按你定的规矩来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        StepCard(
            index = 1,
            title = "设置家长密码",
            detail = "改设置、临时解锁、解除守护都需要它。忘了密码只能卸载重装。",
            done = pinReady,
            actionText = if (pinReady) "重新设置" else "设置密码",
            onAction = onSetPin,
        )
        Spacer(Modifier.height(10.dp))
        StepCard(
            index = 2,
            title = "开启无障碍守护",
            detail = "在「设置 → 无障碍 → 小鲸守护 · 使用守护」里打开。不开这个，防护无法拦截任何应用。",
            done = accessibilityEnabled,
            actionText = if (accessibilityEnabled) "已开启" else "去开启",
            onAction = onOpenAccessibility,
        )
        Spacer(Modifier.height(10.dp))
        StepCard(
            index = 3,
            title = "开启卸载保护（强烈建议）",
            detail = "不开这个，长按桌面图标就能直接卸载，前面的规矩全白设。开启后卸载前必须先停用设备管理员。",
            done = deviceAdminActive,
            actionText = if (deviceAdminActive) "已开启" else "去开启",
            onAction = onEnableDeviceAdmin,
            optional = true,
        )

        Spacer(Modifier.height(18.dp))
        Text("定规矩", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        RulesSection(config = config, onChange = onConfigChange)

        Spacer(Modifier.height(20.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = ready,
            onClick = onFinish,
        ) {
            Text(if (ready) "开始守护" else "请先完成第 1、2 步")
        }
        if (!ready) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "提示：开启无障碍后回到本应用，状态会自动刷新。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    detail: String,
    done: Boolean,
    actionText: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.74f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.9f),
        ),
        shadowElevation = 2.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) StatusGood else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (done) "✓" else "$index",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (optional) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "可选",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusWarn,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (done) {
                    Text(
                        "已完成",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusGood,
                    )
                } else {
                    OutlinedButton(onClick = onAction) { Text(actionText) }
                }
            }
        }
    }
}
