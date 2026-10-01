package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet-level tests for the DNS-only tunnel: IPv4/IPv6 UDP parsing, reply construction
 * (addresses/ports swapped) and correct header/UDP checksums — the Linux IP stack validates the
 * checksums of packets injected into a tun, so a wrong checksum silently drops every answer.
 */
class IpPacketCodecTest {

    private val ipv4Src = byteArrayOf(10, 243.toByte(), 53, 53)
    private val ipv4Dst = byteArrayOf(10, 243.toByte(), 53, 53) // DNS-only tunnel: src == dst == fake resolver
    private val ipv6Src = ByteArray(16).also { it[0] = 0xfd.toByte(); it[1] = 0x42; it[15] = 0x53 }
    private val ipv6Dst = ipv6Src.copyOf()

    private val dns = DnsPacketCodec.buildQuery(0x1234, "www.pornhub.com", DnsPacketCodec.TYPE_A)

    // ------------------------------------------------------------------ parsing

    @Test
    fun `parses an IPv4 UDP DNS packet`() {
        val packet = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV4, ipv4Src, ipv4Dst, 5353, 53, dns)
        val parsed = IpPacketCodec.parseUdpPacket(packet)!!
        assertEquals(IpPacketCodec.IPV4, parsed.version)
        assertEquals(5353, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertEquals(dns.size, parsed.payloadLength)
        assertEquals("www.pornhub.com", DnsPacketCodec.parse(packet, parsed.payloadOffset, parsed.payloadLength)!!.domain)
    }

    @Test
    fun `parses an IPv6 UDP DNS packet`() {
        val packet = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV6, ipv6Src, ipv6Dst, 41821, 53, dns)
        val parsed = IpPacketCodec.parseUdpPacket(packet)!!
        assertEquals(IpPacketCodec.IPV6, parsed.version)
        assertEquals(41821, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertEquals(dns.size, parsed.payloadLength)
    }

    @Test
    fun `rejects non-UDP, truncated and empty packets`() {
        assertNull(IpPacketCodec.parseUdpPacket(ByteArray(0)))
        assertNull(IpPacketCodec.parseUdpPacket(ByteArray(28))) // v4 header + UDP but version bits 0

        val tcp = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV4, ipv4Src, ipv4Dst, 1234, 53, dns)
            .also { it[9] = 6 } // protocol TCP
        assertNull(IpPacketCodec.parseUdpPacket(tcp))

        val truncated = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV4, ipv4Src, ipv4Dst, 1234, 53, dns)
            .copyOf(30) // claims a longer packet than delivered
        assertNull(IpPacketCodec.parseUdpPacket(truncated))
    }

    // ------------------------------------------------------------------ replies

    @Test
    fun `reply swaps addresses and ports and preserves the payload`() {
        val queryPacket = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV4, ipv4Src, byteArrayOf(10, 0, 0, 2), 5353, 53, dns)
        val queryInfo = IpPacketCodec.parseUdpPacket(queryPacket)!!
        val response = IpPacketCodec.buildUdpResponse(queryInfo, dns)

        val parsedResponse = IpPacketCodec.parseUdpPacket(response)!!
        assertTrue(parsedResponse.srcAddress.contentEquals(byteArrayOf(10, 0, 0, 2)))
        assertTrue(parsedResponse.dstAddress.contentEquals(ipv4Src))
        assertEquals(53, parsedResponse.srcPort)
        assertEquals(5353, parsedResponse.dstPort)
        assertEquals(
            "www.pornhub.com",
            DnsPacketCodec.parse(response, parsedResponse.payloadOffset, parsedResponse.payloadLength)!!.domain
        )
    }

    // ------------------------------------------------------------------ checksums (independent verifier)

    @Test
    fun `IPv4 packets carry a valid header checksum`() {
        val packet = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV4, ipv4Src, ipv4Dst, 5353, 53, dns)
        // Summing the header *including* its checksum must fold to the all-ones pattern.
        assertTrue(verifyOnesComplementRegion(packet, 0, 20))
    }

    @Test
    fun `IPv4 packets carry a valid UDP checksum`() {
        val packet = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV4, ipv4Src, ipv4Dst, 5353, 53, dns)
        assertTrue(verifyUdpChecksum(packet, version = 4))
    }

    @Test
    fun `IPv6 packets carry a valid (mandatory) UDP checksum`() {
        val packet = IpPacketCodec.buildUdpPacket(IpPacketCodec.IPV6, ipv6Src, ipv6Dst, 41821, 53, dns)
        assertTrue(verifyUdpChecksum(packet, version = 6))
        assertEquals(0x60, packet[0].toInt() and 0xF0)
    }

    /** RFC 1071 verifier: the 16-bit ones' complement sum of the region must be the all-ones pattern. */
    private fun verifyOnesComplementRegion(data: ByteArray, from: Int, to: Int): Boolean {
        val total = fold(sum16(data, from, to))
        return total == 0xFFFF
    }

    private fun sum16(data: ByteArray, from: Int, to: Int): Long {
        var sum = 0L
        var i = from
        while (i < to) {
            val word = if (i + 1 < to) {
                ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            } else {
                (data[i].toInt() and 0xFF) shl 8
            }
            sum += word
            i += 2
        }
        return sum
    }

    private fun fold(sum: Long): Int {
        var s = sum
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s shr 16)
        return s.toInt()
    }

    /** Verifies the UDP checksum: pseudo-header + UDP header (with checksum) + payload must sum to 0xFFFF. */
    private fun verifyUdpChecksum(packet: ByteArray, version: Int): Boolean {
        val udpOffset = if (version == 4) 20 else 40
        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val checksum = ((packet[udpOffset + 6].toInt() and 0xFF) shl 8) or (packet[udpOffset + 7].toInt() and 0xFF)
        if (checksum == 0) return false // a zero field means "not computed" — invalid for IPv6, and not what we send

        val pseudo = when (version) {
            4 -> ByteArray(12).also {
                System.arraycopy(packet, 12, it, 0, 4)
                System.arraycopy(packet, 16, it, 4, 4)
                it[9] = 17.toByte()
                it[10] = (udpLength ushr 8).toByte()
                it[11] = (udpLength and 0xFF).toByte()
            }
            else -> ByteArray(40).also {
                System.arraycopy(packet, 8, it, 0, 16)
                System.arraycopy(packet, 24, it, 16, 16)
                DnsPacketCodec.writeInt(it, 32, udpLength)
                it[39] = 17.toByte()
            }
        }
        val total = fold(sum16(pseudo, 0, pseudo.size) + sum16(packet, udpOffset, udpOffset + udpLength))
        return total == 0xFFFF
    }
}
