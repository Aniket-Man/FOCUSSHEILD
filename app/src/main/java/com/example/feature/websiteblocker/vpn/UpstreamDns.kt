package com.example.feature.websiteblocker.vpn

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Chooses which real resolvers the filter forwards queries to.
 *
 * The VPN announces itself (via `Builder.addDnsServer`) as the system resolver with fake,
 * tunnel-local addresses; queries that pass the blocklist must be relayed to the *actual* DNS
 * servers of the current network. This object performs that selection:
 *
 *  * candidates come from the active [android.net.LinkProperties] (so Wi-Fi ↔ mobile-data
 *    transitions switch resolvers automatically);
 *  * the tunnel's own fake addresses are filtered out (forwarding to ourselves would loop);
 *  * when no usable server is known (no link properties yet, IPv6-only oddities, OEM quirks) the
 *    public fallback resolvers are used.
 *
 * Kept free of Android types on the selection boundary (`List<String>` in, `List<String>` out)
 * so it is unit-testable.
 */
object UpstreamDnsServers {

    const val VPN_DNS_IPV4 = "10.243.53.53"
    const val VPN_DNS_IPV6 = "fd42:53::53"

    /** Public fallbacks used only when the current network exposes no usable DNS server. */
    val FALLBACK_SERVERS = listOf("8.8.8.8", "1.1.1.1")

    private val tunnelOwnAddresses: Set<InetAddress> = try {
        setOf(
            InetAddress.getByName(VPN_DNS_IPV4),
            InetAddress.getByName(VPN_DNS_IPV6)
        )
    } catch (e: Exception) {
        emptySet()
    }

    /**
     * Normalizes the current network's resolver addresses, dropping blanks, unparsable entries and
     * the tunnel's own addresses; falls back to [FALLBACK_SERVERS] when nothing usable remains.
     */
    fun current(linkDnsServerAddresses: List<String>): List<String> {
        val usable = LinkedHashSet<String>()
        for (raw in linkDnsServerAddresses) {
            val address = canonicalLiteralOrNull(raw) ?: continue
            if (tunnelOwnAddresses.any { it == address }) continue
            usable.add(address.hostAddress ?: continue)
        }
        return if (usable.isEmpty()) FALLBACK_SERVERS else usable.toList()
    }

    /**
     * Parses a literal IP address or returns null. The syntactic pre-check matters:
     * `InetAddress.getByName` performs a real DNS lookup for hostnames, and the resolver list may
     * contain anything a network stack chose to report.
     */
    internal fun canonicalLiteralOrNull(raw: String): InetAddress? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val looksLikeLiteral = trimmed.contains(':') || run {
            val parts = trimmed.split('.')
            parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all { it in '0'..'9' } }
        }
        if (!looksLikeLiteral) return null
        return try {
            InetAddress.getByName(trimmed)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Relays DNS datagrams to an upstream resolver over plain UDP.
 *
 * The socket is routed **around** the VPN via [socketProtector] (the service supplies
 * `VpnService.protect(socket)`), which is what makes this loop-free: the tunnel only routes its
 * own fake DNS addresses, so a protected socket always leaves through the real network.
 *
 * Uses `java.net` only (no Android imports) so it runs in plain JVM unit tests with a fake
 * protector.
 */
class UpstreamDnsResolver(
    private val servers: () -> List<String>,
    private val socketProtector: (DatagramSocket) -> Boolean = { true },
    private val timeoutMs: Int = 1500,
    private val maxServersTried: Int = 3,
    /** Overridable so JVM tests can point the resolver at a local echo server. */
    private val upstreamPort: Int = DNS_PORT
) {

    /**
     * Sends the exact [query] bytes to the first reachable upstream and returns the response
     * bytes, or null when every candidate server failed/timed out (the caller then answers
     * SERVFAIL so the client does not hang).
     */
    fun resolve(query: ByteArray, length: Int = query.size): ByteArray? {
        if (length < MIN_DNS_BYTES) return null
        val candidates = servers().take(maxServersTried)
        for (server in candidates) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = timeoutMs
                if (!socketProtector(socket)) {
                    socket.close()
                    continue
                }
                val target = InetAddress.getByName(server)
                socket.send(DatagramPacket(query, 0, length, target, upstreamPort))
                val buffer = ByteArray(MAX_DATAGRAM_BYTES)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                if (response.length >= MIN_DNS_BYTES) {
                    return buffer.copyOf(response.length)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Upstream $server failed: ${e.message}")
                // try the next candidate
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "UpstreamDnsResolver"
        private const val MIN_DNS_BYTES = 12
        private const val MAX_DATAGRAM_BYTES = 65535
        const val DNS_PORT = 53
    }
}
