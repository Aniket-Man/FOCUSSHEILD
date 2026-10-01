package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTL cache behaviour: ID patching on hits, expiry with an injected clock, TTL clamping,
 * eviction and network-change flushes.
 */
class DnsCacheTest {

    private class FakeClock(var now: Long = 1_000_000L)

    private fun responseFor(transactionId: Int, ttlSeconds: Int): ByteArray {
        val query = DnsPacketCodec.buildQuery(transactionId, "example.com", DnsPacketCodec.TYPE_A)
        val parsed = DnsPacketCodec.parse(query)!!
        val blocked = DnsPacketCodec.buildBlockedResponse(parsed, query)
        // Rewrite the TTL so every answer TTL is testable — the blocked builder always writes
        // BLOCKED_TTL_SECONDS, so patch it the way a real upstream answer would differ.
        DnsPacketCodec.writeInt(blocked, 12 + parsed.questionLength + 6, ttlSeconds)
        return blocked
    }

    @Test
    fun `hit returns the stored response with the new transaction id`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })
        val stored = responseFor(transactionId = 0x1111, ttlSeconds = 60)
        cache.store(DnsCache.keyFor("example.com", DnsPacketCodec.TYPE_A), stored, ttlSeconds = 60)

        val hit = cache.lookup(DnsCache.keyFor("example.com", DnsPacketCodec.TYPE_A), transactionId = 0x9999)!!
        assertEquals(0x9999, DnsPacketCodec.readShort(hit, 0))
        // Everything after the ID is byte-identical to the stored upstream answer.
        assertTrue(hit.copyOfRange(2, hit.size).contentEquals(stored.copyOfRange(2, stored.size)))
        // The stored original is not mutated.
        assertEquals(0x1111, DnsPacketCodec.readShort(stored, 0))
    }

    @Test
    fun `miss and expiry return null`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })
        assertNull(cache.lookup(DnsCache.keyFor("missing.com", DnsPacketCodec.TYPE_A), 1))

        cache.store(DnsCache.keyFor("example.com", DnsPacketCodec.TYPE_A), responseFor(1, ttlSeconds = 60), 60)
        clock.now += 59_999
        assertEquals(true, cache.lookup(DnsCache.keyFor("example.com", DnsPacketCodec.TYPE_A), 2) != null)
        clock.now += 2
        assertNull(cache.lookup(DnsCache.keyFor("example.com", DnsPacketCodec.TYPE_A), 2))
    }

    @Test
    fun `ttl is clamped to the configured bounds`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })

        cache.store(DnsCache.keyFor("a.com", 1), responseFor(1, 1), ttlSeconds = 0)
        clock.now += 10_001
        assertNull("zero TTL must be raised to the minimum", cache.lookup(DnsCache.keyFor("a.com", 1), 1))

        clock.now = 1_000_000
        cache.store(DnsCache.keyFor("b.com", 1), responseFor(1, 1), ttlSeconds = 86_400)
        clock.now += 601_000
        assertNull("huge TTLs must be capped", cache.lookup(DnsCache.keyFor("b.com", 1), 1))
        clock.now -= 1_001
        assertEquals(
            "capped entry must still be valid just before the cap",
            true,
            cache.lookup(DnsCache.keyFor("b.com", 1), 1) != null
        )
    }

    @Test
    fun `null ttl falls back to the default`() {
        val clock = FakeClock()
        val cache = DnsCache(clock = { clock.now })
        cache.store(DnsCache.keyFor("c.com", 1), responseFor(1, 1), ttlSeconds = null)
        clock.now += DnsPacketCodec.DEFAULT_CACHE_TTL_SECONDS * 1000L - 1
        assertEquals(true, cache.lookup(DnsCache.keyFor("c.com", 1), 1) != null)
        clock.now += 2
        assertNull(cache.lookup(DnsCache.keyFor("c.com", 1), 1))
    }

    @Test
    fun `overflow evicts expired entries and clears as a last resort`() {
        val clock = FakeClock()
        val cache = DnsCache(maxEntries = 3, clock = { clock.now })
        for (i in 1..3) {
            cache.store(DnsCache.keyFor("domain$i.com", 1), responseFor(1, 1), ttlSeconds = 60)
        }
        assertEquals(3, cache.size)

        // Expired entries are dropped instead of a full reset.
        clock.now += 120_000
        cache.store(DnsCache.keyFor("domain4.com", 1), responseFor(1, 1), ttlSeconds = 60)
        assertEquals(1, cache.size)

        // A cache full of live entries clears entirely (bounded, simple policy), then refills.
        cache.store(DnsCache.keyFor("domain5.com", 1), responseFor(1, 1), ttlSeconds = 60)
        assertEquals(2, cache.size)
        cache.store(DnsCache.keyFor("domain6.com", 1), responseFor(1, 1), ttlSeconds = 60)
        assertEquals(3, cache.size)
        cache.store(DnsCache.keyFor("domain7.com", 1), responseFor(1, 1), ttlSeconds = 60)
        assertEquals(1, cache.size)
        assertEquals(true, cache.lookup(DnsCache.keyFor("domain7.com", 1), 1) != null)
        assertNull("older live entries were dropped by the full reset", cache.lookup(DnsCache.keyFor("domain6.com", 1), 1))
    }

    @Test
    fun `clear drops everything - network change semantics`() {
        val cache = DnsCache()
        cache.store(DnsCache.keyFor("d.com", 1), responseFor(1, 1), 60)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.lookup(DnsCache.keyFor("d.com", 1), 1))
    }
}
