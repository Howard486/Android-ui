package com.foldspace.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldspace.launcher.settings.PowerMode

val LocalThemeTokens: ProvidableCompositionLocal<ThemeTokens> =
    staticCompositionLocalOf { Themes.Minimal }

/** §12.3 — the effective motion budget, after Power Mode has had its say. */
val LocalMotionLevel: ProvidableCompositionLocal<MotionLevel> =
    staticCompositionLocalOf { MotionLevel.Full }

object FoldSpaceTheme {
    val tokens: ThemeTokens
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current

    val motion: MotionLevel
        @Composable @ReadOnlyComposable get() = LocalMotionLevel.current
}

/**
 * §12.3 — Battery Saver downgrades motion regardless of the theme's own
 * setting. Themes can ask for less motion, never for more than the power mode
 * allows.
 */
fun effectiveMotion(tokens: ThemeTokens, powerMode: PowerMode, systemPowerSave: Boolean): MotionLevel =
    when {
        systemPowerSave || powerMode == PowerMode.BatterySaver -> MotionLevel.None
        powerMode == PowerMode.Performance -> tokens.motion
        // Smart: never full-throttle by default; the launcher is always on screen.
        tokens.motion == MotionLevel.Full -> MotionLevel.Reduced
        else -> tokens.motion
    }

@Composable
fun FoldSpaceTheme(
    tokens: ThemeTokens,
    motionLevel: MotionLevel,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = tokens.accent,
        onPrimary = tokens.scrim,
        secondary = tokens.accentSecondary,
        background = tokens.scrim,
        onBackground = tokens.textPrimary,
        surface = tokens.surface,
        onSurface = tokens.textPrimary,
        surfaceVariant = tokens.surfaceElevated,
        onSurfaceVariant = tokens.textSecondary,
        outline = tokens.outline,
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(tokens.cardRadius / 1.5f),
        large = RoundedCornerShape(tokens.cardRadius),
        extraLarge = RoundedCornerShape(tokens.cardRadius * 1.4f),
    )

    CompositionLocalProvider(
        LocalThemeTokens provides tokens,
        LocalMotionLevel provides motionLevel,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FoldSpaceTypography,
            shapes = shapes,
            content = content,
        )
    }
}

private val FoldSpaceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)
