package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Upstream resolver selection (network-change semantics) and the UDP relay itself, exercised
 * against a real local echo server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpstreamDnsTest {

    // ------------------------------------------------------------------ server selection

    @Test
    fun `link resolvers are used verbatim when present`() {
        // Note: InetAddress canonicalizes IPv6 into its full expanded form.
        assertEquals(
            listOf("192.168.1.1", "fd00:0:0:0:0:0:0:1"),
            UpstreamDnsServers.current(listOf("192.168.1.1", "fd00::1"))
        )
    }

    @Test
    fun `duplicates and blanks are collapsed`() {
        assertEquals(
            listOf("192.168.1.1"),
            UpstreamDnsServers.current(listOf("192.168.1.1", "192.168.1.1", "  ", "garbage-"))
        )
    }

    @Test
    fun `the tunnel's own fake addresses are never selected - no forwarding loop`() {
        assertEquals(
            UpstreamDnsServers.FALLBACK_SERVERS,
            UpstreamDnsServers.current(listOf(UpstreamDnsServers.VPN_DNS_IPV4, UpstreamDnsServers.VPN_DNS_IPV6))
        )
        // Even mixed with real servers the fake ones are dropped.
        assertEquals(
            listOf("192.168.1.1"),
            UpstreamDnsServers.current(listOf(UpstreamDnsServers.VPN_DNS_IPV4, "192.168.1.1"))
        )
    }

    @Test
    fun `no usable link resolver falls back to public servers`() {
        assertEquals(UpstreamDnsServers.FALLBACK_SERVERS, UpstreamDnsServers.current(emptyList()))
    }

    // ------------------------------------------------------------------ relay

    @Test
    fun `relays a query to the upstream and returns its response verbatim`() {
        val query = DnsPacketCodec.buildQuery(0x2A2A, "example.com", DnsPacketCodec.TYPE_A)
        val server = EchoDnsServer()
        server.start()

        val protectorCalls = AtomicInteger()
        val resolver = UpstreamDnsResolver(
            servers = { listOf("127.0.0.1") },
            socketProtector = { protectorCalls.incrementAndGet(); true },
            timeoutMs = 5_000,
            upstreamPort = server.port
        )
        try {
            val response = resolver.resolve(query)
            assertNotNull("resolver must answer from the echo server", response)
            assertEquals(0x2A2A, DnsPacketCodec.readShort(response!!, 0))
            assertEquals("example.com", DnsPacketCodec.parse(response)!!.domain)
            assertTrue("the relay socket must be protected (VpnService.protect)", protectorCalls.get() >= 1)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `unreachable upstreams yield null so the caller can SERVFAIL`() {
        // Port 1 on loopback is not listening and no ICMP reply will produce a datagram.
        val resolver = UpstreamDnsResolver(
            servers = { listOf("127.0.0.1") },
            timeoutMs = 200,
            upstreamPort = 1
        )
        assertNull(resolver.resolve(DnsPacketCodec.buildQuery(1, "example.com", 1)))
    }

    @Test
    fun `protector refusal skips the server instead of looping into the tunnel`() {
        val resolver = UpstreamDnsResolver(
            servers = { listOf("127.0.0.1") },
            socketProtector = { false }, // protect() failed: the socket would loop through the VPN
            timeoutMs = 200,
            upstreamPort = 1
        )
        assertNull(resolver.resolve(DnsPacketCodec.buildQuery(1, "example.com", 1)))
    }

    /** Minimal real UDP server: replies with the same transaction id / question and one A record. */
    private class EchoDnsServer {
        private val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
        private val running = AtomicBoolean(true)
        private val started = CountDownLatch(1)
        private val thread = Thread {
            started.countDown()
            while (running.get()) {
                try {
                    val buffer = ByteArray(65535)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.soTimeout = 500
                    socket.receive(packet)
                    val parsed = DnsPacketCodec.parse(buffer, 0, packet.length) ?: continue
                    val answer = ByteArray(16).also {
                        it[0] = 0xC0.toByte(); it[1] = 0x0C
                        DnsPacketCodec.writeShort(it, 2, DnsPacketCodec.TYPE_A)
                        DnsPacketCodec.writeShort(it, 4, DnsPacketCodec.CLASS_IN)
                        DnsPacketCodec.writeInt(it, 6, 300)
                        DnsPacketCodec.writeShort(it, 10, 4)
                        it[12] = 127; it[13] = 0; it[14] = 0; it[15] = 1
                    }
                    val response = (buffer.copyOf(packet.length) + answer).also {
                        DnsPacketCodec.writeShort(it, 2, 0x8180)
                        DnsPacketCodec.writeShort(it, 6, 1)
                    }
                    socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
                } catch (_: Exception) {
                    // timeout or socket closed: keep looping until stopped
                }
            }
        }

        val port: Int get() = socket.localPort

        fun start() {
            thread.start()
            started.await(2, TimeUnit.SECONDS)
        }

        fun stop() {
            running.set(false)
            socket.close()
        }
    }
}
