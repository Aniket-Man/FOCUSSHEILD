package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end decision tests at the DNS datagram level: a real wire-format query goes in and the
 * filter's BLOCK / CACHED / FORWARD outcome comes out — covering blocked domains, blocked
 * subdomains, allowlists and their precedence, non-matching domains, malformed queries and the
 * upstream-response caching path.
 */
class DnsFilterTest {

    private lateinit var cache: DnsCache
    private lateinit var filter: DnsFilter

    private val blocklist = DomainBlocklist(
        blockedDomains = setOf("pornhub.com", "xvideos.com"),
        blockedTlds = setOf("xxx"),
        allowedDomains = setOf("safe.example.com")
    )

    @Before
    fun setUp() {
        cache = DnsCache()
        filter = DnsFilter(blocklist, cache)
    }

    private fun aQuery(domain: String, transactionId: Int = 0x1234, qtype: Int = DnsPacketCodec.TYPE_A): ByteArray =
        DnsPacketCodec.buildQuery(transactionId, domain, qtype)

    @Test
    fun `blocked domain is answered locally with 0_0_0_0 and the query id`() {
        val query = aQuery("pornhub.com", transactionId = 0x2468)
        val outcome = filter.handleQuery(query, 0, query.size)

        assertTrue(outcome is DnsFilterOutcome.LocalResponse)
        val response = (outcome as DnsFilterOutcome.LocalResponse).response
        assertEquals(0x2468, DnsPacketCodec.readShort(response, 0))
        assertEquals(1, DnsPacketCodec.readShort(response, 6)) // one answer
        val reparsed = DnsPacketCodec.parse(response)!!
        assertEquals("pornhub.com", reparsed.domain)
        // A record RDATA (answerStart + 12 … + 15) is 0.0.0.0
        val answerStart = 12 + reparsed.questionLength
        for (i in 12..15) {
            assertEquals(0, response[answerStart + i].toInt())
        }
    }

    @Test
    fun `blocked subdomain is answered locally`() {
        for (domain in listOf("www.pornhub.com", "cdn.xvideos.com", "anything.xxx")) {
            val query = aQuery(domain)
            val outcome = filter.handleQuery(query, 0, query.size)
            assertTrue("expected $domain to be blocked", outcome is DnsFilterOutcome.LocalResponse)
        }
    }

    @Test
    fun `non-matching domain is forwarded unchanged`() {
        val query = aQuery("example.com", transactionId = 0x4321)
        val outcome = filter.handleQuery(query, 0, query.size)

        assertTrue(outcome is DnsFilterOutcome.Forward)
        outcome as DnsFilterOutcome.Forward
        assertTrue(outcome.query.contentEquals(query))
        assertNotNull(outcome.parsed)
        assertEquals("example.com", outcome.parsed!!.domain)
    }

    @Test
    fun `allowlisted domain is forwarded`() {
        val query = aQuery("safe.example.com")
        val outcome = filter.handleQuery(query, 0, query.size)
        assertTrue(outcome is DnsFilterOutcome.Forward)
    }

    @Test
    fun `allowlist precedence - allowed domain that would otherwise be blocked`() {
        val allowWins = DnsFilter(
            DomainBlocklist(
                blockedDomains = setOf("example.com"),
                blockedTlds = emptySet(),
                allowedDomains = setOf("safe.example.com")
            ),
            cache
        )
        assertTrue(allowWins.handleQuery(aQuery("example.com"), 0, aQuery("example.com").size) is DnsFilterOutcome.LocalResponse)
        assertTrue(allowWins.handleQuery(aQuery("other.example.com"), 0, aQuery("other.example.com").size) is DnsFilterOutcome.LocalResponse)
        assertTrue(allowWins.handleQuery(aQuery("safe.example.com"), 0, aQuery("safe.example.com").size) is DnsFilterOutcome.Forward)
    }

    @Test
    fun `malformed queries are forwarded verbatim - never silently broken`() {
        val malformed = listOf(
            ByteArray(0),
            ByteArray(9),
            byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00), // truncated header
            aQuery("a.com").copyOf(14) // question cut off mid-label
        )
        for (payload in malformed) {
            val outcome = filter.handleQuery(payload, 0, payload.size)
            assertTrue("malformed input must forward, not decide (got $outcome)", outcome is DnsFilterOutcome.Forward)
            outcome as DnsFilterOutcome.Forward
            assertTrue(outcome.query.contentEquals(payload))
            assertNull(outcome.parsed)
        }
    }

    @Test
    fun `response datagrams and non-IN classes are forwarded`() {
        val response = DnsPacketCodec.buildBlockedResponse(
            DnsPacketCodec.parse(aQuery("example.com"))!!,
            aQuery("example.com")
        )
        assertTrue(filter.handleQuery(response, 0, response.size) is DnsFilterOutcome.Forward)

        // version.bind / QCLASS CH: QNAME(7+1 + 4+1 + root=14) + QTYPE(2) puts QCLASS at offset 28.
        val chaos = aQuery("version.bind", qtype = 16).let {
            DnsPacketCodec.writeShort(it, 28, 3) // QCLASS = CH
            it
        }
        val outcome = filter.handleQuery(chaos, 0, chaos.size)
        assertTrue(outcome is DnsFilterOutcome.Forward)
        assertNull((outcome as DnsFilterOutcome.Forward).parsed)
    }

    @Test
    fun `upstream responses are cached and later hits are served with a patched id`() {
        val first = aQuery("example.org", transactionId = 0x0001)
        val outcome = filter.handleQuery(first, 0, first.size)
        assertTrue(outcome is DnsFilterOutcome.Forward)

        // Simulate the upstream answer (a valid NOERROR response with the same question/id).
        val upstream = buildUpstreamResponse(first)
        filter.onUpstreamResponse(upstream, upstream.size)

        val second = aQuery("example.org", transactionId = 0x7B7B)
        val cachedOutcome = filter.handleQuery(second, 0, second.size)
        assertTrue(cachedOutcome is DnsFilterOutcome.LocalResponse)
        val served = (cachedOutcome as DnsFilterOutcome.LocalResponse).response
        assertEquals(0x7B7B, DnsPacketCodec.readShort(served, 0))
        assertEquals("example.org", DnsPacketCodec.parse(served)!!.domain)
    }

    @Test
    fun `queries inside a larger packet buffer are decided at their offset`() {
        // Mirrors the real call path: the DNS datagram sits at payloadOffset inside the IP packet.
        val query = aQuery("pornhub.com")
        val buffer = ByteArray(query.size + 28)
        System.arraycopy(query, 0, buffer, 28, query.size)
        val outcome = filter.handleQuery(buffer, 28, query.size)
        assertTrue(outcome is DnsFilterOutcome.LocalResponse)
        assertEquals("pornhub.com", DnsPacketCodec.parse((outcome as DnsFilterOutcome.LocalResponse).response)!!.domain)
    }

    @Test
    fun `feeding a blocked answer back upstream cannot un-block the domain`() {
        val blockedQuery = aQuery("pornhub.com", transactionId = 1)
        val outcome = filter.handleQuery(blockedQuery, 0, blockedQuery.size)
        val response = (outcome as DnsFilterOutcome.LocalResponse).response
        // Re-feeding our own blocked answer as an "upstream response" must never poison the
        // blocklist decision into an allow.
        filter.onUpstreamResponse(response, response.size)
        val again = aQuery("pornhub.com", transactionId = 2)
        assertTrue(filter.handleQuery(again, 0, again.size) is DnsFilterOutcome.LocalResponse)
    }

    // ------------------------------------------------------------------ helpers

    /** Builds a minimal valid upstream answer: same id/question, one A record with TTL 300. */
    private fun buildUpstreamResponse(query: ByteArray): ByteArray {
        val parsed = DnsPacketCodec.parse(query)!!
        val answer = ByteArray(16).also {
            it[0] = 0xC0.toByte(); it[1] = 0x0C
            DnsPacketCodec.writeShort(it, 2, DnsPacketCodec.TYPE_A)
            DnsPacketCodec.writeShort(it, 4, DnsPacketCodec.CLASS_IN)
            DnsPacketCodec.writeInt(it, 6, 300)
            DnsPacketCodec.writeShort(it, 10, 4)
            it[12] = 93.toByte(); it[13] = 184.toByte(); it[14] = 216.toByte(); it[15] = 34.toByte()
        }
        return (query + answer).also {
            DnsPacketCodec.writeShort(it, 2, 0x8180) // QR=1, RD=1, RA=1
            DnsPacketCodec.writeShort(it, 6, 1)      // ANCOUNT
        }
    }
}
