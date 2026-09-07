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
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Manages floating display-over-app HUD banners and toast alerts.
 * Displays a compact, sleek floating notification pill at the bottom of the screen with the Focus Shield app logo
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
     * Renders a compact floating HUD pill banner at the bottom of the screen or falls back to standard Toast.
     */
    fun showHud(
        context: Context,
        title: String,
        message: String,
        accentColor: Int = Color.parseColor("#38BDF8"),
        durationMs: Long = 2800L
    ) {
        mainHandler.post {
            try {
                if (Settings.canDrawOverlays(context)) {
                    renderWindowOverlay(context, title, message, accentColor, durationMs)
                } else {
                    // Fallback to stylized Toast if overlay permission is not granted
                    Toast.makeText(
                        context.applicationContext,
                        "🛡️ FocusShield: $title\n$message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying HUD overlay", e)
                try {
                    Toast.makeText(
                        context.applicationContext,
                        "🛡️ $title: $message",
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
        durationMs: Long
    ) {
        dismissActiveHud()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        // Square card with small rounded corners and white background
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(16), dp(10))
            gravity = Gravity.CENTER_VERTICAL

            val bg = GradientDrawable().apply {
                setColor(Color.WHITE) // Pure white background
                cornerRadius = dp(10).toFloat() // Small rounded corners (square card shape)
                setStroke(dp(1), Color.parseColor("#E2E8F0")) // Subtle clean border
            }
            background = bg
            elevation = dp(10).toFloat()
        }

        // Left accent strip indicator for visual hierarchy
        val accentStrip = View(context).apply {
            val stripParams = LinearLayout.LayoutParams(dp(4), dp(32)).apply {
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

        // Main App Logo (App Launcher Icon)
        val appLogoView = ImageView(context).apply {
            val logoSize = dp(32)
            layoutParams = LinearLayout.LayoutParams(logoSize, logoSize).apply {
                marginEnd = dp(10)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER

            // Use the main Focus Shield app icon
            val appIconDrawable = try {
                context.packageManager.getApplicationIcon(context.packageName)
            } catch (_: Exception) {
                ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                    ?: ContextCompat.getDrawable(context, R.drawable.ic_focus_shield_logo)
            }
            setImageDrawable(appIconDrawable)
        }
        rootLayout.addView(appLogoView)

        // Text content container
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#0F172A")) // High-contrast dark slate
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        textContainer.addView(titleView)

        val messageView = TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#475569")) // Medium dark slate for readability on white
            textSize = 11.5f
            setPadding(0, dp(1), 0, 0)
            maxLines = 1
        }
        textContainer.addView(messageView)

        rootLayout.addView(textContainer)

        // Tap to dismiss
        rootLayout.setOnClickListener {
            dismissActiveHud()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48) // Positioned neatly near the bottom of the screen
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
            mainHandler.postDelayed(dismissRunnable!!, durationMs)
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
