package com.fangchenmi.guard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 取自「小鲸 / 大肥鱼」那套设定图的配色 ──────────────────────────────────
// 主色 #1E3A8A · 辅助 #3B82F6 · 点缀 #F4F6FB · 金币 #F5C542 · 阴影 #C7C9DE
val WhaleNavy = Color(0xFF1E3A8A)
val WhaleNavyDeep = Color(0xFF142B6B)
val WhaleBlue = Color(0xFF3B82F6)
val WhaleSky = Color(0xFF93C5FD)
val Paper = Color(0xFFF4F6FB)
val Gold = Color(0xFFF5C542)
val GoldDeep = Color(0xFFD9A21B)
val InkDeep = Color(0xFF0B1533)
val SilverGrey = Color(0xFFC7C9DE)

// 兼容旧名字，避免各处 import 到处改
val SeaDeep = WhaleNavyDeep
val SeaMid = WhaleNavy
val SeaBright = WhaleBlue
val SeaFoam = WhaleSky
val SeaMist = Paper
val SeaGlass = Color(0xFFDCE6FB)
val Coral = Gold
val CoralDeep = GoldDeep
val Sunset = Gold

/** 孩子那端的深海底色：比家长端更暗，像真的沉到水底。 */
val AbyssTop = Color(0xFF070D21)
val AbyssMid = Color(0xFF0B1533)
val AbyssBottom = Color(0xFF12224F)

private val LightColors = lightColorScheme(
    primary = WhaleNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FB),
    onPrimaryContainer = WhaleNavyDeep,
    secondary = Gold,
    onSecondary = Color(0xFF3A2C00),
    secondaryContainer = Color(0xFFFFEFC2),
    onSecondaryContainer = Color(0xFF5C4600),
    background = Paper,
    onBackground = InkDeep,
    surface = Color.White,
    onSurface = InkDeep,
    surfaceVariant = Color(0xFFE3E9F7),
    onSurfaceVariant = Color(0xFF5A6480),
    outline = Color(0xFFB9C2DA),
    error = Color(0xFFD64545),
)

private val DarkColors = darkColorScheme(
    primary = WhaleSky,
    onPrimary = Color(0xFF06184A),
    primaryContainer = Color(0xFF1B3A8C),
    onPrimaryContainer = Color(0xFFDCE6FB),
    secondary = Gold,
    onSecondary = Color(0xFF3A2C00),
    secondaryContainer = Color(0xFF5C4600),
    onSecondaryContainer = Color(0xFFFFEFC2),
    background = Color(0xFF0B1533),
    onBackground = Color(0xFFE4EAF8),
    surface = Color(0xFF12204A),
    onSurface = Color(0xFFE4EAF8),
    surfaceVariant = Color(0xFF1B2C5C),
    onSurfaceVariant = Color(0xFFAEB9D6),
    outline = Color(0xFF3A4A7A),
    error = Color(0xFFFF9A8F),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp),
)

/** 倒计时、额度这类数字统一用等宽字体，跳动时不会左右抖。 */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

@Composable
fun GuardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
