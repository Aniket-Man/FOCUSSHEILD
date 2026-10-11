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

    val isShowing: Boolean
        get() = overlayView != null

    fun isShowingFor(packageName: String): Boolean =
        overlayView != null && showingPackageName == packageName

    /**
     * Called from [AppLimitManager.onForegroundPackageChanged]. The overlay has no activity
     * lifecycle, so this is the only thing that dismisses it when the limited app it belongs
     * to leaves the foreground (Home, app switch, SystemUI, IME) instead of letting the
     * full-screen scrim sit on top of whatever comes next. Returning to the limited app
     * re-raises it through the accessibility gate.
     */
    fun onForegroundPackageChanged(packageName: String) {
        val showing = showingPackageName ?: return
        if (packageName != showing) {
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
            // If already showing for this package, keep it
            if (overlayView != null && showingPackageName == packageName) {
                onResult?.invoke(true)
                return@post
            }
            // If showing for another package, remove previous first. This must be the
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
                    Log.i(TAG, "Floating App Limit overlay presented for $appName ($packageName)")
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

        // --- Top row: brand mark (left) + "Turn off" pill (right) ---
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        val brandIcon = ImageView(context).apply {
            setImageResource(R.drawable.focus_shield_wordmark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(20)
            )
        }
        topRow.addView(brandIcon)
        topRow.addView(
            View(context),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )
        topRow.addView(turnOffPill(context, packageName, appName))
        sheet.addView(topRow)

        // --- Strict-mode upsell banner (only when strict mode is off for this app) ---
        if (!isStrict) {
            sheet.addView(strictModeBanner(context))
        }

        // --- Usage stats card ---
        sheet.addView(
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
            text = "How long do you want to use?"
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(12))
        }
        sheet.addView(promptText)

        if (!isExhausted) {
            // --- Duration options in a 2-column grid (2 / 5 / 10 / 20 mins) ---
            val options = listOf(2, 5, 10, 20)
                .filter { it <= remainingDailyMinutes.coerceAtLeast(2) }
                .ifEmpty { listOf(remainingDailyMinutes.coerceAtLeast(1)) }

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
                        durationButton(context, packageName, appName, mins),
                        LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                            leftMargin = dp(6)
                            rightMargin = dp(6)
                        }
                    )
                }
                if (row.size == 1) {
                    durationRow.addView(
                        View(context),
                        LinearLayout.LayoutParams(0, 0, 1f)
                    )
                }
                sheet.addView(durationRow)
            }
        } else {
            // Daily limit reached: emergency pass if permitted
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
                sheet.addView(emergencyBtn)
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
            }
        }
        sheet.addView(closeBtn)

        root.addView(sheet)
        return root
    }

    /** "Turn off" pill: power glyph + label; strict mode refuses with a toast (issue #68). */
    private fun turnOffPill(context: Context, packageName: String, appName: String): View {
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
            setOnClickListener { onTurnOffClicked(context, packageName, appName) }
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
     * Turns the limited app's block off for today — unless strict mode forbids it, in which
     * case the pill does nothing but explain why. Then it leaves to Home so the now-allowed
     * app is not sitting behind the blocker.
     */
    private fun onTurnOffClicked(context: Context, packageName: String, appName: String) {
        overlayScope.launch {
            val allowed = try {
                AppLimitStrictModeEngine.instance.canTurnOffLimit(packageName)
            } catch (_: Exception) {
                false
            }
            if (!allowed) {
                Toast.makeText(
                    context,
                    "Strict Mode is on: $appName's limit can't be turned off",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            hideOverlay()
            AppLimitManager.instance.leaveBlockForToday(packageName)
            Log.i(TAG, "Limit for $appName ($packageName) turned off for today by the user")
            goHome(context)
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
        minutes: Int
    ): View {
        return TextView(context).apply {
            text = "$minutes mins"
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
                hideOverlay()
                AppLimitManager.instance.startTemporarySession(
                    packageName = packageName,
                    appName = appName,
                    durationMinutes = minutes
                )
                // User remains right here in the limited app!
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

    /** Opens the FocusShield app itself (strict-mode upsell, "Try now"). */
    private fun openFocusShield(context: Context) {
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
