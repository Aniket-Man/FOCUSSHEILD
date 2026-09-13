package com.example.feature.update.data

import com.example.feature.update.domain.SemanticVersion
import com.example.feature.update.domain.UpdateInfo
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Reads the newest release from the GitHub Releases API.
 *
 * Uses [OkHttpClient] — already a project dependency, so no new networking stack is introduced
 * (prompt.txt §14). The client is intentionally *unauthenticated*: the anon-key/apikey handling in
 * `SupabaseHttp` is Supabase-specific and would be wrong to reuse here, and shipping a GitHub token
 * is forbidden (prompt.txt §15).
 *
 * Consequence, stated plainly: while `Aniket-Man/FOCUSSHEILD` is private, GitHub answers **404** to
 * this call and the checker reports "couldn't check for updates" rather than inventing a result.
 * Making the repository public, or pointing [repoSlug] at a backend that serves the same JSON shape,
 * is what makes the feature live.
 */
class GitHubReleaseRepository(
    private val repoSlug: String = DEFAULT_REPO_SLUG,
    private val client: OkHttpClient = defaultClient()
) : UpdateRepository {

    override suspend fun fetchLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/repos/$repoSlug/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub rejects requests without a User-Agent.
            .header("User-Agent", "FocusShield-Android")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateSourceUnavailableException(
                "Couldn't reach the update server. Check your connection and try again."
            )
        }

        val body = response.body?.string().orEmpty()
        val code = response.code
        response.close()

        when {
            code == 404 -> throw UpdateSourceUnavailableException(
                "No published release was found for this app."
            )
            code == 403 -> throw UpdateSourceUnavailableException(
                "The update server refused the request. Try again later."
            )
            code !in 200..299 -> throw UpdateSourceUnavailableException(
                "Couldn't check for updates (server said $code)."
            )
        }

        parseRelease(body)
    }

    /** Maps GitHub's release JSON onto [UpdateInfo]; returns null if the payload is unusable. */
    private fun parseRelease(body: String): UpdateInfo? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw UpdateSourceUnavailableException("The update server sent an unreadable response.")
        }

        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val versionName = SemanticVersion.normalize(tag)
        if (versionName.isBlank()) return null

        val assets = json.optJSONArray("assets")
        val apk = pickApkAsset(assets, versionName)

        return UpdateInfo(
            versionName = versionName,
            versionCode = null, // GitHub releases carry no Android versionCode; the tag is the truth.
            tagName = tag,
            title = json.optString("name").ifBlank { tag },
            releaseNotes = json.optString("body").orEmpty(),
            apkUrl = apk?.first,
            apkAssetName = apk?.second,
            releasePageUrl = json.optString("html_url").orEmpty(),
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Chooses the APK asset. Only a name ending in `.apk` is ever eligible; when several qualify the
     * one naming the release version wins, then the shortest name (so `FocusShield-v1.0.1.apk` beats
     * `FocusShield-v1.0.1-debug-unsigned.apk`). prompt.txt §3 — never assume an arbitrary asset.
     */
    private fun pickApkAsset(assets: org.json.JSONArray?, versionName: String): Pair<String, String>? {
        if (assets == null) return null
        val candidates = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .mapNotNull { asset ->
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
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

    companion object {
        /** Owner/repo of the release feed. Swap here (or pass a backend URL) to change the source. */
        const val DEFAULT_REPO_SLUG = "Aniket-Man/FOCUSSHEILD"
        private const val API_BASE = "https://api.github.com"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
