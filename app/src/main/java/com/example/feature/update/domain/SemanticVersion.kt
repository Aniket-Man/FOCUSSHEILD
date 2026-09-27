package com.example.feature.update.domain

/**
 * Semantic-version comparison for the update checker.
 *
 * GitHub tags are free-form, so this exists to answer one question safely: is the published release
 * actually newer than what is installed? A plain string compare would rank `1.0.10` below `1.0.9`
 * and treat `1.0` as newer than `1.0.0`, so versions are compared component-by-component instead
 * (prompt.txt §2 — "Do NOT simply compare strings alphabetically").
 *
 * Tolerated shapes: `v1.0.1`, `1.0.1`, `1.0`, `2.0.0-rc1`, `1.4.0+build.7`. A missing component
 * counts as zero, so `1.0` and `1.0.0` are the same version. Build metadata after `+` never
 * participates in precedence. A pre-release ranks *below* its bare release, so `2.0.0-rc1` does not
 * outrank an installed `2.0.0`, and two pre-releases are compared identifier by identifier with
 * numeric identifiers compared numerically — `2.0.0-beta.10` is newer than `2.0.0-beta.2`.
 *
 * A version core that is not strictly numeric (`release/1.4.0`, `1.rc.5`, `release-2024`) fails to
 * parse and compares equal to everything: a tag the updater cannot read must never be offered as an
 * update. Earlier versions *dropped* the offending segment instead of failing — `1.rc.5` became
 * `[1, 5]` and `release/1.4.0` became `[4, 0]` — so garbage tags could outrank the installed build
 * and an older APK could be offered as an update.
 */
object SemanticVersion {

    /** Strips the leading `v`/`V` GitHub tags conventionally carry: `v1.0.1` → `1.0.1`. */
    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Numeric components of the version core, with any pre-release suffix split off.
     * Returns null when any core segment is not a plain non-negative integer — such a tag is
     * uncomparable rather than partially parsed.
     */
    private fun parse(raw: String): Pair<List<Int>, String?>? {
        val normalized = normalize(raw)
        // Build metadata (`+...`) is ignored for precedence (semver §10) and must not leak into the
        // pre-release split: `1.0.0+build-2` has no pre-release at all.
        val withoutBuild = normalized.substringBefore('+')
        val corePart = withoutBuild.substringBefore('-')
        val preRelease = if ('-' in withoutBuild) {
            withoutBuild.substringAfter('-').ifBlank { null }
        } else {
            null
        }
        val parts = corePart.split('.').map { segment ->
            if (segment.isEmpty() || !segment.all(Char::isDigit)) return null
            segment.toIntOrNull() ?: return null
        }
        return parts to preRelease
    }

    /**
     * Compares two versions. Returns a positive number when [a] is newer than [b], negative when
     * older, and 0 when they are the same version — or when either side is unparseable, because a
     * tag neither side can read must never be treated as newer.
     */
    fun compare(a: String, b: String): Int {
        val parsedA = parse(a)
        val parsedB = parse(b)
        if (parsedA == null || parsedB == null) return 0
        val (coreA, preA) = parsedA
        val (coreB, preB) = parsedB
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
            else -> comparePreRelease(preA, preB)
        }
    }

    /** True when [candidate] is strictly newer than [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    /**
     * Whether a published release is newer than the running build, for the update checker.
     *
     * When both sides carry a `versionCode` it is decisive: Android itself refuses to install a
     * lower versionCode, so a name-only "newer" could offer an APK the installer will reject, and
     * equal codes mean equal builds whatever the tag says. Falls back to component-wise name
     * comparison when either side omits the code (GitHub tags never state one; dev builds may not).
     */
    fun isNewerRelease(
        candidate: String,
        candidateVersionCode: Int?,
        installed: String,
        installedVersionCode: Int?
    ): Boolean =
        if (candidateVersionCode != null && installedVersionCode != null) {
            candidateVersionCode > installedVersionCode
        } else {
            isNewer(candidate, installed)
        }

    /**
     * Pre-release precedence (semver §11.4): compare identifiers pairwise — numeric against
     * numeric numerically, alphanumeric lexically, numeric always below alphanumeric — and, when
     * every shared identifier is equal, let the longer identifier list win.
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val idsA = a.split('.')
        val idsB = b.split('.')
        val shared = minOf(idsA.size, idsB.size)
        for (i in 0 until shared) {
            val left = idsA[i]
            val right = idsB[i]
            val leftNumeric = left.isNotEmpty() && left.all(Char::isDigit)
            val rightNumeric = right.isNotEmpty() && right.all(Char::isDigit)
            val cmp = when {
                leftNumeric && rightNumeric -> compareNumericIdentifier(left, right)
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left.compareTo(right)
            }
            if (cmp != 0) return cmp
        }
        return idsA.size - idsB.size
    }

    /** Numeric identifiers compare by magnitude: leading zeros never change a version's rank. */
    private fun compareNumericIdentifier(a: String, b: String): Int {
        val strippedA = a.dropWhile { it == '0' }.ifEmpty { "0" }
        val strippedB = b.dropWhile { it == '0' }.ifEmpty { "0" }
        return when {
            strippedA.length != strippedB.length -> strippedA.length - strippedB.length
            else -> strippedA.compareTo(strippedB)
        }
    }
}
