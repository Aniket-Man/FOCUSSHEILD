package com.example.core.permission

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.MainActivity
import kotlinx.coroutines.delay

/**
 * The permissions FocusShield walks the user through during onboarding, in the order they are asked
 * for. Declaration order IS the flow order: the list is consumed top to bottom and only the first
 * ungranted entry is ever offered, so the user has exactly one decision in front of them at a time.
 */
enum class OnboardingPermission(
    val title: String,
    val rationale: String,
    val whyItMatters: String,
    val isMandatory: Boolean
) {
    DISPLAY_OVER_APPS(
        title = "Display over other apps",
        rationale = "This lets us cover a distracting app the moment you open it.",
        whyItMatters = "A block is only a block if it appears on top of the app you just opened. " +
            "This permission is what allows FocusShield to draw the focus shield over Instagram or " +
            "a game instead of quietly logging that you opened it.",
        isMandatory = true
    ),
    USAGE_ACCESS(
        title = "Usage permission",
        rationale = "This allows us to track your app usage.",
        whyItMatters = "Android only reports how long you spent in an app to apps you have granted " +
            "Usage Access. Without it FocusShield cannot tell that you have used 12 of your 30 " +
            "allowed YouTube minutes, so daily app limits stop working entirely.",
        isMandatory = true
    ),
    BACKGROUND(
        title = "Background permission",
        rationale = "This keeps your timers and blocks running when the app is closed.",
        whyItMatters = "Your phone aggressively puts apps to sleep to save battery. If FocusShield " +
            "is put to sleep, your focus session timer freezes and blocked apps quietly open again. " +
            "This exemption is what lets a session survive you closing the app.",
        isMandatory = true
    ),
    NOTIFICATIONS(
        title = "Notification permission",
        rationale = "This shows your live focus timer and break reminders.",
        whyItMatters = "Your running session, the countdown to your next break, and the warning " +
            "before an app limit runs out are all delivered as notifications. Optional, but focus " +
            "sessions are much easier to follow with them on.",
        isMandatory = false
    );

    fun isGranted(context: Context): Boolean = when (this) {
        DISPLAY_OVER_APPS -> FocusPermissionManager.isOverlayPermissionGranted(context)
        USAGE_ACCESS -> FocusPermissionManager.isUsageAccessGranted(context)
        BACKGROUND -> FocusPermissionManager.isBatteryOptimizationIgnored(context)
        NOTIFICATIONS -> FocusPermissionManager.isNotificationPermissionGranted(context)
    }

    /**
     * Sends the user to the exact system screen where this permission is toggled.
     */
    fun request(context: Context) = when (this) {
        DISPLAY_OVER_APPS -> FocusPermissionManager.openOverlaySettings(context)
        USAGE_ACCESS -> FocusPermissionManager.openUsageAccessSettings(context)
        BACKGROUND -> FocusPermissionManager.requestIgnoreBatteryOptimization(context)
        NOTIFICATIONS -> FocusPermissionManager.openNotificationSettings(context)
    }

    companion object {
        /** The flow, in ask order. */
        val flow: List<OnboardingPermission> = entries.toList()

        /**
         * The single permission the user should be asked for right now, or null when the flow is
         * complete.
         */
        fun next(context: Context): OnboardingPermission? = flow.firstOrNull { !it.isGranted(context) }

        fun allMandatoryGranted(context: Context): Boolean =
            flow.filter { it.isMandatory }.all { it.isGranted(context) }
    }
}

private const val SETTLE_DELAY_MILLIS = 900L
private const val POLL_INTERVAL_MILLIS = 350L
private const val AWAIT_TIMEOUT_MILLIS = 5 * 60 * 1000L
private const val RETURN_TO_APP_RETRY_COUNT = 3
private const val RETURN_TO_APP_RETRY_MILLIS = 250L

/**
 * While [awaiting] is non-null, watches that permission and pulls FocusShield back to the front the
 * instant Android reports it granted — so the user is returned to the permission list to grant the
 * next one instead of being abandoned deep inside system Settings.
 *
 * The poll runs from the composition scope rather than a lifecycle-scoped one on purpose: our
 * activity is *stopped* while the user is in Settings, and a lifecycle-aware coroutine would be
 * suspended exactly when the grant we are waiting for happens.
 *
 * [onSettled] fires with `true` on a grant and `false` if the user never completes it, so the caller
 * can clear its awaiting state either way.
 */
@Composable
fun AwaitPermissionGrantEffect(
    awaiting: OnboardingPermission?,
    onSettled: (granted: Boolean) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(awaiting) {
        val target = awaiting ?: return@LaunchedEffect

        // Let the Settings screen actually come up first. Polling immediately would see the
        // still-ungranted permission, and on an already-granted one we would "return" to a screen
        // the user has not left yet.
        delay(SETTLE_DELAY_MILLIS)

        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (target.isGranted(context)) {
                // Settings often needs one frame to commit the switch before it accepts an
                // activity launch. Retrying the same idempotent reorder makes the hand-off
                // reliable on Samsung, Xiaomi, and stock Android without opening duplicate
                // FocusShield activities.
                repeat(RETURN_TO_APP_RETRY_COUNT) {
                    bringFocusShieldToFront(context)
                    delay(RETURN_TO_APP_RETRY_MILLIS)
                }
                onSettled(true)
                return@LaunchedEffect
            }
            delay(POLL_INTERVAL_MILLIS)
        }
        onSettled(false)
    }
}

/**
 * Brings the existing FocusShield task back to the foreground without recreating it, so onboarding
 * resumes on the same page with its state intact.
 *
 * Android 10+ restricts starting activities from the background. FocusShield qualifies for the
 * "app has been granted SYSTEM_ALERT_WINDOW" exemption once "Display over other apps" is on; before
 * that this can be silently dropped by the platform, in which case the user simply presses back and
 * the screen refreshes on resume as it always did.
 */
private fun bringFocusShieldToFront(context: Context) {
    try {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Blocked by background-activity-start policy; ON_RESUME refresh remains the fallback.
    }
}
