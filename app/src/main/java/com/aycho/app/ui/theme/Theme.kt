package com.aycho.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ===== aycho 品牌配色：蓝 + 黑 =====

// 主色调（蓝）
val Primary = Color(0xFF2F7BF6)
val PrimaryDark = Color(0xFF1B5BD0)
val PrimaryLight = Color(0xFF6FA6FF)
val Secondary = Color(0xFF4A9EFF)
val SecondaryDark = Color(0xFF2F7BF6)
val SecondaryLight = Color(0xFF8CBEFF)

// 深色主题背景（纯黑 / 近黑）
val BackgroundDark = Color(0xFF000000)
val BackgroundCard = Color(0xFF0E1116)
val BackgroundInput = Color(0xFF161A21)
val SurfaceVariant = Color(0xFF1C222C)

// 深色主题文字颜色
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF9BA6B5)
val TextHint = Color(0xFF5B6472)

// 浅色主题背景色
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundCardLight = Color(0xFFF4F6FA)
val BackgroundInputLight = Color(0xFFE9EDF4)
val SurfaceVariantLight = Color(0xFFDDE3ED)

// 浅色主题文字颜色
val TextPrimaryLight = Color(0xFF0B1220)
val TextSecondaryLight = Color(0xFF5A6473)
val TextHintLight = Color(0xFF9AA3B2)

// 状态颜色
val Success = Color(0xFF3FB950)
val Error = Color(0xFFF85149)
val Warning = Color(0xFFD29922)

// 主题颜色数据类
data class AychoColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondary: Color,
    val background: Color,
    val backgroundCard: Color,
    val backgroundInput: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    val isDark: Boolean
)

// 深色主题颜色
val DarkAychoColors = AychoColors(
    primary = Primary,
    primaryDark = PrimaryDark,
    primaryLight = PrimaryLight,
    secondary = Secondary,
    background = BackgroundDark,
    backgroundCard = BackgroundCard,
    backgroundInput = BackgroundInput,
    surfaceVariant = SurfaceVariant,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textHint = TextHint,
    success = Success,
    error = Error,
    warning = Warning,
    isDark = true
)

// 浅色主题颜色
val LightAychoColors = AychoColors(
    primary = Primary,
    primaryDark = PrimaryDark,
    primaryLight = PrimaryLight,
    secondary = Secondary,
    background = BackgroundLight,
    backgroundCard = BackgroundCardLight,
    backgroundInput = BackgroundInputLight,
    surfaceVariant = SurfaceVariantLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textHint = TextHintLight,
    success = Success,
    error = Error,
    warning = Warning,
    isDark = false
)

// CompositionLocal 用于访问当前主题颜色
val LocalAychoColors = staticCompositionLocalOf { DarkAychoColors }

// Material 3 深色配色方案
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Secondary,
    onSecondary = Color.Black,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = Color.White,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = BackgroundCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = Color.White
)

// Material 3 浅色配色方案
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Color.Black,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = Color.Black,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = BackgroundCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = Error,
    onError = Color.White
)

// 主题模式枚举
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun AychoTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme
    val aychoColors = if (isDarkTheme) DarkAychoColors else LightAychoColors

    CompositionLocalProvider(LocalAychoColors provides aychoColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

// 便捷访问当前主题颜色
object AychoTheme {
    val colors: AychoColors
        @Composable
        get() = LocalAychoColors.current
}
