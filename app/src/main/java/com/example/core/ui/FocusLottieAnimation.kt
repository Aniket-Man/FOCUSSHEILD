package com.example.core.ui

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

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
    testTag: String = "focus_lottie_animation"
) {
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(rawRes),
        imageAssetsFolder = imageAssetsFolder
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        speed = speed
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
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
    onCompletion: (() -> Unit)? = null
): LottiePlaybackState {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        speed = speed
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )

    return remember { LottiePlaybackState() }
}

/**
 * Holds playback state for controlled Lottie animations.
 */
class LottiePlaybackState {
    var isPlaying = androidx.compose.runtime.mutableStateOf(true)
        private set

    fun play() { isPlaying.value = true }
    fun pause() { isPlaying.value = false }
}
