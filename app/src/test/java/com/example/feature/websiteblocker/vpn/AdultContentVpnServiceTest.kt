package com.example.feature.websiteblocker.vpn

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Service-level lifecycle tests under Robolectric (which has no real VPN stack):
 *
 *  * `ACTION_STOP` stops the service — the toggle-OFF path leaves nothing running;
 *  * `ACTION_START` / a null `START_STICKY` restart must never crash and must stop itself
 *    cleanly when the tunnel cannot be established ("fail closed"). On a real device the
 *    controller only dispatches starts once consent exists; here the failure path is exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdultContentVpnServiceTest {

    private fun startWith(intent: Intent?): AdultContentVpnService =
        Robolectric.buildService(AdultContentVpnService::class.java, intent)
            .create()
            .startCommand(0, 1)
            .get()

    @Test
    fun `ACTION_STOP stops the service and leaves nothing running`() {
        val service = startWith(Intent(AdultContentVpnService.ACTION_STOP))
        assertTrue("stop action must stop the service", shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `ACTION_START steps down safely when the tunnel cannot be established`() {
        val service = startWith(Intent(AdultContentVpnService.ACTION_START))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `null intent (START_STICKY restart) is treated as START and still fails safe`() {
        val service = startWith(null)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `destroy after a failed start is clean`() {
        val controller = Robolectric.buildService(
            AdultContentVpnService::class.java,
            Intent(AdultContentVpnService.ACTION_START)
        )
        val service = controller.create().startCommand(0, 1).get()
        controller.destroy()
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
