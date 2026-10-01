package com.example.feature.websiteblocker.vpn

/**
 * Minimal DNS message codec for the DNS-level 18+ filter: parses the question section of a
 * datagram, builds local BLOCK/NODATA/SERVFAIL answers, and reads answer TTLs for caching.
 *
 * Only what a DNS filter needs on the hot path is implemented:
 *  * parsing tolerates but never *needs* EDNS (the OPT pseudo-record lives in the additional
 *    section, which is ignored);
 *  * a compression pointer inside the **question** is treated as malformed (questions are never
 *    compressed in practice); pointers inside answers are skipped correctly for TTL extraction;
 *  * every read is bounds-checked, so a truncated/hostile datagram can never throw.
 *
 * Pure Kotlin (no Android imports) so the wire format is unit-testable in isolation.
 */
object DnsPacketCodec {

    /** QTYPE values the filter distinguishes. */
    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    /** QCLASS IN — the only class the filter makes block decisions for. */
    const val CLASS_IN = 1

    /** Response codes the filter emits itself. */
    const val RCODE_SERVFAIL = 2

    /** TTL carried by locally generated blocked answers: short, so an unblock takes effect quickly. */
    const val BLOCKED_TTL_SECONDS = 10

    /** TTL used when a response carries no usable answer TTL. */
    const val DEFAULT_CACHE_TTL_SECONDS = 30

    /** Bounds applied to cached TTLs (see [DnsCache]). */
    const val MIN_CACHE_TTL_SECONDS = 10
    const val MAX_CACHE_TTL_SECONDS = 600

    private const val HEADER_BYTES = 12
    private const val MAX_NAME_BYTES = 254

    /** The parsed question of a DNS message (query or response). */
    data class DnsQuery(
        val transactionId: Int,
        val isResponse: Boolean,
        val recursionDesired: Boolean,
        val domain: String,
        val qtype: Int,
        val qclass: Int,
        /** Absolute offset of the question section inside [payload] (start of QNAME). */
        val questionOffset: Int,
        /** Byte length of the whole question section (QNAME + QTYPE + QCLASS). */
        val questionLength: Int
    )

    /**
     * Parses the first question of the message in [payload] between [offset] and
     * `offset + length`. Returns null for anything the filter cannot safely interpret
     * (too short, zero questions, malformed label lengths, compression pointer in the question,
     * question overrunning the datagram).
     */
    fun parse(payload: ByteArray, offset: Int = 0, length: Int = payload.size): DnsQuery? {
        if (length < HEADER_BYTES) return null
        val end = offset + length

        val transactionId = readShort(payload, offset)
        val flagsHigh = payload[offset + 2].toInt() and 0xFF
        val qdCount = readShort(payload, offset + 4)
        if (qdCount < 1) return null

        var pos = offset + HEADER_BYTES
        val name = StringBuilder()
        while (true) {
            if (pos >= end) return null
            val labelLength = payload[pos].toInt() and 0xFF
            if (labelLength == 0) {
                pos++
                break
            }
            // A compression pointer (or any reserved length bits) inside a question is malformed
            // for our purposes: questions are a plain label sequence.
            if (labelLength and 0xC0 != 0) return null
            if (pos + 1 + labelLength > end) return null
            if (name.isNotEmpty()) name.append('.')
            for (i in 1..labelLength) {
                val b = payload[pos + i].toInt() and 0xFF
                name.append(if (b in 65..90) (b + 32).toChar() else b.toChar())
            }
            pos += 1 + labelLength
            if (name.length > MAX_NAME_BYTES) return null
        }
        if (pos + 4 > end) return null

        val questionOffset = offset + HEADER_BYTES
        return DnsQuery(
            transactionId = transactionId,
            isResponse = flagsHigh and 0x80 != 0,
            recursionDesired = flagsHigh and 0x40 != 0,
            domain = name.toString(),
            qtype = readShort(payload, pos),
            qclass = readShort(payload, pos + 2),
            questionOffset = questionOffset,
            questionLength = pos + 4 - questionOffset
        )
    }

    /**
     * Builds the local answer for a blocked domain:
     *  * `A`    → `0.0.0.0`
     *  * `AAAA` → `::`
     *  * any other QTYPE (HTTPS/SVCB, MX, TXT, …) → NOERROR with **no** answers, so an HTTPS RR
     *    cannot leak the real server IPs of a blocked domain as embedded hints.
     */
    fun buildBlockedResponse(query: DnsQuery, payload: ByteArray): ByteArray {
        val isAddressType = query.qtype == TYPE_A || query.qtype == TYPE_AAAA
        val rdataLength = when (query.qtype) {
            TYPE_A -> 4
            TYPE_AAAA -> 16
            else -> 0
        }
        // NAME pointer (2) + TYPE (2) + CLASS (2) + TTL (4) + RDLENGTH (2) + RDATA
        val answerBytes = if (isAddressType) 12 + rdataLength else 0
        val question = payload.copyOfRange(query.questionOffset, query.questionOffset + query.questionLength)
        val out = ByteArray(HEADER_BYTES + question.size + answerBytes)

        writeShort(out, 0, query.transactionId)
        // QR=1, opcode 0, RD copied from the query; RA=1 in the low byte, rcode NOERROR.
        writeShort(out, 2, if (query.recursionDesired) 0x8180 else 0x8080)
        writeShort(out, 4, 1)                      // QDCOUNT
        writeShort(out, 6, if (isAddressType) 1 else 0) // ANCOUNT
        writeShort(out, 8, 0)                      // NSCOUNT
        writeShort(out, 10, 0)                     // ARCOUNT (OPT intentionally omitted)
        System.arraycopy(question, 0, out, HEADER_BYTES, question.size)

        if (isAddressType) {
            var p = HEADER_BYTES + question.size
            out[p++] = 0xC0.toByte()               // NAME: pointer to the question at offset 12
            out[p++] = 0x0C
            writeShort(out, p, query.qtype); p += 2
            writeShort(out, p, CLASS_IN); p += 2
            writeInt(out, p, BLOCKED_TTL_SECONDS); p += 4
            writeShort(out, p, rdataLength)        // RDATA itself stays 0.0.0.0 / ::
        }
        return out
    }

    /**
     * Builds a response with the given non-zero rcode (e.g. SERVFAIL) and no answers, so a client
     * fails fast instead of timing out when every upstream resolver was unreachable. Returns null
     * when [query] could not be parsed (nothing safe to echo).
     */
    fun buildErrorResponse(query: DnsQuery?, payload: ByteArray, rcode: Int): ByteArray? {
        if (query == null) return null
        val question = payload.copyOfRange(query.questionOffset, query.questionOffset + query.questionLength)
        val out = ByteArray(HEADER_BYTES + question.size)
        writeShort(out, 0, query.transactionId)
        writeShort(out, 2, (if (query.recursionDesired) 0x8180 else 0x8080) or (rcode and 0x0F))
        writeShort(out, 4, 1)
        writeShort(out, 6, 0)
        writeShort(out, 8, 0)
        writeShort(out, 10, 0)
        System.arraycopy(question, 0, out, HEADER_BYTES, question.size)
        return out
    }

    /**
     * Smallest answer TTL of a response, used as the cache lifetime. Handles compression pointers
     * and non-address record types; returns null when no answer could be read.
     */
    fun minAnswerTtl(payload: ByteArray, offset: Int = 0, length: Int = payload.size): Int? {
        val query = parse(payload, offset, length) ?: return null
        val anCount = readShort(payload, offset + 6)
        if (anCount < 1) return null

        val end = offset + length
        var pos = query.questionOffset + query.questionLength
        var minTtl: Int? = null
        repeat(anCount) {
            pos = skipName(payload, pos, end) ?: return null
            if (pos + 10 > end) return null
            val ttl = readInt(payload, pos + 4)
            val rdLength = readShort(payload, pos + 8)
            pos += 10 + rdLength
            if (pos > end) return null
            if (ttl >= 0) {
                minTtl = if (minTtl == null) ttl else minOf(minTtl!!, ttl)
            }
        }
        return minTtl
    }

    /**
     * Builds a plain query datagram (QDCOUNT=1, RD=1). Used by tests; kept next to the parser so
     * the wire format has exactly one definition.
     */
    fun buildQuery(transactionId: Int, domain: String, qtype: Int, qclass: Int = CLASS_IN): ByteArray {
        val normalized = normalizeQueryDomain(domain)
        require(normalized.isNotBlank() && normalized.contains('.')) { "invalid domain: '$domain'" }
        val labels = normalized.split('.')
        val questionSize = labels.sumOf { it.length + 1 } + 1 + 4
        val out = ByteArray(HEADER_BYTES + questionSize)

        writeShort(out, 0, transactionId)
        writeShort(out, 2, 0x0100) // RD=1
        writeShort(out, 4, 1)      // QDCOUNT
        var p = HEADER_BYTES
        for (label in labels) {
            require(label.length <= 63) { "label too long: $label" }
            out[p++] = label.length.toByte()
            for (c in label) out[p++] = c.code.toByte()
        }
        out[p++] = 0
        writeShort(out, p, qtype); p += 2
        writeShort(out, p, qclass)
        return out
    }

    private fun normalizeQueryDomain(domain: String): String =
        DomainBlocklist.normalize(domain)

    /** Skips a (possibly compressed) domain name starting at [pos]; returns the new position or null on overrun. */
    private fun skipName(payload: ByteArray, pos: Int, end: Int): Int? {
        var p = pos
        var jumps = 0
        while (true) {
            if (p >= end || jumps > 64) return null
            val length = payload[p].toInt() and 0xFF
            if (length == 0) return p + 1
            if (length and 0xC0 == 0xC0) {
                if (p + 1 >= end) return null
                return p + 2
            }
            p += 1 + length
            jumps++
        }
    }

    internal fun readShort(data: ByteArray, index: Int): Int =
        ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)

    internal fun readInt(data: ByteArray, index: Int): Int =
        (readShort(data, index) shl 16) or readShort(data, index + 2)

    internal fun writeShort(out: ByteArray, index: Int, value: Int) {
        out[index] = ((value ushr 8) and 0xFF).toByte()
        out[index + 1] = (value and 0xFF).toByte()
    }

    internal fun writeInt(out: ByteArray, index: Int, value: Int) {
        writeShort(out, index, (value ushr 16) and 0xFFFF)
        writeShort(out, index + 2, value and 0xFFFF)
    }
}
