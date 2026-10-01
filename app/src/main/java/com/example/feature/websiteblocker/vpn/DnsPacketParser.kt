package com.example.feature.websiteblocker.vpn

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * Parser and packet synthesizer for raw IP/UDP/DNS packets passing through the VPN TUN interface.
 */
object DnsPacketParser {

    /**
     * Parses an IP packet from the TUN buffer.
     * Returns null if the packet is malformed, not UDP, or not directed to port 53.
     */
    fun parseIpPacket(data: ByteArray, length: Int): IpPacketInfo? {
        if (length < 28) return null // Minimum IPv4 (20) + UDP (8)

        val version = (data[0].toInt() and 0xF0) ushr 4
        return when (version) {
            4 -> parseIpv4Packet(data, length)
            6 -> parseIpv6Packet(data, length)
            else -> null
        }
    }

    private fun parseIpv4Packet(data: ByteArray, length: Int): IpPacketInfo? {
        if (length < 28) return null
        val ihl = (data[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return null

        val protocol = data[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val srcIp = ByteArray(4) { data[12 + it] }
        val dstIp = ByteArray(4) { data[16 + it] }

        val srcPort = ((data[ihl].toInt() and 0xFF) shl 8) or (data[ihl + 1].toInt() and 0xFF)
        val dstPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or (data[ihl + 3].toInt() and 0xFF)

        val udpLen = ((data[ihl + 4].toInt() and 0xFF) shl 8) or (data[ihl + 5].toInt() and 0xFF)
        val payloadLen = udpLen - 8
        val payloadOffset = ihl + 8

        if (payloadOffset + payloadLen > length || payloadLen < 12) return null

        return IpPacketInfo(
            version = 4,
            protocol = 17,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            ipHeaderLength = ihl,
            udpPayloadOffset = payloadOffset,
            udpPayloadLength = payloadLen
        )
    }

    private fun parseIpv6Packet(data: ByteArray, length: Int): IpPacketInfo? {
        if (length < 48) return null // IPv6 (40) + UDP (8)
        val nextHeader = data[6].toInt() and 0xFF
        if (nextHeader != 17) return null // UDP only

        val srcIp = ByteArray(16) { data[8 + it] }
        val dstIp = ByteArray(16) { data[24 + it] }

        val ihl = 40
        val srcPort = ((data[ihl].toInt() and 0xFF) shl 8) or (data[ihl + 1].toInt() and 0xFF)
        val dstPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or (data[ihl + 3].toInt() and 0xFF)

        val udpLen = ((data[ihl + 4].toInt() and 0xFF) shl 8) or (data[ihl + 5].toInt() and 0xFF)
        val payloadLen = udpLen - 8
        val payloadOffset = ihl + 8

        if (payloadOffset + payloadLen > length || payloadLen < 12) return null

        return IpPacketInfo(
            version = 6,
            protocol = 17,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            ipHeaderLength = ihl,
            udpPayloadOffset = payloadOffset,
            udpPayloadLength = payloadLen
        )
    }

    /**
     * Parses the DNS payload from an IP/UDP packet.
     * Returns null if the DNS payload is malformed or invalid.
     */
    fun parseDnsQuery(data: ByteArray, ipInfo: IpPacketInfo): ParsedDnsQuery? {
        val offset = ipInfo.udpPayloadOffset
        val length = ipInfo.udpPayloadLength
        if (length < 12) return null

        return try {
            val buffer = ByteBuffer.wrap(data, offset, length)
            val id = buffer.short.toInt() and 0xFFFF
            val flags = buffer.short.toInt() and 0xFFFF
            val qdCount = buffer.short.toInt() and 0xFFFF
            val anCount = buffer.short.toInt() and 0xFFFF
            val nsCount = buffer.short.toInt() and 0xFFFF
            val arCount = buffer.short.toInt() and 0xFFFF

            val header = DnsHeader(id, flags, qdCount, anCount, nsCount, arCount)
            if (header.isResponse) return null // We only intercept client queries

            val questions = mutableListOf<DnsQuestion>()
            for (i in 0 until qdCount) {
                val qname = readDomainName(buffer, offset) ?: return null
                if (buffer.remaining() < 4) return null
                val qtype = buffer.short.toInt() and 0xFFFF
                val qclass = buffer.short.toInt() and 0xFFFF
                questions.add(DnsQuestion(qname, qtype, qclass))
            }

            val dnsPayload = ByteArray(length)
            System.arraycopy(data, offset, dnsPayload, 0, length)

            ParsedDnsQuery(
                ipInfo = ipInfo,
                header = header,
                questions = questions,
                dnsPayload = dnsPayload
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads a domain name in label format from the buffer.
     */
    fun readDomainName(buffer: ByteBuffer, dnsBaseOffset: Int): String? {
        val parts = mutableListOf<String>()
        var jumped = false
        var jumpsCount = 0
        val maxJumps = 5

        while (buffer.hasRemaining()) {
            val len = buffer.get().toInt() and 0xFF
            if (len == 0) break

            if ((len and 0xC0) == 0xC0) {
                // Compression pointer
                if (!buffer.hasRemaining()) return null
                val b2 = buffer.get().toInt() and 0xFF
                val pointerOffset = ((len and 0x3F) shl 8) or b2

                if (!jumped) {
                    jumped = true
                }
                jumpsCount++
                if (jumpsCount > maxJumps) return null // Prevent circular compression loops

                val absoluteTarget = dnsBaseOffset + pointerOffset
                if (absoluteTarget >= buffer.capacity() || absoluteTarget < dnsBaseOffset) return null
                buffer.position(absoluteTarget)
            } else {
                if (len > buffer.remaining()) return null
                val labelBytes = ByteArray(len)
                buffer.get(labelBytes)
                parts.add(String(labelBytes, Charsets.UTF_8))
            }
        }

        return if (parts.isNotEmpty()) parts.joinToString(".") else null
    }

    /**
     * Synthesizes a blocked DNS response (Sinkhole: 0.0.0.0 for A, :: for AAAA)
     * and wraps it in a complete IP/UDP response packet directed back to the client.
     */
    fun buildBlockedResponsePacket(query: ParsedDnsQuery, ttlSeconds: Int = 300): ByteArray? {
        val firstQuestion = query.questions.firstOrNull() ?: return null
        val dnsResponsePayload = buildBlockedDnsPayload(query, firstQuestion, ttlSeconds) ?: return null
        return buildIpPacket(query.ipInfo, dnsResponsePayload)
    }

    /**
     * Builds raw DNS response payload for blocked queries.
     */
    fun buildBlockedDnsPayload(
        query: ParsedDnsQuery,
        question: DnsQuestion,
        ttlSeconds: Int = 300
    ): ByteArray? {
        val stream = ByteArrayOutputStream()
        val dos = DataOutputStream(stream)

        try {
            // Header: Transaction ID
            dos.writeShort(query.header.id)
            // Flags: Standard response, Authoritative, Recursion Available, No Error (0x8580)
            dos.writeShort(0x8180)
            dos.writeShort(1) // QDCOUNT = 1
            dos.writeShort(1) // ANCOUNT = 1
            dos.writeShort(0) // NSCOUNT = 0
            dos.writeShort(0) // ARCOUNT = 0

            // Question section
            writeDomainLabels(dos, question.qname)
            dos.writeShort(question.qtype)
            dos.writeShort(question.qclass)

            // Answer section: Compression pointer to QNAME (offset 12 = 0xC00C)
            dos.writeShort(0xC00C)
            dos.writeShort(question.qtype)
            dos.writeShort(question.qclass)
            dos.writeInt(ttlSeconds)

            when (question.qtype) {
                DnsConstants.TYPE_A -> {
                    dos.writeShort(4)
                    dos.write(DnsConstants.SINKHOLE_IPV4)
                }
                DnsConstants.TYPE_AAAA -> {
                    dos.writeShort(16)
                    dos.write(DnsConstants.SINKHOLE_IPV6)
                }
                else -> {
                    // For other query types (e.g. HTTPS, TXT), return NOERROR with 0 answers
                    val emptyStream = ByteArrayOutputStream()
                    val emptyDos = DataOutputStream(emptyStream)
                    emptyDos.writeShort(query.header.id)
                    emptyDos.writeShort(0x8180)
                    emptyDos.writeShort(1) // QDCOUNT
                    emptyDos.writeShort(0) // ANCOUNT
                    emptyDos.writeShort(0)
                    emptyDos.writeShort(0)
                    writeDomainLabels(emptyDos, question.qname)
                    emptyDos.writeShort(question.qtype)
                    emptyDos.writeShort(question.qclass)
                    emptyDos.flush()
                    return emptyStream.toByteArray()
                }
            }

            dos.flush()
            return stream.toByteArray()
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Builds a response IP packet by wrapping the given DNS payload with the appropriate
     * reversed IP and UDP headers (swapping client <-> server).
     */
    fun buildIpPacket(ipInfo: IpPacketInfo, dnsPayload: ByteArray): ByteArray {
        val isIpv4 = ipInfo.version == 4
        val ipHeaderLen = if (isIpv4) 20 else 40
        val udpHeaderLen = 8
        val totalLength = ipHeaderLen + udpHeaderLen + dnsPayload.size

        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet)

        if (isIpv4) {
            // IPv4 Header
            buffer.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
            buffer.put(0x00.toByte()) // DSCP/ECN
            buffer.putShort(totalLength.toShort()) // Total Length
            buffer.putShort(0.toShort()) // Identification
            buffer.putShort(0x4000.toShort()) // Don't fragment flag
            buffer.put(64.toByte()) // TTL
            buffer.put(17.toByte()) // Protocol UDP (17)
            buffer.putShort(0.toShort()) // Checksum placeholder

            // Swap Source IP and Destination IP
            buffer.put(ipInfo.dstIp) // Src IP of response is Dst IP of query
            buffer.put(ipInfo.srcIp) // Dst IP of response is Src IP of query

            // Compute IPv4 checksum
            val checksum = computeIpv4Checksum(packet, 0, 20)
            packet[10] = ((checksum ushr 8) and 0xFF).toByte()
            packet[11] = (checksum and 0xFF).toByte()
        } else {
            // IPv6 Header (40 bytes)
            buffer.put(0x60.toByte()) // Version 6, Traffic Class 0
            buffer.put(0x00.toByte())
            buffer.putShort(0.toShort()) // Flow Label
            buffer.putShort((udpHeaderLen + dnsPayload.size).toShort()) // Payload Length
            buffer.put(17.toByte()) // Next Header = UDP (17)
            buffer.put(64.toByte()) // Hop Limit

            // Swap Source and Destination IP
            buffer.put(ipInfo.dstIp)
            buffer.put(ipInfo.srcIp)
        }

        // UDP Header (8 bytes)
        val udpOffset = buffer.position()
        buffer.putShort(ipInfo.dstPort.toShort()) // Source Port (53)
        buffer.putShort(ipInfo.srcPort.toShort()) // Destination Port (client's port)
        val udpLen = udpHeaderLen + dnsPayload.size
        buffer.putShort(udpLen.toShort()) // UDP Length
        buffer.putShort(0.toShort()) // Checksum placeholder (0 is valid for IPv4 UDP)

        // DNS Payload
        buffer.put(dnsPayload)

        // If IPv6, UDP checksum is mandatory
        if (!isIpv4) {
            val udpChecksum = computeIpv6UdpChecksum(packet, ipInfo.dstIp, ipInfo.srcIp, udpOffset, udpLen)
            packet[udpOffset + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
            packet[udpOffset + 7] = (udpChecksum and 0xFF).toByte()
        }

        return packet
    }

    private fun writeDomainLabels(dos: DataOutputStream, domain: String) {
        val clean = domain.trim().trimEnd('.')
        if (clean.isBlank()) {
            dos.writeByte(0)
            return
        }
        for (label in clean.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
    }

    private fun computeIpv4Checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun computeIpv6UdpChecksum(
        packet: ByteArray,
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpOffset: Int,
        udpLen: Int
    ): Int {
        var sum = 0
        // IPv6 Pseudo Header: Src IP (16) + Dst IP (16) + Upper-Layer Packet Length (4) + Next Header (4)
        for (i in 0 until 16 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
        }
        for (i in 0 until 16 step 2) {
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += udpLen and 0xFFFF
        sum += 17 // UDP protocol

        // UDP Header and Payload
        var i = udpOffset
        val end = udpOffset + udpLen
        while (i < end - 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }
}
