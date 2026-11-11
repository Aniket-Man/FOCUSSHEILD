package com.example.feature.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
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
import com.example.core.design.FocusShieldTheme
import com.example.data.preferences.FocusPreferences
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
    private var isExpanded by mutableStateOf(false)
    private var overlayError by mutableStateOf<String?>(null)

    companion object {
        private const val TAG = "FloatingTimerService"
        private const val NOTIFICATION_ID = 8802
        private const val CHANNEL_ID = "floating_timer_channel"

        private const val ACTION_EXPAND = "com.example.focusshield.EXPAND_FLOATING_TIMER"

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
        if (intent?.action == ACTION_EXPAND) setOverlayExpanded(true)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOverlayWindow(isExpanded)
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
                getString(R.string.floating_timer_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.floating_timer_channel_description)
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

        val expandIntent = PendingIntent.getService(
            this, 102, Intent(this, FloatingTimerService::class.java).setAction(ACTION_EXPAND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_timer_notification_title))
            .setContentText(overlayError ?: getString(R.string.floating_timer_notification_content))
            .setStyle(NotificationCompat.BigTextStyle().bigText(overlayError ?: getString(R.string.floating_timer_notification_content)))
            .setSmallIcon(R.drawable.ic_focus_shield_app_icon)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_focus_shield_app_icon, getString(R.string.floating_timer_expand), expandIntent)
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
        val dimensions = overlayDimensions(expanded = isExpanded)

        windowLayoutParams = WindowManager.LayoutParams(
            dimensions.width,
            dimensions.height,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Physical edge docking must not reverse in an RTL locale.
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = FloatingTimerLayout.clampY(initialY, screenHeight, dimensions.height, dpToPx(24), dpToPx(48))
            title = "FocusShieldFloatingTimer"
        }

        var dragOffsetX = 0f

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingTimerService)
            setViewTreeSavedStateRegistryOwner(this@FloatingTimerService)
            setContent {
                val app = applicationContext as FocusShieldApp
                val prefs by app.preferencesRepository.preferencesFlow.collectAsState(initial = FocusPreferences())
                val darkTheme = when (prefs.themeMode) {
                    "DARK" -> true
                    "LIGHT" -> false
                    else -> isSystemInDarkTheme()
                }
                FocusShieldTheme(darkTheme = darkTheme) {
                    FloatingTimerOverlayRoot(
                    onDragStart = {
                        dragOffsetX = 0f
                    },
                    onDragBy = { dx, dy ->
                        val lp = copyOverlayLayout() ?: return@FloatingTimerOverlayRoot
                        val metrics = resources.displayMetrics
                        val newY = FloatingTimerLayout.clampY(lp.y + dy.roundToInt(), metrics.heightPixels, lp.height, dpToPx(24), dpToPx(48))

                        dragOffsetX += dx
                        val screenWidth = metrics.widthPixels
                        var edgeChanged = false
                        var targetRightEdge = dockedOnRightEdge
                        if (!dockedOnRightEdge && dragOffsetX > screenWidth * 0.35f) {
                            targetRightEdge = true
                            lp.gravity = Gravity.TOP or Gravity.RIGHT
                            lp.x = 0
                            dragOffsetX = 0f
                            edgeChanged = true
                        } else if (dockedOnRightEdge && dragOffsetX < -screenWidth * 0.35f) {
                            targetRightEdge = false
                            lp.gravity = Gravity.TOP or Gravity.LEFT
                            lp.x = 0
                            dragOffsetX = 0f
                            edgeChanged = true
                        }

                        if (newY != lp.y || edgeChanged) {
                            lp.y = newY
                            if (applyOverlayLayout(lp)) dockedOnRightEdge = targetRightEdge
                        }
                    },
                    onDragEnd = { dragOffsetX = 0f },
                    onMove = ::moveOverlay
                    )
                }
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

    private fun overlayDimensions(expanded: Boolean): FloatingTimerDimensions {
        val metrics = resources.displayMetrics
        return FloatingTimerLayout.dimensions(expanded, metrics.widthPixels, metrics.heightPixels, metrics.density, resources.configuration.fontScale)
    }

    private fun copyOverlayLayout(): WindowManager.LayoutParams? = windowLayoutParams?.let { current ->
        WindowManager.LayoutParams().apply { copyFrom(current) }
    }

    private fun setOverlayExpanded(expanded: Boolean) {
        if (updateOverlayWindow(expanded)) isExpanded = expanded
    }

    private fun updateOverlayWindow(expanded: Boolean): Boolean {
        val lp = copyOverlayLayout() ?: return false
        val dimensions = overlayDimensions(expanded)
        lp.width = dimensions.width
        lp.height = dimensions.height
        lp.y = FloatingTimerLayout.clampY(lp.y, resources.displayMetrics.heightPixels, lp.height, dpToPx(24), dpToPx(48))
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (expanded) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        return applyOverlayLayout(lp).also { applied ->
            if (applied && expanded) overlayView?.requestFocus()
        }
    }

    private fun applyOverlayLayout(lp: WindowManager.LayoutParams): Boolean {
        val view = overlayView ?: return false
        val manager = windowManager ?: return false
        try {
            manager.updateViewLayout(view, lp)
            windowLayoutParams = lp
            return true
        } catch (exception: Exception) {
            reportOverlayFailure(exception)
            return false
        }
    }

    private fun moveOverlay(move: FloatingTimerMove) {
        val lp = copyOverlayLayout() ?: return
        var targetRightEdge = dockedOnRightEdge
        when (move) {
            FloatingTimerMove.UP -> lp.y -= dpToPx(48)
            FloatingTimerMove.DOWN -> lp.y += dpToPx(48)
            FloatingTimerMove.LEFT_EDGE -> targetRightEdge = false
            FloatingTimerMove.RIGHT_EDGE -> targetRightEdge = true
        }
        lp.y = FloatingTimerLayout.clampY(lp.y, resources.displayMetrics.heightPixels, lp.height, dpToPx(24), dpToPx(48))
        lp.gravity = Gravity.TOP or (if (targetRightEdge) Gravity.RIGHT else Gravity.LEFT)
        lp.x = 0
        if (applyOverlayLayout(lp)) dockedOnRightEdge = targetRightEdge
    }

    private fun reportOverlayFailure(exception: Exception) {
        Log.e(TAG, "Unable to update floating timer window", exception)
        showOverlayError(getString(R.string.floating_timer_layout_error))
    }

    private fun showOverlayError(message: String?) {
        overlayError = message
        // A failed expansion cannot display the card, so recovery also stays in the notification.
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to update floating timer notification", exception)
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
            setOverlayExpanded(false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch MainActivity from floating overlay", e)
            showOverlayError(getString(R.string.floating_timer_open_error))
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
        onDragStart: () -> Unit,
        onDragBy: (Float, Float) -> Unit,
        onDragEnd: () -> Unit,
        onMove: (FloatingTimerMove) -> Unit
    ) {
        val activeSession by FocusSessionManager.instance.activeSession.collectAsState()
        val sessionState by FocusSessionManager.instance.sessionState.collectAsState()
        val session = activeSession
        val isPaused = sessionState == SessionState.PAUSED
        val isStopwatch = session?.mode == SessionMode.STOPWATCH
        val displayMillis = if (isStopwatch) {
            session?.elapsedDurationMillis ?: 0L
        } else {
            session?.remainingDurationMillis ?: 0L
        }
        FloatingTimerOverlay(
            expanded = isExpanded,
            dockedRight = dockedOnRightEdge,
            formattedTime = FocusWidgetSnapshot.formatCountdown(displayMillis),
            paused = isPaused,
            strict = session?.isStrictModeEnabled == true && !isStopwatch,
            stopwatch = isStopwatch,
            onExpand = { setOverlayExpanded(true) },
            onCollapse = { setOverlayExpanded(false) },
            onTogglePause = {
                val changed = if (isPaused) FocusSessionManager.instance.resumeSession()
                    else FocusSessionManager.instance.pauseSession()
                if (changed) {
                    FocusSessionManager.instance.saveActiveSessionToDisk(this@FloatingTimerService)
                    serviceScope.launch {
                        FocusShieldWidgetUpdater.updateAll(this@FloatingTimerService, force = true)
                    }
                } else {
                    showOverlayError(getString(R.string.floating_timer_pause_error))
                }
            },
            onOpenApp = { openApp() },
            onMove = onMove,
            errorMessage = overlayError,
            onDismissError = { showOverlayError(null) },
            dragModifier = Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, amount ->
                        change.consume()
                        onDragBy(amount.x, amount.y)
                    }
                )
            }
        )
    }
}
