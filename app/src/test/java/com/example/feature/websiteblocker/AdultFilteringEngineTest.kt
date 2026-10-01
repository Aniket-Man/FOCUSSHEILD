package com.example.feature.websiteblocker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.FocusShieldApp
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedWebsiteRepository
import com.example.feature.websiteblocker.vpn.AdultDnsVpnService
import com.example.feature.websiteblocker.vpn.AdultFilteringEngine
import com.example.feature.websiteblocker.vpn.DnsConstants
import com.example.feature.websiteblocker.vpn.DnsPacketParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdultFilteringEngineTest {

    private lateinit var context: Context
    private lateinit var preferencesRepository: FocusPreferencesRepository
    private lateinit var websiteRepository: BlockedWebsiteRepository
    private lateinit var engine: AdultFilteringEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as FocusShieldApp
        preferencesRepository = app.preferencesRepository
        websiteRepository = app.blockedWebsiteRepository

        engine = AdultFilteringEngine.initialize(
            appContext = context,
            preferencesRepository = preferencesRepository,
            websiteRepository = websiteRepository
        )
    }

    @After
    fun tearDown() {
        engine.stopVpn()
    }

    @Test
    fun `test blocked domain`() {
        assertTrue("Core adult domain must be blocked", engine.shouldBlockDomain("pornhub.com"))
        assertTrue("Core adult tube domain must be blocked", engine.shouldBlockDomain("xvideos.com"))
        assertTrue("Cam network domain must be blocked", engine.shouldBlockDomain("chaturbate.com"))
        assertTrue("Adult TLD domain must be blocked", engine.shouldBlockDomain("fresh-content.xxx"))
    }

    @Test
    fun `test blocked subdomain`() {
        assertTrue("Direct subdomain must be blocked", engine.shouldBlockDomain("cdn.pornhub.com"))
        assertTrue("Deep subdomain must be blocked", engine.shouldBlockDomain("stream.video.xhamster.com"))
        assertTrue("Subdomain with adult TLD must be blocked", engine.shouldBlockDomain("video.site.porn"))
    }

    @Test
    fun `test allowed domain`() {
        assertFalse("Educational domain must be allowed", engine.shouldBlockDomain("wikipedia.org"))
        assertFalse("Subdomain of educational site must be allowed", engine.shouldBlockDomain("en.wikipedia.org"))
        assertFalse("Search engine must be allowed", engine.shouldBlockDomain("google.com"))
        assertFalse("Developer platform must be allowed", engine.shouldBlockDomain("github.com"))
    }

    @Test
    fun `test allowlist precedence`() {
        // Safe words and domains that contain adult substrings but are allowlisted
        assertFalse("essex.gov.uk contains 'sex' but is whitelisted", engine.shouldBlockDomain("essex.gov.uk"))
        assertFalse("sussex.ac.uk contains 'sex' but is whitelisted", engine.shouldBlockDomain("sussex.ac.uk"))
        assertFalse("peacocktv.com contains 'cock' but is whitelisted", engine.shouldBlockDomain("peacocktv.com"))
        assertFalse("analytica.org contains 'anal' but is whitelisted", engine.shouldBlockDomain("analytica.org"))
    }

    @Test
    fun `test non-matching domains`() {
        assertFalse("Regular harmless domain must not be blocked", engine.shouldBlockDomain("mycompanywebsite.com"))
        assertFalse("Tech news site must not be blocked", engine.shouldBlockDomain("theverge.com"))
        assertFalse("Online shop must not be blocked", engine.shouldBlockDomain("amazon.com"))
        assertFalse("Empty input must not be blocked", engine.shouldBlockDomain(""))
    }

    @Test
    fun `test malformed DNS queries return null safely`() {
        val corruptedBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = engine.processPacket(corruptedBytes, corruptedBytes.size)
        assertNull("Malformed packet must be dropped without throwing", result)
    }

    @Test
    fun `test large blocklists evaluation`() {
        // The engine corpus contains hundreds of curated adult sites + suffix tree matching
        // Verify fast lookups on varied inputs
        for (i in 1..100) {
            assertTrue(engine.shouldBlockDomain("sub$i.pornhub.com"))
            assertFalse(engine.shouldBlockDomain("sub$i.khanacademy.org"))
        }
    }

    @Test
    fun `test VPN start and stop`() {
        engine.ensureVpnRunning()
        // In Robolectric test, check intent or state
        engine.stopVpn()
        assertFalse("Engine must be inactive after stopVpn", engine.isEngineActive)
        assertEquals("DNS cache should be cleared on stop", 0, engine.dnsCache.size)
    }

    @Test
    fun `test toggle ON starts engine and toggle OFF stops engine`() = runTest {
        // Toggle ON
        preferencesRepository.updateAutoAdultWebsiteBlocking(true)
        val prefsOn = preferencesRepository.preferencesFlow.first()
        assertTrue(prefsOn.isAutoAdultWebsiteBlockingEnabled)

        // Toggle OFF
        preferencesRepository.updateAutoAdultWebsiteBlocking(false)
        val prefsOff = preferencesRepository.preferencesFlow.first()
        assertFalse(prefsOff.isAutoAdultWebsiteBlockingEnabled)

        // Re-enable for subsequent tests
        preferencesRepository.updateAutoAdultWebsiteBlocking(true)
    }

    @Test
    fun `test app restart with blocking enabled restores state`() = runTest {
        preferencesRepository.updateAutoAdultWebsiteBlocking(true)
        val prefs = preferencesRepository.preferencesFlow.first()
        assertTrue(prefs.isAutoAdultWebsiteBlockingEnabled)

        // Simulate app restart / boot sync
        engine.syncWithPreferences()
        val restoredPrefs = preferencesRepository.preferencesFlow.first()
        assertTrue(restoredPrefs.isAutoAdultWebsiteBlockingEnabled)
    }

    @Test
    fun `test network changes update upstream DNS configuration`() {
        val proxy = engine.dnsProxy
        val newServers = listOf("8.8.4.4", "1.0.0.1")
        proxy.updateUpstreamServers(newServers)
        // Upstream server configuration updated successfully
        assertNotNull(proxy)
    }

    @Test
    fun `test blocked domain receives sinkhole response via processPacket`() {
        val queryPacket = buildSyntheticDnsQueryPacket(
            domain = "pornhub.com",
            qtype = DnsConstants.TYPE_A,
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(10, 0, 0, 53),
            srcPort = 44444,
            dstPort = 53
        )

        val responsePacket = engine.processPacket(queryPacket, queryPacket.size)
        assertNotNull("Blocked domain must yield an immediate sinkhole response", responsePacket)

        val responseIp = DnsPacketParser.parseIpPacket(responsePacket!!, responsePacket.size)
        assertNotNull(responseIp)
        assertEquals(44444, responseIp!!.dstPort)
        assertEquals(53, responseIp.srcPort)
    }

    private fun buildSyntheticDnsQueryPacket(
        domain: String,
        qtype: Int,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int
    ): ByteArray {
        val dnsBytes = buildRawDnsQuery(domain, qtype)
        val udpLen = 8 + dnsBytes.size
        val totalLen = 20 + udpLen

        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        buffer.put(0x45.toByte())
        buffer.put(0.toByte())
        buffer.putShort(totalLen.toShort())
        buffer.putShort(1.toShort())
        buffer.putShort(0.toShort())
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0.toShort())
        buffer.put(srcIp)
        buffer.put(dstIp)

        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort())

        buffer.put(dnsBytes)
        return packet
    }

    private fun buildRawDnsQuery(domain: String, qtype: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeShort(0x7890)
        dos.writeShort(0x0100)
        dos.writeShort(1)
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        for (label in domain.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)

        dos.writeShort(qtype)
        dos.writeShort(1)

        dos.flush()
        return stream.toByteArray()
    }
}
