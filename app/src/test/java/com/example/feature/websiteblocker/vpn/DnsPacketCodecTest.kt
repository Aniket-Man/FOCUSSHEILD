package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format tests for the DNS codec: question parsing (including hostile/malformed datagrams),
 * locally generated block/SERVFAIL answers, and TTL extraction for caching.
 */
class DnsPacketCodecTest {

    private fun query(domain: String, qtype: Int, transactionId: Int = 0x1234): ByteArray =
        DnsPacketCodec.buildQuery(transactionId, domain, qtype)

    // ------------------------------------------------------------------ parsing

    @Test
    fun `parses an A query`() {
        val parsed = DnsPacketCodec.parse(query("www.pornhub.com", DnsPacketCodec.TYPE_A))!!
        assertEquals(0x1234, parsed.transactionId)
        assertEquals("www.pornhub.com", parsed.domain)
        assertEquals(DnsPacketCodec.TYPE_A, parsed.qtype)
        assertEquals(DnsPacketCodec.CLASS_IN, parsed.qclass)
        assertEquals(false, parsed.isResponse)
        assertEquals(true, parsed.recursionDesired)
        assertEquals(12, parsed.questionOffset)
    }

    @Test
    fun `parses an AAAA query`() {
        val parsed = DnsPacketCodec.parse(query("example.com", DnsPacketCodec.TYPE_AAAA))!!
        assertEquals(DnsPacketCodec.TYPE_AAAA, parsed.qtype)
        assertEquals("example.com", parsed.domain)
    }

    @Test
    fun `uppercase QNAME is lowercased`() {
        val raw = query("EXAMPLE.COM", DnsPacketCodec.TYPE_A)
        assertEquals("example.com", DnsPacketCodec.parse(raw)!!.domain)
    }

    @Test
    fun `parses at a non-zero offset inside an IP packet buffer`() {
        val dns = query("example.com", DnsPacketCodec.TYPE_A)
        val buffer = ByteArray(64)
        System.arraycopy(dns, 0, buffer, 28, dns.size)
        val parsed = DnsPacketCodec.parse(buffer, 28, dns.size)!!
        assertEquals("example.com", parsed.domain)
        assertEquals(28 + 12, parsed.questionOffset)
    }

    @Test
    fun `malformed queries return null instead of throwing`() {
        assertNull(DnsPacketCodec.parse(ByteArray(0)))                       // empty
        assertNull(DnsPacketCodec.parse(ByteArray(11)))                      // truncated header
        val zeroQuestions = DnsPacketCodec.buildQuery(1, "a.com", 1).also {
            DnsPacketCodec.writeShort(it, 4, 0) // QDCOUNT = 0
        }
        assertNull(DnsPacketCodec.parse(zeroQuestions))                      // no question
        assertNull(DnsPacketCodec.parse(query("a.com", 1), 0, 13))          // header only

        // Label claims 63 bytes but the datagram ends immediately after the label header.
        val lying = ByteArray(20)
        DnsPacketCodec.writeShort(lying, 4, 1)
        lying[12] = 63
        assertNull(DnsPacketCodec.parse(lying))

        // Compression pointer in the question: treated as malformed.
        val pointer = query("a.com", 1).also {
            it[12] = 0xC0.toByte()
            it[13] = 0x0C
        }
        assertNull(DnsPacketCodec.parse(pointer))

        // Question (QTYPE/QCLASS) overruns the datagram: name consumes everything.
        val overrun = ByteArray(13)
        DnsPacketCodec.writeShort(overrun, 4, 1)
        assertNull(DnsPacketCodec.parse(overrun))
    }

    @Test
    fun `a response message is parsed with isResponse set`() {
        val response = DnsPacketCodec.buildBlockedResponse(
            DnsPacketCodec.parse(query("pornhub.com", DnsPacketCodec.TYPE_A))!!,
            query("pornhub.com", DnsPacketCodec.TYPE_A)
        )
        val parsed = DnsPacketCodec.parse(response)!!
        assertEquals(true, parsed.isResponse)
        assertEquals("pornhub.com", parsed.domain)
    }

    // ------------------------------------------------------------------ blocked responses

    @Test
    fun `blocked A answer carries the query id, question and 0_0_0_0`() {
        val raw = query("pornhub.com", DnsPacketCodec.TYPE_A, transactionId = 0xBEEF)
        val parsed = DnsPacketCodec.parse(raw)!!
        val response = DnsPacketCodec.buildBlockedResponse(parsed, raw)

        val reparsed = DnsPacketCodec.parse(response)!!
        assertEquals(0xBEEF, reparsed.transactionId)
        assertEquals(true, reparsed.isResponse)
        assertEquals("pornhub.com", reparsed.domain)
        assertEquals(DnsPacketCodec.TYPE_A, reparsed.qtype)

        // QR=1 and RA=1 in the flags word.
        assertEquals(0x80, (DnsPacketCodec.readShort(response, 2) shr 8) and 0x80)
        assertEquals(0x80, DnsPacketCodec.readShort(response, 2) and 0x80)
        // One answer.
        assertEquals(1, DnsPacketCodec.readShort(response, 6))

        // Answer starts after header+question: NAME pointer, then type/class/ttl/rdlength/rdata.
        val answerStart = 12 + parsed.questionLength
        assertEquals(0xC00C, DnsPacketCodec.readShort(response, answerStart))
        assertEquals(DnsPacketCodec.TYPE_A, DnsPacketCodec.readShort(response, answerStart + 2))
        assertEquals(DnsPacketCodec.CLASS_IN, DnsPacketCodec.readShort(response, answerStart + 4))
        assertEquals(DnsPacketCodec.BLOCKED_TTL_SECONDS, DnsPacketCodec.readShort(response, answerStart + 8))
        assertEquals(4, DnsPacketCodec.readShort(response, answerStart + 10))
        // RDATA (answerStart+12..+15) = 0.0.0.0
        for (i in 12..15) {
            assertEquals(0, response[answerStart + i].toInt())
        }
    }

    @Test
    fun `blocked AAAA answer carries the query id and the unspecified IPv6 address`() {
        val raw = query("www.xnxx.com", DnsPacketCodec.TYPE_AAAA, transactionId = 0x07D0)
        val response = DnsPacketCodec.buildBlockedResponse(DnsPacketCodec.parse(raw)!!, raw)
        assertEquals(0x07D0, DnsPacketCodec.readShort(response, 0))
        assertEquals(1, DnsPacketCodec.readShort(response, 6))
        val parsed = DnsPacketCodec.parse(raw)!!
        val answerStart = 12 + parsed.questionLength
        assertEquals(DnsPacketCodec.TYPE_AAAA, DnsPacketCodec.readShort(response, answerStart + 2))
        assertEquals(16, DnsPacketCodec.readShort(response, answerStart + 10))
        for (i in 12..27) {
            assertEquals(0, response[answerStart + i].toInt())
        }
    }

    @Test
    fun `blocked HTTPS query gets NOERROR with zero answers - no IP hints leak`() {
        val raw = query("pornhub.com", 65) // HTTPS/SVCB record
        val response = DnsPacketCodec.buildBlockedResponse(DnsPacketCodec.parse(raw)!!, raw)
        assertEquals(0, DnsPacketCodec.readShort(response, 2) and 0x0F) // rcode NOERROR
        assertEquals(1, DnsPacketCodec.readShort(response, 4))          // question echoed
        assertEquals(0, DnsPacketCodec.readShort(response, 6))          // no answers
        assertEquals(12 + DnsPacketCodec.parse(raw)!!.questionLength, response.size)
    }

    @Test
    fun `servfail response echoes the question with rcode 2`() {
        val raw = query("example.org", DnsPacketCodec.TYPE_A, transactionId = 0x4242)
        val parsed = DnsPacketCodec.parse(raw)!!
        val response = DnsPacketCodec.buildErrorResponse(parsed, raw, DnsPacketCodec.RCODE_SERVFAIL)!!
        assertEquals(0x4242, DnsPacketCodec.readShort(response, 0))
        assertEquals(DnsPacketCodec.RCODE_SERVFAIL, DnsPacketCodec.readShort(response, 2) and 0x0F)
        assertEquals(1, DnsPacketCodec.readShort(response, 4))
        assertEquals(0, DnsPacketCodec.readShort(response, 6))
        assertEquals("example.org", DnsPacketCodec.parse(response)!!.domain)
    }

    // ------------------------------------------------------------------ TTL extraction

    @Test
    fun `minAnswerTtl returns the smallest answer TTL`() {
        val raw = query("example.com", DnsPacketCodec.TYPE_A)
        val parsed = DnsPacketCodec.parse(raw)!!
        // Two A answers: TTL 300 and 120.
        val answer = { ttl: Int ->
            ByteArray(16).also {
                it[0] = 0xC0.toByte(); it[1] = 0x0C
                DnsPacketCodec.writeShort(it, 2, DnsPacketCodec.TYPE_A)
                DnsPacketCodec.writeShort(it, 4, DnsPacketCodec.CLASS_IN)
                DnsPacketCodec.writeInt(it, 6, ttl)
                DnsPacketCodec.writeShort(it, 10, 4)
            }
        }
        val response = (raw + answer(300) + answer(120)).also {
            DnsPacketCodec.writeShort(it, 2, 0x8180) // QR=1, RA=1
            DnsPacketCodec.writeShort(it, 6, 2)      // ANCOUNT = 2
        }
        assertEquals(120, DnsPacketCodec.minAnswerTtl(response))
    }

    @Test
    fun `minAnswerTtl is null without parseable answers`() {
        assertNull(DnsPacketCodec.minAnswerTtl(query("example.com", DnsPacketCodec.TYPE_A)))
        assertNull(DnsPacketCodec.minAnswerTtl(ByteArray(8)))
    }
}
