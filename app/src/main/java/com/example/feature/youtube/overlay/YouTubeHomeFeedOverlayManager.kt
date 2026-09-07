package com.example.feature.youtube.overlay

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
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Over-the-display Study Mode popup shown over the YouTube Home feed during an active study session.
 *
 * Behavior contract:
 * - The YouTube Home page REMAINS VISIBLE behind a translucent scrim (Home is a navigation surface only).
 * - The popup explains that Home feed browsing can be distracting and nudges the user toward Search.
 * - The prominent white [Search] button dismisses the popup and navigates directly to YouTube Search.
 * - The subtle "Not now" option dismisses the popup for the current Home visit (no re-show loop).
 */
object YouTubeHomeFeedOverlayManager {

    private const val TAG = "YouTubeOverlayManager"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var windowManager: WindowManager? = null

    val isShowing: Boolean
        get() = overlayView != null

    /**
     * Shows the Study Mode distraction popup over the YouTube Home feed.
     *
     * @param onSearchClick invoked when the user taps the white Search button (popup already dismissed).
     * @param onDismiss invoked when the user taps "Not now" (popup already dismissed).
     */
    fun showHomeFeedStudyModePopup(
        context: Context,
        onSearchClick: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        mainHandler.post {
            if (overlayView != null) return@post // Already presenting

            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm

                val view = buildPopupView(context, onSearchClick, onDismiss)

                if (addViewSafely(wm, view, context)) {
                    overlayView = view
                    Log.i(TAG, "Study Mode Home feed popup presented.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Study Mode popup", e)
            }
        }
    }

    /**
     * Removes the Study Mode popup if currently presented.
     */
    fun hideHomeFeedPopup() {
        mainHandler.post {
            val view = overlayView ?: return@post
            val wm = windowManager
            try {
                wm?.removeView(view)
                Log.i(TAG, "Study Mode Home feed popup removed.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove Study Mode popup", e)
            } finally {
                overlayView = null
                windowManager = null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Window management
    // -------------------------------------------------------------------------

    private fun addViewSafely(wm: WindowManager, view: View, context: Context): Boolean {
        // Primary: accessibility overlay window type — usable without SYSTEM_ALERT_WINDOW
        // permission because this manager is driven by the FocusShield accessibility service.
        try {
            wm.addView(view, buildParams(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY))
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Accessibility overlay window type rejected, falling back: ${e.message}")
        }

        // Fallback: standard application overlay when the overlay permission is granted.
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
            wm.addView(view, buildParams(fallbackType))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            false
        }
    }

    private fun buildParams(windowType: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    // -------------------------------------------------------------------------
    // Popup view construction
    // -------------------------------------------------------------------------

    private fun buildPopupView(
        context: Context,
        onSearchClick: (() -> Unit)?,
        onDismiss: (() -> Unit)?
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        // Full-screen translucent scrim: the Home feed stays visible behind it.
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#B3000000"))
        }

        // Dark rounded card
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1B2E"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            elevation = dp(12).toFloat()
        }

        // Shield icon
        val icon = TextView(context).apply {
            text = "🛡️"
            textSize = 30f
            gravity = Gravity.CENTER
        }

        // Title
        val title = TextView(context).apply {
            text = "Study Mode Active"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }

        // Primary message
        val message = TextView(context).apply {
            text = "The YouTube Home feed can be distracting during your study session."
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }

        // Secondary instruction
        val subMessage = TextView(context).apply {
            text = "Use Search to find the specific content you need instead."
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        // Prominent white Search button
        val searchButton = TextView(context).apply {
            text = "🔍  Search"
            textSize = 15f
            setTextColor(Color.parseColor("#0F172A"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(12), dp(32), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            elevation = dp(4).toFloat()
            setOnClickListener {
                hideHomeFeedPopup()
                onSearchClick?.invoke()
            }
        }

        // Subtle dismiss option for the current Home visit
        val notNow = TextView(context).apply {
            text = "Not now"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(4))
            setOnClickListener {
                hideHomeFeedPopup()
                onDismiss?.invoke()
            }
        }

        card.addView(icon)
        card.addView(title)
        card.addView(message)
        card.addView(subMessage)
        card.addView(searchButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(22); gravity = Gravity.CENTER_HORIZONTAL })
        card.addView(notNow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2); gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }
}
