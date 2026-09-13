package com.jiyibi.ledger.ui.theme

import androidx.compose.ui.graphics.Color

/* ---------------- 品牌配色方案 ---------------- */

/** 一套主题的品牌色位（不含中性表面色，中性色在各方案间共用） */
data class BrandTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color
)

/**
 * 可选主题色。
 * @param id 持久化标识
 * @param label 设置页展示名
 * @param swatch 设置页色块
 */
data class BrandPalette(
    val id: String,
    val label: String,
    val swatch: Color,
    val light: BrandTones,
    val dark: BrandTones
)

/** 全部可选主题色 */
val brandPalettes: List<BrandPalette> = listOf(
    BrandPalette(
        id = "purple",
        label = "薰衣草",
        swatch = Color(0xFF6750A4),
        light = BrandTones(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B)
        ),
        dark = BrandTones(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8)
        )
    ),
    BrandPalette(
        id = "blue",
        label = "晴空蓝",
        swatch = Color(0xFF0B57D0),
        light = BrandTones(
            primary = Color(0xFF0B57D0),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD3E3FD),
            onPrimaryContainer = Color(0xFF041E49),
            secondaryContainer = Color(0xFFD3E3FD),
            onSecondaryContainer = Color(0xFF041E49)
        ),
        dark = BrandTones(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF062E6F),
            primaryContainer = Color(0xFF0842A0),
            onPrimaryContainer = Color(0xFFD3E3FD),
            secondaryContainer = Color(0xFF0842A0),
            onSecondaryContainer = Color(0xFFD3E3FD)
        )
    ),
    BrandPalette(
        id = "green",
        label = "青草绿",
        swatch = Color(0xFF146C2E),
        light = BrandTones(
            primary = Color(0xFF146C2E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC4EED0),
            onPrimaryContainer = Color(0xFF072711),
            secondaryContainer = Color(0xFFC4EED0),
            onSecondaryContainer = Color(0xFF072711)
        ),
        dark = BrandTones(
            primary = Color(0xFF6DD58C),
            onPrimary = Color(0xFF0A3818),
            primaryContainer = Color(0xFF0F5223),
            onPrimaryContainer = Color(0xFFC4EED0),
            secondaryContainer = Color(0xFF0F5223),
            onSecondaryContainer = Color(0xFFC4EED0)
        )
    ),
    BrandPalette(
        id = "orange",
        label = "暖阳橙",
        swatch = Color(0xFF8F4C00),
        light = BrandTones(
            primary = Color(0xFF8F4C00),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDCC2),
            onPrimaryContainer = Color(0xFF2E1500),
            secondaryContainer = Color(0xFFFFDCC2),
            onSecondaryContainer = Color(0xFF2E1500)
        ),
        dark = BrandTones(
            primary = Color(0xFFFFB77D),
            onPrimary = Color(0xFF4D2600),
            primaryContainer = Color(0xFF6E3900),
            onPrimaryContainer = Color(0xFFFFDCC2),
            secondaryContainer = Color(0xFF6E3900),
            onSecondaryContainer = Color(0xFFFFDCC2)
        )
    ),
    BrandPalette(
        id = "pink",
        label = "蔷薇粉",
        swatch = Color(0xFFB3265E),
        light = BrandTones(
            primary = Color(0xFFB3265E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9E2),
            onPrimaryContainer = Color(0xFF3E001D),
            secondaryContainer = Color(0xFFFFD9E2),
            onSecondaryContainer = Color(0xFF3E001D)
        ),
        dark = BrandTones(
            primary = Color(0xFFFFB0C8),
            onPrimary = Color(0xFF660033),
            primaryContainer = Color(0xFF8E0049),
            onPrimaryContainer = Color(0xFFFFD9E2),
            secondaryContainer = Color(0xFF8E0049),
            onSecondaryContainer = Color(0xFFFFD9E2)
        )
    ),
    BrandPalette(
        id = "teal",
        label = "静谧青",
        swatch = Color(0xFF00696D),
        light = BrandTones(
            primary = Color(0xFF00696D),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF9CF1F5),
            onPrimaryContainer = Color(0xFF002021),
            secondaryContainer = Color(0xFF9CF1F5),
            onSecondaryContainer = Color(0xFF002021)
        ),
        dark = BrandTones(
            primary = Color(0xFF80D4D8),
            onPrimary = Color(0xFF003739),
            primaryContainer = Color(0xFF004F52),
            onPrimaryContainer = Color(0xFF9CF1F5),
            secondaryContainer = Color(0xFF004F52),
            onSecondaryContainer = Color(0xFF9CF1F5)
        )
    ),
    BrandPalette(
        id = "brown",
        label = "咖啡棕",
        swatch = Color(0xFF7A5732),
        light = BrandTones(
            primary = Color(0xFF7A5732),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFBDDBA),
            onPrimaryContainer = Color(0xFF2B1700),
            secondaryContainer = Color(0xFFFBDDBA),
            onSecondaryContainer = Color(0xFF2B1700)
        ),
        dark = BrandTones(
            primary = Color(0xFFE9BF94),
            onPrimary = Color(0xFF452B09),
            primaryContainer = Color(0xFF5F411D),
            onPrimaryContainer = Color(0xFFFBDDBA),
            secondaryContainer = Color(0xFF5F411D),
            onSecondaryContainer = Color(0xFFFBDDBA)
        )
    )
)

/** 按 id 查找主题色，找不到时回退到第一套 */
fun findBrandPalette(id: String): BrandPalette =
    brandPalettes.firstOrNull { it.id == id } ?: brandPalettes.first()

/* ---------------- 中性表面色 · 浅色（所有主题共用） ---------------- */

val Surface = Color(0xFFFEF7FF)
val OnSurface = Color(0xFF1D1B20)
val SurfaceVariant = Color(0xFFE7E0EC)
val OnSurfaceVariant = Color(0xFF49454F)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF7F2FA)
val SurfaceContainer = Color(0xFFF3EDF7)
val SurfaceContainerHigh = Color(0xFFECE6F0)
val SurfaceContainerHighest = Color(0xFFE6E0E9)
val Outline = Color(0xFF79747E)
val OutlineVariant = Color(0xFFCAC4D0)
val ErrorRed = Color(0xFFB3261E)
val InverseSurface = Color(0xFF322F35)
val InverseOnSurface = Color(0xFFF5EFF7)

val MoneyOutLight = Color(0xFFB3261E)
val MoneyInLight = Color(0xFF146C2E)

/* ---------------- 中性表面色 · 深色 ---------------- */

val DarkSurface = Color(0xFF141218)
val DarkOnSurface = Color(0xFFE6E0E9)
val DarkSurfaceVariant = Color(0xFF49454F)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkSurfaceContainerLowest = Color(0xFF0F0D13)
val DarkSurfaceContainerLow = Color(0xFF1D1B20)
val DarkSurfaceContainer = Color(0xFF211F26)
val DarkSurfaceContainerHigh = Color(0xFF2B2930)
val DarkSurfaceContainerHighest = Color(0xFF36343B)
val DarkOutline = Color(0xFF938F99)
val DarkOutlineVariant = Color(0xFF49454F)
val DarkErrorRed = Color(0xFFF2B8B5)
val DarkInverseSurface = Color(0xFFE6E0E9)
val DarkInverseOnSurface = Color(0xFF322F35)

val MoneyOutDark = Color(0xFFF2B8B5)
val MoneyInDark = Color(0xFF7EDFA0)
