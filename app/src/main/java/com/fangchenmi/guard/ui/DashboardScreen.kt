package com.fangchenmi.guard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.R
import com.fangchenmi.guard.Features
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.policy.UnlockPolicy
import com.fangchenmi.guard.core.usage.MeterState
import com.fangchenmi.guard.ui.components.SectionCard
import com.fangchenmi.guard.ui.components.StatusGood
import com.fangchenmi.guard.ui.components.StatusWarn
import com.fangchenmi.guard.ui.components.WaterBar
import com.fangchenmi.guard.ui.components.WhaleImage
import com.fangchenmi.guard.ui.components.WhaleSpeech
import com.fangchenmi.guard.ui.components.drawOceanBubbles
import com.fangchenmi.guard.ui.theme.GoldDeep
import com.fangchenmi.guard.ui.theme.SeaBright
import com.fangchenmi.guard.ui.theme.SeaDeep
import com.fangchenmi.guard.ui.theme.SeaMid

@Composable
fun DashboardScreen(
    config: GuardConfig,
    usage: MeterState,
    accessibilityEnabled: Boolean,
    deviceAdminActive: Boolean,
    labelOf: (String) -> String,
    onOpenSettings: () -> Unit,
    onTempUnlock: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    val totalLimit = config.totalDailyMinutes
    val usedMinutes = (usage.totalMs / 60_000).toInt()
    val remaining = if (totalLimit <= 0) -1 else (totalLimit - usedMinutes).coerceAtLeast(0)
    val unlockedFor = (config.unlockUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val unlocksLeft = UnlockPolicy.remainingUnlocks(config, usage, com.fangchenmi.guard.Features.limits)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("小鲸守护", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            StatusChip(
                text = when {
                    !config.enabled -> "未开启"
                    !accessibilityEnabled -> "缺少权限"
                    else -> "守护中"
                },
                good = config.enabled && accessibilityEnabled,
            )
        }
        Spacer(Modifier.height(14.dp))

        OceanHero(
            remainingMinutes = remaining,
            usedMinutes = usedMinutes,
            totalLimit = totalLimit,
        )

        Spacer(Modifier.height(14.dp))
        WhaleSpeech(
            text = whaleLine(config, remaining, accessibilityEnabled),
            sleeping = !config.enabled,
        )

        if (unlockedFor > 0) {
            Spacer(Modifier.height(12.dp))
            GlassBanner(
                text = "家长已临时解锁，还剩 ${formatMinutesText(unlockedFor / 60_000)}",
                tone = StatusGood,
            )
        }

        if (!Features.isPro) {
            Spacer(Modifier.height(12.dp))
            Text(
                "免费版 · 升级后可解锁：分应用限额、护眼强制休息、更多临时解锁",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        if (!accessibilityEnabled) {
            Spacer(Modifier.height(12.dp))
            ActionCard(
                title = "还差一步：开启无障碍守护",
                detail = "没有这个权限，我根本看不见你在用什么应用。设置 → 无障碍 → 小鲸守护 · 使用守护。",
                actionText = "去开启",
                onAction = onOpenAccessibility,
                warn = true,
            )
        }

        if (!config.enabled) {
            Spacer(Modifier.height(12.dp))
            ActionCard(
                title = "守护当前是关闭的",
                detail = "打开守护我才会开始计时和拦截。",
                actionText = "去开启",
                onAction = onOpenSettings,
                warn = true,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "今天的规矩", subtitle = "时间就是海水，被我一口喝光") {
            StatRow(
                "单次连续上限",
                when {
                    !Features.limits.allowBreak -> "付费版功能，免费版不强制休息"
                    config.sessionMinutes <= 0 -> "不限"
                    else -> "${config.sessionMinutes} 分钟，之后休息 ${config.breakMinutes} 分钟"
                },
            )
            StatRow("停机时段", if (config.bedtimeEnabled) "${formatMinuteOfDay(config.bedtimeStartMinute)} — ${formatMinuteOfDay(config.bedtimeEndMinute)}" else "未启用")
            if (Features.limits.allowBreak) {
                StatRow("今日强制休息", "${usage.breaksToday} 次")
            }
            StatRow(
                "临时解锁",
                "${usage.unlocksToday} 次 / ${usage.unlockMinutesToday} 分钟" +
                    if (unlocksLeft >= 0) "（今天还剩 $unlocksLeft 次）" else "",
            )
            StatRow("卸载保护", if (deviceAdminActive) "已开启" else "未开启")
        }

        val ranked = usage.perAppMs.entries
            .filter { it.value >= 60_000L }
            .sortedByDescending { it.value }
            .take(5)
        if (ranked.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "今天吃掉了最多时间") {
                ranked.forEachIndexed { index, (pkg, ms) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(22.dp),
                        )
                        Text(
                            labelOf(pkg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatMinutesText(ms / 60_000), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(50),
                onClick = onOpenSettings,
            ) { Text("家长置置") }
            OutlinedButton(
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(50),
                enabled = config.enabled && unlocksLeft != 0,
                border = BorderStroke(1.4.dp, MaterialTheme.colorScheme.primary),
                onClick = onTempUnlock,
            ) {
                Text(
                    when {
                        unlocksLeft == 0 -> "今天次数已用完"
                        else -> "临时解锁 ${config.unlockMinutes} 分钟"
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "只统计娱乐类应用；学习和工具类不计时。所有数据只存在这台置备上。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
    }
}

private fun whaleLine(config: GuardConfig, remainingMinutes: Int, accessibilityEnabled: Boolean): String = when {
    !accessibilityEnabled -> "我还没拿到「看屏幕」的许可证…先帮我把无障碍打开吧。"
    !config.enabled -> "我现在在睡觉呼呼呼。把守护打开，我才管得住你。"
    remainingMinutes < 0 -> "今天不限量，不过我也会盯着你的眼睛哦。"
    remainingMinutes <= 0 -> "时间被我吃光啦，明天见！"
    remainingMinutes <= 15 -> "只剩 $remainingMinutes 分钟了…省着点用，别一口气吃光。"
    else -> "还有 ${formatMinutesText(remainingMinutes.toLong())}，够玩一会儿，别急着扒。"
}

/** 深海主卡：左侧大数字 + 水位条，右侧小鲸立绘。 */
@Composable
private fun OceanHero(
    remainingMinutes: Int,
    usedMinutes: Int,
    totalLimit: Int,
) {
    val exhausted = totalLimit > 0 && remainingMinutes <= 0
    val transition = rememberInfiniteTransition(label = "hero")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "heroProgress",
    )
    val remainingFraction = when {
        totalLimit <= 0 -> 1f
        remainingMinutes <= 0 -> 0f
        else -> remainingMinutes.toFloat() / totalLimit
    }
    val whale = when {
        exhausted -> R.drawable.whale_sleepy
        remainingMinutes in 1..15 -> R.drawable.whale_surprised
        else -> R.drawable.whale_wave
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (exhausted) GoldDeep else SeaBright,
                            if (exhausted) Color(0xFFB9860F) else SeaMid,
                            if (exhausted) Color(0xFF6B4C00) else SeaDeep,
                        ),
                    ),
                ),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawOceanBubbles(t, Color.White, count = 8)
            }
            Row(
                modifier = Modifier.padding(start = 22.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (remainingMinutes < 0) "今日不限时长" else "今天还能玩",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            remainingMinutes < 0 -> "不限"
                            remainingMinutes <= 0 -> "吃光了"
                            else -> formatMinutesText(remainingMinutes.toLong())
                        },
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))
                    WaterBar(remainingFraction = remainingFraction)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (totalLimit <= 0) {
                            "今日已用 ${formatMinutesText(usedMinutes.toLong())}"
                        } else {
                            "已用 ${formatMinutesText(usedMinutes.toLong())} / 额度 $totalLimit 分钟"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                WhaleBadge(res = whale)
            }
        }
    }
}

/** 贴在主卡右侧的小鲸「贴纸」：白色圆底，保证深蓝背景上不糊。 */
@Composable
private fun WhaleBadge(@androidx.annotation.DrawableRes res: Int) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        WhaleImage(res = res, height = 96.dp)
    }
}

@Composable
fun StatusChip(text: String, good: Boolean) {
    val color = if (good) StatusGood else StatusWarn
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.size(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun GlassBanner(text: String, tone: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.3f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun ActionCard(
    title: String,
    detail: String,
    actionText: String,
    onAction: () -> Unit,
    warn: Boolean = false,
    danger: Boolean = false,
) {
    val tone = when {
        danger -> MaterialTheme.colorScheme.error
        warn -> StatusWarn
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.74f),
        border = BorderStroke(1.2.dp, tone.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone)
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.2.dp, tone),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tone),
                onClick = onAction,
            ) { Text(actionText) }
        }
    }
}
