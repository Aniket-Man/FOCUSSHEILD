package com.example.feature.websiteblocker.vpn

/**
 * IP/UDP packet codec for the DNS-only VPN tunnel.
 *
 * The VPN interface routes exactly one /32 (v4) and one /128 (v6) address — the fake DNS server —
 * so the only traffic that can arrive on the tun is UDP destined for port 53. This codec parses
 * those packets and builds the reply packet that is written back into the tun: the source and
 * destination of the query are swapped and correct IPv4-header / UDP checksums are computed
 * (the Linux IP stack validates checksums of packets injected into a tun, and the UDP checksum is
 * mandatory for IPv6).
 *
 * Pure Kotlin (no Android imports) so the packet format is unit-testable in isolation.
 */
object IpPacketCodec {

    const val IPV4 = 4
    const val IPV6 = 6
    const val PROTOCOL_UDP = 17
    const val DNS_PORT = 53

    private const val IPV4_HEADER_MIN_BYTES = 20
    private const val IPV6_HEADER_BYTES = 40
    private const val UDP_HEADER_BYTES = 8

    /** A parsed UDP datagram inside an IP packet read from the tun. Immutable and thread-safe. */
    class UdpPacketInfo(
        val version: Int,
        val srcAddress: ByteArray,
        val dstAddress: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        /** Absolute offset of the UDP payload (the DNS message) inside the packet buffer. */
        val payloadOffset: Int,
        val payloadLength: Int
    )

    /**
     * Parses an IPv4/IPv6 packet and its UDP header. Returns null when the packet is not plain
     * UDP or is malformed/truncated — the caller must drop such packets (they cannot be a DNS
     * query this filter can act on, and there is no other traffic to forward through a DNS-only
     * tunnel).
     */
    fun parseUdpPacket(packet: ByteArray, length: Int = packet.size): UdpPacketInfo? {
        if (length < 1) return null
        return when ((packet[0].toInt() shr 4) and 0x0F) {
            IPV4 -> parseIpv4(packet, length)
            IPV6 -> parseIpv6(packet, length)
            else -> null
        }
    }

    /**
     * Builds the reply datagram for [query]: swaps IP addresses and UDP ports and carries
     * [dnsPayload] as the body, with freshly computed checksums for the new header.
     */
    fun buildUdpResponse(query: UdpPacketInfo, dnsPayload: ByteArray): ByteArray =
        buildUdpPacket(
            version = query.version,
            srcAddress = query.dstAddress,
            dstAddress = query.srcAddress,
            srcPort = query.dstPort,
            dstPort = query.srcPort,
            payload = dnsPayload
        )

    /** Builds a complete IPv4/IPv6 + UDP + payload packet. Used for replies and by tests. */
    fun buildUdpPacket(
        version: Int,
        srcAddress: ByteArray,
        dstAddress: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        require(srcPort in 0..0xFFFF && dstPort in 0..0xFFFF) { "port out of range" }
        return when (version) {
            IPV4 -> buildIpv4(srcAddress, dstAddress, srcPort, dstPort, payload)
            else -> buildIpv6(srcAddress, dstAddress, srcPort, dstPort, payload)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------

    private fun parseIpv4(packet: ByteArray, length: Int): UdpPacketInfo? {
        if (length < IPV4_HEADER_MIN_BYTES + UDP_HEADER_BYTES) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_HEADER_MIN_BYTES || length < ihl + UDP_HEADER_BYTES) return null
        if (packet[9].toInt() and 0xFF != PROTOCOL_UDP) return null

        val totalLength = DnsPacketCodec.readShort(packet, 2)
        if (totalLength < ihl + UDP_HEADER_BYTES || totalLength > length) return null

        val srcPort = DnsPacketCodec.readShort(packet, ihl)
        val dstPort = DnsPacketCodec.readShort(packet, ihl + 2)
        val udpLength = DnsPacketCodec.readShort(packet, ihl + 4)
        if (udpLength < UDP_HEADER_BYTES || ihl + udpLength > totalLength) return null

        return UdpPacketInfo(
            version = IPV4,
            srcAddress = packet.copyOfRange(12, 16),
            dstAddress = packet.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            payloadOffset = ihl + UDP_HEADER_BYTES,
            payloadLength = udpLength - UDP_HEADER_BYTES
        )
    }

    private fun parseIpv6(packet: ByteArray, length: Int): UdpPacketInfo? {
        if (length < IPV6_HEADER_BYTES + UDP_HEADER_BYTES) return null
        // Extension headers are not expected for DNS on the tunnel's own /128 route; anything
        // that is not a plain UDP next-header cannot be parsed here and is dropped.
        if (packet[6].toInt() and 0xFF != PROTOCOL_UDP) return null

        val payloadLengthField = DnsPacketCodec.readShort(packet, 4)
        if (payloadLengthField < UDP_HEADER_BYTES ||
            IPV6_HEADER_BYTES + payloadLengthField > length
        ) {
            return null
        }

        return UdpPacketInfo(
            version = IPV6,
            srcAddress = packet.copyOfRange(8, 24),
            dstAddress = packet.copyOfRange(24, 40),
            srcPort = DnsPacketCodec.readShort(packet, IPV6_HEADER_BYTES),
            dstPort = DnsPacketCodec.readShort(packet, IPV6_HEADER_BYTES + 2),
            payloadOffset = IPV6_HEADER_BYTES + UDP_HEADER_BYTES,
            payloadLength = payloadLengthField - UDP_HEADER_BYTES
        )
    }

    // ---------------------------------------------------------------------------------------
    // Building
    // ---------------------------------------------------------------------------------------

    private fun buildIpv4(srcAddress: ByteArray, dstAddress: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        require(srcAddress.size == 4 && dstAddress.size == 4) { "IPv4 addresses must be 4 bytes" }
        val totalLength = IPV4_HEADER_MIN_BYTES + UDP_HEADER_BYTES + payload.size
        require(totalLength <= 0xFFFF) { "IPv4 packet too large" }
        val out = ByteArray(totalLength)

        out[0] = 0x45 // version 4, IHL 5
        DnsPacketCodec.writeShort(out, 2, totalLength)
        out[8] = 64   // TTL
        out[9] = PROTOCOL_UDP.toByte()
        System.arraycopy(srcAddress, 0, out, 12, 4)
        System.arraycopy(dstAddress, 0, out, 16, 4)
        writeUdpSegment(out, IPV4_HEADER_MIN_BYTES, srcPort, dstPort, payload, srcAddress, dstAddress, version = IPV4)

        // Header checksum is computed over the header with the checksum field zeroed (it still is).
        DnsPacketCodec.writeShort(out, 10, onesComplementChecksum(out, 0, IPV4_HEADER_MIN_BYTES))
        return out
    }

    private fun buildIpv6(srcAddress: ByteArray, dstAddress: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        require(srcAddress.size == 16 && dstAddress.size == 16) { "IPv6 addresses must be 16 bytes" }
        val payloadLength = UDP_HEADER_BYTES + payload.size
        require(IPV6_HEADER_BYTES + payloadLength <= 0xFFFF) { "IPv6 packet too large" }
        val out = ByteArray(IPV6_HEADER_BYTES + payloadLength)

        out[0] = 0x60 // version 6
        DnsPacketCodec.writeShort(out, 4, payloadLength)
        out[6] = PROTOCOL_UDP.toByte()
        out[7] = 64   // hop limit
        System.arraycopy(srcAddress, 0, out, 8, 16)
        System.arraycopy(dstAddress, 0, out, 24, 16)
        writeUdpSegment(out, IPV6_HEADER_BYTES, srcPort, dstPort, payload, srcAddress, dstAddress, version = IPV6)
        return out
    }

    /** Writes the UDP header + payload at [offset] with a correct checksum (pseudo-header included). */
    private fun writeUdpSegment(
        out: ByteArray,
        offset: Int,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        srcAddress: ByteArray,
        dstAddress: ByteArray,
        version: Int
    ) {
        val udpLength = UDP_HEADER_BYTES + payload.size
        DnsPacketCodec.writeShort(out, offset, srcPort)
        DnsPacketCodec.writeShort(out, offset + 2, dstPort)
        DnsPacketCodec.writeShort(out, offset + 4, udpLength)
        // checksum field (offset+6) is still zero here — it must be for the computation
        System.arraycopy(payload, 0, out, offset + UDP_HEADER_BYTES, payload.size)

        val checksum = when (version) {
            IPV4 -> udpChecksum(out, offset, udpLength, buildIpv4PseudoHeader(srcAddress, dstAddress, udpLength))
            else -> udpChecksum(out, offset, udpLength, buildIpv6PseudoHeader(srcAddress, dstAddress, udpLength))
        }
        // A computed checksum of 0x0000 is transmitted as 0xFFFF (RFC 1071 / RFC 768).
        DnsPacketCodec.writeShort(out, offset + 6, if (checksum == 0) 0xFFFF else checksum)
    }

    /** UDP checksum over pseudo-header + UDP header (checksum field zeroed) + payload. */
    private fun udpChecksum(out: ByteArray, offset: Int, udpLength: Int, pseudoHeader: ByteArray): Int {
        val sum = sumBytes(pseudoHeader, 0, pseudoHeader.size) + sumBytes(out, offset, offset + udpLength)
        return foldOnesComplement(sum)
    }

    private fun buildIpv4PseudoHeader(srcAddress: ByteArray, dstAddress: ByteArray, udpLength: Int): ByteArray {
        val pseudo = ByteArray(12)
        System.arraycopy(srcAddress, 0, pseudo, 0, 4)
        System.arraycopy(dstAddress, 0, pseudo, 4, 4)
        pseudo[8] = 0
        pseudo[9] = PROTOCOL_UDP.toByte()
        DnsPacketCodec.writeShort(pseudo, 10, udpLength)
        return pseudo
    }

    private fun buildIpv6PseudoHeader(srcAddress: ByteArray, dstAddress: ByteArray, udpLength: Int): ByteArray {
        val pseudo = ByteArray(40)
        System.arraycopy(srcAddress, 0, pseudo, 0, 16)
        System.arraycopy(dstAddress, 0, pseudo, 16, 16)
        DnsPacketCodec.writeInt(pseudo, 32, udpLength)
        pseudo[36] = 0
        pseudo[37] = 0
        pseudo[38] = 0
        pseudo[39] = PROTOCOL_UDP.toByte()
        return pseudo
    }

    /** Standard 16-bit ones' complement checksum (RFC 1071). */
    private fun onesComplementChecksum(data: ByteArray, from: Int, to: Int): Int =
        foldOnesComplement(sumBytes(data, from, to))

    private fun sumBytes(data: ByteArray, from: Int, to: Int): Long {
        var sum = 0L
        var i = from
        while (i + 1 < to) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < to) {
            sum += (data[i].toInt() and 0xFF) shl 8 // odd trailing byte is padded with a zero octet
        }
        return sum
    }

    private fun foldOnesComplement(sum: Long): Int {
        var s = sum
        while (s ushr 16 != 0L) {
            s = (s and 0xFFFF) + (s shr 16)
        }
        return (s.toInt().inv()) and 0xFFFF
    }
}
