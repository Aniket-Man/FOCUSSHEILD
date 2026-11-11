package com.example.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Contrast utilities for user/content-derived accents, such as reward badge colors. */
object FocusContrast {
    fun ratio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.compositeOver(background).luminance()
        val backgroundLuminance = background.luminance()
        return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
    }

    /** A solid fill always has a black or white foreground that clears 4.5:1. */
    fun foregroundFor(background: Color): Color =
        if (ratio(Color.Black, background) >= ratio(Color.White, background)) Color.Black else Color.White

    /** Keep an accent's hue where possible, moving its tone only as far as text contrast needs. */
    fun readableAccent(accent: Color, background: Color, minimumRatio: Float = 4.5f): Color {
        val opaqueAccent = accent.copy(alpha = 1f)
        if (ratio(opaqueAccent, background) >= minimumRatio) return opaqueAccent
        val destination = foregroundFor(background)
        for (step in 1..20) {
            val candidate = lerp(opaqueAccent, destination, step / 20f)
            if (ratio(candidate, background) >= minimumRatio) return candidate
        }
        return destination
    }

    /** Opaque containers make derived-accent contrast independent of decorative layers beneath. */
    fun tintedSurface(accent: Color, surface: Color, alpha: Float = 0.12f): Color =
        accent.copy(alpha = alpha.coerceIn(0f, 1f)).compositeOver(surface)
}
