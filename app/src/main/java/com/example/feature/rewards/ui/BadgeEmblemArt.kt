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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.feature.rewards.domain.BadgeIconType
import com.example.feature.rewards.domain.BadgeShapeType
import com.example.feature.rewards.domain.RewardBadge

@Composable
fun BadgeEmblemArt(
    badge: RewardBadge,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    showGlow: Boolean = true
) {
    val primaryColor = Color(badge.primaryColorHex)
    val accentColor = Color(badge.accentColorHex)
    val glowColor = Color(badge.secondaryGlowHex)

    val infiniteTransition = rememberInfiniteTransition(label = "badge_aura")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

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
            // Ambient Radial Glow
            Box(
                modifier = Modifier
                    .size(size * 1.15f)
                    .scale(pulseGlow)
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

        // Custom Geometric Canvas Emblems
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            if (!isUnlocked) {
                // Locked Metallic Emboss Ring
                drawCircle(
                    color = Color(0x33888888),
                    radius = w * 0.46f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0x22FFFFFF),
                    radius = w * 0.44f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                return@Canvas
            }

            when (badge.shapeType) {
                BadgeShapeType.HEXAGON_SHIELD -> {
                    val path = Path().apply {
                        moveTo(cx, 4.dp.toPx())
                        lineTo(w - 4.dp.toPx(), h * 0.28f)
                        lineTo(w - 6.dp.toPx(), h * 0.75f)
                        lineTo(cx, h - 3.dp.toPx())
                        lineTo(6.dp.toPx(), h * 0.75f)
                        lineTo(4.dp.toPx(), h * 0.28f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(accentColor, primaryColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        )
                    )
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.8f), glowColor.copy(alpha = 0.5f)),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                BadgeShapeType.DIAMOND_PRISM -> {
                    val path = Path().apply {
                        moveTo(cx, 3.dp.toPx())
                        lineTo(w - 4.dp.toPx(), cy)
                        lineTo(cx, h - 3.dp.toPx())
                        lineTo(4.dp.toPx(), cy)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor, primaryColor),
                            center = Offset(cx, cy),
                            radius = w * 0.5f
                        )
                    )
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, accentColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
                BadgeShapeType.STAR_BURST -> {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(primaryColor, accentColor, glowColor, primaryColor),
                            center = Offset(cx, cy)
                        ),
                        radius = w * 0.46f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, glowColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        radius = w * 0.44f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                BadgeShapeType.CROWN_BANNER -> {
                    val path = Path().apply {
                        moveTo(8.dp.toPx(), 8.dp.toPx())
                        lineTo(w - 8.dp.toPx(), 8.dp.toPx())
                        lineTo(w - 4.dp.toPx(), h * 0.72f)
                        lineTo(cx, h - 2.dp.toPx())
                        lineTo(4.dp.toPx(), h * 0.72f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor, primaryColor)
                        )
                    )
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.9f), glowColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
                BadgeShapeType.OCTAGON_CREST -> {
                    val offset = w * 0.25f
                    val path = Path().apply {
                        moveTo(offset, 3.dp.toPx())
                        lineTo(w - offset, 3.dp.toPx())
                        lineTo(w - 3.dp.toPx(), offset)
                        lineTo(w - 3.dp.toPx(), h - offset)
                        lineTo(w - offset, h - 3.dp.toPx())
                        lineTo(offset, h - 3.dp.toPx())
                        lineTo(3.dp.toPx(), h - offset)
                        lineTo(3.dp.toPx(), offset)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(accentColor, primaryColor)
                        )
                    )
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.8f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                BadgeShapeType.CIRCLE_RING -> {
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(accentColor, primaryColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        radius = w * 0.46f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, glowColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        radius = w * 0.44f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Inner Icon Container
        if (isUnlocked) {
            Icon(
                imageVector = iconVector,
                contentDescription = badge.title,
                tint = Color.White,
                modifier = Modifier.size(size * 0.48f)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.75f)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceSubtle)
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(size * 0.38f)
                )
            }
        }
    }
}
