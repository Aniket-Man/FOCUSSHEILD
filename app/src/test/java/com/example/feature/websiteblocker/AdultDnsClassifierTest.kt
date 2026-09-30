package com.example.feature.websiteblocker

import com.example.feature.websiteblocker.engine.AdultDnsClassifier
import com.example.feature.websiteblocker.engine.AdultDnsVerdict
import com.example.feature.websiteblocker.engine.DnsQueryResult
import com.example.feature.websiteblocker.engine.DnsTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Regression tests for issue #56:
 *  - first visit must be classified (bounded wait, not fire-and-forget),
 *  - NXDOMAIN / lookup failures are never "adult",
 *  - DoH fallback when plain DNS fails,
 *  - cached verdicts never stall the caller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdultDnsClassifierTest {

    private class RecordingTransport(
        private val udpHandler: () -> DnsQueryResult = { DnsQueryResult.Failed },
        private val dohHandler: () -> DnsQueryResult = { DnsQueryResult.Failed }
    ) : DnsTransport {
        val udpServers = mutableListOf<String>()
        val dohEndpoints = mutableListOf<String>()

        override fun queryUdp(domain: String, server: String): DnsQueryResult {
            udpServers.add(server)
            return udpHandler()
        }

        override fun queryDoh(domain: String, endpoint: String): DnsQueryResult {
            dohEndpoints.add(endpoint)
            return dohHandler()
        }
    }

    @Before
    fun setUp() {
        AdultDnsClassifier.resetForTest()
    }

    @After
    fun tearDown() {
        AdultDnsClassifier.resetForTest()
    }

    @Test
    fun `first visit to a family-sinkholed domain is ADULT`() {
        val transport = RecordingTransport(
            udpHandler = { DnsQueryResult.Resolved(listOf("0.0.0.0")) }
        )
        AdultDnsClassifier.transport = transport

        val verdict = AdultDnsClassifier.verdict("freshvault-video.example")

        assertEquals(AdultDnsVerdict.ADULT, verdict)
        assertTrue("expected at least one UDP lookup", transport.udpServers.isNotEmpty())
    }

    @Test
    fun `first visit to a domain the family resolver allows is SAFE`() {
        val transport = RecordingTransport(
            udpHandler = { DnsQueryResult.Resolved(listOf("93.184.216.34")) }
        )
        AdultDnsClassifier.transport = transport

        val verdict = AdultDnsClassifier.verdict("allowed-site.example")

        assertEquals(AdultDnsVerdict.SAFE, verdict)
        assertTrue(transport.udpServers.isNotEmpty())
    }

    @Test
    fun `NXDOMAIN is UNKNOWN and never adult`() {
        val transport = RecordingTransport(
            udpHandler = { DnsQueryResult.NotClassified },
            dohHandler = { DnsQueryResult.NotClassified }
        )
        AdultDnsClassifier.transport = transport

        val verdict = AdultDnsClassifier.verdict("does-not-exist-typo.example")

        assertEquals(AdultDnsVerdict.UNKNOWN, verdict)
        assertNotEquals(AdultDnsVerdict.ADULT, verdict)
        assertTrue("DoH must not run when UDP answered without failure", transport.dohEndpoints.isEmpty())
    }

    @Test
    fun `UDP failure falls back to DoH which can still classify as ADULT`() {
        val transport = RecordingTransport(
            udpHandler = { DnsQueryResult.Failed },
            dohHandler = { DnsQueryResult.Resolved(listOf("0.0.0.0")) }
        )
        AdultDnsClassifier.transport = transport

        val verdict = AdultDnsClassifier.verdict("doh-only-adult.example")

        assertEquals(AdultDnsVerdict.ADULT, verdict)
        assertTrue("DoH fallback must be attempted after UDP failures", transport.dohEndpoints.isNotEmpty())
    }

    @Test
    fun `total lookup failure degrades to UNKNOWN not ADULT`() {
        val transport = RecordingTransport(
            udpHandler = { DnsQueryResult.Failed },
            dohHandler = { DnsQueryResult.Failed }
        )
        AdultDnsClassifier.transport = transport

        val verdict = AdultDnsClassifier.verdict("network-down.example")

        assertEquals(AdultDnsVerdict.UNKNOWN, verdict)
    }

    @Test
    fun `second call is served from cache without another lookup`() {
        val transport = RecordingTransport(
            udpHandler = { DnsQueryResult.Resolved(listOf("0.0.0.0")) }
        )
        AdultDnsClassifier.transport = transport

        assertEquals(AdultDnsVerdict.ADULT, AdultDnsClassifier.verdict("cached-adult-site.example"))
        val lookupsAfterFirst = transport.udpServers.size

        assertEquals(AdultDnsVerdict.ADULT, AdultDnsClassifier.verdict("cached-adult-site.example"))
        assertEquals(lookupsAfterFirst, transport.udpServers.size)
    }

    @Test
    fun `wait budget bounds the stall while the background lookup still lands`() {
        val transport = RecordingTransport(
            udpHandler = {
                Thread.sleep(1_000)
                DnsQueryResult.Resolved(listOf("0.0.0.0"))
            }
        )
        AdultDnsClassifier.transport = transport

        // First call: budget exceeded -> UNKNOWN immediately, background lookup keeps running.
        val startedAt = System.currentTimeMillis()
        val first = AdultDnsClassifier.verdict("slow-adult-site.example", waitBudgetMs = 50)
        val elapsed = System.currentTimeMillis() - startedAt
        assertEquals(AdultDnsVerdict.UNKNOWN, first)
        assertTrue("wait must respect the budget (took ${elapsed}ms)", elapsed < 2_000)

        // Wait until the single background lookup has actually started.
        val startDeadline = System.currentTimeMillis() + 2_000
        while (transport.udpServers.isEmpty() && System.currentTimeMillis() < startDeadline) {
            Thread.sleep(10)
        }

        // Within the cooldown the caller must not stall again, and no second lookup starts.
        val second = AdultDnsClassifier.verdict("slow-adult-site.example", waitBudgetMs = 50)
        assertEquals(AdultDnsVerdict.UNKNOWN, second)
        Thread.sleep(100)
        assertEquals("single-flight lookup expected", 1, transport.udpServers.size)

        // Once the background lookup finishes, the cached verdict blocks the domain.
        val deadline = System.currentTimeMillis() + 5_000
        var resolved = AdultDnsVerdict.UNKNOWN
        while (System.currentTimeMillis() < deadline && resolved != AdultDnsVerdict.ADULT) {
            resolved = AdultDnsClassifier.verdict("slow-adult-site.example", waitBudgetMs = 50)
            if (resolved != AdultDnsVerdict.ADULT) Thread.sleep(50)
        }
        assertEquals(AdultDnsVerdict.ADULT, resolved)
    }

    @Test
    fun `parseDnsResponse treats NXDOMAIN as NotClassified`() {
        val packet = dnsResponsePacket(rcode = 3, answers = emptyList())
        val result = AdultDnsClassifier.parseDnsResponse(packet, packet.size)
        assertEquals(DnsQueryResult.NotClassified, result)
    }

    @Test
    fun `parseDnsResponse surfaces a sinkhole A record as Resolved`() {
        val packet = dnsResponsePacket(rcode = 0, answers = listOf(0, 0, 0, 0))
        val result = AdultDnsClassifier.parseDnsResponse(packet, packet.size)
        assertTrue(result is DnsQueryResult.Resolved)
        assertEquals(listOf("0.0.0.0"), (result as DnsQueryResult.Resolved).ips)
    }

    @Test
    fun `parseDnsResponse surfaces a real A record as Resolved`() {
        val packet = dnsResponsePacket(rcode = 0, answers = listOf(93, 184, 216, 34))
        val result = AdultDnsClassifier.parseDnsResponse(packet, packet.size)
        assertTrue(result is DnsQueryResult.Resolved)
        assertEquals(listOf("93.184.216.34"), (result as DnsQueryResult.Resolved).ips)
    }

    @Test
    fun `buildDnsQuery encodes the domain as an A query`() {
        val query = AdultDnsClassifier.buildDnsQuery("example.com")
        // header(12) + "example" label(8) + "com" label(4) + terminator(1) + QTYPE(2) + QCLASS(2)
        assertEquals(29, query.size)
        assertEquals(0, query[query.size - 4].toInt() and 0xFF) // QTYPE high byte (A = 1)
        assertEquals(1, query[query.size - 3].toInt() and 0xFF) // QTYPE low byte
        assertEquals(0, query[query.size - 2].toInt() and 0xFF) // QCLASS high byte
        assertEquals(1, query[query.size - 1].toInt() and 0xFF) // QCLASS low byte
        assertFalse(query.isEmpty())
    }

    /** Builds a minimal DNS response packet with one optional A-record answer. */
    private fun dnsResponsePacket(rcode: Int, answers: List<Int>): ByteArray {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)
        dos.writeShort(0x1234)                     // ID
        dos.writeShort(0x8180 or rcode)            // flags (QR + RD + RA | rcode)
        dos.writeShort(1)                          // QDCOUNT
        dos.writeShort(if (answers.isEmpty()) 0 else 1) // ANCOUNT (one answer record)
        dos.writeShort(0)                          // NSCOUNT
        dos.writeShort(0)                          // ARCOUNT
        // Question: example.com
        for (label in listOf("example", "com")) {
            dos.writeByte(label.length)
            dos.write(label.toByteArray(Charsets.UTF_8))
        }
        dos.writeByte(0)
        dos.writeShort(1)                          // QTYPE A
        dos.writeShort(1)                          // QCLASS IN
        // Answer (compression pointer to the question name at offset 12)
        if (answers.isNotEmpty()) {
            dos.writeShort(0xC00C)                 // NAME pointer
            dos.writeShort(1)                      // TYPE A
            dos.writeShort(1)                      // CLASS IN
            dos.writeInt(60)                       // TTL
            dos.writeShort(4)                      // RDLENGTH
            answers.forEach { dos.writeByte(it) }  // RDATA
        }
        dos.flush()
        return stream.toByteArray()
    }
}
