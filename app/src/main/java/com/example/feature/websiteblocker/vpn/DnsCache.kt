package com.example.feature.websiteblocker.vpn

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe TTL cache of upstream DNS answers, keyed by `qtype|domain`.
 *
 * The stored payload is the raw upstream response; a cache hit is served by copying it and
 * patching the transaction ID (the first two bytes) to match the client's query — the rest of the
 * message is byte-identical for the same (name, type) pair, so no re-encoding is needed on the
 * hot path.
 *
 * TTLs are clamped to [DnsPacketCodec.MIN_CACHE_TTL_SECONDS]…[DnsPacketCodec.MAX_CACHE_TTL_SECONDS]:
 * long upstream TTLs are capped so a network change cannot serve stale answers for long, and
 * zero/low TTLs are raised slightly so a busy resolver's retry storm cannot flush the cache.
 *
 * Pure Kotlin (no Android imports) so caching behaviour is unit-testable with an injected clock.
 */
class DnsCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private data class Entry(val response: ByteArray, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    val size: Int
        get() = entries.size

    /**
     * Returns a response for [key] with its transaction ID rewritten to [transactionId], or null
     * on a miss/expired entry.
     */
    fun lookup(key: String, transactionId: Int): ByteArray? {
        val entry = entries[key] ?: return null
        if (clock() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        val response = entry.response.copyOf()
        response[0] = ((transactionId ushr 8) and 0xFF).toByte()
        response[1] = (transactionId and 0xFF).toByte()
        return response
    }

    /**
     * Caches [response] under [key] for [ttlSeconds] (null → [DnsPacketCodec.DEFAULT_CACHE_TTL_SECONDS]),
     * clamped to the configured bounds.
     */
    fun store(key: String, response: ByteArray, ttlSeconds: Int?) {
        val ttl = (ttlSeconds ?: DnsPacketCodec.DEFAULT_CACHE_TTL_SECONDS)
            .coerceIn(DnsPacketCodec.MIN_CACHE_TTL_SECONDS, DnsPacketCodec.MAX_CACHE_TTL_SECONDS)
        evictIfNeeded()
        entries[key] = Entry(response.copyOf(), clock() + ttl * 1000L)
    }

    /** Drops everything — used when the default network (and therefore the resolver) changes. */
    fun clear() {
        entries.clear()
    }

    private fun evictIfNeeded() {
        if (entries.size < maxEntries) return
        val now = clock()
        entries.entries.removeIf { it.value.expiresAt <= now }
        if (entries.size >= maxEntries) {
            // Still full after dropping expired entries: a full reset is the simple, bounded
            // policy — DNS clients simply re-query, and the cache refills within seconds.
            entries.clear()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 2048

        fun keyFor(domain: String, qtype: Int): String = "$qtype|$domain"
    }
}
