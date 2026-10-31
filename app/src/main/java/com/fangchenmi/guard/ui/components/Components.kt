package com.fangchenmi.guard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.ui.theme.Gold
import com.fangchenmi.guard.ui.theme.GoldDeep
import com.fangchenmi.guard.ui.theme.Coral
import com.fangchenmi.guard.ui.theme.CoralDeep
import com.fangchenmi.guard.ui.theme.SeaBright
import com.fangchenmi.guard.ui.theme.SeaFoam
import com.fangchenmi.guard.ui.theme.WhaleBlue

val StatusGood = Color(0xFF1E8E6E)
val StatusWarn = CoralDeep

/** 带标题的毛玻璃分组卡片。 */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** 一行「标签 + 控件」。 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(title, subtitle) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 数值步进器：− 90 分钟 + */
@Composable
fun MinuteStepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 10,
    min: Int = 0,
    max: Int = 720,
    displayZero: String = "不限",
    unit: String = "分钟",
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        StepperButton("−", enabled = value > min) {
            onChange((value - step).coerceAtLeast(min))
        }
        Box(modifier = Modifier.width(84.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (value == 0 && displayZero.isNotEmpty()) displayZero else "$value $unit",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        StepperButton("+", enabled = value < max) {
            onChange((value + step).coerceAtMost(max))
        }
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) {
        WhaleBlue
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val fg = if (enabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * 额度进度条。底槽是半透明白，水位是渐变海蓝；用完了变金色（金币吃光）。
 */
@Composable
fun WaterBar(
    remainingFraction: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = remainingFraction.coerceIn(0f, 1f)
    val exhausted = fraction <= 0.001f
    val fill = if (exhausted) {
        Brush.horizontalGradient(listOf(Gold, GoldDeep))
    } else {
        Brush.horizontalGradient(listOf(SeaFoam, Color.White))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.24f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(fill),
        )
    }
}

/** 海面气泡色，供图标底色等使用。 */
val BubbleWhite = Color(0xFFF2FBFF)
val ReefBlue = SeaBright
