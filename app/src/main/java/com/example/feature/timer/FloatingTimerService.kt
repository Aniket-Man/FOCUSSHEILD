package com.example.feature.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.FocusShieldApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.SessionMode
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.session.service.ForegroundServiceTypes
import com.example.feature.widgets.FocusShieldWidgetUpdater
import com.example.feature.widgets.FocusWidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Edge-Docked Expandable Floating Countdown Overlay Service:
 *
 * Uses fixed deterministic window dimensions for both states (Collapsed Edge Tab vs.
 * Expanded Countdown Banner) and tabular numerals (`tnum`) so the WindowManager Surface
 * never resizes mid-frame or during per-second countdown ticks, preventing BLASTBufferQueue
 * buffer rejection errors.
 */
class FloatingTimerService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var dockedOnRightEdge by mutableStateOf(false)

    companion object {
        private const val TAG = "FloatingTimerService"
        private const val NOTIFICATION_ID = 8802
        private const val CHANNEL_ID = "floating_timer_channel"

        private const val COLLAPSED_WIDTH_DP = 28
        private const val COLLAPSED_HEIGHT_DP = 52
        private const val EXPANDED_WIDTH_DP = 234
        private const val EXPANDED_HEIGHT_DP = 68

        private val _isOverlayShowing = MutableStateFlow(false)
        val isOverlayShowing: StateFlow<Boolean> = _isOverlayShowing.asStateFlow()

        fun start(context: Context, forceShow: Boolean = false) {
            val app = context.applicationContext as? FocusShieldApp
            val enabledInPrefs = app?.preferencesRepository?.isFloatingTimerOverlayEnabledSync() ?: true
            if (!forceShow && !enabledInPrefs) return

            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingTimerService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start FloatingTimerService: ${e.message}")
                }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingTimerService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop FloatingTimerService: ${e.message}")
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        if (!startForegroundSafely(buildNotification())) {
            stopSelf()
            return
        }

        if (FocusSessionManager.instance.activeSession.value == null) {
            FocusSessionManager.instance.restoreActiveSessionFromDisk(this)
        }

        if (Settings.canDrawOverlays(this)) {
            setupOverlayWindow()
            observeSessionState()
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (FocusSessionManager.instance.activeSession.value == null) {
            FocusSessionManager.instance.restoreActiveSessionFromDisk(this)
        }
        val current = FocusSessionManager.instance.activeSession.value
        if (current == null || current.isCompleted || current.isCancelled) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (Settings.canDrawOverlays(this) && overlayView == null) {
            setupOverlayWindow()
            observeSessionState()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val current = FocusSessionManager.instance.activeSession.value
        if (current != null && (current.state == SessionState.RUNNING || current.state == SessionState.PAUSED)) {
            FocusSessionManager.instance.saveActiveSessionToDisk(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlayWindow()
        _isOverlayShowing.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(notification: Notification): Boolean {
        val declaredTypes = declaredForegroundServiceTypes()
        val resolution = ForegroundServiceTypes.resolve(Build.VERSION.SDK_INT, declaredTypes)
        return try {
            when (val choice = resolution.choice) {
                is ForegroundServiceTypes.Choice.Typed ->
                    startForeground(NOTIFICATION_ID, notification, choice.type)
                ForegroundServiceTypes.Choice.Untyped ->
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground for FloatingTimerService: ${e.message}")
            false
        }
    }

    private fun declaredForegroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            val component = ComponentName(this, javaClass)
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getServiceInfo(component, 0)
            }
            info.foregroundServiceType
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Focus Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live focus countdown overlay over other apps"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_timer_notification_title))
            .setContentText(getString(R.string.floating_timer_notification_content))
            .setSmallIcon(R.drawable.ic_focus_shield_app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupOverlayWindow() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenHeight = resources.displayMetrics.heightPixels
        val initialY = (screenHeight * 0.42f).roundToInt()

        windowLayoutParams = WindowManager.LayoutParams(
            dpToPx(COLLAPSED_WIDTH_DP),
            dpToPx(COLLAPSED_HEIGHT_DP),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = initialY
            title = "FocusShieldFloatingTimer"
        }

        var dragOffsetX = 0f

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingTimerService)
            setViewTreeSavedStateRegistryOwner(this@FloatingTimerService)
            setContent {
                FloatingTimerOverlayRoot(
                    isDockedRight = dockedOnRightEdge,
                    onExpandedChanged = { expanded ->
                        val lp = this@FloatingTimerService.windowLayoutParams ?: return@FloatingTimerOverlayRoot
                        val targetW = dpToPx(if (expanded) EXPANDED_WIDTH_DP else COLLAPSED_WIDTH_DP)
                        val targetH = dpToPx(if (expanded) EXPANDED_HEIGHT_DP else COLLAPSED_HEIGHT_DP)
                        if (lp.width != targetW || lp.height != targetH) {
                            lp.width = targetW
                            lp.height = targetH
                            try {
                                windowManager?.updateViewLayout(overlayView, lp)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onDragStart = {
                        dragOffsetX = 0f
                    },
                    onDragBy = { dx, dy ->
                        val lp = this@FloatingTimerService.windowLayoutParams ?: return@FloatingTimerOverlayRoot
                        val metrics = resources.displayMetrics
                        val maxHeight = (metrics.heightPixels - dpToPx(120)).coerceAtLeast(dpToPx(80))
                        val newY = (lp.y + dy.roundToInt()).coerceIn(dpToPx(60), maxHeight)

                        dragOffsetX += dx
                        val screenWidth = metrics.widthPixels
                        var edgeChanged = false
                        if (!dockedOnRightEdge && dragOffsetX > screenWidth * 0.35f) {
                            dockedOnRightEdge = true
                            lp.gravity = Gravity.TOP or Gravity.END
                            lp.x = 0
                            dragOffsetX = 0f
                            edgeChanged = true
                        } else if (dockedOnRightEdge && dragOffsetX < -screenWidth * 0.35f) {
                            dockedOnRightEdge = false
                            lp.gravity = Gravity.TOP or Gravity.START
                            lp.x = 0
                            dragOffsetX = 0f
                            edgeChanged = true
                        }

                        if (newY != lp.y || edgeChanged) {
                            lp.y = newY
                            try {
                                windowManager?.updateViewLayout(overlayView, lp)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onDragEnd = {
                        val lp = this@FloatingTimerService.windowLayoutParams ?: return@FloatingTimerOverlayRoot
                        if (lp.x != 0) {
                            lp.x = 0
                            try {
                                windowManager?.updateViewLayout(overlayView, lp)
                            } catch (_: Exception) {
                            }
                        }
                    }
                )
            }
        }

        overlayView = composeView

        try {
            windowManager?.addView(overlayView, windowLayoutParams)
            _isOverlayShowing.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not add floating overlay view: ${e.message}")
            overlayView = null
            _isOverlayShowing.value = false
            stopSelf()
        }
    }

    private fun openApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                pendingIntent.send()
            } catch (_: PendingIntent.CanceledException) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch MainActivity from floating overlay: ${e.message}")
        }
    }

    private fun observeSessionState() {
        serviceScope.launch {
            FocusSessionManager.instance.activeSession.collectLatest { session ->
                if (session == null) {
                    val restored = FocusSessionManager.instance.restoreActiveSessionFromDisk(this@FloatingTimerService)
                    if (restored == null || restored.isCompleted || restored.isCancelled) {
                        stopSelf()
                    }
                } else if (session.isCompleted || session.isCancelled) {
                    stopSelf()
                }
            }
        }
    }

    private fun removeOverlayWindow() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.d(TAG, "Overlay view removal skipped: ${e.message}")
            }
        }
        overlayView = null
        _isOverlayShowing.value = false
    }

    @Composable
    private fun FloatingTimerOverlayRoot(
        isDockedRight: Boolean,
        onExpandedChanged: (Boolean) -> Unit,
        onDragStart: () -> Unit,
        onDragBy: (Float, Float) -> Unit,
        onDragEnd: () -> Unit
    ) {
        val activeSession by FocusSessionManager.instance.activeSession.collectAsState()
        val sessionState by FocusSessionManager.instance.sessionState.collectAsState()

        var isExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(isExpanded) {
            onExpandedChanged(isExpanded)
        }

        val session = activeSession
        val isPaused = sessionState == SessionState.PAUSED
        val isStopwatch = session?.mode == SessionMode.STOPWATCH
        val displayMillis = if (isStopwatch) {
            session?.elapsedDurationMillis ?: 0L
        } else {
            session?.remainingDurationMillis ?: 0L
        }
        val subtitleText = if (isStopwatch) "Time elapsed" else "Time remaining"

        Box(
            modifier = Modifier
                .size(
                    width = if (isExpanded) EXPANDED_WIDTH_DP.dp else COLLAPSED_WIDTH_DP.dp,
                    height = if (isExpanded) EXPANDED_HEIGHT_DP.dp else COLLAPSED_HEIGHT_DP.dp
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragBy(dragAmount.x, dragAmount.y)
                        }
                    )
                }
        ) {
            if (!isExpanded) {
                CollapsedEdgeTab(
                    isPaused = isPaused,
                    isDockedRight = isDockedRight,
                    onExpand = { isExpanded = true }
                )
            } else {
                ExpandedCountdownBanner(
                    formattedTime = FocusWidgetSnapshot.formatCountdown(displayMillis),
                    subtitleText = subtitleText,
                    isPaused = isPaused,
                    isStrict = session?.isStrictModeEnabled == true && !isStopwatch,
                    isDockedRight = isDockedRight,
                    onTogglePause = {
                        if (isPaused) {
                            FocusSessionManager.instance.resumeSession()
                        } else {
                            FocusSessionManager.instance.pauseSession()
                        }
                        FocusSessionManager.instance.saveActiveSessionToDisk(this@FloatingTimerService)
                        serviceScope.launch {
                            FocusShieldWidgetUpdater.updateAll(this@FloatingTimerService, force = true)
                        }
                    },
                    onOpenApp = {
                        isExpanded = false
                        openApp()
                    },
                    onCollapse = {
                        isExpanded = false
                    }
                )
            }
        }
    }

    /**
     * Collapsed vertical edge tab (`>` on left edge, `<` on right edge) with a solid purple body
     * and pure white chevron icon.
     */
    @Composable
    private fun CollapsedEdgeTab(
        isPaused: Boolean,
        isDockedRight: Boolean,
        onExpand: () -> Unit
    ) {
        val tabShape = if (isDockedRight) {
            RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        } else {
            RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp)
        }

        val containerColor by animateColorAsState(
            targetValue = if (isPaused) {
                com.example.core.design.FocusColors.LightPalette.primaryDark // Deep Purple #6D28D9
            } else {
                com.example.core.design.FocusColors.LightPalette.primary // Vibrant Purple #7C3AED
            },
            animationSpec = tween(180),
            label = "edge_tab_bg"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(tabShape)
                .background(containerColor)
                .border(
                    1.dp,
                    com.example.core.design.FocusColors.LightPalette.surface.copy(alpha = 0.4f),
                    tabShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand
                )
                .testTag("floating_timer_edge_tab")
        ) {
            Icon(
                imageVector = if (isDockedRight) {
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                },
                contentDescription = "Expand Countdown Timer",
                tint = com.example.core.design.FocusColors.LightPalette.surface, // Pure White #FFFFFF
                modifier = Modifier.size(20.dp)
            )
        }
    }

    /**
     * Expanded countdown banner with:
     * - Whole body in FocusShield Purple (`#7C3AED` / `#6D28D9`)
     * - Pure White (`#FFFFFF`) circular Pause/Play button with Purple (`#6D28D9`) icon
     * - Pure White (`#FFFFFF`) countdown numerals and soft white `"Time remaining"` subtitle
     * - Pure White (`#FFFFFF`) circular Close (`✕`) button with Purple (`#6D28D9`) icon
     */
    @Composable
    private fun ExpandedCountdownBanner(
        formattedTime: String,
        subtitleText: String,
        isPaused: Boolean,
        isStrict: Boolean,
        isDockedRight: Boolean,
        onTogglePause: () -> Unit,
        onOpenApp: () -> Unit,
        onCollapse: () -> Unit
    ) {
        val bannerShape = if (isDockedRight) {
            RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        } else {
            RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp)
        }

        val bannerBackground by animateColorAsState(
            targetValue = if (isPaused) {
                com.example.core.design.FocusColors.LightPalette.primaryDark // Royal Purple #6D28D9
            } else {
                com.example.core.design.FocusColors.LightPalette.primary // Vibrant Purple #7C3AED
            },
            animationSpec = tween(200),
            label = "banner_bg_color"
        )

        val whiteColor = com.example.core.design.FocusColors.LightPalette.surface // Pure White #FFFFFF
        val purpleIconColor = com.example.core.design.FocusColors.LightPalette.primaryDark // #6D28D9

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clip(bannerShape)
                .background(bannerBackground)
                .border(1.dp, whiteColor.copy(alpha = 0.35f), bannerShape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("floating_timer_overlay_card")
        ) {
            // Left Circular Play / Pause Button (Pure White circle with Purple icon)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(whiteColor)
                    .clickable(
                        enabled = !isStrict,
                        onClick = {
                            if (!isStrict) {
                                onTogglePause()
                            } else {
                                onOpenApp()
                            }
                        }
                    )
                    .testTag("floating_timer_pause_resume_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (isPaused) "Resume Session" else "Pause Session",
                    tint = purpleIconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Countdown + "Time remaining" Column (Tap forwards to FocusShield app)
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenApp
                    )
                    .testTag("floating_timer_open_app_area")
            ) {
                Text(
                    text = formattedTime,
                    style = TextStyle(
                        color = whiteColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitleText,
                    color = whiteColor.copy(alpha = 0.9f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Circular Close / Collapse (`✕`) Button (Pure White circle with Purple icon)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(whiteColor)
                    .clickable(onClick = onCollapse)
                    .testTag("floating_timer_collapse_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Collapse Countdown to Edge Tab",
                    tint = purpleIconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
