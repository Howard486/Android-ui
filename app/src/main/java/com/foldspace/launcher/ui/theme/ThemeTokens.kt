package com.foldspace.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.settings.ThemeId

/** §15 — motion budget, driven by Power Mode rather than by the theme alone. */
enum class MotionLevel { None, Reduced, Full }

/**
 * §15 Theme tokens. A theme is a *UI token set plus layout preset*, not a
 * wallpaper — so everything the launcher draws reads from here.
 */
data class ThemeTokens(
    val id: ThemeId,
    val scrim: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val accent: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val cardRadius: Dp,
    val cardOpacity: Float,
    /** §11.4 / §15.2 — heavy blur is a battery cost, so the theme opts in. */
    val usesBlur: Boolean,
    val iconCornerRadius: Dp,
    val motion: MotionLevel,
) {
    fun surfaceAlpha(): Color = surface.copy(alpha = cardOpacity)
}

object Themes {

    val Minimal = ThemeTokens(
        id = ThemeId.Minimal,
        scrim = Color(0x99000000),
        surface = Color(0xFF14171D),
        surfaceElevated = Color(0xFF1C2028),
        outline = Color(0x14FFFFFF),
        accent = Color(0xFF7FA8FF),
        accentSecondary = Color(0xFFB79BFF),
        textPrimary = Color(0xFFF3F5F9),
        textSecondary = Color(0xFFAAB2C0),
        textMuted = Color(0xFF71798A),
        cardRadius = 22.dp,
        cardOpacity = 0.72f,
        usesBlur = false,
        iconCornerRadius = 16.dp,
        motion = MotionLevel.Full,
    )

    val Cyber = ThemeTokens(
        id = ThemeId.Cyber,
        scrim = Color(0xAA05070B),
        surface = Color(0xFF0C1119),
        surfaceElevated = Color(0xFF122032),
        outline = Color(0x3327E8C3),
        accent = Color(0xFF27E8C3),
        accentSecondary = Color(0xFFFF6BD6),
        textPrimary = Color(0xFFEAFBF7),
        textSecondary = Color(0xFF8FB3AE),
        textMuted = Color(0xFF5F7B78),
        cardRadius = 10.dp,
        cardOpacity = 0.80f,
        usesBlur = false,
        iconCornerRadius = 8.dp,
        motion = MotionLevel.Full,
    )

    val Executive = ThemeTokens(
        id = ThemeId.Executive,
        scrim = Color(0xB30A0C10),
        surface = Color(0xFF171A20),
        surfaceElevated = Color(0xFF20242C),
        outline = Color(0x1FD8C9A8),
        accent = Color(0xFFD8C9A8),
        accentSecondary = Color(0xFF8FA0BF),
        textPrimary = Color(0xFFF6F4EF),
        textSecondary = Color(0xFFB7B2A7),
        textMuted = Color(0xFF7E7A72),
        cardRadius = 6.dp,
        cardOpacity = 0.86f,
        usesBlur = false,
        iconCornerRadius = 12.dp,
        motion = MotionLevel.Reduced,
    )

    val AiDesk = ThemeTokens(
        id = ThemeId.AiDesk,
        scrim = Color(0x99070912),
        surface = Color(0xFF12141F),
        surfaceElevated = Color(0xFF1B1E2E),
        accent = Color(0xFF8C7BFF),
        accentSecondary = Color(0xFF4FD1FF),
        outline = Color(0x268C7BFF),
        textPrimary = Color(0xFFF2F1FA),
        textSecondary = Color(0xFFA9A6C4),
        textMuted = Color(0xFF6E6B8A),
        cardRadius = 26.dp,
        cardOpacity = 0.70f,
        usesBlur = true,
        iconCornerRadius = 18.dp,
        motion = MotionLevel.Full,
    )

    /**
     * §15.1 "Battery / True Black". Fully opaque black surfaces so an OLED
     * panel can actually switch those pixels off — a translucent card would
     * defeat the entire point of the preset.
     */
    val TrueBlack = ThemeTokens(
        id = ThemeId.TrueBlack,
        scrim = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceElevated = Color(0xFF0A0A0A),
        outline = Color(0x1AFFFFFF),
        accent = Color(0xFFBFC6D2),
        accentSecondary = Color(0xFF8A93A3),
        textPrimary = Color(0xFFE9ECF1),
        textSecondary = Color(0xFF9AA1AD),
        textMuted = Color(0xFF636A76),
        cardRadius = 14.dp,
        cardOpacity = 1f,
        usesBlur = false,
        iconCornerRadius = 14.dp,
        motion = MotionLevel.None,
    )

    fun of(id: ThemeId): ThemeTokens = when (id) {
        ThemeId.Minimal -> Minimal
        ThemeId.Cyber -> Cyber
        ThemeId.Executive -> Executive
        ThemeId.AiDesk -> AiDesk
        ThemeId.TrueBlack -> TrueBlack
    }
}
