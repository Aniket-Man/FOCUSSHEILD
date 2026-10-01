package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The complete command table connecting the existing 18+ toggle to the engine, plus the tunnel
 * recovery policy. Pure boolean inputs → deterministic commands.
 */
class VpnEnginePolicyTest {

    private val S = VpnEnginePolicy.Command.StartVpn
    private val P = VpnEnginePolicy.Command.RequestVpnPermission
    private val X = VpnEnginePolicy.Command.StopVpn
    private val N = VpnEnginePolicy.Command.Noop

    @Test
    fun `full command table`() {
        // desired, granted, running, foreground -> command
        val table = listOf(
            // Toggle OFF: stop when running, otherwise nothing.
            listOf(false, true, true, true) to X,
            listOf(false, true, true, false) to X,
            listOf(false, true, false, true) to N,
            listOf(false, true, false, false) to N,
            listOf(false, false, true, true) to X,   // consent gone but engine running: still stop
            listOf(false, false, false, false) to N,
            // Toggle ON, already running: leave it alone.
            listOf(true, true, true, true) to N,
            listOf(true, true, true, false) to N,
            listOf(true, false, true, true) to N,    // running but consent revoked: onRevoke path stops it
            // Toggle ON, not running, consent granted: start (foreground or background alike).
            listOf(true, true, false, true) to S,
            listOf(true, true, false, false) to S,
            // Toggle ON, not running, consent missing: prompt only while visible, else defer.
            listOf(true, false, false, true) to P,
            listOf(true, false, false, false) to N
        )

        for ((inputs, expected) in table) {
            val (desired, granted, running, foreground) = inputs
            assertEquals(
                "desired=$desired granted=$granted running=$running foreground=$foreground",
                expected,
                VpnEnginePolicy.resolve(desired, granted, running, foreground)
            )
        }
    }
}

class VpnRecoveryPolicyTest {

    @Test
    fun `retries are bounded`() {
        repeat(VpnRecoveryPolicy.MAX_RECOVERY_ATTEMPTS) { attempt ->
            assertTrue("attempt $attempt must be retried", VpnRecoveryPolicy.shouldRetry(attempt))
        }
        assertFalse(VpnRecoveryPolicy.shouldRetry(VpnRecoveryPolicy.MAX_RECOVERY_ATTEMPTS))
        assertFalse(VpnRecoveryPolicy.shouldRetry(VpnRecoveryPolicy.MAX_RECOVERY_ATTEMPTS + 5))
        assertFalse(VpnRecoveryPolicy.shouldRetry(-1))
    }

    @Test
    fun `backoff grows exponentially and is capped`() {
        assertEquals(1_000L, VpnRecoveryPolicy.backoffMillis(0))
        assertEquals(2_000L, VpnRecoveryPolicy.backoffMillis(1))
        assertEquals(4_000L, VpnRecoveryPolicy.backoffMillis(2))
        assertEquals(8_000L, VpnRecoveryPolicy.backoffMillis(3))
        assertEquals(16_000L, VpnRecoveryPolicy.backoffMillis(4))
        assertEquals(30_000L, VpnRecoveryPolicy.backoffMillis(5))
        assertEquals(30_000L, VpnRecoveryPolicy.backoffMillis(99))
        assertEquals(1_000L, VpnRecoveryPolicy.backoffMillis(-3)) // bounded below
    }
}
