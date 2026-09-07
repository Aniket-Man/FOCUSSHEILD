package com.example.feature.rewards.scratch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.ui.FocusLottieAnimation
import com.example.data.local.entity.ScratchCardEntity

/**
 * Scratch card reward dialog shown after an eligible focus session completes.
 * The user physically scratches the foil overlay to reveal the reward underneath.
 *
 * @param card The un-revealed scratch card to display.
 * @param onRevealed Called once the card is fully scratched (persists reveal state).
 * @param onDismiss Called when the user closes the dialog after revealing.
 */
@Composable
fun ScratchCardDialog(
    card: ScratchCardEntity,
    onRevealed: () -> Unit,
    onDismiss: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    var revealedNotified by remember { mutableStateOf(false) }

    val foilAlpha by animateFloatAsState(
        targetValue = if (revealed) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "foilAlpha"
    )

    if (revealed && !revealedNotified) {
        revealedNotified = true
        onRevealed()
    }

    Dialog(
        onDismissRequest = { if (revealed) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = FocusColors.Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (revealed) "Reward Unlocked!" else "Session Complete!",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = FocusColors.TextPrimary
                    )
                )
                Text(
                    text = if (revealed) "Here's what you earned" else "Scratch the card to reveal your reward",
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                )

                ScratchCardSurface(
                    card = card,
                    foilAlpha = foilAlpha,
                    onScratchProgress = { progress ->
                        if (progress >= 0.45f && !revealed) {
                            revealed = true
                        }
                    }
                )

                if (revealed) {
                    FocusLottieAnimation(
                        rawRes = R.raw.confetti,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        iterations = 1
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Collect", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "👆 Use your finger to scratch",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * The interactive scratch surface: reward content sits underneath, and a foil
 * layer on top is erased by drag gestures (BlendMode.Clear inside its own
 * graphics layer so only the foil is erased). A coarse cell grid tracks the
 * scratched percentage without reading pixels back from the GPU.
 */
@Composable
private fun ScratchCardSurface(
    card: ScratchCardEntity,
    foilAlpha: Float,
    onScratchProgress: (Float) -> Unit
) {
    val scratchPath = remember { Path() }
    // Redraw revision: bumped on every drag so the Canvas invalidates its draw lambda
    val scratchRevision = remember { mutableIntStateOf(0) }
    val scratchedCells = remember { HashSet<Int>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        // Layer 1: reward content (revealed underneath)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                    )
                )
                .border(1.5.dp, FocusColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = card.rewardEmoji, fontSize = 52.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = card.rewardTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = card.rewardMessage,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFC7D2FE),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${card.studyMinutes}m focused",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFFA5B4FC),
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        // Layer 2: foil overlay, erased by scratching (own graphics layer so
        // BlendMode.Clear only clears the foil, not the reward underneath)
        if (foilAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = foilAlpha
                        shape = RoundedCornerShape(20.dp)
                        clip = true
                    }
                    .pointerInput(card.sessionId) {
                        val cellsX = 16
                        val cellsY = 10
                        val cellCount = cellsX * cellsY
                        detectDragGestures(
                            onDragStart = { offset ->
                                scratchPath.moveTo(offset.x, offset.y)
                                scratchedCells.add(
                                    cellIndexFor(offset, cellsX, cellsY, size.width.toFloat(), size.height.toFloat())
                                )
                                scratchRevision.intValue++
                                onScratchProgress(scratchedCells.size.toFloat() / cellCount)
                            },
                            onDrag = { change, _ ->
                                scratchPath.lineTo(change.position.x, change.position.y)
                                scratchedCells.add(
                                    cellIndexFor(change.position, cellsX, cellsY, size.width.toFloat(), size.height.toFloat())
                                )
                                scratchRevision.intValue++
                                onScratchProgress(scratchedCells.size.toFloat() / cellCount)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Reading the revision state invalidates this draw on every drag
                    scratchRevision.intValue

                    // Foil background
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF9CA3AF),
                                Color(0xFFE5E7EB),
                                Color(0xFF9CA3AF),
                                Color(0xFFD1D5DB),
                                Color(0xFF9CA3AF)
                            )
                        )
                    )

                    // Decorative ring pattern
                    drawCircle(
                        color = Color(0xFFB0B5C0).copy(alpha = 0.6f),
                        radius = 130f,
                        center = Offset(size.width / 2f, size.height / 2f - 8f),
                        style = Stroke(width = 5f)
                    )
                    drawCircle(
                        color = Color(0xFFB0B5C0).copy(alpha = 0.4f),
                        radius = 165f,
                        center = Offset(size.width / 2f, size.height / 2f - 8f),
                        style = Stroke(width = 3f)
                    )

                    // Erase scratched strokes
                    drawPath(
                        path = scratchPath,
                        color = Color.Transparent,
                        style = Stroke(width = 55f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        blendMode = BlendMode.Clear
                    )
                }

                // Hint text on top of the foil (non-hit-testable, so drags pass through)
                Text(
                    text = "SCRATCH\nHERE",
                    color = Color(0xFF4B5563),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun cellIndexFor(
    position: Offset,
    cellsX: Int,
    cellsY: Int,
    width: Float,
    height: Float
): Int {
    if (width <= 0f || height <= 0f) return 0
    val col = (position.x / width * cellsX).toInt().coerceIn(0, cellsX - 1)
    val row = (position.y / height * cellsY).toInt().coerceIn(0, cellsY - 1)
    return row * cellsX + col
}
