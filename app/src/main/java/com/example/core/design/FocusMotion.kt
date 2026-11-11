package com.example.core.design

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay

@Immutable
data class FocusMotionPolicy(val reduceMotion: Boolean)

/** Motion is opt-in outside the app theme as well (including isolated previews). */
val LocalFocusMotion = staticCompositionLocalOf { FocusMotionPolicy(reduceMotion = true) }

@Composable
internal fun rememberFocusMotionPolicy(): FocusMotionPolicy {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    fun readReducedMotion() = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) <= 0f
    var reduceMotion by remember(context, isPreview) {
        mutableStateOf(isPreview || readReducedMotion())
    }

    DisposableEffect(context, isPreview) {
        if (isPreview) {
            onDispose { }
        } else {
            val resolver = context.contentResolver
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduceMotion = readReducedMotion()
                }
            }
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
            // Close the gap between the initial read and observer registration.
            reduceMotion = readReducedMotion()
            onDispose { resolver.unregisterContentObserver(observer) }
        }
    }

    return FocusMotionPolicy(reduceMotion)
}

/** Ambient effects settle within five seconds; functional timers are not stopped by this policy. */
@Composable
fun rememberDecorativeMotionEnabled(): Boolean {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        settled = true
    }
    return !LocalFocusMotion.current.reduceMotion && !settled
}

/** Infinite effects only exist while ambient motion is allowed, so static states use no frames. */
@Composable
fun rememberFocusInfiniteFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String,
    staticValue: Float = initialValue
): State<Float> {
    return if (rememberDecorativeMotionEnabled()) {
        rememberInfiniteTransition(label = label).animateFloat(
            initialValue = initialValue,
            targetValue = targetValue,
            animationSpec = animationSpec,
            label = label
        )
    } else {
        rememberUpdatedState(staticValue)
    }
}
