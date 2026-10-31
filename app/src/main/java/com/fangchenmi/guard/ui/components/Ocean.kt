package com.fangchenmi.guard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ── 背景用的是素材包里那张小鱼图，不自己画 ─────────────────────────────────

private class Bubble(
    val x: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float,
    val wobble: Float,
)

private val BUBBLES: List<Bubble> = run {
    val random = Random(20261031)
    List(16) {
        Bubble(
            x = random.nextFloat(),
            radius = 2.5f + random.nextFloat() * 10f,
            speed = 0.22f + random.nextFloat() * 0.42f,
            phase = random.nextFloat(),
            wobble = random.nextFloat() * (2 * PI.toFloat()),
        )
    }
}

/** 画一串上升的气泡。progress 由统一的无限动画驱动，0..1 循环。 */
fun DrawScope.drawOceanBubbles(progress: Float, tint: Color, count: Int = BUBBLES.size) {
    BUBBLES.take(count).forEach { bubble ->
        val cycle = (progress * bubble.speed * 4f + bubble.phase) % 1f
        val y = size.height * (1.04f - cycle * 1.10f)
        val wobble = sin(progress * 2 * PI.toFloat() * 3f + bubble.wobble) * 14f
        val x = size.width * bubble.x + wobble
        val radius = bubble.radius * density
        val fade = (1f - cycle).coerceIn(0f, 1f)
        drawCircle(
            color = tint.copy(alpha = 0.30f * fade),
            radius = radius,
            center = Offset(x, y),
            style = Stroke(width = 1.6f * density),
        )
        drawCircle(
            color = tint.copy(alpha = 0.14f * fade),
            radius = radius * 0.34f,
            center = Offset(x - radius * 0.35f, y - radius * 0.35f),
        )
    }
}

/**
 * 家长端的「浅海」背景：奶油蓝渐变 + 上升气泡。
 * 所有元素由同一条无限时间轴推算，不额外开线程。
 */
@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val top = if (dark) Color(0xFF0B1533) else Color(0xFFF4F6FB)
    val bottom = if (dark) Color(0xFF123A6B) else Color(0xFFD6E4FA)
    val bubbleTint = Color.White

    val transition = rememberInfiniteTransition(label = "ocean")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "oceanProgress",
    )

    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.05f else 0.6f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, -size.height * 0.15f),
                    radius = size.height * 1.05f,
                ),
            )
            drawOceanBubbles(progress, bubbleTint, count = 13)
        }

        content()
    }
}

/** 毛玻璃卡片：半透明白 + 亮边，压在浅海背景上像一块浮冰。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f),
        border = BorderStroke(
            width = 1.dp,
            color = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.7f),
        ),
        shadowElevation = 0.dp,
        content = { Column(modifier = Modifier.padding(18.dp), content = content) },
    )
}
