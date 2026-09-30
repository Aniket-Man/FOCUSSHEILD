package com.example.feature.websiteblocker.engine

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Family-DNS verdict for a domain: is it adult/18+ content?
 */
enum class AdultDnsVerdict {
    /** The family resolver sinkholed the domain (e.g. answered `0.0.0.0`) → adult. */
    ADULT,

    /** The family resolver answered with a real IP → not classified as adult. */
    SAFE,

    /** No definitive answer (NXDOMAIN, no A record, or the lookup failed/timed out). Never treated as adult. */
    UNKNOWN
}

/** Result of a single DNS lookup against one resolver. */
internal sealed class DnsQueryResult {
    /** NOERROR with at least one A record (may include sinkhole addresses such as `0.0.0.0`). */
    data class Resolved(val ips: List<String>) : DnsQueryResult()

    /** NXDOMAIN or NOERROR without an A record: the resolver had nothing to say about adult content. */
    object NotClassified : DnsQueryResult()

    /** Timeout, network error, SERVFAIL/REFUSED, or a malformed response. */
    object Failed : DnsQueryResult()
}

/** Transport seam so the classification logic is unit-testable without a network (issue #56). */
internal interface DnsTransport {
    fun queryUdp(domain: String, server: String): DnsQueryResult
    fun queryDoh(domain: String, endpoint: String): DnsQueryResult
}

/**
 * Real-time adult-content classifier backed by family-filter DNS resolvers.
 *
 * A family resolver (Cloudflare 1.1.1.3, CleanBrowsing family, AdGuard family) answers
 * `0.0.0.0` / a sinkhole address for domains it categorizes as adult, and a real IP for
 * everything else. The sinkhole answer *is* the "is this site 18+" signal.
 *
 * Decision contract (issue #56):
 *  - Cache-first: a finished verdict is returned synchronously.
 *  - On a cache miss the lookup is awaited up to [DEFAULT_WAIT_BUDGET_MS] so the **first**
 *    visit can be classified, instead of the old fire-and-forget behaviour that always
 *    returned "not adult" for a domain it had never seen before.
 *  - After a wait that exceeded the budget, further waits for the same domain are skipped
 *    for [WAIT_COOLDOWN_MS] (bounded main-thread cost), while the background lookup
 *    finishes and fills the cache.
 *  - NXDOMAIN / no-answer / lookup failure → [AdultDnsVerdict.UNKNOWN], never ADULT.
 *  - UDP/53 first, DoH (`application/dns-message`) as a fallback for networks that swallow
 *    plain DNS.
 */
object AdultDnsClassifier {

    private const val TAG = "AdultDnsClassifier"

    /** How long a caller may block awaiting a not-yet-cached verdict. */
    internal const val DEFAULT_WAIT_BUDGET_MS = 400L

    /** After a wait hit the budget, don't wait again for this domain for this long. */
    private const val WAIT_COOLDOWN_MS = 2_000L

    private const val SAFE_TTL_MS = 30 * 60 * 1000L
    private const val UNKNOWN_TTL_MS = 30_000L

    // Family-filter DNS servers (plain UDP/53).
    private val FAMILY_DNS_SERVERS = listOf(
        "185.228.168.168", // CleanBrowsing family filter
        "1.1.1.3",         // Cloudflare 1.1.1.1 for Families (malware + adult)
        "94.140.14.14"     // AdGuard family DNS
    )

    // DoH fallbacks, used only when plain DNS produced no definitive answer and at least
    // one resolver failed (networks that drop/hijack UDP/53).
    private val DOH_ENDPOINTS = listOf(
        "https://family.cloudflare-dns.com/dns-query",
        "https://family.cleanbrowsing.org/dns-query"
    )

    // Addresses family resolvers return instead of the real IP for adult/blocked domains.
    private val SINKHOLE_IPS = setOf(
        "0.0.0.0",
        "127.0.0.1",
        "185.228.168.10",
        "185.228.169.10",
        "::",
        "::1"
    )

    @VisibleForTesting
    internal var transport: DnsTransport = SystemDnsTransport()

    private class CacheEntry(val verdict: AdultDnsVerdict, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = ConcurrentHashMap<String, Deferred<AdultDnsVerdict>>()
    private val waitCooldownUntil = ConcurrentHashMap<String, Long>()
    private val classifierScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Classifies [domain], waiting up to [waitBudgetMs] for a first-ever lookup so the
     * caller can decide on the visit in front of it. Never throws; failures degrade to
     * [AdultDnsVerdict.UNKNOWN].
     */
    fun verdict(domain: String, waitBudgetMs: Long = DEFAULT_WAIT_BUDGET_MS): AdultDnsVerdict {
        val clean = domain.trim().lowercase()
        if (clean.isBlank() || !clean.contains('.')) return AdultDnsVerdict.UNKNOWN

        // 1. Finished verdicts are instant.
        cache[clean]?.let { entry ->
            if (System.currentTimeMillis() < entry.expiresAt) return entry.verdict
        }

        // 2. A previous wait for this domain already hit the budget — don't stall again.
        waitCooldownUntil[clean]?.let { until ->
            if (System.currentTimeMillis() < until) return AdultDnsVerdict.UNKNOWN
        }

        // 3. Single-flight background lookup (already running ones are reused).
        val deferred = inFlight.computeIfAbsent(clean) {
            classifierScope.async { resolveAndClassify(clean) }
        }

        // 4. Bounded wait so the first visit is classified.
        val verdict = runBlocking { withTimeoutOrNull(waitBudgetMs) { deferred.await() } }
        if (verdict == null) {
            waitCooldownUntil[clean] = System.currentTimeMillis() + WAIT_COOLDOWN_MS
            Log.d(TAG, "Wait budget of ${waitBudgetMs}ms exceeded for '$clean'; verdict deferred to background lookup")
            return AdultDnsVerdict.UNKNOWN
        }
        return verdict
    }

    private fun resolveAndClassify(domain: String): AdultDnsVerdict {
        val verdict = try {
            queryVerdict(domain)
        } catch (e: Exception) {
            Log.d(TAG, "Classification exception for $domain: ${e.message}")
            AdultDnsVerdict.UNKNOWN
        }
        val now = System.currentTimeMillis()
        val expiresAt = when (verdict) {
            AdultDnsVerdict.ADULT -> Long.MAX_VALUE // sinkhole answers don't change
            AdultDnsVerdict.SAFE -> now + SAFE_TTL_MS
            AdultDnsVerdict.UNKNOWN -> now + UNKNOWN_TTL_MS
        }
        cache[domain] = CacheEntry(verdict, expiresAt)
        inFlight.remove(domain)
        waitCooldownUntil.remove(domain)
        Log.d(TAG, "Classified '$domain' as $verdict")
        return verdict
    }

    private fun queryVerdict(domain: String): AdultDnsVerdict {
        var anyUdpFailure = false

        for (server in FAMILY_DNS_SERVERS) {
            when (val result = safeUdp(domain, server)) {
                is DnsQueryResult.Resolved -> {
                    if (result.ips.any { SINKHOLE_IPS.contains(it) }) {
                        Log.w(TAG, "[DNS] '$domain' sinkholed ($result.ips) via $server -> ADULT")
                        return AdultDnsVerdict.ADULT
                    }
                    if (result.ips.isNotEmpty()) return AdultDnsVerdict.SAFE
                }
                DnsQueryResult.NotClassified -> Unit // NXDOMAIN/no-answer: try the next resolver
                DnsQueryResult.Failed -> anyUdpFailure = true
            }
        }

        // Plain DNS was unusable (dropped/hijacked): retry over encrypted DoH.
        if (anyUdpFailure) {
            for (endpoint in DOH_ENDPOINTS) {
                when (val result = safeDoh(domain, endpoint)) {
                    is DnsQueryResult.Resolved -> {
                        if (result.ips.any { SINKHOLE_IPS.contains(it) }) {
                            Log.w(TAG, "[DoH] '$domain' sinkholed ($result.ips) via $endpoint -> ADULT")
                            return AdultDnsVerdict.ADULT
                        }
                        if (result.ips.isNotEmpty()) return AdultDnsVerdict.SAFE
                    }
                    else -> Unit
                }
            }
        }

        return AdultDnsVerdict.UNKNOWN
    }

    private fun safeUdp(domain: String, server: String): DnsQueryResult = try {
        transport.queryUdp(domain, server)
    } catch (e: Exception) {
        DnsQueryResult.Failed
    }

    private fun safeDoh(domain: String, endpoint: String): DnsQueryResult = try {
        transport.queryDoh(domain, endpoint)
    } catch (e: Exception) {
        DnsQueryResult.Failed
    }

    @VisibleForTesting
    internal fun resetForTest() {
        cache.clear()
        inFlight.clear()
        waitCooldownUntil.clear()
        transport = SystemDnsTransport()
    }

    // ---------------------------------------------------------------------------------------
    // Production transport: raw UDP/53 with a DoH fallback.
    // ---------------------------------------------------------------------------------------

    private class SystemDnsTransport : DnsTransport {

        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
        }

        override fun queryUdp(domain: String, server: String): DnsQueryResult {
            var socket: DatagramSocket? = null
            return try {
                socket = DatagramSocket()
                socket.soTimeout = 800 // per-resolver budget

                val queryBytes = buildDnsQuery(domain)
                val serverAddress = InetAddress.getByName(server)
                socket.send(DatagramPacket(queryBytes, queryBytes.size, serverAddress, 53))

                val receiveBuffer = ByteArray(512)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(receivePacket)

                parseDnsResponse(receivePacket.data, receivePacket.length)
            } catch (e: Exception) {
                DnsQueryResult.Failed
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        override fun queryDoh(domain: String, endpoint: String): DnsQueryResult {
            return try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/dns-message")
                    .post(buildDnsQuery(domain).toRequestBody(DNS_MESSAGE_MEDIA_TYPE))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return DnsQueryResult.Failed
                    val body = response.body?.bytes() ?: return DnsQueryResult.Failed
                    parseDnsResponse(body, body.size)
                }
            } catch (e: Exception) {
                DnsQueryResult.Failed
            }
        }
    }

    /**
     * Builds a standard DNS Type A (IPv4) query packet for the given domain (RFC 1035).
     */
    internal fun buildDnsQuery(domain: String): ByteArray {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        // Transaction ID (random-ish 2 bytes)
        dos.writeShort((System.nanoTime() and 0xFFFF).toInt())
        // Flags: standard query, Recursion Desired (0x0100)
        dos.writeShort(0x0100)
        dos.writeShort(1) // QDCOUNT
        dos.writeShort(0) // ANCOUNT
        dos.writeShort(0) // NSCOUNT
        dos.writeShort(0) // ARCOUNT

        // QNAME: length-prefixed labels
        for (label in domain.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // terminator

        dos.writeShort(1) // QTYPE: A
        dos.writeShort(1) // QCLASS: IN

        dos.flush()
        return stream.toByteArray()
    }

    /**
     * Parses a DNS response. NOERROR with A records → [DnsQueryResult.Resolved],
     * NXDOMAIN or no A records → [DnsQueryResult.NotClassified] (**never** coerced into a
     * sinkhole answer — that was a false "18+" source, issue #56), anything else →
     * [DnsQueryResult.Failed].
     */
    internal fun parseDnsResponse(data: ByteArray, length: Int): DnsQueryResult {
        if (length < 12) return DnsQueryResult.Failed
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.short // transaction ID
            val flags = buffer.short.toInt()
            val rcode = flags and 0x000F

            if (rcode == 3) return DnsQueryResult.NotClassified // NXDOMAIN
            if (rcode != 0) return DnsQueryResult.Failed         // SERVFAIL/REFUSED/…

            val qdCount = buffer.short.toInt() and 0xFFFF
            val anCount = buffer.short.toInt() and 0xFFFF
            buffer.short // NSCOUNT
            buffer.short // ARCOUNT

            // Skip the question section
            repeat(qdCount) {
                skipDomainName(buffer)
                buffer.short // QTYPE
                buffer.short // QCLASS
            }

            val ips = mutableListOf<String>()
            repeat(anCount) {
                skipDomainName(buffer)
                val type = buffer.short.toInt() and 0xFFFF
                val clazz = buffer.short.toInt() and 0xFFFF
                buffer.int // TTL
                val rdLength = buffer.short.toInt() and 0xFFFF

                if (type == 1 && clazz == 1 && rdLength == 4) {
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt() and 0xFF
                    val b3 = buffer.get().toInt() and 0xFF
                    val b4 = buffer.get().toInt() and 0xFF
                    ips.add("$b1.$b2.$b3.$b4")
                } else {
                    buffer.position(buffer.position() + rdLength)
                }
            }

            if (ips.isEmpty()) DnsQueryResult.NotClassified else DnsQueryResult.Resolved(ips)
        } catch (e: Exception) {
            DnsQueryResult.Failed
        }
    }

    private fun skipDomainName(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val len = buffer.get().toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                buffer.get() // second byte of a compression pointer
                break
            } else {
                buffer.position(buffer.position() + len)
            }
        }
    }

    private val DNS_MESSAGE_MEDIA_TYPE = "application/dns-message".toMediaType()
}
