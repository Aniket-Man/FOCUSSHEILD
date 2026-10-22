package com.example.feature.applimits.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.FocusShieldApp
import com.example.core.design.FocusShieldTheme
import com.example.core.util.MediaPauseHelper
import com.example.feature.applimits.engine.AppLimitManager
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.applimits.engine.AppLimitStrictModeEngine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class AppLimitOverlayParams(
    val packageName: String,
    val appName: String,
    val mode: AppLimitOverlayMode,
    val selectedMinutes: Int,
    val usedMinutes: Int,
    val remainingDailyMinutes: Int,
    val dailyLimitMinutes: Int,
    val emergencyCount: Int,
    val emergencyAllowed: Int,
    val isStrict: Boolean,
    val streakDays: Int
)

class AppLimitOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_OVERLAY_MODE = "extra_overlay_mode"
        const val EXTRA_SELECTED_MINUTES = "extra_selected_minutes"
        const val EXTRA_USED_MINUTES = "extra_used_minutes"
        const val EXTRA_REMAINING_DAILY_MINUTES = "extra_remaining_daily_minutes"
        const val EXTRA_DAILY_LIMIT_MINUTES = "extra_daily_limit_minutes"
        const val EXTRA_EMERGENCY_COUNT = "extra_emergency_count"
        const val EXTRA_EMERGENCY_ALLOWED = "extra_emergency_allowed"
        const val EXTRA_IS_STRICT = "extra_is_strict"
        const val EXTRA_STREAK_DAYS = "extra_streak_days"

        /**
         * The overlay instance that is currently resumed, or null.
         *
         * AppLimitManager verifies its launches against this: `Context.startActivity()` does NOT
         * throw when Android's background-activity-start rules silently drop a launch, so the
         * absence of an exception proves nothing. Without a real signal the "time's up" popup
         * could fail to appear with no error and no retry.
         */
        @Volatile
        private var visibleInstance: AppLimitOverlayActivity? = null

        /** Package of the app whose block [visibleInstance] is showing. */
        @Volatile
        private var visiblePackageName: String? = null

        val isVisible: Boolean get() = visibleInstance != null

        /**
         * True while the blocker *for [packageName]* is the overlay currently on screen.
         *
         * This is the per-app signal the launch pipeline needs: it is what stops a second overlay
         * being pushed on top of the one the user is looking at (which finishes the first instance
         * and can have its own launch dropped in the handshake), and it is how a verified launch is
         * recognised for the app it belongs to.
         */
        fun isVisibleFor(packageName: String): Boolean =
            visibleInstance != null && visiblePackageName == packageName

        /**
         * Finishes the blocker if it belongs to [packageName]. Used when the limit is disabled or
         * deleted while its blocker is on screen; otherwise the stale screen would keep blocking
         * an app that is no longer limited until the user dismissed it manually.
         */
        fun dismissIfShowingFor(packageName: String) {
            val instance = visibleInstance ?: return
            if (visiblePackageName != packageName) return
            instance.runOnUiThread {
                if (visibleInstance === instance) instance.finish()
            }
        }
    }

    private val overlayParamsState = MutableStateFlow<AppLimitOverlayParams?>(null)

    private fun extractParams(intent: Intent): AppLimitOverlayParams {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Limited App"
        val modeStr = intent.getStringExtra(EXTRA_OVERLAY_MODE) ?: AppLimitOverlayMode.AWAITING_SELECTION.name
        val mode = try { AppLimitOverlayMode.valueOf(modeStr) } catch (e: Exception) { AppLimitOverlayMode.AWAITING_SELECTION }
        val selectedMinutes = intent.getIntExtra(EXTRA_SELECTED_MINUTES, 0)
        val usedMinutes = intent.getIntExtra(EXTRA_USED_MINUTES, 0)
        val remainingDailyMinutes = intent.getIntExtra(EXTRA_REMAINING_DAILY_MINUTES, 60)
        val dailyLimitMinutes = intent.getIntExtra(EXTRA_DAILY_LIMIT_MINUTES, 60)
        val emergencyCount = intent.getIntExtra(EXTRA_EMERGENCY_COUNT, 0)
        val emergencyAllowed = intent.getIntExtra(EXTRA_EMERGENCY_ALLOWED, 1)
        val isStrict = intent.getBooleanExtra(EXTRA_IS_STRICT, false)
        val streakDays = intent.getIntExtra(EXTRA_STREAK_DAYS, 0)

        return AppLimitOverlayParams(
            packageName = packageName,
            appName = appName,
            mode = mode,
            selectedMinutes = selectedMinutes,
            usedMinutes = usedMinutes,
            remainingDailyMinutes = remainingDailyMinutes,
            dailyLimitMinutes = dailyLimitMinutes,
            emergencyCount = emergencyCount,
            emergencyAllowed = emergencyAllowed,
            isStrict = isStrict,
            streakDays = streakDays
        )
    }

    /**
     * Returns the user to the limited app and closes the blocker.
     *
     * Always called on the main thread: the emergency path runs its database work off the main
     * thread and comes back through `onResult`, and both `startActivity` and `finish` must happen
     * on the UI thread.
     */
    private fun openLimitedAppAndFinish(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launchIntent)
            }
        } catch (_: Exception) {
        }
        finish()
    }

    /**
     * Closes the blocker by taking the user to the launcher — the "Close <app>" button and system
     * back both mean exactly this.
     *
     * The engine is told first. Leaving the app *is* an answer to the blocker, but nothing used to
     * record it: `AppLimitManager` had already dropped the expired session before raising this
     * screen, so the events that arrive while the limited app is torn down behind us re-evaluated
     * the limit and threw a fresh "how long do you want to use?" popup over the home screen the
     * user had just reached (issue #53). Enforcement resumes on their next genuine entry.
     */
    private fun goHomeAndFinish(packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            try {
                AppLimitManager.instance.onBlockerDismissedByUser(packageName)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val activePkg = com.example.core.accessibility.FocusAccessibilityService.instance
                            ?.rootInActiveWindow?.packageName?.toString()
                        if (activePkg == null || activePkg != packageName) {
                            AppLimitManager.instance.onUserLeftDismissedApp(
                                dismissedPackage = packageName,
                                observedPackage = activePkg?.takeIf { it.isNotBlank() } ?: "com.android.launcher3"
                            )
                        }
                    } catch (_: Exception) {}
                }, 500L)
            } catch (e: Exception) {
                android.util.Log.w("AppLimitOverlay", "Could not record blocker dismissal: ${e.message}")
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MediaPauseHelper.pauseMedia(this)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHomeAndFinish(overlayParamsState.value?.packageName)
            }
        })

        overlayParamsState.value = extractParams(intent)

        setContent {
            FocusShieldTheme {
                val params by overlayParamsState.collectAsState()
                params?.let { data ->
                    AppLimitOverlayScreen(
                        packageName = data.packageName,
                        appName = data.appName,
                        mode = data.mode,
                        selectedMinutes = data.selectedMinutes,
                        usedMinutes = data.usedMinutes,
                        remainingDailyMinutes = data.remainingDailyMinutes,
                        dailyLimitMinutes = data.dailyLimitMinutes,
                        emergencyUsesCount = data.emergencyCount,
                        emergencyUsesAllowed = data.emergencyAllowed,
                        isStrict = data.isStrict,
                        streakDays = data.streakDays,
                        onSelectDuration = { durationMinutes ->
                            lifecycleScope.launch {
                                // Await the session before reopening the app. Hiding this blocker
                                // first and starting the session in a detached coroutine left a
                                // window in which the app was foreground with no session and no
                                // blocker; it also reopened the app even when the limit had been
                                // disabled or the allowance had changed underneath the prompt.
                                val started = try {
                                    AppLimitManager.instance.startTemporarySession(
                                        packageName = data.packageName,
                                        appName = data.appName,
                                        durationMinutes = durationMinutes
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                        "AppLimitOverlay",
                                        "Failed to start ${durationMinutes}m session for ${data.packageName}",
                                        e
                                    )
                                    false
                                }
                                if (started) {
                                    openLimitedAppAndFinish(data.packageName)
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        "This limit changed. Close ${data.appName} or reopen it to try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onUseEmergency = {
                            // Only step aside once the pass has actually been spent. An emergency
                            // pass can be refused (the day's allowance is already used up, and the
                            // count this popup was built with can be one launch old); dismissing
                            // the blocker anyway left the app completely unblocked with no session
                            // running and no blocker to come back to.
                            AppLimitManager.instance.startEmergencySession(data.packageName, data.appName) { started ->
                                if (started) {
                                    openLimitedAppAndFinish(data.packageName)
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        "No emergency passes left for ${data.appName} today.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onEnableStrictMode = { onResult ->
                            // lifecycleScope (Main) instead of a detached IO scope: the write's
                            // outcome must reach the screen, which used to flip its strict state
                            // optimistically while a swallowed exception left the DB unchanged
                            // and the user believing strict mode was on (issue #27, item 7).
                            lifecycleScope.launch {
                                try {
                                    FocusShieldApp.instance.appLimitRepository.updateStrictMode(data.packageName, true)
                                    onResult(true)
                                } catch (e: Exception) {
                                    android.util.Log.e("AppLimitOverlay", "Failed to enable strict mode for ${data.packageName}", e)
                                    Toast.makeText(
                                        applicationContext,
                                        "Couldn't enable Strict Mode for ${data.appName}. Please try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onResult(false)
                                }
                            }
                        },
                        onTurnOffAndResetStreak = {
                            lifecycleScope.launch {
                                // The bypass row must exist before the blocker steps aside and
                                // the app is reopened. Firing this write into a detached scope
                                // let the gate re-evaluate first and block the app again.
                                val bypassed = try {
                                    AppLimitManager.instance.leaveBlockForToday(data.packageName)
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                        "AppLimitOverlay",
                                        "Failed to turn off limit for ${data.packageName}",
                                        e
                                    )
                                    false
                                }
                                if (bypassed) {
                                    AppLimitStrictModeEngine.instance.onUserQuitLimit(data.packageName)
                                    openLimitedAppAndFinish(data.packageName)
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        "Couldn't turn off ${data.appName}'s limit. Please try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        onGoToHome = { goHomeAndFinish(data.packageName) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        MediaPauseHelper.pauseMedia(this)
        val params = extractParams(intent)
        overlayParamsState.value = params
        // Same instance, possibly a different app now — keep the per-app marker truthful.
        if (visibleInstance === this) visiblePackageName = params.packageName
    }

    override fun onResume() {
        super.onResume()
        visiblePackageName = overlayParamsState.value?.packageName
        visibleInstance = this
    }

    override fun onPause() {
        // Only clear the marker if we are still the instance it points at — a newer overlay may
        // already have resumed while this one was pausing.
        if (visibleInstance === this) {
            visibleInstance = null
            visiblePackageName = null
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Replaces the old android:noHistory="true" behaviour, which is intentionally gone (see
        // AndroidManifest) because it left a dying singleInstance record that swallowed the next
        // relaunch. The overlay must still not linger as a stale, invisible task once something
        // else owns the screen — e.g. after GLOBAL_ACTION_HOME or an app switch.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        if (visibleInstance === this) {
            visibleInstance = null
            visiblePackageName = null
        }
        super.onDestroy()
    }
}

