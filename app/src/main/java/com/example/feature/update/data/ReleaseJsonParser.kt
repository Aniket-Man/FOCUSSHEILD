package com.example.feature.update.data

import com.example.feature.update.domain.SemanticVersion
import com.example.feature.update.domain.UpdateInfo
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses a release payload into [UpdateInfo].
 *
 * Two shapes are accepted, on purpose:
 *
 *  1. **GitHub Releases** (`tag_name` / `assets[].browser_download_url`) — the default source.
 *  2. **A normalised feed** (`versionName`, `apkUrl`, `releaseNotes`, …) — what a small backend or
 *     Supabase Edge Function would serve. This is the supported way to make the updater work while
 *     the repository is private: the app cannot read a private repo's releases without shipping a
 *     token, and shipping a token is forbidden (`docs/UPDATE_SYSTEM.md` §1).
 *
 * Kept separate from [GitHubReleaseRepository] so the parsing — the part that can silently pick the
 * wrong asset or invent a version — is unit-tested without a network.
 */
internal object ReleaseJsonParser {

    /**
     * @return the release, or null when the payload is valid JSON but carries no usable version.
     * @throws ReleasePayloadException when the payload is not JSON at all.
     */
    fun parse(body: String): UpdateInfo? {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ReleasePayloadException("the update server sent an unreadable response")
        }

        // Normalised feed first: it is explicit about the version, so it never depends on tag naming.
        val explicitVersion = json.optString("versionName").takeIf { it.isNotBlank() }
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() }
        val rawVersion = explicitVersion ?: tag ?: return null

        val versionName = SemanticVersion.normalize(rawVersion)
        if (versionName.isBlank()) return null

        val apk = pickApkAsset(json.optJSONArray("assets"), json.optString("apkUrl"), versionName)

        return UpdateInfo(
            versionName = versionName,
            versionCode = json.optInt("versionCode", 0).takeIf { it > 0 },
            tagName = tag ?: "v$versionName",
            title = json.optString("title").ifBlank {
                json.optString("name").ifBlank { rawVersion }
            },
            releaseNotes = json.optString("releaseNotes").ifBlank { json.optString("body").orEmpty() },
            apkUrl = apk?.first,
            apkAssetName = apk?.second,
            releasePageUrl = json.optString("releasePageUrl").ifBlank { json.optString("html_url").orEmpty() },
            publishedAt = json.optString("publishedAt").ifBlank {
                json.optString("published_at").takeIf { it.isNotBlank() }
            }
        )
    }

    /**
     * Chooses the APK: an explicit `apkUrl` when the feed provides one, otherwise a `.apk` asset.
     *
     * Only a name ending in `.apk` is ever eligible; when several qualify the one naming the release
     * version wins, then the shortest name (so `FocusShield-v1.0.1.apk` beats
     * `FocusShield-v1.0.1-debug-unsigned.apk`). Nothing is ever assumed about an arbitrary asset.
     */
    private fun pickApkAsset(
        assets: JSONArray?,
        explicitApkUrl: String,
        versionName: String
    ): Pair<String, String>? {
        if (explicitApkUrl.isNotBlank()) {
            val name = explicitApkUrl.substringAfterLast('/').substringBefore('?')
            if (name.endsWith(".apk", ignoreCase = true)) return explicitApkUrl to name
        }
        if (assets == null) return null

        val candidates = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .mapNotNull { asset ->
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url").ifBlank { asset.optString("url") }
                if (name.isBlank() || url.isBlank()) null else name to url
            }
            .filter { (name, _) -> name.endsWith(".apk", ignoreCase = true) }
        if (candidates.isEmpty()) return null

        val best = candidates.sortedWith(
            compareByDescending<Pair<String, String>> { it.first.contains(versionName) }
                .thenBy { it.first.length }
        ).first()
        return best.second to best.first
    }
}

/** The release payload was not readable at all (not the same as "no release published"). */
internal class ReleasePayloadException(reason: String) : Exception(reason)
