package com.example.feature.update.domain

/**
 * Release tracks supported by the FocusShield Update Server.
 *
 * When a user selects a channel in the New Updates screen, the updater queries the release server
 * exclusively for releases published to that track (e.g. selecting [BETA] only offers Beta builds,
 * never Production or Alpha builds).
 */
enum class UpdateChannel(
    val key: String,
    val displayName: String,
    val badgeLabel: String,
    val description: String
) {
    PRODUCTION(
        key = "production",
        displayName = "Production Server",
        badgeLabel = "STABLE",
        description = "Official stable releases verified for everyday use."
    ),
    BETA(
        key = "beta",
        displayName = "Beta Server",
        badgeLabel = "BETA",
        description = "Preview upcoming features and improvements before production."
    ),
    PRE_RELEASE(
        key = "prerelease",
        displayName = "Pre-Release Server",
        badgeLabel = "PRE-RELEASE",
        description = "Release candidates and near-final builds for early testing."
    ),
    ALPHA(
        key = "alpha",
        displayName = "Alpha Server",
        badgeLabel = "ALPHA",
        description = "Early experimental builds with new features in active development."
    ),
    INTERNAL(
        key = "internal",
        displayName = "Internal Server",
        badgeLabel = "INTERNAL",
        description = "Internal developer and QA testing builds."
    );

    companion object {
        /**
         * Normalizes any raw channel string from preferences or a server release document into a
         * canonical channel key (`production`, `beta`, `prerelease`, `alpha`, or `internal`).
         * Blank or unrecognized values default to `production`.
         */
        fun normalizeKey(raw: String?): String {
            val cleaned = raw?.trim()?.lowercase().orEmpty()
            return when (cleaned) {
                "", "production", "prod", "stable", "release", "production-server" -> PRODUCTION.key
                "beta", "production-beta", "beta-server", "public-beta" -> BETA.key
                "prerelease", "pre-release", "pre_release", "pre release", "rc", "pre-release-server" -> PRE_RELEASE.key
                "alpha", "alpha-server" -> ALPHA.key
                "internal", "internal-server", "dev", "qa" -> INTERNAL.key
                else -> cleaned
            }
        }

        /**
         * Resolves a raw channel key or label to an [UpdateChannel] enum entry, defaulting to
         * [PRODUCTION] if unrecognized.
         */
        fun fromKey(raw: String?): UpdateChannel {
            val normalized = normalizeKey(raw)
            return entries.firstOrNull { it.key == normalized } ?: PRODUCTION
        }
    }
}
