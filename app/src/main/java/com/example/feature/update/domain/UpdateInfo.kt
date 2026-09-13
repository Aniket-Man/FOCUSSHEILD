package com.example.feature.update.domain

/**
 * One published FocusShield release, normalized from whatever source produced it.
 *
 * Deliberately source-agnostic: [com.example.feature.update.data.GitHubReleaseRepository] builds one
 * of these today, and a FocusShield backend could build the same shape later without the UI or the
 * checker changing (prompt.txt §15 — the repository is private, so the source must stay swappable).
 *
 * @param versionName normalized, leading `v` stripped — `v1.0.1` and `1.0.1` both arrive as `1.0.1`.
 * @param versionCode the release's own Android version code when it states one. GitHub tags do not,
 *   so this is usually null; [UpdateInfo.versionName] is the comparison of record.
 * @param apkUrl null when the release carries no `.apk` asset — the UI then offers no install button
 *   and says so, rather than linking something that is not an APK (prompt.txt §3).
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int? = null,
    val tagName: String,
    val title: String,
    val releaseNotes: String = "",
    val apkUrl: String? = null,
    val apkAssetName: String? = null,
    val releasePageUrl: String = "",
    val publishedAt: String? = null
) {
    /** True when there is something the user can actually download and install. */
    val isInstallable: Boolean get() = apkUrl != null
}
