package com.example.feature.websiteblocker

import com.example.feature.websiteblocker.vpn.DnsCache
import com.example.feature.websiteblocker.vpn.DnsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DnsCacheTest {

    private lateinit var cache: DnsCache

    @Before
    fun setUp() {
        cache = DnsCache(maxEntries = 50, defaultTtlMs = 1000L)
    }

    @Test
    fun `stores and retrieves cached DNS response with rewritten transaction ID`() {
        val originalPayload = byteArrayOf(
            0x12.toByte(), 0x34.toByte(), // ID = 0x1234
            0x81.toByte(), 0x80.toByte(), // Flags
            0x00, 0x01,                   // QD
            0x00, 0x01                    // AN
        )

        cache.put("example.com", DnsConstants.TYPE_A, originalPayload, ttlSeconds = 60)

        // Query with a different Transaction ID 0x5678
        val cached = cache.get("example.com", DnsConstants.TYPE_A, queryId = 0x5678)
        assertNotNull(cached)
        assertEquals(0x56.toByte(), cached!![0])
        assertEquals(0x78.toByte(), cached[1])
        assertEquals("Flags remain untouched", 0x81.toByte(), cached[2])
    }

    @Test
    fun `cache miss returns null`() {
        val result = cache.get("not-cached.org", DnsConstants.TYPE_A, queryId = 0x1111)
        assertNull(result)
    }

    @Test
    fun `different query types are cached separately`() {
        val payloadA = byteArrayOf(0x01, 0x01, 0x00, 0x00)
        val payloadAaaa = byteArrayOf(0x02, 0x02, 0x00, 0x00)

        cache.put("example.com", DnsConstants.TYPE_A, payloadA, ttlSeconds = 60)
        cache.put("example.com", DnsConstants.TYPE_AAAA, payloadAaaa, ttlSeconds = 60)

        val cachedA = cache.get("example.com", DnsConstants.TYPE_A, queryId = 0x9999)
        val cachedAaaa = cache.get("example.com", DnsConstants.TYPE_AAAA, queryId = 0x9999)

        assertNotNull(cachedA)
        assertNotNull(cachedAaaa)
        assertEquals(2, cache.size)
    }
}
