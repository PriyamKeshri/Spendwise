package com.expensetracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Midnight violet: a fixed brand palette (no wallpaper-based dynamic colour), tuned for light and dark.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF241A7A),
    secondary = Color(0xFF625F80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7E3FF),
    onSecondaryContainer = Color(0xFF3F31B8),
    tertiary = Color(0xFF0E9F6E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFF5E5),
    onTertiaryContainer = Color(0xFF00382A),
    error = Color(0xFFD92D4A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239),
    background = Color(0xFFF4F4FB),
    onBackground = Color(0xFF15132B),
    surface = Color(0xFFF4F4FB),
    onSurface = Color(0xFF15132B),
    surfaceVariant = Color(0xFFE8E7F2),
    onSurfaceVariant = Color(0xFF6B6A85),
    surfaceTint = Color(0xFF5B4BDB),
    outline = Color(0xFFA9A7BE),
    outlineVariant = Color(0xFFE3E2EE),
    inverseSurface = Color(0xFF2B2940),
    inverseOnSurface = Color(0xFFF2F1FA),
    inversePrimary = Color(0xFFC9C2FF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE4E3EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F0F8),
    surfaceContainerHighest = Color(0xFFECEBF5),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFC9C2FF),
    onPrimary = Color(0xFF2A1F80),
    primaryContainer = Color(0xFF2E2A5C),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFFC6C3DE),
    onSecondary = Color(0xFF2E2C44),
    secondaryContainer = Color(0xFF2E2A5C),
    onSecondaryContainer = Color(0xFFC9C2FF),
    tertiary = Color(0xFF4ADBA3),
    onTertiary = Color(0xFF003826),
    tertiaryContainer = Color(0xFF0F3B2C),
    onTertiaryContainer = Color(0xFFA8F0D2),
    error = Color(0xFFFF8A9A),
    onError = Color(0xFF5C0A1A),
    errorContainer = Color(0xFF4A1621),
    onErrorContainer = Color(0xFFFFD9DE),
    background = Color(0xFF0D0E1A),
    onBackground = Color(0xFFF1F1F8),
    surface = Color(0xFF0D0E1A),
    onSurface = Color(0xFFF1F1F8),
    surfaceVariant = Color(0xFF262A40),
    onSurfaceVariant = Color(0xFF9A9BB5),
    surfaceTint = Color(0xFFC9C2FF),
    outline = Color(0xFF5C5F7A),
    outlineVariant = Color(0xFF2A2D44),
    inverseSurface = Color(0xFFE6E5F2),
    inverseOnSurface = Color(0xFF1B1C2C),
    inversePrimary = Color(0xFF5B4BDB),
    surfaceBright = Color(0xFF2A2D44),
    surfaceDim = Color(0xFF0D0E1A),
    surfaceContainerLowest = Color(0xFF08090F),
    surfaceContainerLow = Color(0xFF171A2B),
    surfaceContainer = Color(0xFF141626),
    surfaceContainerHigh = Color(0xFF1E2135),
    surfaceContainerHighest = Color(0xFF262A40),
)

/** Brand colours that Material's colour roles don't cover. */
@Immutable
data class AppColors(
    /** The violet summary card. */
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /** Money coming in. */
    val income: Color,
    /** Spending up / down versus last month, as a pill with its own background. */
    val upContainer: Color,
    val onUpContainer: Color,
    val downContainer: Color,
    val onDownContainer: Color,
    /** Background of progress bars and chart tracks. */
    val track: Color,
)

internal val LightAppColors = AppColors(
    hero = Color(0xFF5B4BDB),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFD9D4FF),
    income = Color(0xFF0E9F6E),
    upContainer = Color(0xFFFFE4E6),
    onUpContainer = Color(0xFF9F1239),
    downContainer = Color(0xFFD1FAE5),
    onDownContainer = Color(0xFF065F46),
    track = Color(0xFFECEBF5),
)

internal val DarkAppColors = AppColors(
    hero = Color(0xFF6C5CE7),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFCFC8FF),
    income = Color(0xFF4ADBA3),
    upContainer = Color(0xFFFFE4E6),
    onUpContainer = Color(0xFF9F1239),
    downContainer = Color(0xFFD1FAE5),
    onDownContainer = Color(0xFF065F46),
    track = Color(0xFF262A40),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
