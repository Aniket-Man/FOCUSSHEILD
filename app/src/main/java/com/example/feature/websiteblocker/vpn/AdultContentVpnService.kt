package com.example.feature.websiteblocker.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.feature.session.service.ForegroundServiceTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Local DNS-filtering VPN — the enforcement layer of the **existing** 18+ blocking toggle.
 *
 * How it filters
 * --------------
 * The VPN interface routes *only* its own fake DNS addresses (one IPv4 /32, one IPv6 /128):
 * the system resolver sends every DNS query into the tun, while **all other traffic leaves through
 * the real network untouched**. That keeps the blast radius of the filter to DNS only — when the
 * service stops, networking is exactly as it was before.
 *
 * Each datagram is parsed (`IpPacketCodec` → `DnsPacketCodec`), the QNAME is matched against the
 * 18+ blocklist ([DomainBlocklist], fed by [AdultBlocklistSource]); a match is answered locally
 * with `0.0.0.0` / `::` (or NOERROR-NODATA for HTTPS/SVCB records so embedded IP hints cannot leak
 * a blocked host's real address). Everything else is answered from [DnsCache] or relayed, byte for
 * byte, to the current network's resolvers through a [VpnService.protect]ed socket
 * ([UpstreamDnsResolver]) — so exotic-but-valid DNS still works and normal domains resolve
 * unchanged.
 *
 * Lifecycle (all driven by [VpnFilterEngineController], which observes the existing preference)
 * ----------------------------------------------------------------------------
 *  * toggle ON + consent granted → [ACTION_START] → foreground service → tunnel up;
 *  * toggle OFF (or the controller stopping it) → [ACTION_STOP] / `stopService` → tunnel down,
 *    normal networking restored, nothing left running;
 *  * `START_STICKY` + null-intent re-check → the engine survives process kills and re-reads the
 *    toggle before coming back up;
 *  * unexpected tun loss → bounded re-establish with backoff ([VpnRecoveryPolicy]);
 *  * `onRevoke` (user revoked consent, or another VPN took over) → stop cleanly; the controller
 *    re-prompts/restarts when possible.
 *
 * On Android 14+ the service runs with the `specialUse` foreground type (no time limit, unlike
 * `dataSync`); Android 10–13 fall back to `dataSync`, matching the manifest declaration and the
 * shared [ForegroundServiceTypes] resolver. See docs/FOREGROUND_SERVICE.md for the type contract.
 */
class AdultContentVpnService : VpnService() {

    private val tag = "AdultContentVpnService"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()

    private val dnsCache = DnsCache()
    private val blocklist = AdultBlocklistSource.blocklist()
    private val dnsFilter = DnsFilter(blocklist, dnsCache)
    private val upstreamServers = AtomicReference(UpstreamDnsServers.FALLBACK_SERVERS)
    private val resolver = UpstreamDnsResolver(
        servers = { upstreamServers.get() },
        socketProtector = { socket -> protect(socket) }
    )

    @Volatile
    private var tunnelActive = false

    /** True as soon as a stop is in progress; keeps the recovery path from fighting the shutdown. */
    @Volatile
    private var shuttingDown = false

    private var tunnel: ParcelFileDescriptor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var recoveryAttempts = 0

    // ---------------------------------------------------------------------------------------
    // Service lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                // A null intent is the START_STICKY restart after a process kill: re-check the
                // *existing* toggle state before coming back up, so a kill can never resurrect a
                // filter the user turned off.
                val desired = VpnFilterEngineController.instance?.desiredEnabledSnapshot ?: true
                if (!desired) {
                    Log.i(tag, "18+ toggle is OFF; not (re)starting the DNS filter")
                    shuttingDown = true
                    stopEngine()
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (tunnelActive) return START_STICKY

                // Enter the foreground *before* anything that can stop the service: after
                // startForegroundService() the platform requires a startForeground() call even on
                // the failure path.
                startForegroundWithProperType(buildNotification())
                return if (startTunnel()) {
                    START_STICKY
                } else {
                    stepDown()
                    START_NOT_STICKY
                }
            }

            ACTION_STOP -> {
                Log.i(tag, "Stop requested; restoring normal networking")
                stepDown()
                return START_NOT_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        Log.w(tag, "VPN revoked by the platform (consent revoked or another VPN took over)")
        shuttingDown = true
        stopEngine()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        shuttingDown = true
        stopEngine()
        serviceScope.cancel()
        VpnFilterEngineController.instance?.onEngineRunningChanged(false)
        Log.i(tag, "DNS filter service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------------------------------
    // Tunnel setup / teardown
    // ---------------------------------------------------------------------------------------

    private fun startTunnel(): Boolean {
        if (vpnConsentMissing()) {
            Log.w(tag, "VPN consent is missing; refusing to establish the tunnel")
            return false
        }
        val descriptor: ParcelFileDescriptor = try {
            Builder()
                .setSession(SESSION_NAME)
                .setMtu(TUNNEL_MTU)
                // DNS-only tunnel: route exactly the fake resolver addresses, nothing else.
                .addAddress(UpstreamDnsServers.VPN_DNS_IPV4, 32)
                .addRoute(UpstreamDnsServers.VPN_DNS_IPV4, 32)
                .addDnsServer(UpstreamDnsServers.VPN_DNS_IPV4)
                .addAddress(UpstreamDnsServers.VPN_DNS_IPV6, 128)
                .addRoute(UpstreamDnsServers.VPN_DNS_IPV6, 128)
                .addDnsServer(UpstreamDnsServers.VPN_DNS_IPV6)
                .setBlocking(true)
                .establish()
                ?: run {
                    Log.e(tag, "establish() returned null (VPN revoked or another VPN owns the tunnel)")
                    null
                }
        } catch (e: Exception) {
            Log.e(tag, "Failed to establish the DNS filter tunnel", e)
            null
        } ?: return false

        return try {
            tunnel = descriptor
            tunnelActive = true
            recoveryAttempts = 0
            registerNetworkCallback()
            Thread({ runTunnelLoop(descriptor) }, TUNNEL_THREAD_NAME).start()
            VpnFilterEngineController.instance?.onEngineRunningChanged(true)
            Log.i(
                tag,
                "DNS filter tunnel up: ${blocklist.blockedDomainCount} blocked 18+ domains, " +
                    "MTU=$TUNNEL_MTU, upstreams=${upstreamServers.get()}"
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "Tunnel post-setup failed", e)
            tunnelActive = false
            closeTunnelQuietly()
            false
        }
    }

    /**
     * Whether VPN consent is missing. `prepare()` returning an intent means the user has not
     * granted (or has revoked) the VPN permission; a throwing `prepare()` (some OEM/test
     * environments) is treated as "cannot confirm consent" — the engine refuses to start rather
     * than assuming permission.
     */
    private fun vpnConsentMissing(): Boolean = try {
        VpnService.prepare(this) != null
    } catch (e: Exception) {
        Log.w(tag, "VpnService.prepare() failed: ${e.message}")
        true
    }

    /** Idempotent stop: tears the tunnel down, flushes state and lets the reader thread exit. */
    private fun stopEngine() {
        shuttingDown = true
        tunnelActive = false
        closeTunnelQuietly()
        unregisterNetworkCallback()
        dnsCache.clear()
    }

    private fun stepDown() {
        stopEngine()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTunnelQuietly() {
        try {
            tunnel?.close()
        } catch (_: Exception) {
        }
        tunnel = null
    }

    // ---------------------------------------------------------------------------------------
    // Packet loop
    // ---------------------------------------------------------------------------------------

    private fun runTunnelLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(READ_BUFFER_BYTES)
        Log.i(tag, "Tunnel reader loop started")
        while (tunnelActive && !shuttingDown) {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                Log.w(tag, "TUN read ended: ${e.message}")
                break
            }
            if (length < 0) break
            if (length == 0) continue
            try {
                handlePacket(buffer, length, output)
            } catch (e: Exception) {
                Log.w(tag, "Packet handling error", e)
            }
        }
        onTunnelLoopExited()
    }

    private fun handlePacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        // The tunnel routes only its own DNS addresses, so anything arriving is UDP/53; anything
        // unparseable is dropped (there is no other traffic to forward through a DNS-only tunnel).
        val udp = IpPacketCodec.parseUdpPacket(packet, length) ?: return
        if (udp.dstPort != IpPacketCodec.DNS_PORT) return

        when (val outcome = dnsFilter.handleQuery(packet, udp.payloadOffset, udp.payloadLength)) {
            is DnsFilterOutcome.LocalResponse ->
                writeResponse(udp, outcome.response, output)

            is DnsFilterOutcome.Forward ->
                serviceScope.launch {
                    val response = resolver.resolve(outcome.query)
                    if (response != null) {
                        dnsFilter.onUpstreamResponse(response, response.size)
                        writeResponse(udp, response, output)
                    } else {
                        // Fail fast instead of letting the client hang on its own timeout.
                        DnsPacketCodec.buildErrorResponse(
                            outcome.parsed,
                            outcome.query,
                            DnsPacketCodec.RCODE_SERVFAIL
                        )?.let { writeResponse(udp, it, output) }
                    }
                }
        }
    }

    private fun writeResponse(query: IpPacketCodec.UdpPacketInfo, dnsResponse: ByteArray, output: FileOutputStream) {
        try {
            val packet = IpPacketCodec.buildUdpResponse(query, dnsResponse)
            synchronized(writeLock) { output.write(packet) }
        } catch (e: Exception) {
            Log.w(tag, "Failed to write a DNS response to the TUN: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Recovery (unexpected tunnel loss) & network changes
    // ---------------------------------------------------------------------------------------

    private fun onTunnelLoopExited() {
        tunnelActive = false
        closeTunnelQuietly()
        unregisterNetworkCallback()
        dnsCache.clear()
        if (!shuttingDown) {
            Log.w(tag, "Tunnel lost unexpectedly; scheduling recovery")
            scheduleRecovery()
        }
    }

    private fun scheduleRecovery() {
        val attempt = recoveryAttempts
        if (!VpnRecoveryPolicy.shouldRetry(attempt)) {
            Log.e(tag, "Recovery budget exhausted after $attempt attempts; stopping the filter")
            stopSelf()
            return
        }
        val backoff = VpnRecoveryPolicy.backoffMillis(attempt)
        recoveryAttempts = attempt + 1
        serviceScope.launch {
            delay(backoff)
            if (shuttingDown || tunnelActive) return@launch
            val desired = VpnFilterEngineController.instance?.desiredEnabledSnapshot ?: true
            if (!desired) return@launch
            Log.i(tag, "Re-establishing the DNS filter tunnel (attempt ${attempt + 1})")
            if (!startTunnel()) {
                scheduleRecovery()
            }
        }
    }

    /**
     * Wi-Fi ↔ mobile-data transitions: the tun itself survives a default-network change, so the
     * tunnel is *not* restarted — only the upstream resolvers are re-read from the new link and the
     * answer cache is flushed (answers from the old network may be wrong for the new one).
     */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshUpstreams()

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                applyUpstreams(linkProperties.dnsServers.mapNotNull { it.hostAddress })

            override fun onLost(network: Network) = refreshUpstreams()
        }
        try {
            connectivity.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(tag, "Network callback registration failed: ${e.message}")
        }
        refreshUpstreams()
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    private fun refreshUpstreams() {
        try {
            val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
            val properties: LinkProperties? = connectivity.getLinkProperties(connectivity.activeNetwork)
            if (properties != null) {
                applyUpstreams(properties.dnsServers.mapNotNull { it.hostAddress })
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not refresh upstream DNS servers: ${e.message}")
        }
    }

    private fun applyUpstreams(serverAddresses: List<String>) {
        val next = UpstreamDnsServers.current(serverAddresses)
        val previous = upstreamServers.getAndSet(next)
        if (previous != next) {
            dnsCache.clear()
            Log.i(tag, "Upstream DNS servers now: $next")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Foreground notification (same contract as the session service)
    // ---------------------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle("🛡️ FocusShield Web Filter")
            .setContentText("18+ website filtering is active (DNS-level protection).")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun startForegroundWithProperType(notification: Notification) {
        val declaredTypes = declaredForegroundServiceTypes()
        val resolution = ForegroundServiceTypes.resolve(Build.VERSION.SDK_INT, declaredTypes)
        if (resolution.warning != null) {
            Log.w(tag, resolution.warning)
        }
        try {
            when (val choice = resolution.choice) {
                is ForegroundServiceTypes.Choice.Typed ->
                    startForeground(NOTIFICATION_ID, notification, choice.type)

                ForegroundServiceTypes.Choice.Untyped ->
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(
                tag,
                "DNS filter entered the foreground (sdk=${Build.VERSION.SDK_INT}, " +
                    "declared=0x${declaredTypes.toString(16)}, choice=${resolution.choice})"
            )
        } catch (e: Exception) {
            // IllegalArgumentException (type mismatch), IllegalStateException (background start,
            // missing type) and SecurityException (missing FGS permission) all mean "cannot be a
            // foreground service right now" — step down instead of crashing the process.
            Log.e(tag, "startForeground failed; stepping the DNS filter down", e)
            stepDown()
        }
    }

    private fun declaredForegroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            val component = ComponentName(this, javaClass)
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getServiceInfo(component, 0)
            }
            info.foregroundServiceType
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(tag, "Could not read this service's manifest entry: ${e.message}")
            0
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status shown while the 18+ website filter (DNS-level) is running"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.focusshield.ACTION_START_ADULT_DNS_FILTER"
        const val ACTION_STOP = "com.example.focusshield.ACTION_STOP_ADULT_DNS_FILTER"
        const val CHANNEL_ID = "focus_shield_dns_filter_v1"
        private const val CHANNEL_NAME = "FocusShield Web Filter Status"
        private const val NOTIFICATION_ID = 4001
        private const val SESSION_NAME = "FocusShield Adult Content Filter"
        private const val TUNNEL_MTU = 4200
        private const val TUNNEL_THREAD_NAME = "adult-dns-filter-tun"
        private const val READ_BUFFER_BYTES = 65535
    }
}
