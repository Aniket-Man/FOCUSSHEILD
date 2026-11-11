package com.example.core.ui

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.core.design.LocalFocusMotion
import com.example.core.design.rememberDecorativeMotionEnabled

/**
 * A reusable Lottie animation wrapper for FocusShield.
 *
 * @param rawRes The raw resource ID of the Lottie JSON file.
 * @param modifier Modifier to apply to the animation.
 * @param iterations Number of times to play. Use [LottieConstants.IterateForever] for looping.
 * @param speed Playback speed multiplier (1f = normal).
 * @param imageAssetsFolder Optional assets subfolder containing the animation's
 *   bitmap references (used by device-specific OEM walkthrough animations).
 * @param testTag Optional test tag for UI testing.
 */
@Composable
fun FocusLottieAnimation(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    iterations: Int = 1,
    speed: Float = 1f,
    imageAssetsFolder: String? = null,
    testTag: String = "focus_lottie_animation",
    staticProgress: Float = 0.5f
) {
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(rawRes),
        imageAssetsFolder = imageAssetsFolder
    )
    val motionEnabled = rememberDecorativeMotionEnabled()
    val reduceMotion = LocalFocusMotion.current.reduceMotion
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = motionEnabled,
        restartOnPlay = false,
        iterations = iterations,
        speed = speed
    )

    LottieAnimation(
        composition = composition,
        progress = { if (reduceMotion) staticProgress.coerceIn(0f, 1f) else progress },
        modifier = modifier.testTag(testTag)
    )
}

/**
 * A Lottie animation with play control returned to the caller.
 *
 * @param rawRes The raw resource ID of the Lottie JSON file.
 * @param modifier Modifier to apply to the animation.
 * @param iterations Number of times to play. Use [LottieConstants.IterateForever] for looping.
 * @param speed Playback speed multiplier.
 * @param onCompletion Called when the animation finishes playing.
 * @return A [LottiePlaybackState] that can be used to control play/pause/reset.
 */
@Composable
fun FocusLottieAnimationControlled(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    iterations: Int = 1,
    speed: Float = 1f,
    onCompletion: (() -> Unit)? = null,
    staticProgress: Float = 0.5f
): LottiePlaybackState {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val playback = remember { LottiePlaybackState() }
    val reduceMotion = LocalFocusMotion.current.reduceMotion
    val resetGeneration = playback.resetGeneration.intValue
    val animation = key(resetGeneration) {
        animateLottieCompositionAsState(
            composition = composition,
            isPlaying = playback.isPlaying.value && !reduceMotion,
            restartOnPlay = false,
            iterations = iterations,
            speed = speed
        )
    }
    val currentOnCompletion by rememberUpdatedState(onCompletion)
    var completionDelivered by remember(composition, resetGeneration) { mutableStateOf(false) }

    LaunchedEffect(composition, animation.isAtEnd, reduceMotion, resetGeneration) {
        if (composition != null && !completionDelivered && (reduceMotion || animation.isAtEnd)) {
            completionDelivered = true
            currentOnCompletion?.invoke()
        }
    }

    LottieAnimation(
        composition = composition,
        progress = { if (reduceMotion) staticProgress.coerceIn(0f, 1f) else animation.progress },
        modifier = modifier
    )

    return playback
}

/**
 * Holds playback state for controlled Lottie animations.
 */
@Stable
class LottiePlaybackState {
    var isPlaying = androidx.compose.runtime.mutableStateOf(true)
        private set
    internal val resetGeneration = mutableIntStateOf(0)

    fun play() { isPlaying.value = true }
    fun pause() { isPlaying.value = false }
    fun reset() {
        isPlaying.value = false
        resetGeneration.intValue++
    }
}
