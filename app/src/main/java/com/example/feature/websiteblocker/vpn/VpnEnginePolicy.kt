package com.example.feature.websiteblocker.vpn

/**
 * The pure decision core connecting the existing 18+ blocking toggle to the VPN engine.
 *
 * Every input is a plain boolean, so the complete command table is unit-testable without Android:
 *
 * | desired (toggle) | permission | running | foreground | command           |
 * |------------------|------------|---------|------------|-------------------|
 * | OFF              | –          | yes     | –          | StopVpn           |
 * | OFF              | –          | no      | –          | Noop              |
 * | ON               | –          | yes     | –          | Noop              |
 * | ON               | granted    | no      | –          | StartVpn          |
 * | ON               | missing    | no      | foreground | RequestVpnPermission |
 * | ON               | missing    | no      | background | Noop (defer)      |
 */
object VpnEnginePolicy {

    sealed interface Command {
        /** The toggle is ON and consent exists — start (or keep) the VPN filter. */
        data object StartVpn : Command

        /** The toggle is OFF — stop the VPN and restore normal networking. */
        data object StopVpn : Command

        /** The toggle is ON but VPN consent is missing and the app is visible — ask once. */
        data object RequestVpnPermission : Command

        /** Nothing to do. */
        data object Noop : Command
    }

    fun resolve(
        desiredEnabled: Boolean,
        vpnPermissionGranted: Boolean,
        engineRunning: Boolean,
        appInForeground: Boolean
    ): Command {
        return when {
            !desiredEnabled && engineRunning -> Command.StopVpn
            !desiredEnabled -> Command.Noop
            engineRunning -> Command.Noop
            vpnPermissionGranted -> Command.StartVpn
            appInForeground -> Command.RequestVpnPermission
            else -> Command.Noop
        }
    }
}

/**
 * Bounded self-healing policy for the tunnel reader loop: when the tun interface is torn down
 * unexpectedly (system teardown, OEM network reconfiguration) the service re-establishes it with
 * exponential backoff, giving up after [MAX_RECOVERY_ATTEMPTS] consecutive failures.
 */
object VpnRecoveryPolicy {

    const val MAX_RECOVERY_ATTEMPTS = 5
    const val MAX_BACKOFF_MS = 30_000L

    fun shouldRetry(consecutiveFailures: Int): Boolean =
        consecutiveFailures in 0 until MAX_RECOVERY_ATTEMPTS

    fun backoffMillis(attempt: Int): Long {
        val bounded = attempt.coerceIn(0, 5)
        return minOf(MAX_BACKOFF_MS, 1000L shl bounded)
    }
}
