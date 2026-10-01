package com.example.feature.websiteblocker.vpn

/**
 * DNS constants and data representations for local DNS interception.
 */
object DnsConstants {
    const val PORT_DNS = 53

    // DNS Query Types (RFC 1035, RFC 3596)
    const val TYPE_A = 1
    const val TYPE_NS = 2
    const val TYPE_CNAME = 5
    const val TYPE_SOA = 6
    const val TYPE_PTR = 12
    const val TYPE_MX = 15
    const val TYPE_TXT = 16
    const val TYPE_AAAA = 28
    const val TYPE_SRV = 33
    const val TYPE_HTTPS = 65
    const val TYPE_ANY = 255

    const val CLASS_IN = 1

    // DNS Header Flag masks
    const val FLAG_QR_RESPONSE = 0x8000
    const val FLAG_AA_AUTHORITATIVE = 0x0400
    const val FLAG_TC_TRUNCATION = 0x0200
    const val FLAG_RD_RECURSION_DESIRED = 0x0100
    const val FLAG_RA_RECURSION_AVAILABLE = 0x0080

    // RCODEs
    const val RCODE_NOERROR = 0
    const val RCODE_FORMERR = 1
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_NOTIMP = 4
    const val RCODE_REFUSED = 5

    // Default Sinkhole IPs
    val SINKHOLE_IPV4 = byteArrayOf(0, 0, 0, 0)
    val SINKHOLE_IPV6 = ByteArray(16) // all zeros ::
}

data class DnsQuestion(
    val qname: String,
    val qtype: Int,
    val qclass: Int
)

data class DnsHeader(
    val id: Int,
    val flags: Int,
    val qdCount: Int,
    val anCount: Int,
    val nsCount: Int,
    val arCount: Int
) {
    val isResponse: Boolean get() = (flags and DnsConstants.FLAG_QR_RESPONSE) != 0
    val rcode: Int get() = flags and 0x000F
}

data class IpPacketInfo(
    val version: Int, // 4 or 6
    val protocol: Int, // 17 for UDP
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val ipHeaderLength: Int,
    val udpPayloadOffset: Int,
    val udpPayloadLength: Int
)

data class ParsedDnsQuery(
    val ipInfo: IpPacketInfo,
    val header: DnsHeader,
    val questions: List<DnsQuestion>,
    val dnsPayload: ByteArray
)
