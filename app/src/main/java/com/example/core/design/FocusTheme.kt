package com.example.core.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private val LightColorScheme = lightColorScheme(
    primary = FocusColors.LightPalette.primary,
    onPrimary = FocusColors.LightPalette.textOnDark,
    primaryContainer = FocusColors.LightPalette.primaryContainer,
    onPrimaryContainer = FocusColors.LightPalette.onPrimaryContainer,
    inversePrimary = FocusColors.LightPalette.primaryDark,
    secondary = FocusColors.LightPalette.emeraldSuccess,
    onSecondary = FocusColors.LightPalette.textPrimary, // 5.33:1 on green-600
    secondaryContainer = FocusColors.LightPalette.emeraldLight,
    onSecondaryContainer = FocusColors.LightPalette.emeraldText,
    tertiary = FocusColors.LightPalette.amberText,
    onTertiary = FocusColors.LightPalette.textOnDark,
    tertiaryContainer = FocusColors.LightPalette.amberLight,
    onTertiaryContainer = FocusColors.LightPalette.amberText,
    background = FocusColors.LightPalette.background,
    onBackground = FocusColors.LightPalette.textPrimary,
    surface = FocusColors.LightPalette.surface,
    onSurface = FocusColors.LightPalette.textPrimary,
    surfaceVariant = FocusColors.LightPalette.surfaceVariant,
    onSurfaceVariant = FocusColors.LightPalette.textSecondary,
    surfaceTint = FocusColors.LightPalette.primary,
    inverseSurface = FocusColors.LightPalette.textPrimary,
    inverseOnSurface = FocusColors.LightPalette.background,
    error = FocusColors.LightPalette.blockedText,
    onError = FocusColors.LightPalette.textOnDark,
    errorContainer = FocusColors.LightPalette.blockedLight,
    onErrorContainer = FocusColors.LightPalette.blockedText,
    outline = FocusColors.LightPalette.controlBorder,
    outlineVariant = FocusColors.LightPalette.cardBorderSubtle,
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6DAF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7FF),
    surfaceContainer = FocusColors.LightPalette.surfaceVariant,
    surfaceContainerHigh = Color(0xFFF1E7FD),
    surfaceContainerHighest = FocusColors.LightPalette.surfaceSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = FocusColors.DarkPalette.primary,
    onPrimary = FocusColors.DarkPalette.background,
    primaryContainer = FocusColors.DarkPalette.primaryContainer,
    onPrimaryContainer = FocusColors.DarkPalette.onPrimaryContainer,
    inversePrimary = FocusColors.DarkPalette.primaryDark,
    secondary = FocusColors.DarkPalette.emeraldSuccess,
    onSecondary = Color(0xFF06240F), // 7.27:1 on green-500
    secondaryContainer = FocusColors.DarkPalette.emeraldLight,
    onSecondaryContainer = FocusColors.DarkPalette.emeraldText,
    tertiary = FocusColors.DarkPalette.amberOrange,
    onTertiary = FocusColors.DarkPalette.background, // 10.52:1 on amber-400
    tertiaryContainer = FocusColors.DarkPalette.amberLight,
    onTertiaryContainer = FocusColors.DarkPalette.amberText,
    background = FocusColors.DarkPalette.background,
    onBackground = FocusColors.DarkPalette.textPrimary,
    surface = FocusColors.DarkPalette.surface,
    onSurface = FocusColors.DarkPalette.textPrimary,
    surfaceVariant = FocusColors.DarkPalette.surfaceVariant,
    onSurfaceVariant = FocusColors.DarkPalette.textSecondary,
    surfaceTint = FocusColors.DarkPalette.primary,
    inverseSurface = FocusColors.LightPalette.background,
    inverseOnSurface = FocusColors.DarkPalette.background,
    error = FocusColors.DarkPalette.blockedText,
    onError = FocusColors.DarkPalette.background,
    errorContainer = FocusColors.DarkPalette.blockedLight,
    onErrorContainer = FocusColors.DarkPalette.blockedText,
    outline = FocusColors.DarkPalette.controlBorder,
    outlineVariant = FocusColors.DarkPalette.cardBorderSubtle,
    scrim = Color.Black,
    surfaceBright = FocusColors.DarkPalette.surfaceSubtle,
    surfaceDim = Color(0xFF1A1025),
    surfaceContainerLowest = Color(0xFF150C1F),
    surfaceContainerLow = Color(0xFF251834),
    surfaceContainer = FocusColors.DarkPalette.surface,
    surfaceContainerHigh = FocusColors.DarkPalette.surfaceVariant,
    surfaceContainerHighest = FocusColors.DarkPalette.surfaceSubtle
)

@Composable
fun FocusShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val palette = if (darkTheme) FocusColors.DarkPalette else FocusColors.LightPalette
    val motionPolicy = rememberFocusMotionPolicy()
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                // Let Compose draw behind the system navigation area so the bottom
                // dock can remain translucent instead of sitting above an opaque bar.
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setNavigationBarDividerColor(android.graphics.Color.TRANSPARENT)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalFocusColors provides palette,
        LocalFocusMotion provides motionPolicy
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FocusTypography,
            shapes = Shapes(
                small = FocusShapes.small,
                medium = FocusShapes.medium,
                large = FocusShapes.large,
                extraLarge = FocusShapes.extraLarge
            ),
            content = content
        )
    }
}
