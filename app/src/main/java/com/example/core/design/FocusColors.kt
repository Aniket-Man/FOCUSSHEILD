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
        primaryContainer = Color(0xFFE9D5FF), // purple light container
        onPrimaryContainer = Color(0xFF5B21B6), // purple-800
        gradientStart = Color(0xFF8B5CF6),
        gradientEnd = Color(0xFF6D28D9),
        purpleGradientLight = Color(0xFFC084FC),
        emeraldSuccess = Color(0xFF16A34A), // green-600
        emeraldLight = Color(0xFFDCFCE7),
        coralWarning = Color(0xFFEA580C),
        coralLight = Color(0xFFFFEDD5),
        amberOrange = Color(0xFFD97706),
        amberLight = Color(0xFFFEF3C7),
        cyanBlue = Color(0xFF0284C7),
        cyanLight = Color(0xFFE0F2FE),
        background = Color(0xFFF3ECFF), // Requested light mode background
        surface = Color(0xFFFFFFFF), // pure white card surface
        surfaceVariant = Color(0xFFF8F2FF),
        surfaceSubtle = Color(0xFFECE0FD),
        cardBorder = Color(0xFFE0D0F7),
        cardBorderSubtle = Color(0xFFEBE0FA),
        textPrimary = Color(0xFF1E152A), // rich dark slate purple
        textSecondary = Color(0xFF5C4B75),
        textMuted = Color(0xFF8E7AA8),
        textOnDark = Color(0xFFFFFFFF),
        textOnDarkMuted = Color(0xFFF3E8FF),
        pomodoroGreen = Color(0xFF16A34A),
        channelsAmber = Color(0xFFD97706),
        blockedRed = Color(0xFFDC2626),
        historyPurple = Color(0xFF7C3AED),
        isDark = false
    )

    // --- DARK PALETTE ---
    val DarkPalette = FocusColorPalette(
        primary = Color(0xFFA78BFA), // vibrant purple-400
        primaryDark = Color(0xFF7C3AED), // purple-600
        primaryLight = Color(0xFFC084FC), // purple-300
        primaryContainer = Color(0xFF352048), // deep purple dark container
        onPrimaryContainer = Color(0xFFF3E8FF),
        gradientStart = Color(0xFF9333EA),
        gradientEnd = Color(0xFF6D28D9),
        purpleGradientLight = Color(0xFFC084FC),
        emeraldSuccess = Color(0xFF22C55E),
        emeraldLight = Color(0xFF143823),
        coralWarning = Color(0xFFFB923C),
        coralLight = Color(0xFF381F14),
        amberOrange = Color(0xFFFBBF24),
        amberLight = Color(0xFF36260E),
        cyanBlue = Color(0xFF38BDF8),
        cyanLight = Color(0xFF122C42),
        background = Color(0xFF21152F), // Requested dark mode background
        surface = Color(0xFF2B1D3D), // elevated dark surface
        surfaceVariant = Color(0xFF322247), // card background
        surfaceSubtle = Color(0xFF3D2A54), // chip/border background
        cardBorder = Color(0xFF4C3668), // card border
        cardBorderSubtle = Color(0xFF3B2754),
        textPrimary = Color(0xFFFFFFFF), // pure white text
        textSecondary = Color(0xFFD4C4EA), // soft lavender
        textMuted = Color(0xFFA592BF),
        textOnDark = Color(0xFFFFFFFF),
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

    // Unified Onboarding Palette (Pages 2–5): #6D28D9 and #1E1235
    val OnboardingPurple = Color(0xFF6D28D9)
    val OnboardingLavender = Color(0xFF1E1235)

    // Universal Toggle Button Green (#31C831)
    val ToggleGreen = Color(0xFF31C831)

    val SwitchColors: androidx.compose.material3.SwitchColors
        @Composable
        get() {
            val isDark = LocalFocusColors.current.isDark
            return androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ToggleGreen,
                uncheckedThumbColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF94A3B8),
                uncheckedTrackColor = if (isDark) Color(0xFF24242A) else Color(0xFFE2E8F0),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        }
}

val MaterialTheme.focusColors: FocusColorPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalFocusColors.current

