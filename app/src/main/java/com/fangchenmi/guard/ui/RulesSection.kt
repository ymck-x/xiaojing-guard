package com.fangchenmi.guard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.Features
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.ui.components.MinuteStepper
import com.fangchenmi.guard.ui.components.SectionCard
import com.fangchenmi.guard.ui.components.SettingRow
import com.fangchenmi.guard.ui.components.ToggleRow
import java.util.Locale

fun formatMinuteOfDay(minute: Int): String =
    String.format(Locale.CHINA, "%02d:%02d", (minute / 60) % 24, minute % 60)

fun formatMinutesText(minutes: Long): String = when {
    minutes < 0 -> "不限"
    minutes < 60 -> "$minutes 分钟"
    minutes % 60 == 0L -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }) {
        Text("$label ${formatMinuteOfDay(minuteOfDay)}")
    }
    if (show) {
        val state = rememberTimePickerState(
            initialHour = (minuteOfDay / 60) % 24,
            initialMinute = minuteOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("选择$label") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * 60 + state.minute)
                    show = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("取消") }
            },
        )
    }
}

/** 规则编辑区。首次引导和家长设置共用同一份，避免两套界面出现不一致。 */
@Composable
fun RulesSection(
    config: GuardConfig,
    onChange: (GuardConfig) -> Unit,
) {
    SectionCard(
        title = "能用多久",
        subtitle = "只看娱乐类应用；学习和工具类不计时。",
    ) {
        SettingRow("每日可用", "用完就整台平板锁住") {
            MinuteStepper(
                value = config.totalDailyMinutes,
                onChange = { onChange(config.copy(totalDailyMinutes = it)) },
                step = 10,
                max = 720,
            )
        }
        if (Features.limits.allowBreak) {
            SettingRow("单次连续", "到点强制休息，防止一次玩太久") {
                MinuteStepper(
                    value = config.sessionMinutes,
                    onChange = { onChange(config.copy(sessionMinutes = it)) },
                    step = 5,
                    max = 180,
                )
            }
            if (config.sessionMinutes > 0) {
                SettingRow("每次休息", "休息期间整台平板暂停") {
                    MinuteStepper(
                        value = config.breakMinutes,
                        onChange = { onChange(config.copy(breakMinutes = it)) },
                        step = 5,
                        min = 1,
                        max = 60,
                        displayZero = "1 分钟",
                    )
                }
            }
        } else {
            SettingRow("单次连续", "付费版：到点强制休息，防止一次玩太久") {
                Text(
                    "🔒",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionCard(
        title = "休息时间",
        subtitle = "睡觉、上课这种时段整台平板锁住。",
    ) {
        ToggleRow(
            title = "启用停机时段",
            checked = config.bedtimeEnabled,
            onCheckedChange = { onChange(config.copy(bedtimeEnabled = it)) },
        )
        if (config.bedtimeEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeField("开始", config.bedtimeStartMinute) {
                    onChange(config.copy(bedtimeStartMinute = it))
                }
                Spacer(Modifier.height(0.dp))
                Text("  —  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimeField("结束", config.bedtimeEndMinute) {
                    onChange(config.copy(bedtimeEndMinute = it))
                }
            }
            Text(
                text = if (config.bedtimeStartMinute == config.bedtimeEndMinute) {
                    "开始和结束时间相同，等于没有开机时间"
                } else {
                    "每天 ${formatMinuteOfDay(config.bedtimeStartMinute)} 到 " +
                        "${formatMinuteOfDay(config.bedtimeEndMinute)} 不能玩"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
