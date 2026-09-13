package com.example.feature.rewards.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.feature.rewards.domain.BadgeIconType
import com.example.feature.rewards.domain.BadgeShapeType
import com.example.feature.rewards.domain.RewardBadge

/**
 * The emblem at the centre of a badge: a distinct container silhouette per [BadgeShapeType] with a
 * solid gradient face, a shading wash, a specular highlight, a rim bevel and a soft drop shadow —
 * so an earned badge reads as a struck metal medallion rather than a Material icon in a box.
 *
 * @param pulse animate the halo. Reserve this for a badge the student has just earned; leaving it
 *   off means no infinite animation is composed at all, which keeps a screen full of badges calm.
 */
@Composable
fun BadgeEmblemArt(
    badge: RewardBadge,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    showGlow: Boolean = true,
    pulse: Boolean = false
) {
    val primaryColor = Color(badge.primaryColorHex)
    val glowColor = Color(badge.secondaryGlowHex)

    val iconVector: ImageVector = when (badge.iconType) {
        BadgeIconType.TROPHY -> Icons.Rounded.EmojiEvents
        BadgeIconType.MEDAL -> Icons.Rounded.MilitaryTech
        BadgeIconType.STAR -> Icons.Rounded.Star
        BadgeIconType.CROWN -> Icons.Rounded.WorkspacePremium
        BadgeIconType.DIAMOND -> Icons.Rounded.Diamond
        BadgeIconType.SHIELD -> Icons.Rounded.Shield
        BadgeIconType.FLAME -> Icons.Rounded.Whatshot
        BadgeIconType.LIGHTNING -> Icons.Rounded.FlashOn
        BadgeIconType.ROCKET -> Icons.Rounded.RocketLaunch
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked && showGlow) {
            GlowHalo(
                glowColor = glowColor,
                primaryColor = primaryColor,
                size = size,
                pulsing = pulse
            )
        }

        Canvas(modifier = Modifier.size(size)) {
            // `this.size` — the DrawScope's pixel size, not the `size: Dp` parameter above.
            val w = this.size.width
            val h = this.size.height
            if (isUnlocked) {
                drawEarnedEmblem(badge, w, h)
            } else {
                drawLockedMedallion(w, h)
            }
        }

        if (isUnlocked) {
            Icon(
                imageVector = iconVector,
                contentDescription = badge.title,
                tint = Color.White,
                modifier = Modifier.size(size * 0.44f)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.72f)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceSubtle)
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(size * 0.34f)
                )
            }
        }
    }
}

/**
 * Ambient halo behind an earned emblem. Split out so the infinite transition is only ever composed
 * when [pulsing] is true — a non-pulsing badge costs no animation frames.
 */
@Composable
private fun GlowHalo(
    glowColor: Color,
    primaryColor: Color,
    size: Dp,
    pulsing: Boolean
) {
    val haloModifier = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "badge_aura")
        val scale by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aura_pulse"
        )
        Modifier.scale(scale)
    } else {
        Modifier
    }

    Box(
        modifier = haloModifier
            .size(size * 1.15f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.35f),
                        primaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Silhouette for a shape type, inset by [pad] so the bevel stroke and shadow stay inside the canvas.
 */
private fun emblemOutline(shape: BadgeShapeType, w: Float, h: Float, pad: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    return Path().apply {
        when (shape) {
            BadgeShapeType.CIRCLE_RING, BadgeShapeType.STAR_BURST ->
                addOval(Rect(Offset(pad, pad), Size(w - pad * 2f, h - pad * 2f)))

            BadgeShapeType.HEXAGON_SHIELD -> {
                moveTo(cx, pad)
                lineTo(w - pad, h * 0.28f)
                lineTo(w - pad * 1.6f, h * 0.76f)
                lineTo(cx, h - pad * 0.7f)
                lineTo(pad * 1.6f, h * 0.76f)
                lineTo(pad, h * 0.28f)
                close()
            }

            BadgeShapeType.DIAMOND_PRISM -> {
                moveTo(cx, pad * 0.7f)
                lineTo(w - pad, cy)
                lineTo(cx, h - pad * 0.7f)
                lineTo(pad, cy)
                close()
            }

            BadgeShapeType.CROWN_BANNER -> {
                moveTo(pad * 2.4f, pad * 2.2f)
                lineTo(w - pad * 2.4f, pad * 2.2f)
                lineTo(w - pad, h * 0.72f)
                lineTo(cx, h - pad * 0.5f)
                lineTo(pad, h * 0.72f)
                close()
            }

            BadgeShapeType.OCTAGON_CREST -> {
                val o = w * 0.25f
                moveTo(o, pad * 0.8f)
                lineTo(w - o, pad * 0.8f)
                lineTo(w - pad * 0.8f, o)
                lineTo(w - pad * 0.8f, h - o)
                lineTo(w - o, h - pad * 0.8f)
                lineTo(o, h - pad * 0.8f)
                lineTo(pad * 0.8f, h - o)
                lineTo(pad * 0.8f, o)
                close()
            }
        }
    }
}

/**
 * An earned emblem, painted in five passes: offset shadow, gradient face, curved-face shading,
 * upper-left specular highlight, then the rim bevel.
 */
private fun DrawScope.drawEarnedEmblem(badge: RewardBadge, w: Float, h: Float) {
    val pad = 3.dp.toPx()
    val primary = Color(badge.primaryColorHex)
    val accent = Color(badge.accentColorHex)
    val glow = Color(badge.secondaryGlowHex)
    val outline = emblemOutline(badge.shapeType, w, h, pad)

    // 1. Drop shadow. Layered offsets approximate a blur without a framework blur filter, which
    //    would mean an Android Paint per emblem.
    val shadowStep = 1.1.dp.toPx()
    for (layer in 3 downTo 1) {
        translate(top = layer * shadowStep) {
            drawPath(outline, color = Color.Black.copy(alpha = 0.07f))
        }
    }
    translate(top = shadowStep) {
        drawPath(outline, color = Color.Black.copy(alpha = 0.26f))
    }

    // 2. Face.
    drawPath(
        path = outline,
        brush = Brush.linearGradient(
            colors = listOf(accent, primary),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
    )

    // 3. Shading wash, darkening the lower half so the face reads as curved metal.
    clipPath(outline) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                startY = h * 0.34f,
                endY = h
            ),
            topLeft = Offset.Zero,
            size = Size(w, h)
        )
    }

    // 4. Specular highlight, upper-left, clipped to the face so it never spills past the rim.
    clipPath(outline) {
        val highlightCenter = Offset(w * 0.34f, h * 0.26f)
        val highlightRadius = w * 0.44f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.50f), Color.Transparent),
                center = highlightCenter,
                radius = highlightRadius
            ),
            radius = highlightRadius,
            center = highlightCenter
        )
    }

    // 5. Rim bevel.
    drawPath(
        path = outline,
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.90f), glow, primary.copy(alpha = 0.6f)),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    // A soft well behind the glyph keeps it legible on the lighter faces; skipped at compact sizes
    // where it would just muddy the shape.
    if (w >= 56.dp.toPx()) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.14f),
            radius = w * 0.29f,
            center = Offset(w / 2f, h / 2f)
        )
    }
}

/**
 * A locked badge shows a recessed dark medallion rather than a filled emblem, so it reads as
 * "not yet earned" without competing with the badges the student actually has.
 */
private fun DrawScope.drawLockedMedallion(w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    drawCircle(color = Color(0x33888888), radius = w * 0.46f, center = Offset(cx, cy))
    drawCircle(
        color = Color(0x22FFFFFF),
        radius = w * 0.44f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )
}
