package com.example.feature.websiteblocker.vpn

import java.util.Locale

/**
 * The outcome of matching a queried domain against the filter's domain sets.
 */
sealed interface DomainMatch {

    /** The domain (or one of its parent suffixes) is on the blocklist. */
    data class Blocked(val query: String, val matchedRule: String) : DomainMatch

    /** The domain (or one of its parent suffixes) is on the allowlist — allowlist wins over the blocklist. */
    data class Allowed(val query: String, val matchedRule: String) : DomainMatch

    /** Neither list matched; the query must be forwarded upstream unchanged. */
    data object NotMatched : DomainMatch
}

/**
 * Fast, exact domain matching for large (100k+) blocklists, purpose-built for the DNS-level
 * 18+ filtering engine.
 *
 * Data structure
 * --------------
 * Every list lives in a [HashSet] of pre-normalized domain names. A lookup walks the query's
 * *label suffixes* — for `a.b.pornhub.com` it probes `a.b.pornhub.com`, `b.pornhub.com`,
 * `pornhub.com`, `com` — which costs at most a handful of O(1) hash lookups per query regardless
 * of list size (DNS names are bounded to ~4–5 practical labels by the public-suffix rules).
 *
 * Matching semantics
 * ------------------
 *  * **Subdomain matching**: an entry `pornhub.com` blocks `pornhub.com` and any `*.pornhub.com`.
 *  * **Exact matching without false positives**: the suffix walk only ever compares whole labels,
 *    so `pornhub.com.attacker.net` and `notpornhub.com` do **not** match the `pornhub.com` entry,
 *    and a substring like `porn` inside an unrelated host never matches either (no lexical-stem
 *    matching here by design — that belongs to [com.example.feature.websiteblocker.engine.AdultLexicalClassifier]
 *    where the full URL is available).
 *  * **Adult TLDs**: any domain whose last label is a configured adult TLD (e.g. `.xxx`) is blocked.
 *  * **Allowlist precedence**: the allowlist is consulted first; if the query or any parent suffix
 *    is allowed, the domain is never blocked, even when a deeper/parent entry sits on the blocklist.
 *
 * Pure Kotlin (no Android imports) so the matching contract is unit-testable in isolation.
 */
class DomainBlocklist(
    blockedDomains: Set<String> = emptySet(),
    blockedTlds: Set<String> = emptySet(),
    allowedDomains: Set<String> = emptySet()
) {

    private val blocked: Set<String> =
        blockedDomains.map { normalize(it) }.filter { it.contains('.') }.toHashSet()

    private val adultTlds: Set<String> =
        blockedTlds.map { normalize(it).removePrefix(".") }.filter { it.isNotEmpty() }.toHashSet()

    private val allowed: Set<String> =
        allowedDomains.map { normalize(it) }.filter { it.contains('.') }.toHashSet()

    /** Number of exact blocklist entries held (exposed for logging/diagnostics). */
    val blockedDomainCount: Int get() = blocked.size

    /**
     * Matches [rawDomain] (a domain, a URL, or a host with trailing dot) against the allowlist,
     * blocklist and adult-TLD rules, in that order.
     */
    fun match(rawDomain: String): DomainMatch {
        val domain = normalize(rawDomain)
        // A resolvable domain always has at least two labels; single-label or empty input never matches.
        if (!domain.contains('.')) return DomainMatch.NotMatched

        // 1. Allowlist precedence: any allowed suffix wins over any block rule.
        firstMatchingSuffix(domain, allowed)?.let { return DomainMatch.Allowed(domain, it) }

        // 2. Exact / parent-suffix blocklist match (whole labels only — no false positives).
        firstMatchingSuffix(domain, blocked)?.let { return DomainMatch.Blocked(domain, it) }

        // 3. Adult TLD rule: block everything under a configured adult TLD.
        val tld = domain.substringAfterLast('.', "")
        if (tld.isNotEmpty() && adultTlds.contains(tld)) {
            return DomainMatch.Blocked(domain, ".$tld")
        }

        return DomainMatch.NotMatched
    }

    /** Convenience for callers that only need the verdict. */
    fun isBlocked(rawDomain: String): Boolean = match(rawDomain) is DomainMatch.Blocked

    /**
     * Returns the first suffix of [domain] present in [set], walking from the full name up to the
     * last label, or null when nothing matches.
     */
    private fun firstMatchingSuffix(domain: String, set: Set<String>): String? {
        var candidate = domain
        while (true) {
            if (set.contains(candidate)) return candidate
            val dot = candidate.indexOf('.')
            if (dot == -1) return null
            candidate = candidate.substring(dot + 1)
        }
    }

    companion object {

        /**
         * Normalizes a domain (or a full URL) into a canonical lowercase host:
         * strips surrounding whitespace and trailing dots, URL scheme / userinfo / path / port,
         * and a leading `www.` — mirroring the normalization the manual blocklist already uses
         * ([com.example.data.repository.BlockedWebsiteRepository.normalizeDomain]).
         */
        fun normalize(rawDomain: String): String {
            var domain = rawDomain.trim().lowercase(Locale.ROOT)
            if (domain.isEmpty()) return ""

            // Strip URL syntax when the caller passed a full URL.
            val scheme = domain.indexOf("://")
            if (scheme != -1) domain = domain.substring(scheme + 3)
            val userInfo = domain.indexOf('@')
            if (userInfo != -1) domain = domain.substring(userInfo + 1)
            val path = domain.indexOf('/')
            if (path != -1) domain = domain.substring(0, path)
            val query = domain.indexOf('?')
            if (query != -1) domain = domain.substring(0, query)
            val fragment = domain.indexOf('#')
            if (fragment != -1) domain = domain.substring(0, fragment)
            val port = domain.indexOf(':')
            if (port != -1) domain = domain.substring(0, port)

            while (domain.endsWith(".")) domain = domain.substring(0, domain.length - 1)
            while (domain.startsWith(".")) domain = domain.substring(1)
            if (domain.startsWith("www.")) domain = domain.substring(4)
            return domain
        }
    }
}
