package com.example.feature.websiteblocker

import com.example.feature.websiteblocker.vpn.DnsConstants
import com.example.feature.websiteblocker.vpn.DnsPacketParser
import com.example.feature.websiteblocker.vpn.IpPacketInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

class DnsPacketParserTest {

    @Test
    fun `parses valid IPv4 DNS query packet`() {
        val queryPacket = buildSyntheticDnsQueryPacket(
            domain = "pornhub.com",
            qtype = DnsConstants.TYPE_A,
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(10, 0, 0, 53),
            srcPort = 54321,
            dstPort = 53
        )

        val ipInfo = DnsPacketParser.parseIpPacket(queryPacket, queryPacket.size)
        assertNotNull("IP packet should be successfully parsed", ipInfo)
        assertEquals(4, ipInfo!!.version)
        assertEquals(17, ipInfo.protocol)
        assertEquals(54321, ipInfo.srcPort)
        assertEquals(53, ipInfo.dstPort)

        val dnsQuery = DnsPacketParser.parseDnsQuery(queryPacket, ipInfo)
        assertNotNull("DNS query should be parsed", dnsQuery)
        assertEquals(1, dnsQuery!!.questions.size)
        assertEquals("pornhub.com", dnsQuery.questions[0].qname)
        assertEquals(DnsConstants.TYPE_A, dnsQuery.questions[0].qtype)
        assertEquals(DnsConstants.CLASS_IN, dnsQuery.questions[0].qclass)
    }

    @Test
    fun `synthesizes IPv4 blocked sinkhole response packet`() {
        val queryPacket = buildSyntheticDnsQueryPacket(
            domain = "pornhub.com",
            qtype = DnsConstants.TYPE_A,
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(10, 0, 0, 53),
            srcPort = 54321,
            dstPort = 53
        )

        val ipInfo = DnsPacketParser.parseIpPacket(queryPacket, queryPacket.size)!!
        val dnsQuery = DnsPacketParser.parseDnsQuery(queryPacket, ipInfo)!!

        val responsePacket = DnsPacketParser.buildBlockedResponsePacket(dnsQuery, ttlSeconds = 300)
        assertNotNull("Blocked response packet should be generated", responsePacket)

        // Parse response packet to verify contents
        val responseIp = DnsPacketParser.parseIpPacket(responsePacket!!, responsePacket.size)
        assertNotNull(responseIp)
        assertEquals("Source IP should now be 10.0.0.53", 10, responseIp!!.srcIp[0].toInt())
        assertEquals("Source IP should now be 10.0.0.53", 53, responseIp.srcIp[3].toInt())
        assertEquals("Dst IP should now be 10.0.0.2", 2, responseIp.dstIp[3].toInt())
        assertEquals("Src port should be 53", 53, responseIp.srcPort)
        assertEquals("Dst port should be client port 54321", 54321, responseIp.dstPort)

        // Verify DNS Answer payload
        val dnsBuffer = ByteBuffer.wrap(responsePacket, responseIp.udpPayloadOffset, responseIp.udpPayloadLength)
        val transactionId = dnsBuffer.short.toInt() and 0xFFFF
        assertEquals(dnsQuery.header.id, transactionId)
        val flags = dnsBuffer.short.toInt() and 0xFFFF
        assertTrue("QR bit should be response", (flags and DnsConstants.FLAG_QR_RESPONSE) != 0)
        assertEquals("RCODE should be NOERROR", 0, flags and 0x000F)
        val qdCount = dnsBuffer.short.toInt() and 0xFFFF
        val anCount = dnsBuffer.short.toInt() and 0xFFFF
        assertEquals(1, qdCount)
        assertEquals(1, anCount)
    }

    @Test
    fun `synthesizes IPv6 blocked sinkhole response packet`() {
        val queryPacket = buildSyntheticIpv6DnsQueryPacket(
            domain = "adult-site.xxx",
            qtype = DnsConstants.TYPE_AAAA,
            srcPort = 49152
        )

        val ipInfo = DnsPacketParser.parseIpPacket(queryPacket, queryPacket.size)
        assertNotNull("IPv6 packet should be parsed", ipInfo)
        assertEquals(6, ipInfo!!.version)

        val dnsQuery = DnsPacketParser.parseDnsQuery(queryPacket, ipInfo)!!
        assertEquals("adult-site.xxx", dnsQuery.questions[0].qname)
        assertEquals(DnsConstants.TYPE_AAAA, dnsQuery.questions[0].qtype)

        val responsePacket = DnsPacketParser.buildBlockedResponsePacket(dnsQuery)
        assertNotNull(responsePacket)
        val responseIp = DnsPacketParser.parseIpPacket(responsePacket!!, responsePacket.size)
        assertNotNull(responseIp)
        assertEquals(6, responseIp!!.version)
        assertEquals(49152, responseIp.dstPort)
    }

    @Test
    fun `malformed DNS packets return null without throwing`() {
        // 1. Truncated IP packet
        val truncatedIp = byteArrayOf(0x45, 0x00, 0x00)
        assertNull(DnsPacketParser.parseIpPacket(truncatedIp, truncatedIp.size))

        // 2. Non-UDP packet (TCP protocol 6)
        val tcpPacket = ByteArray(40).apply {
            this[0] = 0x45
            this[9] = 6 // TCP
        }
        assertNull(DnsPacketParser.parseIpPacket(tcpPacket, tcpPacket.size))

        // 3. Truncated DNS payload
        val fakeIpInfo = IpPacketInfo(
            version = 4,
            protocol = 17,
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(10, 0, 0, 53),
            srcPort = 1234,
            dstPort = 53,
            ipHeaderLength = 20,
            udpPayloadOffset = 28,
            udpPayloadLength = 4 // < 12 bytes minimum DNS header
        )
        assertNull(DnsPacketParser.parseDnsQuery(ByteArray(32), fakeIpInfo))

        // 4. Random garbage in DNS payload
        val garbageData = ByteArray(64) { it.toByte() }
        val validUdpIpInfo = IpPacketInfo(
            version = 4,
            protocol = 17,
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(10, 0, 0, 53),
            srcPort = 1234,
            dstPort = 53,
            ipHeaderLength = 20,
            udpPayloadOffset = 28,
            udpPayloadLength = 36
        )
        // Should handle corrupt label safely and return null
        val corruptResult = DnsPacketParser.parseDnsQuery(garbageData, validUdpIpInfo)
        assertNull(corruptResult)
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

        // IPv4 Header
        buffer.put(0x45.toByte())
        buffer.put(0.toByte())
        buffer.putShort(totalLen.toShort())
        buffer.putShort(1.toShort())
        buffer.putShort(0.toShort())
        buffer.put(64.toByte())
        buffer.put(17.toByte()) // UDP
        buffer.putShort(0.toShort()) // checksum placeholder
        buffer.put(srcIp)
        buffer.put(dstIp)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort())

        // DNS Payload
        buffer.put(dnsBytes)

        return packet
    }

    private fun buildSyntheticIpv6DnsQueryPacket(
        domain: String,
        qtype: Int,
        srcPort: Int
    ): ByteArray {
        val dnsBytes = buildRawDnsQuery(domain, qtype)
        val udpLen = 8 + dnsBytes.size
        val totalLen = 40 + udpLen

        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // IPv6 Header (40 bytes)
        buffer.put(0x60.toByte())
        buffer.put(0.toByte())
        buffer.putShort(0.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.put(17.toByte()) // Next Header = UDP
        buffer.put(64.toByte())
        buffer.put(ByteArray(16) { 0x01 }) // Src IP
        buffer.put(ByteArray(16) { 0x02 }) // Dst IP

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(53.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort())

        // DNS Payload
        buffer.put(dnsBytes)

        return packet
    }

    private fun buildRawDnsQuery(domain: String, qtype: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        dos.writeShort(0x1234) // Transaction ID
        dos.writeShort(0x0100) // Standard query + RD
        dos.writeShort(1) // QDCOUNT
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
        dos.writeShort(1) // IN

        dos.flush()
        return stream.toByteArray()
    }
}
