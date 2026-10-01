package com.example.feature.websiteblocker.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * System-wide 18+ website filtering VPN Service using local DNS interception.
 */
class AdultDnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AdultDnsVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        // Start foreground notification for VPN service
        startVpnForeground()

        // Wire engine socket protector to bypass VPN for upstream DNS queries
        AdultFilteringEngine.instance?.setSocketProtector { socket ->
            protect(socket)
        }

        startVpnTunnel()
        registerNetworkChangeCallback()

        AdultFilteringEngine.instance?.onVpnServiceStarted()
        isRunning = true

        return START_STICKY
    }

    private fun startVpnTunnel() {
        if (vpnInterface != null) {
            Log.d(TAG, "VPN tunnel is already running")
            return
        }

        try {
            val builder = Builder()
                .setSession(getString(R.string.app_name) + " 18+ Web Shield")
                .setMtu(VPN_MTU)
                // Virtual IPv4 interface & DNS routing
                .addAddress(VIRTUAL_IPV4_CLIENT, 32)
                .addDnsServer(VIRTUAL_IPV4_DNS)
                .addRoute(VIRTUAL_IPV4_DNS, 32)

            // IPv6 Virtual interface & DNS routing (best-effort)
            try {
                builder.addAddress(VIRTUAL_IPV6_CLIENT, 128)
                builder.addDnsServer(VIRTUAL_IPV6_DNS)
                builder.addRoute(VIRTUAL_IPV6_DNS, 128)
            } catch (e: Exception) {
                Log.d(TAG, "IPv6 address assignment omitted: ${e.message}")
            }

            // Exclude our own app so internal HTTP requests bypass VPN
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.addDisallowedApplication(packageName)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Excluding own package omitted: ${e.message}")
            }

            builder.setBlocking(true)
            val pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "Failed to establish VPN tunnel: builder returned null")
                stopSelf()
                return
            }

            vpnInterface = pfd
            Log.i(TAG, "VPN TUN interface established successfully")

            startPacketProcessing(pfd)
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN interface: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startPacketProcessing(pfd: ParcelFileDescriptor) {
        workerJob?.cancel()
        workerJob = serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packetBuffer = ByteArray(PACKET_BUFFER_SIZE)

            val engine = AdultFilteringEngine.instance

            Log.i(TAG, "Packet processing loop started")
            while (isActive) {
                try {
                    val bytesRead = inputStream.read(packetBuffer)
                    if (bytesRead <= 0) continue

                    val responsePacket = engine?.processPacket(packetBuffer, bytesRead)
                    if (responsePacket != null && responsePacket.isNotEmpty()) {
                        outputStream.write(responsePacket)
                        outputStream.flush()
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        Log.e(TAG, "I/O error reading TUN packet: ${e.message}")
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error processing packet: ${e.message}")
                }
            }
            Log.i(TAG, "Packet processing loop exited")
        }
    }

    private fun registerNetworkChangeCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Underlying network available: $network")
                    try {
                        setUnderlyingNetworks(arrayOf(network))
                    } catch (e: Exception) {
                        Log.d(TAG, "Error updating underlying networks: ${e.message}")
                    }
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "Underlying network lost: $network")
                    try {
                        setUnderlyingNetworks(null)
                    } catch (_: Exception) {}
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        try {
                            setUnderlyingNetworks(arrayOf(network))
                        } catch (_: Exception) {}
                    }
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun startVpnForeground() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("18+ Content Shield Active")
            .setContentText("Protecting browsing and apps against adult websites")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "18+ Content Filter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of active 18+ website blocking shield"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping AdultDnsVpnService")
        isRunning = false
        AdultFilteringEngine.instance?.onVpnServiceStopped()

        workerJob?.cancel()
        workerJob = null

        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "AdultDnsVpnService destroyed")
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by system or user")
        stopVpn()
        super.onRevoke()
    }

    companion object {
        private const val TAG = "AdultDnsVpnService"
        private const val ACTION_STOP = "com.example.action.STOP_VPN"
        private const val CHANNEL_ID = "focus_vpn_channel"
        private const val NOTIFICATION_ID = 9021

        private const val VPN_MTU = 1500
        private const val PACKET_BUFFER_SIZE = 4096

        const val VIRTUAL_IPV4_CLIENT = "10.0.0.2"
        const val VIRTUAL_IPV4_DNS = "10.0.0.53"
        const val VIRTUAL_IPV6_CLIENT = "fd00::2"
        const val VIRTUAL_IPV6_DNS = "fd00::53"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, AdultDnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AdultDnsVpnService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AdultDnsVpnService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop AdultDnsVpnService: ${e.message}")
            }
        }
    }
}
