package com.example.feature.websiteblocker.vpn

/**
 * What the filter decided for one DNS datagram read from the tunnel.
 */
sealed interface DnsFilterOutcome {

    /** The filter answered the query itself (blocked answer or cache hit) — write [response] back. */
    data class LocalResponse(val response: ByteArray) : DnsFilterOutcome

    /**
     * The query must be forwarded to a real resolver unchanged: [query] is the exact datagram
     * bytes (so exotic-but-valid DNS the codec does not model still works), and [parsed] is the
     * parsed question when it was interpretable (null for malformed/non-IN traffic).
     */
    data class Forward(val query: ByteArray, val parsed: DnsPacketCodec.DnsQuery?) : DnsFilterOutcome
}

/**
 * The per-datagram decision engine of the DNS-level 18+ filter:
 *
 * ```
 * DNS datagram → parse question → allowlist? → blocklist → cache → BLOCK / CACHED / FORWARD
 * ```
 *
 * Malformed datagrams and non-IN classes are always forwarded verbatim (fail-open on the parsing
 * path — never silently break resolution), while block decisions are fail-closed against the
 * configured [DomainBlocklist].
 *
 * Pure Kotlin (no Android imports) so the whole decision table is unit-testable at the byte level.
 */
class DnsFilter(
    private val blocklist: DomainBlocklist,
    private val cache: DnsCache
) {

    /**
     * Decides what to do with a DNS datagram located at [offset]/[length] inside [payload]
     * (the tun packet buffer — nothing is copied until an outcome is produced).
     */
    fun handleQuery(payload: ByteArray, offset: Int, length: Int): DnsFilterOutcome {
        val parsed = DnsPacketCodec.parse(payload, offset, length)
            ?: return forward(payload, offset, length, null)

        // A DNS *response* arriving as a "query" or a non-IN class (e.g. CH TXT version.bind)
        // carries no blocklist decision: forward it unchanged.
        if (parsed.isResponse || parsed.qclass != DnsPacketCodec.CLASS_IN) {
            return forward(payload, offset, length, null)
        }

        when (blocklist.match(parsed.domain)) {
            is DomainMatch.Blocked ->
                return DnsFilterOutcome.LocalResponse(
                    DnsPacketCodec.buildBlockedResponse(parsed, payload)
                )
            is DomainMatch.Allowed, DomainMatch.NotMatched -> Unit
        }

        // Allowlist or no match: serve from cache when possible.
        cache.lookup(DnsCache.keyFor(parsed.domain, parsed.qtype), parsed.transactionId)?.let {
            return DnsFilterOutcome.LocalResponse(it)
        }

        return forward(payload, offset, length, parsed)
    }

    /**
     * Called by the transport layer when an upstream resolver answered: caches the response
     * (keyed by its question) so subsequent identical queries are answered locally.
     */
    fun onUpstreamResponse(response: ByteArray, length: Int) {
        val parsed = DnsPacketCodec.parse(response, 0, length) ?: return
        if (!parsed.isResponse) return
        val ttl = DnsPacketCodec.minAnswerTtl(response, 0, length)
        cache.store(DnsCache.keyFor(parsed.domain, parsed.qtype), response.copyOf(length), ttl)
    }

    private fun forward(payload: ByteArray, offset: Int, length: Int, parsed: DnsPacketCodec.DnsQuery?): DnsFilterOutcome =
        DnsFilterOutcome.Forward(payload.copyOfRange(offset, offset + length), parsed)
}
