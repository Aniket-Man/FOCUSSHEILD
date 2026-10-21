package com.example.feature.applimits

import android.content.Context
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.applimits.ui.AppLimitFloatingOverlayManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The floating blocker has no activity lifecycle, so these tests pin the three things that
 * keep it from getting stuck: dismissal when the blocked app leaves the foreground, state
 * clearing when the system removes the window behind our back (otherwise the
 * "blocker already on screen" guard would refuse to ever raise a blocker again), and the
 * ordinary show/hide round-trip the gate depends on.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application keeps the production FocusShieldApp from booting Firebase/Firestore in a
// unit test; its background SQLite worker raced these tests and failed opening a temp DB.
@Config(sdk = [34], application = android.app.Application::class)
class AppLimitFloatingOverlayManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLimitFloatingOverlayManager.hideOverlay()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun show(
        pkg: String = "com.some.limited.app",
        appName: String = "Some App",
        mode: AppLimitOverlayMode = AppLimitOverlayMode.AWAITING_SELECTION,
        usedMinutes: Int = 3,
        remainingDailyMinutes: Int = 12,
        dailyLimitMinutes: Int = 15,
        emergencyUsesCount: Int = 0,
        emergencyUsesAllowed: Int = 1,
        isStrict: Boolean = false,
        streakDays: Int = 0
    ) {
        AppLimitFloatingOverlayManager.showOverlay(
            context = context,
            packageName = pkg,
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
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun currentOverlayView(): View =
        AppLimitFloatingOverlayManager::class.java
            .getDeclaredField("overlayView")
            .apply { isAccessible = true }
            .get(AppLimitFloatingOverlayManager) as View

    private fun containsText(view: View, text: String): Boolean {
        if (view is TextView && view.text == text) return true
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsText(view.getChildAt(i), text)) return true
            }
        }
        return false
    }

    @Test
    fun `a shown overlay reports its own package and no other`() {
        show()

        assertTrue(AppLimitFloatingOverlayManager.isShowing)
        assertTrue(AppLimitFloatingOverlayManager.isShowingFor("com.some.limited.app"))
        assertFalse(AppLimitFloatingOverlayManager.isShowingFor("com.other.app"))
    }

    @Test
    fun `hideOverlay clears every piece of showing state`() {
        show()
        AppLimitFloatingOverlayManager.hideOverlay()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(AppLimitFloatingOverlayManager.isShowing)
        assertFalse(AppLimitFloatingOverlayManager.isShowingFor("com.some.limited.app"))
    }

    @Test
    fun `leaving the blocked package dismisses the blocker`() {
        show()

        AppLimitFloatingOverlayManager.onForegroundPackageChanged("com.android.launcher3")
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(AppLimitFloatingOverlayManager.isShowing)
        assertFalse(AppLimitFloatingOverlayManager.isShowingFor("com.some.limited.app"))
    }

    @Test
    fun `staying in the blocked package keeps the blocker up`() {
        show()

        AppLimitFloatingOverlayManager.onForegroundPackageChanged("com.some.limited.app")
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(AppLimitFloatingOverlayManager.isShowingFor("com.some.limited.app"))
    }

    @Test
    fun `a system-initiated detach resyncs state so the gate cannot stay shut forever`() {
        show()
        val view = currentOverlayView()

        AppLimitFloatingOverlayManager.handleUnexpectedDetach(view)

        assertFalse(AppLimitFloatingOverlayManager.isShowing)
        assertFalse(AppLimitFloatingOverlayManager.isShowingFor("com.some.limited.app"))
    }

    @Test
    fun `a detach callback from an old window does not disturb the current one`() {
        show()
        val oldView = currentOverlayView()

        AppLimitFloatingOverlayManager.hideOverlay()
        shadowOf(Looper.getMainLooper()).idle()
        show()
        val newView = currentOverlayView()

        AppLimitFloatingOverlayManager.handleUnexpectedDetach(oldView)

        assertTrue(AppLimitFloatingOverlayManager.isShowing)
        assertTrue(AppLimitFloatingOverlayManager.isShowingFor("com.some.limited.app"))
        assertTrue(oldView !== newView)
    }

    // --- Bottom-sheet layout (issue #68) ---

    @Test
    fun `the sheet is anchored to the bottom edge with a slide-up animation`() {
        show()
        val root = currentOverlayView()

        val params = root.layoutParams
        assertTrue(
            "the blocker window must be anchored to the bottom edge",
            params is WindowManager.LayoutParams && params.gravity == Gravity.BOTTOM
        )
        if (params is WindowManager.LayoutParams) {
            assertEquals(
                "the sheet must slide up from the bottom",
                R.style.AppLimitBlockerSheetAnimation,
                params.windowAnimations
            )
        }
    }

    @Test
    fun `the selection sheet offers 2 5 10 and 20 minute choices`() {
        show(remainingDailyMinutes = 60, dailyLimitMinutes = 60)
        val root = currentOverlayView()

        assertTrue(containsText(root, "2 mins"))
        assertTrue(containsText(root, "5 mins"))
        assertTrue(containsText(root, "10 mins"))
        assertTrue(containsText(root, "20 mins"))
        assertTrue(containsText(root, "Close Some App"))
        assertTrue(containsText(root, "Turn off"))
        assertTrue(containsText(root, "How long do you want to use?"))
    }

    @Test
    fun `a choice longer than the time left today is not offered`() {
        show(remainingDailyMinutes = 12, dailyLimitMinutes = 15)
        val root = currentOverlayView()

        assertTrue(containsText(root, "10 mins"))
        assertFalse(containsText(root, "20 mins"))
    }

    @Test
    fun `the exact remaining allowance is offered as a max choice`() {
        show(remainingDailyMinutes = 12, dailyLimitMinutes = 15)
        val root = currentOverlayView()

        assertTrue(
            "12 minutes left should be selectable, not just the standard choices below it",
            containsText(root, "12m (Max)")
        )
    }

    @Test
    fun `one minute left does not offer a two minute choice`() {
        show(remainingDailyMinutes = 1, dailyLimitMinutes = 15)
        val root = currentOverlayView()

        assertTrue(containsText(root, "1 min"))
        assertFalse(
            "the engine would have clamped a 2 minute choice to the remaining minute",
            containsText(root, "2 mins")
        )
    }

    @Test
    fun `the strict-mode upsell banner is hidden once strict mode is on`() {
        show(isStrict = false)
        assertTrue(containsText(currentOverlayView(), "Introducing Strict Mode"))

        AppLimitFloatingOverlayManager.hideOverlay()
        shadowOf(Looper.getMainLooper()).idle()
        show(isStrict = true)
        assertFalse(containsText(currentOverlayView(), "Introducing Strict Mode"))
    }

    @Test
    fun `an exhausted daily limit offers the emergency pass instead of minute choices`() {
        show(
            mode = AppLimitOverlayMode.DAILY_LIMIT_REACHED,
            usedMinutes = 15,
            remainingDailyMinutes = 0,
            emergencyUsesAllowed = 2
        )
        val root = currentOverlayView()

        assertTrue(containsText(root, "🚨 Use Emergency Pass (2 left)"))
        assertFalse(containsText(root, "5 mins"))
        assertTrue(containsText(root, "Close Some App"))
    }

    @Test
    fun `an exhausted daily limit in strict mode offers no emergency pass`() {
        show(
            mode = AppLimitOverlayMode.DAILY_LIMIT_REACHED,
            usedMinutes = 15,
            remainingDailyMinutes = 0,
            isStrict = true
        )
        val root = currentOverlayView()

        assertFalse(containsText(root, "🚨 Use Emergency Pass (1 left)"))
        assertTrue(containsText(root, "Close Some App"))
    }
}
