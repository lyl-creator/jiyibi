package com.jiyibi.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 记账语义色（支出红 / 收入绿，遵循国内记账习惯） */
data class MoneyColors(val out: Color, val income: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(MoneyOutLight, MoneyInLight) }

/* ---------------- 主题模式 ---------------- */

object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    val all = listOf(SYSTEM, LIGHT, DARK)

    fun label(mode: String): String = when (mode) {
        LIGHT -> "白天"
        DARK -> "夜间"
        else -> "跟随系统"
    }
}

/* ---------------- 配色构建 ---------------- */

private fun lightSchemeOf(brand: BrandTones) = lightColorScheme(
    primary = brand.primary,
    onPrimary = brand.onPrimary,
    primaryContainer = brand.primaryContainer,
    onPrimaryContainer = brand.onPrimaryContainer,
    secondaryContainer = brand.secondaryContainer,
    onSecondaryContainer = brand.onSecondaryContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface
)

private fun darkSchemeOf(brand: BrandTones) = darkColorScheme(
    primary = brand.primary,
    onPrimary = brand.onPrimary,
    primaryContainer = brand.primaryContainer,
    onPrimaryContainer = brand.onPrimaryContainer,
    secondaryContainer = brand.secondaryContainer,
    onSecondaryContainer = brand.onSecondaryContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkErrorRed,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface
)

private val AppTypography = androidx.compose.material3.Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
        )
    )
}

/**
 * 应用主题。
 *
 * @param themeMode 主题模式：system / light / dark
 * @param themeColor 主题色标识，对应 [brandPalettes] 中的 id
 */
@Composable
fun LedgerTheme(
    themeMode: String = ThemeMode.SYSTEM,
    themeColor: String = "purple",
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    val palette = findBrandPalette(themeColor)
    val scheme = if (dark) darkSchemeOf(palette.dark) else lightSchemeOf(palette.light)
    val money = if (dark) MoneyColors(MoneyOutDark, MoneyInDark)
    else MoneyColors(MoneyOutLight, MoneyInLight)

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography
    ) {
        CompositionLocalProvider(LocalMoneyColors provides money, content = content)
    }
}
