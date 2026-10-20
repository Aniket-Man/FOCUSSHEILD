package com.example.feature.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.MainActivity
import com.example.core.design.FocusShieldTheme
import com.example.core.util.MediaPauseHelper

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"
        const val EXTRA_BLOCK_DECISION = "extra_block_decision"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"

        /**
         * The block-screen instance that is currently resumed, or null.
         *
         * [FocusBlockerManager] verifies its launches against this: `startActivity()` does NOT
         * throw when Android's background-activity-start rules silently drop a launch, so the
         * absence of an exception proves nothing — the same signal AppLimitOverlayActivity
         * provides to the app-limit retry pipeline.
         */
        @Volatile
        private var visibleInstance: BlockOverlayActivity? = null

        /** Package of the app whose block [visibleInstance] is showing. */
        @Volatile
        private var visiblePackageName: String? = null

        /** True while the block screen *for [packageName]* is the one on screen. */
        fun isVisibleFor(packageName: String): Boolean =
            visibleInstance != null && visiblePackageName == packageName
    }

    private val blockedPackageState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val appNameState = androidx.compose.runtime.mutableStateOf("Distraction App")
    private val blockDecisionState = androidx.compose.runtime.mutableStateOf("BLOCK")
    private val channelNameState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val videoTitleState = androidx.compose.runtime.mutableStateOf<String?>(null)

    private fun applyIntentExtras(sourceIntent: Intent?) {
        val i = sourceIntent ?: return
        blockedPackageState.value = i.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        appNameState.value = i.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "Distraction App"
        blockDecisionState.value = i.getStringExtra(EXTRA_BLOCK_DECISION) ?: "BLOCK"
        channelNameState.value = i.getStringExtra(EXTRA_CHANNEL_NAME)
        videoTitleState.value = i.getStringExtra(EXTRA_VIDEO_TITLE)
        if (visibleInstance === this) {
            visiblePackageName = blockedPackageState.value
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        MediaPauseHelper.pauseMedia(this)
        applyIntentExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        applyIntentExtras(intent)
        visiblePackageName = blockedPackageState.value
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

    override fun onDestroy() {
        if (visibleInstance === this) {
            visibleInstance = null
            visiblePackageName = null
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MediaPauseHelper.pauseMedia(this)
        applyIntentExtras(intent)

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

        setContent {
            val blockedPackage = blockedPackageState.value
            val appName = appNameState.value
            val blockDecision = blockDecisionState.value
            val channelName = channelNameState.value
            val videoTitle = videoTitleState.value

            FocusShieldTheme {
                if (blockDecision == "BLOCK_FLOATING_WINDOW") {
                    FloatingWindowBlockOverlayScreen(
                        blockedAppName = appName,
                        blockedPackageName = blockedPackage,
                        onReturnToSession = {
                            val returnIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(returnIntent)
                            finish()
                        }
                    )
                } else {
                    BlockOverlayScreen(
                        blockedAppName = appName,
                        blockDecision = blockDecision,
                        channelName = channelName,
                        videoTitle = videoTitle,
                        blockedPackageName = blockedPackage,
                        onReturnToSession = {
                            val returnIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(returnIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
