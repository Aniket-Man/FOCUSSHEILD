package com.example.feature.websiteblocker

import com.example.feature.websiteblocker.engine.AdultDnsClassifier
import com.example.feature.websiteblocker.engine.DnsQueryResult
import com.example.feature.websiteblocker.engine.DnsTransport
import com.example.feature.websiteblocker.engine.WebsiteBlockerEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Engine 1 regression tests for issue #56: a domain absent from the hardcoded lists must be
 * blocked on its **first** classification when the family resolver sinkholes it, while NXDOMAIN
 * and plain-but-unknown domains must not be blocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebsiteBlockerEngineAdultTest {

    private class RecordingTransport(
        private val udpHandler: () -> DnsQueryResult
    ) : DnsTransport {
        val udpServers = mutableListOf<String>()

        override fun queryUdp(domain: String, server: String): DnsQueryResult {
            udpServers.add(server)
            return udpHandler()
        }

        override fun queryDoh(domain: String, endpoint: String): DnsQueryResult =
            DnsQueryResult.Failed
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
    fun `domain unknown to the lists is blocked on first visit when family DNS sinkholes it`() {
        val transport = RecordingTransport(udpHandler = { DnsQueryResult.Resolved(listOf("0.0.0.0")) })
        AdultDnsClassifier.transport = transport

        val decision = WebsiteBlockerEngine.evaluateAutoAdult(
            "https://freshvault-video.example/watch",
            "freshvault-video.example"
        )

        assertNotNull("first classification must block a sinkholed domain", decision)
        decision!!
        assertEquals("ADULT_AUTOMATIC", decision.engineType)
        assertEquals("Family DNS Filter (Adult Content Blocked)", decision.reason)
        assertTrue(transport.udpServers.isNotEmpty())
    }

    @Test
    fun `lexical fast path still blocks known adult domains without any DNS lookup`() {
        val transport = RecordingTransport(udpHandler = { DnsQueryResult.Failed })
        AdultDnsClassifier.transport = transport

        val decision = WebsiteBlockerEngine.evaluateAutoAdult(
            "https://www.pornhub.com/video",
            "www.pornhub.com"
        )

        assertNotNull(decision)
        decision!!
        assertTrue(
            "expected lexical reason, got: ${decision.reason}",
            decision.reason.startsWith("Adult Content Filter")
        )
        assertTrue("lexical hit must not hit the network", transport.udpServers.isEmpty())
    }

    @Test
    fun `domain the family resolver allows on first visit is not blocked`() {
        val transport = RecordingTransport(udpHandler = { DnsQueryResult.Resolved(listOf("93.184.216.34")) })
        AdultDnsClassifier.transport = transport

        val decision = WebsiteBlockerEngine.evaluateAutoAdult(
            "https://freshvault-video.example/watch",
            "freshvault-video.example"
        )

        assertNull(decision)
    }

    @Test
    fun `NXDOMAIN typo domain is not blocked`() {
        val transport = RecordingTransport(udpHandler = { DnsQueryResult.NotClassified })
        AdultDnsClassifier.transport = transport

        val decision = WebsiteBlockerEngine.evaluateAutoAdult(
            "https://typo-nonexistent-site.example",
            "typo-nonexistent-site.example"
        )

        assertNull("NXDOMAIN must never be reported as adult content", decision)
    }

    @Test
    fun `blank domain yields no decision`() {
        assertNull(WebsiteBlockerEngine.evaluateAutoAdult("https://example.com", "   "))
        assertNull(WebsiteBlockerEngine.evaluateAutoAdult("https://example.com", ""))
    }
}
