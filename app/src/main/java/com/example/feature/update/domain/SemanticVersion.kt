package com.example.feature.update.domain

/**
 * Semantic-version comparison for the update checker.
 *
 * GitHub tags are free-form, so this exists to answer one question safely: is the published release
 * actually newer than what is installed? A plain string compare would rank `1.0.10` below `1.0.9`
 * and treat `1.0` as newer than `1.0.0`, so versions are compared component-by-component instead
 * (prompt.txt §2 — "Do NOT simply compare strings alphabetically").
 *
 * Tolerated shapes: `v1.0.1`, `1.0.1`, `1.0`, `2.0.0-rc1`. A missing component counts as zero, so
 * `1.0` and `1.0.0` are the same version. A pre-release suffix ranks *below* the bare release, so
 * `2.0.0-rc1` does not outrank an installed `2.0.0`.
 */
object SemanticVersion {

    /** Strips the leading `v`/`V` GitHub tags conventionally carry: `v1.0.1` → `1.0.1`. */
    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Numeric components of the version core, with any pre-release suffix split off.
     * Non-numeric components stop the parse rather than throwing — a tag like `release-2024`
     * yields an empty core, which every comparison treats as "not newer".
     */
    private fun parse(raw: String): Pair<List<Int>, String?> {
        val normalized = normalize(raw)
        val corePart = normalized.substringBefore('-')
        val preRelease = normalized.substringAfter('-', "").ifBlank { null }
        val parts = corePart.split('.')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        return parts to preRelease
    }

    /**
     * Compares two versions. Returns a positive number when [a] is newer than [b], negative when
     * older, and 0 when they are the same version.
     */
    fun compare(a: String, b: String): Int {
        val (coreA, preA) = parse(a)
        val (coreB, preB) = parse(b)
        val width = maxOf(coreA.size, coreB.size)
        for (i in 0 until width) {
            val delta = (coreA.getOrNull(i) ?: 0) - (coreB.getOrNull(i) ?: 0)
            if (delta != 0) return delta
        }
        // Same numeric core: the bare release outranks its own pre-release (2.0.0 > 2.0.0-rc1).
        return when {
            preA == null && preB == null -> 0
            preA == null -> 1
            preB == null -> -1
            else -> preA.compareTo(preB)
        }
    }

    /** True when [candidate] is strictly newer than [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0
}
