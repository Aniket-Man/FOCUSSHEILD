package com.example.feature.websiteblocker

import com.example.feature.websiteblocker.vpn.AdultBlocklistProvider
import com.example.feature.websiteblocker.vpn.DomainMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DomainMatcherTest {

    private lateinit var matcher: DomainMatcher

    @Before
    fun setUp() {
        matcher = DomainMatcher()
    }

    @Test
    fun `blocked domain matches exactly`() {
        matcher.addBlockedDomain("pornhub.com")

        assertTrue(matcher.isBlocked("pornhub.com"))
        assertTrue(matcher.isBlocked("PORNHUB.COM"))
        assertTrue(matcher.isBlocked("pornhub.com."))
    }

    @Test
    fun `blocked subdomain matches parent rule`() {
        matcher.addBlockedDomain("pornhub.com")

        assertTrue("Direct subdomain should match", matcher.isBlocked("cdn.pornhub.com"))
        assertTrue("Deep subdomain should match", matcher.isBlocked("video.edge.cdn.pornhub.com"))
        assertTrue("Subdomain with mixed case should match", matcher.isBlocked("Static.Pornhub.Com"))
    }

    @Test
    fun `allowed domain is not blocked`() {
        matcher.addAllowedDomain("wikipedia.org")
        matcher.addAllowedDomain("google.com")

        assertFalse(matcher.isBlocked("wikipedia.org"))
        assertFalse(matcher.isBlocked("en.wikipedia.org"))
        assertFalse(matcher.isBlocked("google.com"))
        assertTrue(matcher.isAllowed("wikipedia.org"))
        assertTrue(matcher.isAllowed("en.wikipedia.org"))
    }

    @Test
    fun `allowlist precedence over blocked parent rule`() {
        matcher.addBlockedDomain("example.com")
        matcher.addAllowedDomain("safe.example.com")

        assertTrue("Parent domain is blocked", matcher.isBlocked("example.com"))
        assertTrue("Other subdomain is blocked", matcher.isBlocked("other.example.com"))
        assertFalse("Allowed subdomain has precedence", matcher.isBlocked("safe.example.com"))
        assertFalse("Child of allowed subdomain has precedence", matcher.isBlocked("sub.safe.example.com"))
    }

    @Test
    fun `non-matching domains are not blocked`() {
        matcher.addBlockedDomain("pornhub.com")

        assertFalse("Prefix similarity must not match", matcher.isBlocked("notpornhub.com"))
        assertFalse("Suffix similarity must not match", matcher.isBlocked("pornhub.community"))
        assertFalse("Similar domain name must not match", matcher.isBlocked("hub.com"))
        assertFalse("Completely different domain must not match", matcher.isBlocked("github.com"))
        assertFalse("Blank input must not match", matcher.isBlocked(""))
        assertFalse("Invalid input must not match", matcher.isBlocked("localhost"))
    }

    @Test
    fun `large blocklists scale and match without degradation`() {
        // Generate 15,000 synthetic blocked domains
        val syntheticDomains = (1..15_000).map { "adult-vault-$it.xxx" }
        matcher.addBlockedDomains(syntheticDomains)
        matcher.addBlockedDomain("special-target.com")
        matcher.addAllowedDomain("safe-hub.com")

        // Assert lookups against the large structure
        assertTrue(matcher.isBlocked("adult-vault-1.xxx"))
        assertTrue(matcher.isBlocked("adult-vault-7500.xxx"))
        assertTrue(matcher.isBlocked("edge.adult-vault-14999.xxx"))
        assertTrue(matcher.isBlocked("special-target.com"))
        assertTrue(matcher.isBlocked("sub.special-target.com"))

        assertFalse(matcher.isBlocked("safe-hub.com"))
        assertFalse(matcher.isBlocked("unknown-safe-site.org"))
    }

    @Test
    fun `provider default matcher includes 18+ corpus and false-positive allowlist`() {
        val providerMatcher = AdultBlocklistProvider.createMatcher()

        // 18+ corpus domains should be blocked
        assertTrue(providerMatcher.isBlocked("pornhub.com"))
        assertTrue(providerMatcher.isBlocked("mobile.xvideos.com"))
        assertTrue(providerMatcher.isBlocked("chaturbate.com"))
        assertTrue(providerMatcher.isBlocked("brazzers.com"))

        // Educational, search and false-positive safe domains should be allowed
        assertFalse("essex.gov.uk contains sex but is whitelisted", providerMatcher.isBlocked("essex.gov.uk"))
        assertFalse("sussex.ac.uk contains sex but is whitelisted", providerMatcher.isBlocked("sussex.ac.uk"))
        assertFalse("wikipedia is whitelisted", providerMatcher.isBlocked("wikipedia.org"))
        assertFalse("google is whitelisted", providerMatcher.isBlocked("google.com"))
    }
}
