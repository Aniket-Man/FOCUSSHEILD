package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusShieldTheme
import com.example.core.navigation.FocusNavGraph
import com.example.data.preferences.FocusPreferences
import com.example.feature.session.notification.PlanLaunchPayload
import com.example.feature.session.notification.StudyPlanAlarmScheduler
import com.example.feature.update.notification.UpdateNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val _incomingPlanPayload = MutableStateFlow<PlanLaunchPayload?>(null)
    val incomingPlanPayload = _incomingPlanPayload.asStateFlow()

    /** Set when the app was opened by tapping the "update available" notification (prompt.txt §5). */
    private val _openUpdatesRequest = MutableStateFlow(false)
    val openUpdatesRequest = _openUpdatesRequest.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* permission granted or denied */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val app = application as FocusShieldApp
            val preferences by app.preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = FocusPreferences()
            )
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (preferences.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            val planPayload by incomingPlanPayload.collectAsStateWithLifecycle()
            val openUpdates by openUpdatesRequest.collectAsStateWithLifecycle()

            FocusShieldTheme(darkTheme = isDarkTheme) {
                FocusNavGraph(
                    incomingPlanPayload = planPayload,
                    onPlanPayloadHandled = {
                        _incomingPlanPayload.value = null
                    },
                    openUpdatesRequest = openUpdates,
                    onOpenUpdatesHandled = { _openUpdatesRequest.value = false }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.core.accessibility.AccessibilityHelper.updateState(this)
        com.example.core.accessibility.AccessibilityHelper.executePendingGrants(this)
        // Automatic re-check on return to foreground. Cooldown-gated inside the checker, so this is
        // cheap and never a per-resume network request (prompt.txt §4).
        try {
            (application as FocusShieldApp).updateManager.checkInBackground()
        } catch (_: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATES, false)) {
            _openUpdatesRequest.value = true
        }

        val isPlanAction = intent.action == StudyPlanAlarmScheduler.ACTION_OPEN_PLAN_SESSION ||
                intent.getBooleanExtra(StudyPlanAlarmScheduler.EXTRA_START_PLAN_SESSION, false)

        if (isPlanAction) {
            val subject = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_SUBJECT) ?: return
            val topic = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_TOPIC) ?: "Focus Block"
            val duration = intent.getIntExtra(StudyPlanAlarmScheduler.EXTRA_DURATION_MINUTES, 60)
            val planId = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_PLAN_ID)
            val notes = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_NOTES) ?: ""
            val startTime = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_START_TIME)
            val endTime = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_END_TIME)
            val autoStart = intent.getBooleanExtra(StudyPlanAlarmScheduler.EXTRA_AUTO_START, false)

            _incomingPlanPayload.value = PlanLaunchPayload(
                planId = planId,
                subject = subject,
                topic = topic,
                durationMinutes = duration,
                startTime = startTime,
                endTime = endTime,
                notes = notes,
                autoStart = autoStart,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}
