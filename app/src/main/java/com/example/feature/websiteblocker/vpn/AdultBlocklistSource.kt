package com.example.feature.websiteblocker.vpn

import com.example.feature.websiteblocker.engine.AdultLexicalClassifier

/**
 * Assembles the 18+ domain blocklist used by the DNS-level filter.
 *
 * The list is **not** duplicated here: it is the exact corpus (500+ verified adult hubs, networks,
 * cam platforms, hentai boards) and the adult-TLD set already maintained by
 * [AdultLexicalClassifier] for the accessibility-based browser filter, exposed through its
 * internal accessors. One list, two enforcement layers.
 *
 * Deliberately **not** included at the DNS layer: the lexical token-stem matching
 * (`ADULT_TOKEN_STEMS`) and the safe-word whitelist of the URL classifier. Stems can only be
 * judged with the full URL/context in view, and a DNS-level false positive would break unrelated
 * lookups system-wide — the VPN layer sticks to exact domain/TLD rules, which cannot false-positive
 * on substring lookalikes.
 */
object AdultBlocklistSource {

    /** Allowlist support: no user-facing allowlist exists yet, so the allow set is empty for now. */
    private val userAllowlist: Set<String> = emptySet()

    fun blocklist(): DomainBlocklist = DomainBlocklist(
        blockedDomains = AdultLexicalClassifier.adultDomainCorpus,
        blockedTlds = AdultLexicalClassifier.adultTlds,
        allowedDomains = userAllowlist
    )
}
