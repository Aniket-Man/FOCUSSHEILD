package com.example.feature.applimits.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.R
import com.example.feature.applimits.engine.AppLimitDurationOptions
import com.example.feature.applimits.engine.AppLimitManager
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.applimits.engine.AppLimitStrictModeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Native floating WindowManager overlay that appears directly on top of limited applications
 * without launching FocusShield activities or changing the Android task backstack.
 */
object AppLimitFloatingOverlayManager {

    private const val TAG = "AppLimitFloatingOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var windowManager: WindowManager? = null

    @Volatile
    private var showingPackageName: String? = null
    @Volatile
    private var showingMode: AppLimitOverlayMode? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun isShowingFor(packageName: String): Boolean =
        overlayView != null && showingPackageName == packageName

    /**
     * Called from [AppLimitManager.onForegroundPackageChanged]. The overlay has no activity
     * lifecycle, so this is the only thing that dismisses it when the limited app it belongs
     * to leaves the foreground (Home, app switch) instead of letting the full-screen scrim sit
     * on top of whatever comes next. Transient system windows (SystemUI, android, IMEs) are
     * ignored so status/nav bar or keyboard events never prematurely dismiss the blocker.
     */
    fun onForegroundPackageChanged(packageName: String) {
        val showing = showingPackageName ?: return
        val filter = com.example.feature.applimits.engine.AppLimitPackageFilter
        // FocusShield's own package is not tracked as a session-movement package, but it is a
        // real foreground change for the blocker: leaving the limited app for the shield must
        // remove the scrim, otherwise it can sit on top of the shield's own UI.
        if (!filter.shouldTrackForeground(packageName) && !filter.isOwnApp(packageName)) {
            return
        }
        if (packageName != showing) {
            // Guard against background/auxiliary window events (e.g. GMS, quicksearchbox, PiP,
            // or transient overlays) firing while the limited app is still the active foreground
            // window behind our non-focusable accessibility overlay.
            if (!filter.isOwnApp(packageName)) {
                val activeWindowPkg = try {
                    com.example.core.accessibility.FocusAccessibilityService.instance
                        ?.rootInActiveWindow?.packageName?.toString()
                } catch (_: Exception) {
                    null
                }
                if (activeWindowPkg == showing) {
                    Log.d(TAG, "Ignoring transient package event $packageName while $showing is still the active window")
                    return
                }
            }
            Log.i(TAG, "Foreground is now $packageName; dismissing floating blocker for $showing")
            hideOverlay()
        }
    }

    fun showOverlay(
        context: Context,
        packageName: String,
        appName: String,
        mode: AppLimitOverlayMode,
        usedMinutes: Int,
        remainingDailyMinutes: Int,
        dailyLimitMinutes: Int,
        emergencyUsesCount: Int = 0,
        emergencyUsesAllowed: Int = 1,
        isStrict: Boolean = false,
        streakDays: Int = 0,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        mainHandler.post {
            // If already showing for this package with the exact same mode, keep it
            if (overlayView != null && showingPackageName == packageName && showingMode == mode) {
                onResult?.invoke(true)
                return@post
            }
            // If showing for another package (or different mode), remove previous first. This must be the
            // synchronous removal, NOT hideOverlay(): that posts its removal to this very
            // queue, so it would run after the new view below is added and tear it down
            // again — the blocker flashed for one frame and then vanished on every launch.
            removeCurrentView()

            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm

                val view = buildOverlayView(
                    context = context,
                    packageName = packageName,
                    appName = appName,
                    mode = mode,
                    usedMinutes = usedMinutes,
                    remainingDailyMinutes = remainingDailyMinutes,
                    dailyLimitMinutes = dailyLimitMinutes,
                    emergencyUsesCount = emergencyUsesCount,
                    emergencyUsesAllowed = emergencyUsesAllowed,
                    isStrict = isStrict,
                    streakDays = streakDays
                )

                // The system can take the window away without us asking (service rebind,
                // token invalidation). Without this resync isShowingFor() would stay true
                // forever and AppLimitManager's "blocker already on screen" guard would refuse
                // to raise any blocker again until the process dies.
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) = handleUnexpectedDetach(v)
                })

                if (addViewSafely(wm, view, context)) {
                    overlayView = view
                    showingPackageName = packageName
                    showingMode = mode
                    Log.i(TAG, "Floating App Limit overlay presented for $appName ($packageName) [mode=$mode]")
                    onResult?.invoke(true)
                } else {
                    onResult?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show floating App Limit overlay", e)
                onResult?.invoke(false)
            }
        }
    }

    fun hideOverlay() {
        mainHandler.post { removeCurrentView() }
    }

    /** Removes the blocker only if it belongs to [packageName], leaving another app's alone. */
    fun hideOverlayFor(packageName: String) {
        mainHandler.post {
            if (showingPackageName == packageName) removeCurrentView()
        }
    }

    /**
     * Removes whatever is showing. Must run on the main thread; callers that are already
     * inside a main-handler runnable (like [showOverlay]) invoke this directly so the
     * removal cannot land in the queue behind the very add it is meant to precede.
     */
    private fun removeCurrentView() {
        val view = overlayView ?: return
        val wm = windowManager
        val pkg = showingPackageName
        // Clear the state before removeView: removal fires the detach listener
        // synchronously, and handleUnexpectedDetach must be able to tell an intentional
        // hide (state already cleared) from a system-initiated removal (state still set).
        overlayView = null
        windowManager = null
        showingPackageName = null
        showingMode = null
        try {
            wm?.removeView(view)
            Log.i(TAG, "Floating App Limit overlay removed for $pkg")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove floating App Limit overlay", e)
        }
    }

    /**
     * Clears the showing state when [view] leaves the window without [hideOverlay] having run.
     * Intentional hides clear the state first, so this is a no-op for them.
     */
    internal fun handleUnexpectedDetach(view: View) {
        if (overlayView !== view) return
        Log.w(TAG, "Floating App Limit overlay for $showingPackageName was removed by the system; resyncing state")
        overlayView = null
        windowManager = null
        showingPackageName = null
        showingMode = null
    }

    private fun addViewSafely(wm: WindowManager, view: View, context: Context): Boolean {
        // 1. Accessibility overlay type (no extra permission needed when launched by FocusAccessibilityService)
        try {
            wm.addView(view, buildLayoutParams(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY))
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Accessibility overlay type rejected: ${e.message}, attempting fallback")
        }

        // 2. Application overlay type when SYSTEM_ALERT_WINDOW is granted
        return try {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot draw overlay: overlay permission not granted")
                return false
            }
            val fallbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            wm.addView(view, buildLayoutParams(fallbackType))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add application overlay view", e)
            false
        }
    }

    private fun buildLayoutParams(windowType: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    // Never take key focus: BACK must keep flowing to the app beneath, and a
                    // focusable overlay window can perturb foreground-window reporting. Same
                    // combination YouTubeHomeFeedOverlayManager has always used.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            windowAnimations = R.style.AppLimitBlockerSheetAnimation
        }
    }

    // ---------------------------------------------------------------------------------------
    // Bottom-sheet layout (issue #68)
    // ---------------------------------------------------------------------------------------

    private fun buildOverlayView(
        context: Context,
        packageName: String,
        appName: String,
        mode: AppLimitOverlayMode,
        usedMinutes: Int,
        remainingDailyMinutes: Int,
        dailyLimitMinutes: Int,
        emergencyUsesCount: Int,
        emergencyUsesAllowed: Int,
        isStrict: Boolean,
        streakDays: Int
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val isExhausted = mode == AppLimitOverlayMode.DAILY_LIMIT_REACHED

        // Root: full-screen translucent scrim; the sheet itself is anchored to the bottom edge.
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setBackgroundColor(Color.parseColor("#CC08090C"))
        }

        // Sheet: full-width, rounded top corners, slides up from the bottom edge.
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#17181F"))
                val radius = dp(28).toFloat()
                // top-left, top-right, bottom-right, bottom-left (x,y pairs)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // --- Top row: white open-book brand mark (left) + "Turn off" pill (right) ---
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        val brandIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_focus_brand_mark)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        }
        val brandTitle = TextView(context).apply {
            text = "FocusShield"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
        }
        topRow.addView(brandIcon)
        topRow.addView(brandTitle)
        topRow.addView(
            View(context),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )

        // Wrapper for the dynamic content of the sheet (stats/options or confirmation)
        val contentWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rightPill = if (isStrict) {
            strictLockedPill(context) {
                Toast.makeText(
                    context,
                    "Strict Mode is on: $appName's limit cannot be turned off or removed",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            turnOffPill(context) {
                onTurnOffClicked(
                    context = context,
                    packageName = packageName,
                    appName = appName,
                    streakDays = streakDays,
                    contentWrapper = contentWrapper,
                    onCancel = {
                        // Rebuild the original content
                        contentWrapper.removeAllViews()
                        buildMainOverlayContent(
                            context = context,
                            packageName = packageName,
                            appName = appName,
                            mode = mode,
                            usedMinutes = usedMinutes,
                            remainingDailyMinutes = remainingDailyMinutes,
                            dailyLimitMinutes = dailyLimitMinutes,
                            emergencyUsesCount = emergencyUsesCount,
                            emergencyUsesAllowed = emergencyUsesAllowed,
                            isStrict = isStrict,
                            streakDays = streakDays,
                            contentWrapper = contentWrapper
                        )
                    }
                )
            }
        }
        topRow.addView(rightPill)
        sheet.addView(topRow)

        buildMainOverlayContent(
            context = context,
            packageName = packageName,
            appName = appName,
            mode = mode,
            usedMinutes = usedMinutes,
            remainingDailyMinutes = remainingDailyMinutes,
            dailyLimitMinutes = dailyLimitMinutes,
            emergencyUsesCount = emergencyUsesCount,
            emergencyUsesAllowed = emergencyUsesAllowed,
            isStrict = isStrict,
            streakDays = streakDays,
            contentWrapper = contentWrapper
        )
        sheet.addView(contentWrapper)

        root.addView(sheet)
        return root
    }

    /** Reusable logic to build the standard App Limit overlay content (stats, duration buttons, close app). */
    private fun buildMainOverlayContent(
        context: Context,
        packageName: String,
        appName: String,
        mode: AppLimitOverlayMode,
        usedMinutes: Int,
        remainingDailyMinutes: Int,
        dailyLimitMinutes: Int,
        emergencyUsesCount: Int,
        emergencyUsesAllowed: Int,
        isStrict: Boolean,
        streakDays: Int,
        contentWrapper: LinearLayout
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val isExhausted = mode == AppLimitOverlayMode.DAILY_LIMIT_REACHED

        // --- Strict-mode upsell banner (only when strict mode is off for this app) ---
        if (!isStrict) {
            contentWrapper.addView(strictModeBanner(context))
        }

        // --- Usage stats card ---
        contentWrapper.addView(
            statsCard(
                context = context,
                packageName = packageName,
                usedMinutes = usedMinutes,
                remainingDailyMinutes = remainingDailyMinutes,
                dailyLimitMinutes = dailyLimitMinutes,
                streakDays = streakDays,
                isExhausted = isExhausted
            )
        )

        // --- Prompt ---
        val promptText = TextView(context).apply {
            text = if (mode == AppLimitOverlayMode.SESSION_COMPLETE) {
                "Time's up! Choose time to continue or exit"
            } else {
                "How long do you want to use?"
            }
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(12))
        }
        contentWrapper.addView(promptText)

        if (!isExhausted) {
            val options = AppLimitDurationOptions.forRemainingMinutes(remainingDailyMinutes)
            options.chunked(2).forEachIndexed { index, row ->
                val durationRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { if (index > 0) topMargin = dp(10) }
                }
                for (mins in row) {
                    durationRow.addView(
                        durationButton(
                            context = context,
                            packageName = packageName,
                            appName = appName,
                            minutes = mins,
                            isMaxChoice = mins == remainingDailyMinutes && remainingDailyMinutes < 20
                        ),
                        LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                            leftMargin = dp(6)
                            rightMargin = dp(6)
                        }
                    )
                }
                if (row.size == 1) {
                    durationRow.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                }
                contentWrapper.addView(durationRow)
            }
        } else {
            val remainingEmergencies = (emergencyUsesAllowed - emergencyUsesCount).coerceAtLeast(0)
            if (remainingEmergencies > 0 && !isStrict) {
                val emergencyBtn = TextView(context).apply {
                    text = "🚨 Use Emergency Pass ($remainingEmergencies left)"
                    textSize = 14f
                    setTextColor(Color.parseColor("#FBBF24"))
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#261F12"))
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), Color.parseColor("#634A1B"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(12) }
                    setOnClickListener {
                        hideOverlay()
                        AppLimitManager.instance.startEmergencySession(packageName, appName) { started ->
                            if (!started) {
                                Toast.makeText(context, "No emergency passes left", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                contentWrapper.addView(emergencyBtn)
            }
        }

        // --- Close button (dark forest green, full width pill, vivid lime text) ---
        val closeBtn = TextView(context).apply {
            text = "Close $appName"
            textSize = 16f
            setTextColor(Color.parseColor("#22E550"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#073E16"))
                cornerRadius = dp(28).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { topMargin = dp(18) }
            setOnClickListener {
                hideOverlay()
                AppLimitManager.instance.onBlockerDismissedByUser(packageName)
                goHome(context)
                schedulePostDismissExitConfirmation(packageName)
            }
        }
        contentWrapper.addView(closeBtn)
    }

    /** Multi-step confirmation for turning off the limit (issue #68). */
    private fun showTurnOffConfirmation(
        context: Context,
        packageName: String,
        appName: String,
        streakDays: Int,
        contentWrapper: LinearLayout,
        onCancel: () -> Unit,
        isStrict: Boolean = false
    ) {
        contentWrapper.removeAllViews()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        // 1. Title & Message
        val title = TextView(context).apply {
            text = "Turn off $appName limit?"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        contentWrapper.addView(title)

        val message = TextView(context).apply {
            text = "Turning off resets your $streakDays-day discipline streak for this app. Are you sure?"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        contentWrapper.addView(message)

        // 2. Buttons: "For today" (disable), "Completely" (remove), "Cancel"
        val optionForToday = TextView(context).apply {
            text = "Turn off for the rest of today"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26272D"))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#333642"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { bottomMargin = dp(12) }
            setOnClickListener {
                overlayScope.launch {
                    val allowed = try {
                        val engine = try { AppLimitStrictModeEngine.instance } catch (_: Exception) { null }
                        !isStrict && (engine?.canTurnOffLimit(packageName) ?: true)
                    } catch (_: Exception) {
                        false
                    }
                    if (!allowed) {
                        Toast.makeText(
                            context,
                            "Strict Mode is on: $appName's limit cannot be turned off or removed",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    val success = AppLimitManager.instance.leaveBlockForToday(packageName, alsoDisableLimit = true)
                    if (success) {
                        hideOverlay()
                        Log.i(TAG, "Limit for $appName turned off for today (disabled)")
                    } else {
                        Toast.makeText(context, "Strict Mode is on: $appName's limit cannot be turned off", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        contentWrapper.addView(optionForToday)

        val optionCompletely = TextView(context).apply {
            text = "Turn off completely"
            textSize = 15f
            setTextColor(Color.parseColor("#F87171")) // soft red
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2D1D1D"))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#4B2A2A"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { bottomMargin = dp(12) }
            setOnClickListener {
                overlayScope.launch {
                    val allowed = try {
                        val engine = try { AppLimitStrictModeEngine.instance } catch (_: Exception) { null }
                        !isStrict && (engine?.canTurnOffLimit(packageName) ?: true)
                    } catch (_: Exception) {
                        false
                    }
                    if (!allowed) {
                        Toast.makeText(
                            context,
                            "Strict Mode is on: $appName's limit cannot be turned off or removed",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    AppLimitManager.instance.removeLimitPermanently(packageName)
                    hideOverlay()
                    Log.i(TAG, "Limit for $appName removed permanently")
                }
            }
        }
        contentWrapper.addView(optionCompletely)

        val cancelBtn = TextView(context).apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onCancel() }
        }
        contentWrapper.addView(cancelBtn)
    }

    /** "Strict Locked" pill: lock glyph + label in amber; strict mode refuses with a toast. */
    private fun strictLockedPill(context: Context, onClick: () -> Unit): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), dp(context, 7), dp(context, 14), dp(context, 7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#272016"))
                cornerRadius = dp(context, 20).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#F59E0B"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        pill.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_lock)
            setColorFilter(Color.parseColor("#F59E0B"))
            layoutParams = LinearLayout.LayoutParams(dp(context, 15), dp(context, 15))
        })
        pill.addView(TextView(context).apply {
            text = "Strict Locked"
            textSize = 12f
            setTextColor(Color.parseColor("#F59E0B"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(context, 6) }
        })
        return pill
    }

    /** "Turn off" pill: power glyph + label; strict mode refuses with a toast (issue #68). */
    private fun turnOffPill(context: Context, onClick: () -> Unit): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), dp(context, 7), dp(context, 14), dp(context, 7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#272A36"))
                cornerRadius = dp(context, 20).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        pill.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_power_settings)
            layoutParams = LinearLayout.LayoutParams(dp(context, 15), dp(context, 15))
        })
        pill.addView(TextView(context).apply {
            text = "Turn off"
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(context, 6) }
        })
        return pill
    }

    /**
     * Shows a confirmation UI before turning off the limit (issue #68).
     */
    private fun onTurnOffClicked(
        context: Context,
        packageName: String,
        appName: String,
        streakDays: Int,
        contentWrapper: LinearLayout,
        onCancel: () -> Unit
    ) {
        overlayScope.launch {
            val allowed = try {
                // Safe check if engine is initialized
                val engine = try { AppLimitStrictModeEngine.instance } catch (_: Exception) { null }
                engine?.canTurnOffLimit(packageName) ?: true
            } catch (_: Exception) {
                false
            }
            if (!allowed) {
                Toast.makeText(
                    context,
                    "Strict Mode is on: $appName's limit cannot be turned off or removed",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            showTurnOffConfirmation(
                context = context,
                packageName = packageName,
                appName = appName,
                streakDays = streakDays,
                contentWrapper = contentWrapper,
                onCancel = onCancel,
                isStrict = false
            )
        }
    }

    /** Amber upsell banner; tapping anywhere on it opens FocusShield. */
    private fun strictModeBanner(context: Context): View {
        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E2610"))
                cornerRadius = dp(context, 16).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#5C4A1A"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4) }
            isClickable = true
            isFocusable = true
            setOnClickListener { openFocusShield(context) }
        }
        banner.addView(TextView(context).apply {
            text = "🔒"
            textSize = 14f
        })
        banner.addView(TextView(context).apply {
            text = "Introducing Strict Mode"
            textSize = 13f
            setTextColor(Color.parseColor("#FBBF24"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(context, 8) }
        })
        banner.addView(
            View(context),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )
        banner.addView(TextView(context).apply {
            text = "Try now ›"
            textSize = 13f
            setTextColor(Color.parseColor("#FBBF24"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        return banner
    }

    /**
     * Stats card: app icon + daily limit, streak, a usage progress bar, and the
     * "Spent today" / "Limit left" pair.
     */
    private fun statsCard(
        context: Context,
        packageName: String,
        usedMinutes: Int,
        remainingDailyMinutes: Int,
        dailyLimitMinutes: Int,
        streakDays: Int,
        isExhausted: Boolean
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2027"))
                cornerRadius = dp(context, 18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 12) }
        }

        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appIcon = loadAppIcon(context, packageName)
        if (appIcon != null) {
            row1.addView(ImageView(context).apply {
                setImageDrawable(appIcon)
                layoutParams = LinearLayout.LayoutParams(dp(context, 24), dp(context, 24))
            })
        }
        row1.addView(TextView(context).apply {
            text = "${formatMinutes(dailyLimitMinutes)} limit"
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(context, 8) }
        })
        row1.addView(
            View(context),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )
        row1.addView(TextView(context).apply {
            text = "🔥 $streakDays days"
            textSize = 12f
            setTextColor(Color.parseColor("#F59E0B"))
        })
        card.addView(row1)

        // Progress bar: thick vivid hot pink red fill matching reference image.
        val total = dailyLimitMinutes.coerceAtLeast(1)
        val usedFraction = (usedMinutes.toFloat() / total.toFloat()).coerceIn(0.04f, 1f)
        val track = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242630"))
                cornerRadius = dp(context, 14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 28)
            ).apply { topMargin = dp(context, 16) }
        }
        track.addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF0055")) // Vivid Hot Pink Red from image
                    cornerRadius = dp(context, 14).toFloat()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, usedFraction)
        )
        if (usedFraction < 1f) {
            track.addView(
                View(context),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - usedFraction)
            )
        }
        card.addView(track)

        // Bottom stats: Left stacked (Spent today) vs Right stacked (Limit left)
        val row3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 16) }
        }

        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = formatMinutes(usedMinutes)
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "Spent today"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
        }
        row3.addView(leftCol)

        row3.addView(
            View(context),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )

        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(TextView(context).apply {
                text = formatMinutes(remainingDailyMinutes)
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.END
            })
            addView(TextView(context).apply {
                text = "Limit left"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
                gravity = Gravity.END
            })
        }
        row3.addView(rightCol)

        card.addView(row3)
        return card
    }

    /** One duration pill; starts the chosen usage session and keeps the user in the app. */
    private fun durationButton(
        context: Context,
        packageName: String,
        appName: String,
        minutes: Int,
        isMaxChoice: Boolean
    ): View {
        return TextView(context).apply {
            text = when {
                minutes == 1 -> "1 min"
                isMaxChoice -> "${minutes}m (Max)"
                else -> "$minutes mins"
            }
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26272D"))
                cornerRadius = dp(context, 28).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#333642"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                overlayScope.launch {
                    // Keep the blocker on screen until the session is actually registered.
                    // Hiding it first and starting asynchronously let the app be usable with no
                    // session (and no blocker) during the gap.
                    val started = try {
                        AppLimitManager.instance.startTemporarySession(
                            packageName = packageName,
                            appName = appName,
                            durationMinutes = minutes
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start $minutes minute session for $packageName", e)
                        false
                    }
                    if (started) {
                        hideOverlay()
                        // User remains right here in the limited app.
                    } else {
                        Toast.makeText(
                            context,
                            "This limit changed. Close and reopen $appName to try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun loadAppIcon(context: Context, packageName: String): Drawable? =
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }

    /** "95m", "4h", "4h 35m" — the reference sheet's minute/hour format. */
    private fun formatMinutes(minutes: Int): String {
        val safeMinutes = minutes.coerceAtLeast(0)
        return if (safeMinutes >= 60) {
            val hours = safeMinutes / 60
            val mins = safeMinutes % 60
            if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
        } else {
            "${safeMinutes}m"
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Leaves to the launcher, preferring the accessibility service's global HOME action. */
    private fun goHome(context: Context) {
        val service = com.example.core.accessibility.FocusAccessibilityService.instance
        if (service != null) {
            try {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                return
            } catch (_: Exception) {
                // fall through to the intent
            }
        }
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to return home: ${e.message}")
        }
    }

    /**
     * Confirms that the user has left [dismissedPackage] after tapping "Close <app>", even if the
     * Home transition was handled by SystemUI/Recents or an OEM launcher that did not emit a
     * tracked window-state accessibility event.
     */
    private fun schedulePostDismissExitConfirmation(dismissedPackage: String) {
        for (delayMs in longArrayOf(450L, 1000L)) {
            mainHandler.postDelayed({
                try {
                    val activePkg = com.example.core.accessibility.FocusAccessibilityService.instance
                        ?.rootInActiveWindow?.packageName?.toString()
                    if (activePkg == null || activePkg != dismissedPackage) {
                        AppLimitManager.instance.onUserLeftDismissedApp(
                            dismissedPackage = dismissedPackage,
                            observedPackage = activePkg?.takeIf { it.isNotBlank() } ?: "com.android.launcher3"
                        )
                    }
                } catch (_: Exception) {}
            }, delayMs)
        }
    }

    /** Opens the FocusShield app itself (strict-mode upsell, "Try now"). */
    private fun openFocusShield(context: Context) {
        // This blocker is a non-focusable accessibility overlay; it would otherwise stay on top
        // of the FocusShield screen it is opening. Dismiss it before the activity starts rather
        // than waiting for a foreground event that the overlay itself complicates.
        hideOverlay()
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent == null) {
                Log.w(TAG, "No launch intent for FocusShield; cannot open the app")
                return
            }
            val service = com.example.core.accessibility.FocusAccessibilityService.instance
            if (service != null) {
                try {
                    service.startActivity(intent)
                    return
                } catch (_: Exception) {
                    // fall through to the plain context
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open FocusShield: ${e.message}")
        }
    }
}
