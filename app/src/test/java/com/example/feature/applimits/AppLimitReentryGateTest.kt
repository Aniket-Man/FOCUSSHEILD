package com.example.feature.applimits

import com.example.feature.applimits.engine.AppLimitReentryGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for "closing the expired-session overlay immediately re-prompts" (issue #53).
 *
 * The sequence these tests encode is the exact one the user reported:
 *
 * ```
 * temporary session expires -> SESSION_COMPLETE blocker -> "Close <app>" -> home
 *      -> stray events from the app being torn down    (must NOT prompt)
 *      -> user does something else                      (must NOT prompt)
 *      -> user genuinely reopens the limited app        (MUST prompt again)
 * ```
 */
class AppLimitReentryGateTest {

    private val limited = "com.google.android.youtube"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val other = "com.whatsapp"

    private lateinit var gate: AppLimitReentryGate

    @Before
    fun setUp() {
        gate = AppLimitReentryGate()
    }

    @Test
    fun `a package nobody dismissed is never suppressed`() {
        assertFalse(gate.isArmedFor(limited))
        gate.onForegroundPackage(limited)
        assertFalse(gate.isArmedFor(limited))
    }

    @Test
    fun `dismissing the blocker suppresses the next decision for that package only`() {
        gate.onBlockerDismissed(limited)

        assertTrue(gate.isArmedFor(limited))
        assertFalse("Other apps must be unaffected", gate.isArmedFor(other))
    }

    @Test
    fun `events that still report the limited app during teardown do not re-open the gate`() {
        gate.onBlockerDismissed(limited)

        // The overlay is finishing and home is being composed; the limited app is still the
        // package naming itself in the events that arrive. This is exactly the window that used
        // to produce the second AWAITING_SELECTION overlay.
        repeat(5) { gate.onForegroundPackage(limited) }

        assertTrue("A teardown event must not count as a re-entry", gate.isArmedFor(limited))
    }

    @Test
    fun `reaching home does not prompt and does not disarm on its own`() {
        gate.onBlockerDismissed(limited)

        gate.onForegroundPackage(launcher)

        assertTrue("Standing on the home screen is not a re-entry", gate.isArmedFor(limited))
    }

    @Test
    fun `a slow teardown never turns continued app events into a re-entry`() {
        gate.onBlockerDismissed(limited)

        // A previous implementation disarmed on elapsed time alone after 1.5 s. On a slow device
        // the home transition can take longer than that, and the next teardown event from the
        // limited app was then mistaken for a fresh entry and raised the blocker over Home.
        repeat(10_000) {
            gate.onForegroundPackage(limited)
            assertTrue(gate.isArmedFor(limited))
        }
    }

    @Test
    fun `reading the gate never mutates suppression state`() {
        gate.onBlockerDismissed(limited)

        repeat(1_000) { assertTrue(gate.isArmedFor(limited)) }

        assertTrue("isArmedFor must be a pure read", gate.isArmedFor(limited))
    }

    @Test
    fun `a genuine re-entry restores normal enforcement`() {
        gate.onBlockerDismissed(limited)
        gate.onForegroundPackage(launcher)

        gate.onForegroundPackage(limited)

        assertFalse("Opening the app again must let the engine decide normally", gate.isArmedFor(limited))
    }

    @Test
    fun `the full reported sequence suppresses exactly one prompt`() {
        gate.onBlockerDismissed(limited)
        gate.onForegroundPackage(limited)   // teardown event
        gate.onForegroundPackage(launcher)  // home
        gate.onForegroundPackage(other)     // user goes somewhere else
        gate.onForegroundPackage(launcher)  // back to home
        assertTrue(gate.isArmedFor(limited))

        gate.onForegroundPackage(limited)   // genuine re-entry
        assertFalse(gate.isArmedFor(limited))

        // ...and enforcement stays on from then on: a later visit is never suppressed again.
        gate.onForegroundPackage(launcher)
        gate.onForegroundPackage(limited)
        assertFalse(gate.isArmedFor(limited))
    }

    @Test
    fun `starting a session or dropping the limit clears the gate immediately`() {
        gate.onBlockerDismissed(limited)
        gate.clear(limited)

        assertFalse(gate.isArmedFor(limited))
    }

    @Test
    fun `two apps are tracked independently`() {
        val instagram = "com.instagram.android"
        gate.onBlockerDismissed(limited)
        gate.onBlockerDismissed(instagram)

        gate.onForegroundPackage(launcher)
        gate.onForegroundPackage(limited)

        assertFalse("YouTube was re-entered", gate.isArmedFor(limited))
        assertTrue("Instagram was not", gate.isArmedFor(instagram))
    }

    @Test
    fun `blank package names are ignored`() {
        gate.onBlockerDismissed("")
        gate.onForegroundPackage("")

        assertFalse(gate.isArmedFor(""))
    }
}
