package com.example.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared visual language for the dashboard and planner surfaces.
 *
 * Two things drove this file: every card was re-deriving its own padding, corner radius, border and
 * shadow inline (so no two quite matched), and the type "scale" was really a pile of one-off
 * `fontSize =` overrides. Both are centralised here so a card looks like a card and a section label
 * looks like a section label no matter which screen renders it.
 */
object FocusCardStyle {
    /** One corner radius for every standard content card. */
    val CardShape = RoundedCornerShape(20.dp)

    /** Uniform internal padding for standard cards. */
    val CardPadding: Dp = 18.dp

    /** Uniform gap between stacked cards / sections on a screen. */
    val CardSpacing: Dp = 14.dp

    /** Screen horizontal inset, matched to [FocusSpacing.screenHorizontal]. */
    val ScreenInset: Dp = 20.dp

    /** Space a screen must leave at the bottom so its last card clears the floating nav dock. */
    val BottomNavClearance: Dp = 108.dp
}

/**
 * FocusShield's semantic type scale. Screens should reach for these instead of hand-tuning
 * `fontSize`/`fontWeight` per call site, so the three tiers stay visually distinct and consistent:
 *
 *  - [sectionLabel]   — small, uppercase, tracked, gray. "TODAY'S PROGRESS".
 *  - [statNumber]/[statNumberLarge] — the big numerals. "4h 23m", "0%".
 *  - [cardTitle]/[primary]/[secondary]/[caption] — supporting copy in descending emphasis.
 */
object FocusType {
    val sectionLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.0.sp,
            color = FocusColors.TextSecondary
        )

    /** Large hero stat, e.g. the main "Time Studied" figure. */
    val statNumberLarge: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.5).sp,
            color = FocusColors.TextPrimary
        )

    /** Standard stat number used inside split/secondary cards. */
    val statNumber: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.25).sp,
            color = FocusColors.TextPrimary
        )

    /** Section / card heading, e.g. "Quick Actions", "Today's Plan". */
    val cardTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = FocusColors.TextPrimary
        )

    /** Emphasised supporting text (card item titles). */
    val primary: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = FocusColors.TextPrimary
        )

    /** Default supporting text. */
    val secondary: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = FocusColors.TextSecondary
        )

    /** Smallest supporting text / metadata. */
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = FocusColors.TextMuted
        )
}

/**
 * Applies the standard card surface — corner radius, surface color, hairline border and uniform
 * inner padding — so callers stop re-specifying it four different ways. Pass [onClick] to make the
 * whole card tappable.
 */
@Composable
fun Modifier.focusCard(
    padding: Dp = FocusCardStyle.CardPadding,
    borderColor: androidx.compose.ui.graphics.Color = FocusColors.CardBorderSubtle,
    onClick: (() -> Unit)? = null
): Modifier {
    val base = this
        .fillMaxWidth()
        .clip(FocusCardStyle.CardShape)
        .background(FocusColors.Surface)
        .border(1.dp, borderColor, FocusCardStyle.CardShape)
    return (if (onClick != null) base.clickable(onClick = onClick) else base)
        .padding(padding)
}
