package com.example.feature.websiteblocker.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-Time DNS Adult Content Classifier.
 * Resolves domains against Family & Adult Filtering DNS resolvers (CleanBrowsing 185.228.168.168 & Cloudflare 1.1.1.3).
 * If the Family DNS server returns a sinkhole (0.0.0.0, 127.0.0.1, or blocked response),
 * the domain is automatically classified as adult/prohibited content.
 *
 * Employs a zero-latency concurrent memory cache so UI threads never wait.
 */
object AdultDnsClassifier {

    private const val TAG = "AdultDnsClassifier"

    // Primary Family Filter DNS servers that block adult & NSFW domains
    private val FAMILY_DNS_SERVERS = listOf(
        "185.228.168.168", // CleanBrowsing Adult Filter DNS
        "1.1.1.3",         // Cloudflare Family Filter DNS (Malware + Adult)
        "94.140.14.14"     // AdGuard Family DNS
    )

    // Known Sinkhole IP addresses returned when a domain is blocked by DNS
    private val BLOCKED_SINKHOLE_IPS = setOf(
        "0.0.0.0",
        "127.0.0.1",
        "185.228.168.10",
        "185.228.169.10",
        "::",
        "::1"
    )

    // Fast in-memory cache: domain -> isAdultBlocked
    private val dnsClassificationCache = ConcurrentHashMap<String, Boolean>()

    private val classifierScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Checks if a domain is classified as adult via DNS.
     * Returns true if already confirmed blocked, false otherwise.
     * Dispatches async DNS lookup if not in cache.
     */
    fun isDomainBlockedByDns(domain: String): Boolean {
        val clean = domain.trim().lowercase()
        if (clean.isBlank() || !clean.contains('.')) return false

        // 1. Instant Cache Hit
        val cached = dnsClassificationCache[clean]
        if (cached != null) {
            return cached
        }

        // 2. Dispatch Async DNS Resolution
        classifierScope.launch {
            resolveAndCacheDomain(clean)
        }

        return false
    }

    /**
     * Resolves domain synchronously against Family DNS server.
     */
    private fun resolveAndCacheDomain(domain: String): Boolean {
        try {
            for (dnsServerIp in FAMILY_DNS_SERVERS) {
                val resolvedIps = queryDnsUdp(domain, dnsServerIp)
                if (resolvedIps.isNotEmpty()) {
                    val isSinkhole = resolvedIps.any { BLOCKED_SINKHOLE_IPS.contains(it) }
                    if (isSinkhole) {
                        Log.w(TAG, "[DNS Classifier] Domain '$domain' resolved to sinkhole $resolvedIps via Family DNS $dnsServerIp -> BLOCKED")
                        dnsClassificationCache[domain] = true
                        return true
                    } else {
                        // Successfully resolved to real external IP -> safe
                        dnsClassificationCache[domain] = false
                        return false
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "DNS resolution exception for $domain: ${e.message}")
        }
        return false
    }

    /**
     * Low-level RFC 1035 UDP DNS Query against the specified DNS server.
     * Returns list of IP addresses resolved by the server.
     */
    private fun queryDnsUdp(domain: String, dnsServerIp: String): List<String> {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 1200 // 1.2s timeout

            val queryBytes = buildDnsQueryPacket(domain)
            val serverAddress = InetAddress.getByName(dnsServerIp)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, serverAddress, 53)
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            return parseDnsResponseIps(receivePacket.data, receivePacket.length)
        } catch (_: Exception) {
            return emptyList()
        } finally {
            socket?.close()
        }
    }

    /**
     * Builds a standard DNS Type A (IPv4) query packet for the given domain.
     */
    private fun buildDnsQueryPacket(domain: String): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(stream)

        // Transaction ID (Random 2 bytes)
        dos.writeShort((System.currentTimeMillis() and 0xFFFF).toInt())
        // Flags: Standard query, Recursion Desired (0x0100)
        dos.writeShort(0x0100)
        // Questions count: 1
        dos.writeShort(1)
        // Answer RRs: 0
        dos.writeShort(0)
        // Authority RRs: 0
        dos.writeShort(0)
        // Additional RRs: 0
        dos.writeShort(0)

        // QNAME: Labels (e.g. 7xvideos3com0)
        val labels = domain.split('.')
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // End of QNAME

        // QTYPE: A record (0x0001)
        dos.writeShort(1)
        // QCLASS: IN (0x0001)
        dos.writeShort(1)

        dos.flush()
        return stream.toByteArray()
    }

    /**
     * Parses the DNS answer section to extract resolved IPv4 addresses.
     */
    private fun parseDnsResponseIps(data: ByteArray, length: Int): List<String> {
        val ips = mutableListOf<String>()
        if (length < 12) return ips

        try {
            val buffer = java.nio.ByteBuffer.wrap(data, 0, length)
            buffer.short // Transaction ID
            val flags = buffer.short.toInt()
            val rcode = flags and 0x000F
            if (rcode == 3) {
                // NXDOMAIN -> on some family DNS, NXDOMAIN indicates blocked
                return listOf("0.0.0.0")
            }

            val qdCount = buffer.short.toInt() and 0xFFFF
            val anCount = buffer.short.toInt() and 0xFFFF
            buffer.short // NSCOUNT
            buffer.short // ARCOUNT

            // Skip Question section
            for (i in 0 until qdCount) {
                skipDomainName(buffer)
                buffer.short // QTYPE
                buffer.short // QCLASS
            }

            // Parse Answer section
            for (i in 0 until anCount) {
                skipDomainName(buffer)
                val type = buffer.short.toInt() and 0xFFFF
                val clazz = buffer.short.toInt() and 0xFFFF
                buffer.int // TTL
                val rdLength = buffer.short.toInt() and 0xFFFF

                if (type == 1 && clazz == 1 && rdLength == 4) {
                    // Type A (IPv4)
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt() and 0xFF
                    val b3 = buffer.get().toInt() and 0xFF
                    val b4 = buffer.get().toInt() and 0xFF
                    ips.add("$b1.$b2.$b3.$b4")
                } else {
                    buffer.position(buffer.position() + rdLength)
                }
            }
        } catch (_: Exception) {}

        return ips
    }

    private fun skipDomainName(buffer: java.nio.ByteBuffer) {
        while (buffer.hasRemaining()) {
            val len = buffer.get().toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                // Pointer compression (2 bytes)
                buffer.get() // Second byte of pointer
                break
            } else {
                buffer.position(buffer.position() + len)
            }
        }
    }
}
