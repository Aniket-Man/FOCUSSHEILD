package com.example.feature.applimits.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import com.example.R
import com.example.feature.applimits.engine.AppLimitManager
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.applimits.engine.AppLimitStrictModeEngine

/**
 * Native floating WindowManager overlay that appears directly on top of limited applications
 * without launching FocusShield activities or changing the Android task backstack.
 */
object AppLimitFloatingOverlayManager {

    private const val TAG = "AppLimitFloatingOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

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
            // If showing for another package, remove previous first
            hideOverlay()

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
        mainHandler.post {
            val view = overlayView ?: return@post
            val wm = windowManager
            try {
                wm?.removeView(view)
                Log.i(TAG, "Floating App Limit overlay removed for $showingPackageName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floating App Limit overlay", e)
            } finally {
                overlayView = null
                windowManager = null
                showingPackageName = null
            }
        }
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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

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

        // Root container: Full screen translucent scrim directly over the limited app
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.parseColor("#CC08090C"))
        }

        // Floating Card
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#17181F"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#2E313D"))
            }
            elevation = dp(16).toFloat()
        }

        // Top Row: Brand Mark + Strict badge (if strict)
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
            setColorFilter(Color.parseColor("#8B5CF6"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        }
        topRow.addView(brandIcon)

        if (isStrict) {
            val strictBadge = TextView(context).apply {
                text = "🔒 Strict Mode"
                textSize = 11f
                setTextColor(Color.parseColor("#F87171"))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A171A"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#4A2024"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = dp(8)
                }
            }
            topRow.addView(strictBadge)
        }

        // Header Title
        val titleText = TextView(context).apply {
            text = appName
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        // Subtitle / Usage stat
        val isExhausted = mode == AppLimitOverlayMode.DAILY_LIMIT_REACHED
        val statusText = TextView(context).apply {
            text = if (isExhausted) {
                "Daily limit of ${dailyLimitMinutes}m reached for today"
            } else {
                "${remainingDailyMinutes}m remaining today • Used ${usedMinutes}m"
            }
            textSize = 13f
            setTextColor(if (isExhausted) Color.parseColor("#F87171") else Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(16))
        }

        card.addView(topRow)
        card.addView(titleText)
        card.addView(statusText)

        // If awaiting selection: show choice buttons
        if (!isExhausted) {
            val promptText = TextView(context).apply {
                text = "How long do you want to use $appName?"
                textSize = 14f
                setTextColor(Color.parseColor("#E2E8F0"))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }
            card.addView(promptText)

            // Duration options row
            val durationRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(16) }
            }

            val options = listOf(5, 10, 15, 30).filter { it <= remainingDailyMinutes.coerceAtLeast(5) }
                .ifEmpty { listOf(remainingDailyMinutes.coerceAtLeast(1)) }

            for (mins in options) {
                val btn = TextView(context).apply {
                    text = "${mins}m"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#272A36"))
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), Color.parseColor("#3B4052"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        leftMargin = dp(4)
                        rightMargin = dp(4)
                    }
                    setOnClickListener {
                        hideOverlay()
                        AppLimitManager.instance.startTemporarySession(
                            packageName = packageName,
                            appName = appName,
                            durationMinutes = mins
                        )
                        // User remains right here in the limited app!
                    }
                }
                durationRow.addView(btn)
            }
            card.addView(durationRow)
        } else {
            // Daily limit reached: Emergency option if permitted
            val remainingEmergencies = (emergencyUsesAllowed - emergencyUsesCount).coerceAtLeast(0)
            if (remainingEmergencies > 0 && !isStrict) {
                val emergencyBtn = TextView(context).apply {
                    text = "🚨 Use Emergency Pass ($remainingEmergencies left)"
                    textSize = 14f
                    setTextColor(Color.parseColor("#FBBF24"))
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#261F12"))
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), Color.parseColor("#634A1B"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(12) }
                    setOnClickListener {
                        hideOverlay()
                        AppLimitManager.instance.startEmergencySession(packageName, appName) { started ->
                            if (!started) {
                                android.widget.Toast.makeText(context, "No emergency passes left", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                card.addView(emergencyBtn)
            }
        }

        // Close App Button (Returns user to launcher Home without opening FocusShield)
        val closeBtn = TextView(context).apply {
            text = "Close $appName"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8B5CF6"))
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                hideOverlay()
                AppLimitManager.instance.onBlockerDismissedByUser(packageName)
                val service = com.example.core.accessibility.FocusAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                } else {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                }
            }
        }
        card.addView(closeBtn)

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }
}
