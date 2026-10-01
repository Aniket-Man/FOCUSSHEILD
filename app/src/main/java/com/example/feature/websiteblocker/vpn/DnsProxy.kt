package com.example.feature.websiteblocker.vpn

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Upstream DNS client that forwards allowed DNS queries to real resolvers.
 *
 * All outbound sockets are protected via [socketProtector] (which invokes [android.net.VpnService.protect]),
 * ensuring upstream traffic bypasses the local VPN TUN interface and routes via the physical network.
 */
class DnsProxy(
    private val socketProtector: ((DatagramSocket) -> Boolean)? = null
) {

    private val upstreamServers = CopyOnWriteArrayList<String>().apply {
        addAll(DEFAULT_UPSTREAM_SERVERS)
    }

    /**
     * Updates the upstream DNS servers (e.g. from network link properties or preferences).
     */
    fun updateUpstreamServers(servers: List<String>) {
        if (servers.isNotEmpty()) {
            upstreamServers.clear()
            upstreamServers.addAll(servers)
        }
    }

    /**
     * Queries upstream DNS server using UDP.
     * Returns raw DNS response payload or null on timeout/failure.
     */
    fun forwardQuery(queryPayload: ByteArray): ByteArray? {
        for (server in upstreamServers) {
            val response = queryServer(queryPayload, server)
            if (response != null) return response
        }
        return null
    }

    private fun queryServer(queryPayload: ByteArray, serverIp: String): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = SOCKET_TIMEOUT_MS

            // CRITICAL: Protect socket from entering VPN loopback
            socketProtector?.let { protect ->
                val protected = protect(socket)
                if (!protected) {
                    Log.w(TAG, "Failed to protect upstream DNS socket for server: $serverIp")
                }
            }

            val serverAddr = InetAddress.getByName(serverIp)
            val sendPacket = DatagramPacket(queryPayload, queryPayload.size, serverAddr, DnsConstants.PORT_DNS)
            socket.send(sendPacket)

            val receiveBuf = ByteArray(MAX_DNS_PACKET_SIZE)
            val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
            socket.receive(receivePacket)

            val result = ByteArray(receivePacket.length)
            System.arraycopy(receiveBuf, 0, result, 0, receivePacket.length)
            result
        } catch (e: Exception) {
            Log.d(TAG, "Upstream DNS query failed against $serverIp: ${e.message}")
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "DnsProxy"
        private const val SOCKET_TIMEOUT_MS = 1500
        private const val MAX_DNS_PACKET_SIZE = 1500

        val DEFAULT_UPSTREAM_SERVERS = listOf(
            "1.1.1.1",       // Cloudflare DNS
            "8.8.8.8",       // Google DNS
            "9.9.9.9",       // Quad9 DNS
            "1.0.0.1"
        )
    }
}
