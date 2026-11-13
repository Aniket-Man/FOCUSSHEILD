package com.example.core.overlay

import android.content.Context
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
import android.widget.Toast
import com.example.R

/**
 * Manages floating display-over-app HUD banners and toast alerts.
 * Displays a compact floating notification pill with the folded-map FocusShield brand mark
 * when an addictive short, adult website, or unapproved channel is intercepted.
 */
object FocusDisplayOverlayNotificationManager {

    private const val TAG = "FocusDisplayOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeOverlayView: View? = null

    @Volatile
    private var windowManager: WindowManager? = null

    private var dismissRunnable: Runnable? = null

    /**
     * Show compact bottom HUD when YouTube Shorts is blocked.
     */
    fun showShortsBlockedHud(context: Context) {
        showHud(
            context = context,
            title = "YouTube Shorts Blocked",
            message = "Shorts are blocked to keep you focused.",
            accentColor = Color.parseColor("#EF4444")
        )
    }

    /**
     * Show compact bottom HUD when Facebook or Instagram Reels are blocked.
     */
    fun showReelsBlockedHud(context: Context, appName: String) {
        showHud(
            context = context,
            title = "$appName Reels Blocked",
            message = "Reels are blocked to protect your study time.",
            accentColor = Color.parseColor("#E11D48")
        )
    }

    /**
     * Show compact bottom HUD when an unapproved YouTube channel is opened.
     */
    fun showChannelBlockedHud(context: Context, channelName: String) {
        val cleanName = if (channelName.isNotBlank() && channelName != "Unapproved Channel") channelName else "This channel"
        showHud(
            context = context,
            title = "Channel Not in Study List",
            message = "$cleanName is not listed for this session.",
            accentColor = Color.parseColor("#38BDF8")
        )
    }

    /**
     * Show compact bottom HUD when Auto-Pilot AI Study Guard blocks a non-study video.
     */
    fun showAiVideoBlockedHud(
        context: Context,
        channelName: String,
        reason: String,
        latencyMs: Long? = null,
        onAllowAnyway: (() -> Unit)? = null
    ) {
        val speedBadge = if (latencyMs != null && latencyMs >= 0L) " (${latencyMs}ms)" else ""
        val cleanReason = reason.trim().ifBlank { "$channelName blocked as entertainment." }
        showHud(
            context = context,
            title = "AI Study Guard Blocked$speedBadge",
            message = cleanReason,
            accentColor = Color.parseColor("#8B5CF6"),
            durationMs = if (onAllowAnyway != null) 4500L else 2800L,
            actionLabel = if (onAllowAnyway != null) "Allow Anyway" else null,
            onActionClick = onAllowAnyway
        )
    }

    /**
     * Show compact bottom HUD when an adult website is blocked.
     */
    fun showAdultSiteBlockedHud(context: Context, domain: String) {
        showHud(
            context = context,
            title = "18+ Adult Content Blocked",
            message = "Adult site '$domain' was blocked. Returned to search.",
            accentColor = Color.parseColor("#DC2626")
        )
    }

    /**
     * Show compact bottom HUD when a custom distracting website is blocked.
     */
    fun showCustomWebsiteBlockedHud(context: Context, domain: String) {
        showHud(
            context = context,
            title = "Domain Blocked",
            message = "'$domain' is in your blocked list. Returned to search.",
            accentColor = Color.parseColor("#F59E0B")
        )
    }

    /**
     * Show compact bottom HUD when split-screen is blocked.
     */
    fun showSplitScreenBlockedHud(context: Context) {
        showHud(
            context = context,
            title = "Split-Screen Blocked",
            message = "Multi-window is disabled during focus sessions.",
            accentColor = Color.parseColor("#EF4444")
        )
    }

    /**
     * Show compact bottom HUD when floating window / PiP is blocked.
     */
    fun showFloatingWindowBlockedHud(context: Context) {
        showHud(
            context = context,
            title = "Floating Window Blocked",
            message = "Picture-in-Picture & floating windows are disabled.",
            accentColor = Color.parseColor("#EF4444")
        )
    }

    /**
     * Dismisses any currently visible HUD overlay safely from any thread.
     */
    fun dismissHud() {
        mainHandler.post {
            dismissActiveHud()
        }
    }

    /**
     * Renders a compact floating HUD pill banner at the bottom of the screen or falls back to standard Toast.
     */
    fun showHud(
        context: Context,
        title: String,
        message: String,
        accentColor: Int = Color.parseColor("#38BDF8"),
        durationMs: Long = 2800L,
        actionLabel: String? = null,
        onActionClick: (() -> Unit)? = null
    ) {
        mainHandler.post {
            try {
                if (Settings.canDrawOverlays(context)) {
                    renderWindowOverlay(
                        context = context,
                        title = title,
                        message = message,
                        accentColor = accentColor,
                        durationMs = durationMs,
                        actionLabel = actionLabel,
                        onActionClick = onActionClick
                    )
                } else {
                    // Fallback to stylized Toast if overlay permission is not granted
                    Toast.makeText(
                        context.applicationContext,
                        "FocusShield: $title\n$message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying HUD overlay", e)
                try {
                    Toast.makeText(
                        context.applicationContext,
                        "$title: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {}
            }
        }
    }

    private fun renderWindowOverlay(
        context: Context,
        title: String,
        message: String,
        accentColor: Int,
        durationMs: Long,
        actionLabel: String? = null,
        onActionClick: (() -> Unit)? = null
    ) {
        dismissActiveHud()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val hasAction = !actionLabel.isNullOrBlank() && onActionClick != null

        // Square card with small rounded corners and white background
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(14), dp(10))
            gravity = Gravity.CENTER_VERTICAL

            val bg = GradientDrawable().apply {
                setColor(Color.WHITE) // Pure white background
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#E2E8F0")) // Subtle clean border
            }
            background = bg
            elevation = dp(10).toFloat()
        }

        // Left accent strip indicator for visual hierarchy
        val accentStrip = View(context).apply {
            val stripParams = LinearLayout.LayoutParams(dp(4), dp(34)).apply {
                marginEnd = dp(10)
            }
            layoutParams = stripParams
            val stripBg = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dp(2).toFloat()
            }
            background = stripBg
        }
        rootLayout.addView(accentStrip)

        // Official FocusShield logo with the white open-book mark
        val appLogoView = ImageView(context).apply {
            val logoSize = dp(30)
            layoutParams = LinearLayout.LayoutParams(logoSize, logoSize).apply {
                marginEnd = dp(10)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_focus_shield_app_icon)
        }
        rootLayout.addView(appLogoView)

        // Text content container
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = if (hasAction) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#0F172A")) // High-contrast dark slate
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(titleView)

        val messageView = TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#475569")) // Medium dark slate for readability on white
            textSize = 11.5f
            setPadding(0, dp(1), 0, 0)
            maxLines = if (hasAction) 2 else 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(messageView)

        rootLayout.addView(textContainer)

        if (hasAction) {
            val actionButton = TextView(context).apply {
                text = actionLabel
                setTextColor(Color.parseColor("#6D28D9"))
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = dp(36)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F3E8FF"))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), Color.parseColor("#DDD6FE"))
                }
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(10)
                }
                setOnClickListener {
                    try {
                        onActionClick?.invoke()
                    } finally {
                        dismissActiveHud()
                    }
                }
            }
            rootLayout.addView(actionButton)
        }

        // Tap card body to dismiss
        rootLayout.setOnClickListener {
            dismissActiveHud()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val overlayWidth = if (hasAction) {
            (context.resources.displayMetrics.widthPixels - dp(24)).coerceAtMost(dp(440))
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        val params = WindowManager.LayoutParams(
            overlayWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(56) // Positioned cleanly above bottom gesture bar
        }

        // Animate entrance: Slide up subtly from bottom
        rootLayout.alpha = 0f
        rootLayout.translationY = dp(20).toFloat()

        try {
            wm.addView(rootLayout, params)
            activeOverlayView = rootLayout

            rootLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .start()

            dismissRunnable = Runnable {
                dismissActiveHud()
            }
            dismissRunnable?.let { mainHandler.postDelayed(it, durationMs) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach window overlay", e)
        }
    }

    private fun dismissActiveHud() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        val view = activeOverlayView ?: return
        val wm = windowManager ?: return

        try {
            view.animate()
                .alpha(0f)
                .translationY(30f)
                .setDuration(180)
                .withEndAction {
                    try {
                        wm.removeView(view)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing HUD view", e)
                    } finally {
                        if (activeOverlayView === view) {
                            activeOverlayView = null
                            windowManager = null
                        }
                    }
                }
                .start()
        } catch (e: Exception) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
            activeOverlayView = null
            windowManager = null
        }
    }
}
