package com.example.feature.websiteblocker.vpn

import java.util.concurrent.ConcurrentHashMap

/**
 * Fast, memory-efficient domain matching data structure for 18+ and custom blocklists.
 *
 * Uses a reversed-label Trie:
 * Domains are hierarchical right-to-left (e.g. "cdn.pornhub.com" -> ["com", "pornhub", "cdn"]).
 * This allows exact matching, subdomain matching, and allowlist overrides in O(k) time,
 * where k is the number of labels in the domain (typically 2-4 lookups), completely independent
 * of the total number of rules in the blocklist (even with tens of thousands of domains).
 */
class DomainMatcher {

    private class TrieNode {
        val children = ConcurrentHashMap<String, TrieNode>()
        @Volatile var isBlocked: Boolean = false
        @Volatile var isAllowed: Boolean = false
    }

    private val root = TrieNode()
    private val exactAllowedDomains = ConcurrentHashMap.newKeySet<String>()

    /**
     * Add a domain to the blocklist.
     * Any subdomains of this domain will also be considered blocked unless explicitly allowlisted.
     */
    fun addBlockedDomain(rawDomain: String) {
        val normalized = normalize(rawDomain) ?: return
        val labels = normalized.split('.').reversed()
        var current = root
        for (label in labels) {
            current = current.children.computeIfAbsent(label) { TrieNode() }
        }
        current.isBlocked = true
    }

    fun addBlockedDomains(domains: Collection<String>) {
        for (domain in domains) {
            addBlockedDomain(domain)
        }
    }

    /**
     * Add a domain to the allowlist.
     * Takes precedence over any block rule for this domain or its subdomains.
     */
    fun addAllowedDomain(rawDomain: String) {
        val normalized = normalize(rawDomain) ?: return
        exactAllowedDomains.add(normalized)
        val labels = normalized.split('.').reversed()
        var current = root
        for (label in labels) {
            current = current.children.computeIfAbsent(label) { TrieNode() }
        }
        current.isAllowed = true
    }

    fun addAllowedDomains(domains: Collection<String>) {
        for (domain in domains) {
            addAllowedDomain(domain)
        }
    }

    /**
     * Evaluates whether [rawDomain] should be blocked.
     *
     * Rules:
     * 1. If domain is blank or invalid -> false (do not block).
     * 2. If domain or any parent domain is on the allowlist -> false (allowlist precedence).
     * 3. If domain or any parent domain is on the blocklist -> true (blocked).
     */
    fun isBlocked(rawDomain: String): Boolean {
        val normalized = normalize(rawDomain) ?: return false

        // Quick check on exact allowlist
        if (exactAllowedDomains.contains(normalized)) {
            return false
        }

        val labels = normalized.split('.').reversed()
        var current = root
        var blockedMatch = false

        for (label in labels) {
            val next = current.children[label] ?: break
            current = next

            // Allowlist on a parent domain overrides any block
            if (current.isAllowed) {
                return false
            }

            if (current.isBlocked) {
                blockedMatch = true
            }
        }

        return blockedMatch
    }

    /**
     * Checks if a domain is specifically allowed by the allowlist.
     */
    fun isAllowed(rawDomain: String): Boolean {
        val normalized = normalize(rawDomain) ?: return false
        if (exactAllowedDomains.contains(normalized)) return true

        val labels = normalized.split('.').reversed()
        var current = root
        for (label in labels) {
            val next = current.children[label] ?: return false
            current = next
            if (current.isAllowed) return true
        }
        return false
    }

    fun clear() {
        root.children.clear()
        exactAllowedDomains.clear()
    }

    companion object {
        fun normalize(domain: String?): String? {
            if (domain.isNullOrBlank()) return null
            var clean = domain.trim().lowercase()
            // Strip trailing dot if present (FQDN DNS format)
            if (clean.endsWith('.')) {
                clean = clean.dropLast(1)
            }
            // Strip scheme if present accidentally
            if (clean.startsWith("http://")) clean = clean.substring(7)
            if (clean.startsWith("https://")) clean = clean.substring(8)
            val slashIdx = clean.indexOf('/')
            if (slashIdx != -1) clean = clean.substring(0, slashIdx)
            val colonIdx = clean.indexOf(':')
            if (colonIdx != -1) clean = clean.substring(0, colonIdx)
            return if (clean.isNotBlank() && clean.contains('.')) clean else null
        }
    }
}
