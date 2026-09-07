package com.example.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette contract for FocusShield supporting both clean Daylight and soothing Late-Night Study dark modes.
 */
@Stable
data class FocusColorPalette(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val purpleGradientLight: Color,
    val emeraldSuccess: Color,
    val emeraldLight: Color,
    val coralWarning: Color,
    val coralLight: Color,
    val amberOrange: Color,
    val amberLight: Color,
    val cyanBlue: Color,
    val cyanLight: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceSubtle: Color,
    val cardBorder: Color,
    val cardBorderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnDark: Color,
    val textOnDarkMuted: Color,
    val pomodoroGreen: Color,
    val channelsAmber: Color,
    val blockedRed: Color,
    val historyPurple: Color,
    val isDark: Boolean = false
)

val LocalFocusColors = staticCompositionLocalOf { FocusColors.LightPalette }

/**
 * Centralized color tokens for FocusShield.
 * Provides static standard constants as well as dynamic theme palettes for Daylight and Late-Night Study.
 */
object FocusColors {

    // --- DAYLIGHT PALETTE ---
    val LightPalette = FocusColorPalette(
        primary = Color(0xFF7C3AED), // purple-600
        primaryDark = Color(0xFF6D28D9), // purple-700
        primaryLight = Color(0xFF8B5CF6), // purple-500
        primaryContainer = Color(0xFFF3E8FF), // purple-100
        onPrimaryContainer = Color(0xFF6B21A8), // purple-800
        gradientStart = Color(0xFF8B5CF6),
        gradientEnd = Color(0xFF6D28D9),
        purpleGradientLight = Color(0xFFC084FC),
        emeraldSuccess = Color(0xFF22C55E), // green-500
        emeraldLight = Color(0xFFDCFCE7), // green-100
        coralWarning = Color(0xFFEA580C), // orange-600 / coral
        coralLight = Color(0xFFFFEDD5), // orange-100
        amberOrange = Color(0xFFF59E0B), // amber-500
        amberLight = Color(0xFFFEF3C7), // amber-100
        cyanBlue = Color(0xFF38BDF8), // sky-400
        cyanLight = Color(0xFFE0F2FE), // sky-100
        background = Color(0xFFFBF8FF), // soft warm lavender canvas
        surface = Color(0xFFFFFFFF), // pure white
        surfaceVariant = Color(0xFFF8FAFC), // slate-50
        surfaceSubtle = Color(0xFFF1F5F9), // slate-100
        cardBorder = Color(0xFFE2E8F0), // slate-200
        cardBorderSubtle = Color(0xFFF1F5F9), // slate-100
        textPrimary = Color(0xFF1E293B), // slate-800
        textSecondary = Color(0xFF64748B), // slate-500
        textMuted = Color(0xFF94A3B8), // slate-400
        textOnDark = Color(0xFFFFFFFF),
        textOnDarkMuted = Color(0xFFF3E8FF),
        pomodoroGreen = Color(0xFF22C55E),
        channelsAmber = Color(0xFFF59E0B),
        blockedRed = Color(0xFFEF4444),
        historyPurple = Color(0xFF8B5CF6),
        isDark = false
    )

    // --- PITCH-BLACK AMOLED DARK PALETTE (Default) ---
    val DarkPalette = FocusColorPalette(
        primary = Color(0xFF8B5CF6), // vibrant purple-500
        primaryDark = Color(0xFF6D28D9), // purple-700
        primaryLight = Color(0xFFA78BFA), // lavender purple-400
        primaryContainer = Color(0xFF26183C), // deep purple dark container
        onPrimaryContainer = Color(0xFFE9D5FF),
        gradientStart = Color(0xFF8B5CF6),
        gradientEnd = Color(0xFF6D28D9),
        purpleGradientLight = Color(0xFFC084FC),
        emeraldSuccess = Color(0xFF22C55E),
        emeraldLight = Color(0xFF143823),
        coralWarning = Color(0xFFFB923C),
        coralLight = Color(0xFF381F14),
        amberOrange = Color(0xFFFBBF24), // amber/gold PRO
        amberLight = Color(0xFF36260E),
        cyanBlue = Color(0xFF38BDF8),
        cyanLight = Color(0xFF122C42),
        background = Color(0xFF000000), // pure pitch black
        surface = Color(0xFF121215), // elevated dark surface
        surfaceVariant = Color(0xFF18181D), // card background
        surfaceSubtle = Color(0xFF22222A), // chip/border background
        cardBorder = Color(0xFF2C2C36), // card border
        cardBorderSubtle = Color(0xFF1F1F26),
        textPrimary = Color(0xFFFFFFFF), // pure white text
        textSecondary = Color(0xFF9CA3AF), // slate-400
        textMuted = Color(0xFF6B7280), // slate-500
        textOnDark = Color(0xFFFFFFFF), // for high-contrast white text on purple buttons
        textOnDarkMuted = Color(0xFFE9D5FF),
        pomodoroGreen = Color(0xFF22C55E),
        channelsAmber = Color(0xFFFBBF24),
        blockedRed = Color(0xFFEF4444),
        historyPurple = Color(0xFFA78BFA),
        isDark = true
    )

    // Dynamic accessors tied to LocalFocusColors.current for instant theme switching
    val Primary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.primary

    val PrimaryDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.primaryDark

    val PrimaryLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.primaryLight

    val PrimaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.primaryContainer

    val OnPrimaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.onPrimaryContainer

    val GradientStart: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.gradientStart

    val GradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.gradientEnd

    val PurpleGradientLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.purpleGradientLight

    val EmeraldSuccess: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.emeraldSuccess

    val EmeraldLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.emeraldLight

    val CoralWarning: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.coralWarning

    val CoralLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.coralLight

    val AmberOrange: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.amberOrange

    val AmberLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.amberLight

    val CyanBlue: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.cyanBlue

    val CyanLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.cyanLight

    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.background

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.surface

    val SurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.surfaceVariant

    val SurfaceSubtle: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.surfaceSubtle

    val CardBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.cardBorder

    val CardBorderSubtle: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.cardBorderSubtle

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.textPrimary

    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.textSecondary

    val TextMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.textMuted

    val TextOnDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.textOnDark

    val TextOnDarkMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.textOnDarkMuted

    val PomodoroGreen: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.pomodoroGreen

    val ChannelsAmber: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.channelsAmber

    val BlockedRed: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.blockedRed

    val HistoryPurple: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current.historyPurple

    val ProGold = Color(0xFFFBBF24)
    val ProGoldText = Color(0xFF000000)
}

val MaterialTheme.focusColors: FocusColorPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalFocusColors.current

