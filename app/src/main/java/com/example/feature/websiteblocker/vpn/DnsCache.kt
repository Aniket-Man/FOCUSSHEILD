package com.example.feature.websiteblocker.vpn

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe DNS response cache.
 * Keyed by (domain, qtype).
 */
class DnsCache(
    private val maxEntries: Int = 1000,
    private val defaultTtlMs: Long = 5 * 60 * 1000L // 5 minutes
) {

    private data class CacheKey(val domain: String, val qtype: Int)

    private data class CacheEntry(
        val rawDnsPayload: ByteArray,
        val expiresAt: Long
    )

    private val entries = ConcurrentHashMap<CacheKey, CacheEntry>()

    /**
     * Retrieve cached DNS payload for [domain] and [qtype], rewriting the transaction ID
     * to match [queryId].
     */
    fun get(domain: String, qtype: Int, queryId: Int): ByteArray? {
        val clean = domain.trim().lowercase().trimEnd('.')
        val key = CacheKey(clean, qtype)
        val entry = entries[key] ?: return null

        val now = System.currentTimeMillis()
        if (now > entry.expiresAt) {
            entries.remove(key)
            return null
        }

        // Clone and rewrite transaction ID (first 2 bytes)
        val response = entry.rawDnsPayload.clone()
        if (response.size >= 2) {
            response[0] = ((queryId ushr 8) and 0xFF).toByte()
            response[1] = (queryId and 0xFF).toByte()
        }
        return response
    }

    /**
     * Store raw DNS response payload for [domain] and [qtype].
     */
    fun put(domain: String, qtype: Int, rawDnsPayload: ByteArray, ttlSeconds: Int = 300) {
        val clean = domain.trim().lowercase().trimEnd('.')
        val key = CacheKey(clean, qtype)

        if (entries.size >= maxEntries) {
            evictExpired()
            if (entries.size >= maxEntries) {
                // If still full, remove an arbitrary entry
                val firstKey = entries.keys().nextElement()
                if (firstKey != null) entries.remove(firstKey)
            }
        }

        val ttlMs = (ttlSeconds.toLong() * 1000L).coerceIn(30_000L, 3600_000L)
        val expiresAt = System.currentTimeMillis() + ttlMs
        entries[key] = CacheEntry(rawDnsPayload.clone(), expiresAt)
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (now > item.value.expiresAt) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        entries.clear()
    }

    val size: Int get() = entries.size
}
