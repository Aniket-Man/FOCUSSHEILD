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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MediaPauseHelper.pauseMedia(this)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
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
                            AppLimitManager.instance.startTemporarySession(
                                packageName = data.packageName,
                                appName = data.appName,
                                durationMinutes = durationMinutes
                            )
                            openLimitedAppAndFinish(data.packageName)
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
                        onEnableStrictMode = {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    FocusShieldApp.instance.appLimitRepository.updateStrictMode(data.packageName, true)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        onTurnOffAndResetStreak = {
                            AppLimitStrictModeEngine.instance.onUserQuitLimit(data.packageName)
                            AppLimitManager.instance.leaveBlockForToday(data.packageName)
                            openLimitedAppAndFinish(data.packageName)
                        },
                        onGoToHome = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish()
                        }
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

