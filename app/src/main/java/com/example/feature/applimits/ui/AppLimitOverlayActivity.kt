package com.example.feature.applimits.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.FocusShieldApp
import com.example.MainActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MediaPauseHelper.pauseMedia(this)

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
                            finish()
                        },
                        onUseEmergency = {
                            AppLimitManager.instance.startEmergencySession(data.packageName, data.appName)
                            finish()
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
                            finish()
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
        overlayParamsState.value = extractParams(intent)
    }
}

