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
    secondary = FocusColors.LightPalette.emeraldSuccess,
    onSecondary = FocusColors.LightPalette.textOnDark,
    secondaryContainer = FocusColors.LightPalette.emeraldLight,
    onSecondaryContainer = FocusColors.LightPalette.emeraldSuccess,
    tertiary = FocusColors.LightPalette.amberOrange,
    onTertiary = FocusColors.LightPalette.textOnDark,
    tertiaryContainer = FocusColors.LightPalette.amberLight,
    onTertiaryContainer = FocusColors.LightPalette.amberOrange,
    background = FocusColors.LightPalette.background,
    onBackground = FocusColors.LightPalette.textPrimary,
    surface = FocusColors.LightPalette.surface,
    onSurface = FocusColors.LightPalette.textPrimary,
    surfaceVariant = FocusColors.LightPalette.surfaceVariant,
    onSurfaceVariant = FocusColors.LightPalette.textSecondary,
    outline = FocusColors.LightPalette.cardBorder,
    outlineVariant = FocusColors.LightPalette.cardBorderSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = FocusColors.DarkPalette.primary,
    onPrimary = FocusColors.DarkPalette.background,
    primaryContainer = FocusColors.DarkPalette.primaryContainer,
    onPrimaryContainer = FocusColors.DarkPalette.onPrimaryContainer,
    secondary = FocusColors.DarkPalette.emeraldSuccess,
    onSecondary = FocusColors.DarkPalette.background,
    secondaryContainer = FocusColors.DarkPalette.emeraldLight,
    onSecondaryContainer = FocusColors.DarkPalette.emeraldSuccess,
    tertiary = FocusColors.DarkPalette.amberOrange,
    onTertiary = FocusColors.DarkPalette.background,
    tertiaryContainer = FocusColors.DarkPalette.amberLight,
    onTertiaryContainer = FocusColors.DarkPalette.amberOrange,
    background = FocusColors.DarkPalette.background,
    onBackground = FocusColors.DarkPalette.textPrimary,
    surface = FocusColors.DarkPalette.surface,
    onSurface = FocusColors.DarkPalette.textPrimary,
    surfaceVariant = FocusColors.DarkPalette.surfaceVariant,
    onSurfaceVariant = FocusColors.DarkPalette.textSecondary,
    outline = FocusColors.DarkPalette.cardBorder,
    outlineVariant = FocusColors.DarkPalette.cardBorderSubtle
)

@Composable
fun FocusShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val palette = if (darkTheme) FocusColors.DarkPalette else FocusColors.LightPalette
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
        LocalFocusColors provides palette
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
