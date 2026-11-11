package com.example.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test

class FocusContrastTest {
    @Test
    fun `black and white reference pair is 21 to 1`() {
        assertEquals(21f, FocusContrast.ratio(Color.Black, Color.White), 0.001f)
        assertEquals(1f, FocusContrast.ratio(Color.White, Color.White), 0.001f)
    }

    @Test
    fun `small semantic text clears 4 point 5 on supported neutral surfaces`() {
        listOf(FocusColors.LightPalette, FocusColors.DarkPalette).forEach { palette ->
            val foregrounds = listOf(palette.textPrimary, palette.textSecondary, palette.textMuted,
                palette.emeraldText, palette.amberText, palette.coralText, palette.cyanText, palette.blockedText)
            val backgrounds = listOf(palette.background, palette.surface, palette.surfaceVariant)
            foregrounds.forEach { foreground -> backgrounds.forEach { background ->
                assertTrue("$foreground on $background", FocusContrast.ratio(foreground, background) >= 4.5f)
            } }
            assertTrue(FocusContrast.ratio(palette.controlBorder, palette.surfaceSubtle) >= 3f)
        }
    }

    @Test
    fun `hero text clears 4 point 5 even under its strongest white glow`() {
        val palette = FocusColors.LightPalette
        listOf(palette.heroGradientStart, palette.heroGradientMid, palette.heroGradientEnd).forEach { stop ->
            val rendered = Color.White.copy(alpha = 0.1869f).compositeOver(stop)
            assertTrue(FocusContrast.ratio(palette.heroOnColorMuted, rendered) >= 4.5f)
        }
    }

    @Test
    fun `derived accent controls use opaque fills and readable foregrounds in both themes`() {
        val accents = listOf(Color(0xFFFFEB3B), Color(0xFF22C55E), Color(0xFFA78BFA), Color(0xFF0284C7), Color.White, Color.Black)
        listOf(FocusColors.LightPalette, FocusColors.DarkPalette).forEach { palette -> accents.forEach { accent ->
            val fill = FocusContrast.tintedSurface(accent, palette.surface)
            assertEquals(1f, fill.alpha, 0f)
            assertTrue(FocusContrast.ratio(FocusContrast.readableAccent(accent, fill), fill) >= 4.5f)
            assertTrue(FocusContrast.ratio(FocusContrast.foregroundFor(accent), accent) >= 4.5f)
        } }
    }
}
