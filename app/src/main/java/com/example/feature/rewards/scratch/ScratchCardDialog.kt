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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
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
 * @param onRevealed Called once scratching passes the reveal threshold (persists reveal state).
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
                        if (progress >= DEFAULT_REVEAL_THRESHOLD && !revealed) {
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
 * layer on top is erased by drag gestures.
 *
 * `BlendMode.Clear` only erases *within its own compositing layer*, so the foil
 * Box is given `CompositingStrategy.Offscreen`. Without that layer the clear
 * blends against the window behind the dialog and the foil is left untouched —
 * the reward then only ever appeared when [onScratchProgress] crossed the
 * threshold and the whole layer faded out. With the layer in place the eraser
 * removes foil exactly where the finger travels, so the reward shows through
 * progressively from the first touch.
 *
 * A coarse cell grid tracks the scratched percentage without reading pixels
 * back from the GPU.
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

    // The hint is drawn into the foil canvas rather than as a sibling, so scratching
    // erases it along with the foil instead of leaving it floating over the reward.
    val textMeasurer = rememberTextMeasurer()
    val hintLayout = remember(textMeasurer) {
        textMeasurer.measure(
            text = AnnotatedString("SCRATCH\nHERE"),
            style = TextStyle(
                color = Color(0xFF4B5563),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )
        )
    }

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

        // Layer 2: foil overlay, erased by scratching. The offscreen compositing
        // strategy is what scopes BlendMode.Clear to the foil alone.
        if (foilAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = foilAlpha
                        shape = RoundedCornerShape(20.dp)
                        clip = true
                    }
                    .pointerInput(card.sessionId) {
                        val cellsX = 16
                        val cellsY = 10
                        val cellCount = cellsX * cellsY
                        var previous = Offset.Unspecified

                        fun report() = onScratchProgress(scratchedCells.size.toFloat() / cellCount)

                        detectDragGestures(
                            onDragStart = { offset ->
                                previous = offset
                                scratchPath.moveTo(offset.x, offset.y)
                                // A contour with no line has zero area, so the stroke pass
                                // cannot render it. An oval gives the touch-down its own
                                // filled dot, which is what makes a tap reveal foil.
                                scratchPath.addOval(
                                    Rect(center = offset, radius = SCRATCH_RADIUS_PX)
                                )
                                scratchedCells.add(
                                    cellIndexFor(offset, cellsX, cellsY, size.width.toFloat(), size.height.toFloat())
                                )
                                scratchRevision.intValue++
                                report()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val position = change.position
                                scratchPath.lineTo(position.x, position.y)
                                // Sample along the segment rather than only at its end: a fast
                                // flick covers many cells between two pointer events, and
                                // counting just the endpoint made the progress ratio lag well
                                // behind what the user had actually scratched away.
                                markCellsAlong(
                                    from = previous,
                                    to = position,
                                    cellsX = cellsX,
                                    cellsY = cellsY,
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                    out = scratchedCells
                                )
                                previous = position
                                scratchRevision.intValue++
                                report()
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

                    // Hint sits on the foil, so it is scratched away with it
                    drawText(
                        textLayoutResult = hintLayout,
                        topLeft = Offset(
                            (size.width - hintLayout.size.width) / 2f,
                            (size.height - hintLayout.size.height) / 2f
                        )
                    )

                    // Erase scratched strokes. The fill pass is a no-op for the line
                    // contours (zero area) and fills the touch-down ovals.
                    drawPath(
                        path = scratchPath,
                        color = Color.Transparent,
                        style = Stroke(
                            width = SCRATCH_RADIUS_PX * 2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        ),
                        blendMode = BlendMode.Clear
                    )
                    drawPath(
                        path = scratchPath,
                        color = Color.Transparent,
                        blendMode = BlendMode.Clear
                    )
                }
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

/**
 * Adds every cell the segment [from] → [to] passes through, stepping at half a cell so a
 * fast drag cannot skip over the cells it visibly scratched.
 */
private fun markCellsAlong(
    from: Offset,
    to: Offset,
    cellsX: Int,
    cellsY: Int,
    width: Float,
    height: Float,
    out: MutableSet<Int>
) {
    if (width <= 0f || height <= 0f) return
    if (from == Offset.Unspecified) {
        out.add(cellIndexFor(to, cellsX, cellsY, width, height))
        return
    }

    val dx = to.x - from.x
    val dy = to.y - from.y
    val step = (minOf(width / cellsX, height / cellsY) / 2f).coerceAtLeast(1f)
    val steps = (hypot(dx, dy) / step).toInt().coerceIn(0, MAX_SEGMENT_SAMPLES)

    for (i in 0..steps) {
        val t = if (steps == 0) 0f else i.toFloat() / steps
        out.add(
            cellIndexFor(Offset(from.x + dx * t, from.y + dy * t), cellsX, cellsY, width, height)
        )
    }
}

/** Fraction of the card that must be scratched before the reward is permanently unlocked. */
private const val DEFAULT_REVEAL_THRESHOLD = 0.45f

/** Foil eraser radius in raw pixels. */
private const val SCRATCH_RADIUS_PX = 27.5f

/** Upper bound on samples per drag segment, so a huge jump cannot spin the loop. */
private const val MAX_SEGMENT_SAMPLES = 256
