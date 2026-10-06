package com.example.feature.applimits

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.applimits.ui.AppLimitFloatingOverlayManager
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
@Config(sdk = [34])
class AppLimitFloatingOverlayManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLimitFloatingOverlayManager.hideOverlay()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun show(pkg: String = "com.some.limited.app") {
        AppLimitFloatingOverlayManager.showOverlay(
            context = context,
            packageName = pkg,
            appName = "Some App",
            mode = AppLimitOverlayMode.AWAITING_SELECTION,
            usedMinutes = 3,
            remainingDailyMinutes = 12,
            dailyLimitMinutes = 15
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun currentOverlayView(): View =
        AppLimitFloatingOverlayManager::class.java
            .getDeclaredField("overlayView")
            .apply { isAccessible = true }
            .get(AppLimitFloatingOverlayManager) as View

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
}
