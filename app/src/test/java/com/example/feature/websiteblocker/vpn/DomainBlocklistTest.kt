package com.example.feature.websiteblocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matching contract of the DNS-level 18+ blocklist: exact matches, subdomain matches,
 * allowlist precedence, and — critically — no false positives on substring/lookalike domains.
 */
class DomainBlocklistTest {

    private val blocklist = DomainBlocklist(
        blockedDomains = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "rule34.xxx",
            "pornhub.com" // duplicates must be harmless
        ),
        blockedTlds = setOf("xxx", "porn", "adult", "sex"),
        allowedDomains = setOf("safe.example.com", "wikipedia.org")
    )

    @Test
    fun `blocked domain is blocked`() {
        assertTrue(blocklist.isBlocked("pornhub.com"))
        assertTrue(blocklist.isBlocked("xvideos.com"))
    }

    @Test
    fun `blocked subdomain is blocked`() {
        assertTrue(blocklist.isBlocked("www.pornhub.com"))
        assertTrue(blocklist.isBlocked("de.xnxx.com"))
        assertTrue(blocklist.isBlocked("a.b.c.xvideos.com"))
    }

    @Test
    fun `non-matching domains are not blocked`() {
        assertFalse(blocklist.isBlocked("example.com"))
        assertFalse(blocklist.isBlocked("youtube.com"))
        assertFalse(blocklist.isBlocked("pornhub.org")) // only .com is on the list
    }

    @Test
    fun `substring and lookalike domains are not blocked - no false positives`() {
        // The suffix walk compares whole labels only.
        assertFalse(blocklist.isBlocked("pornhub.com.attacker.net"))
        assertFalse(blocklist.isBlocked("notpornhub.com"))
        assertFalse(blocklist.isBlocked("pornhub-company.com"))
        assertFalse(blocklist.isBlocked("xvideos.example.com"))
    }

    @Test
    fun `normalization handles case, trailing dots, www, urls and ports`() {
        assertTrue(blocklist.isBlocked("PORNHUB.COM"))
        assertTrue(blocklist.isBlocked("pornhub.com."))
        assertTrue(blocklist.isBlocked("www.pornhub.com"))
        assertTrue(blocklist.isBlocked("https://www.pornhub.com/watch?key=1#top"))
        assertTrue(blocklist.isBlocked("http://pornhub.com:8080/video"))
    }

    @Test
    fun `adult TLD blocks every domain under it`() {
        assertTrue(blocklist.isBlocked("anything.xxx"))
        assertTrue(blocklist.isBlocked("deep.sub.example.porn"))
        assertTrue(blocklist.isBlocked("site.adult"))
    }

    @Test
    fun `ordinary TLDs never trigger the TLD rule`() {
        assertFalse(blocklist.isBlocked("xxx.example.com")) // .xxx inside the name is not the TLD
        assertFalse(blocklist.isBlocked("adult.example.org"))
    }

    @Test
    fun `rule34 domain blocked by corpus and by TLD reports the corpus rule first`() {
        val match = blocklist.match("rule34.xxx")
        assertTrue(match is DomainMatch.Blocked)
        assertEquals("rule34.xxx", (match as DomainMatch.Blocked).matchedRule)
    }

    @Test
    fun `allowlisted domain is allowed`() {
        val match = blocklist.match("wikipedia.org")
        assertTrue(match is DomainMatch.Allowed)
    }

    @Test
    fun `allowlist takes precedence over blocklist`() {
        // safe.example.com sits below an (hypothetical) blocked parent; the allowlist wins.
        val withConflict = DomainBlocklist(
            blockedDomains = setOf("example.com"),
            blockedTlds = emptySet(),
            allowedDomains = setOf("safe.example.com")
        )
        assertTrue(withConflict.isBlocked("example.com"))
        assertFalse(withConflict.isBlocked("safe.example.com"))
        assertFalse(withConflict.isBlocked("sub.safe.example.com"))
        assertTrue(withConflict.isBlocked("other.example.com"))
    }

    @Test
    fun `allowlist entry overrides an adult TLD rule`() {
        val withTldConflict = DomainBlocklist(
            blockedDomains = emptySet(),
            blockedTlds = setOf("xxx"),
            allowedDomains = setOf("educational.xxx")
        )
        assertTrue(withTldConflict.isBlocked("random.xxx"))
        assertFalse(withTldConflict.isBlocked("educational.xxx"))
    }

    @Test
    fun `blank and single-label input never matches`() {
        assertEquals(DomainMatch.NotMatched, blocklist.match(""))
        assertEquals(DomainMatch.NotMatched, blocklist.match("   "))
        assertEquals(DomainMatch.NotMatched, blocklist.match("localhost"))
        assertEquals(DomainMatch.NotMatched, blocklist.match("xxx"))
    }

    @Test
    fun `large blocklists stay correct and fast`() {
        // 100k synthetic entries: correctness first, plus a generous time budget so a regression
        // to a linear scan (~10^9 probes for 20k lookups) would fail on any CI runner.
        // Entries are host0…host99999 so the 10k lookup loop (host0…host9999) matches exactly.
        val big = buildSet {
            while (size < 100_000) {
                val i = size
                add("host$i.domain${i % 997}.category${i % 53}.example")
            }
        }
        val bigList = DomainBlocklist(blockedDomains = big, blockedTlds = setOf("blocked"))
        assertTrue(bigList.isBlocked("host1.domain1.category1.example"))

        val start = System.nanoTime()
        var blockedCount = 0
        repeat(10_000) { n ->
            if (bigList.isBlocked("host$n.domain${n % 997}.category${n % 53}.example")) blockedCount++
            bigList.isBlocked("unrelated$n.example.org")
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(10_000, blockedCount)
        assertTrue("20k lookups on a 100k-entry blocklist took ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
