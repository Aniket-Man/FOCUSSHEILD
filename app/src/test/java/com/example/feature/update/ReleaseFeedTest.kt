package com.example.feature.update

import com.example.feature.update.data.GitHubReleaseRepository
import com.example.feature.update.data.ReleaseJsonParser
import com.example.feature.update.data.ReleaseResponseMapper
import com.example.feature.update.data.UpdateSourceUnavailableException
import com.example.feature.update.domain.SemanticVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 2 regression suite: the update feed.
 *
 * The updater used to have exactly one source (the unauthenticated GitHub API) and no unit tests, so
 * "the repository is private" was indistinguishable from "no release is published", and the asset
 * selection rules were only exercised by installing a release. These tests pin the two supported
 * payload shapes, the `.apk`-only asset rule, the status → message mapping, and the URL resolution.
 *
 * Plain JUnit: nothing here touches Android (OkHttp and org.json are JVM libraries).
 */
class ReleaseFeedTest {

    private fun githubPayload(
        tag: String = "v1.3.1",
        assets: List<Pair<String, String>> = listOf(
            "FocusShield-v1.3.1.apk" to "https://github.com/Aniket-Man/FOCUSSHEILD/releases/download/v1.3.1/FocusShield-v1.3.1.apk"
        )
    ): String {
        val assetJson = assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url"}"""
        }
        return """
            {
              "tag_name": "$tag",
              "name": "FocusShield $tag",
              "body": "Fixes and polish",
              "html_url": "https://github.com/Aniket-Man/FOCUSSHEILD/releases/tag/$tag",
              "published_at": "2026-09-01T10:00:00Z",
              "assets": [ $assetJson ]
            }
        """.trimIndent()
    }

    // ---- URL resolution ------------------------------------------------------------------

    @Test
    fun `without a feed url the GitHub API is used for the configured slug`() {
        val repo = GitHubReleaseRepository(repoSlug = "owner/repo")
        assertEquals(
            "https://api.github.com/repos/owner/repo/releases/latest",
            repo.buildUrl()
        )
    }

    @Test
    fun `a configured feed url replaces the GitHub call`() {
        val repo = GitHubReleaseRepository(feedUrl = "https://feed.example.com/focusshield/release.json")
        assertEquals("https://feed.example.com/focusshield/release.json", repo.buildUrl())
    }

    @Test
    fun `a malformed feed url fails loudly instead of silently falling back to GitHub`() {
        for (bad in listOf("not a url", "ftp://example.com/release.json", "example.com/release.json")) {
            val repo = GitHubReleaseRepository(feedUrl = bad)
            val failure = runCatching { repo.buildUrl() }.exceptionOrNull()
            assertTrue(
                "expected \"$bad\" to be rejected, got $failure",
                failure is UpdateSourceUnavailableException
            )
        }
    }

    // ---- payload parsing -----------------------------------------------------------------

    @Test
    fun `a GitHub release payload parses`() {
        val info = ReleaseJsonParser.parse(githubPayload())
        assertNotNull(info)
        assertEquals("1.3.1", info!!.versionName)
        assertEquals("v1.3.1", info.tagName)
        assertEquals("FocusShield v1.3.1", info.title)
        assertEquals("Fixes and polish", info.releaseNotes)
        assertTrue(info.isInstallable)
        assertEquals(
            "https://github.com/Aniket-Man/FOCUSSHEILD/releases/download/v1.3.1/FocusShield-v1.3.1.apk",
            info.apkUrl
        )
        assertEquals("FocusShield-v1.3.1.apk", info.apkAssetName)
        assertEquals("2026-09-01T10:00:00Z", info.publishedAt)
    }

    @Test
    fun `a normalised feed payload parses without pretending to be GitHub`() {
        val info = ReleaseJsonParser.parse(
            """
            {
              "versionName": "2.0.0",
              "versionCode": 7,
              "apkUrl": "https://dl.example.com/focusshield-2.0.0.apk",
              "title": "FocusShield 2.0",
              "releaseNotes": "Cloud sync improvements",
              "releasePageUrl": "https://example.com/releases/2.0.0"
            }
            """.trimIndent()
        )
        assertNotNull(info)
        assertEquals("2.0.0", info!!.versionName)
        assertEquals(7, info.versionCode)
        assertEquals("focusshield-2.0.0.apk", info.apkAssetName)
        assertEquals("Cloud sync improvements", info.releaseNotes)
        assertEquals("https://example.com/releases/2.0.0", info.releasePageUrl)
        assertTrue(info.isInstallable)
    }

    @Test
    fun `only apk assets are eligible and the version-matching name wins`() {
        val info = ReleaseJsonParser.parse(
            githubPayload(
                tag = "v1.4.0",
                assets = listOf(
                    "release-notes.txt" to "https://github.com/x/notes.txt",
                    "app-universal-debug-unsigned.apk" to "https://github.com/x/debug.apk",
                    "FocusShield-v1.4.0.apk" to "https://github.com/x/release.apk"
                )
            )
        )
        assertEquals("FocusShield-v1.4.0.apk", info!!.apkAssetName)
        assertEquals("https://github.com/x/release.apk", info.apkUrl)
    }

    @Test
    fun `a release with no apk asset is not installable`() {
        val info = ReleaseJsonParser.parse(
            githubPayload(assets = listOf("source.zip" to "https://github.com/x/source.zip"))
        )
        assertNotNull(info)
        assertNull(info!!.apkUrl)
        assertFalse(info.isInstallable)
    }

    @Test
    fun `a payload with no version is 'nothing published', not a failure`() {
        assertNull(ReleaseJsonParser.parse("{}"))
        assertNull(ReleaseJsonParser.parse("""{"draft": true, "assets": []}"""))
    }

    @Test
    fun `unreadable json is a failure, never an empty release`() {
        val failure = runCatching { ReleaseJsonParser.parse("<html>not json</html>") }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(
            "the update server sent an unreadable response",
            failure!!.message
        )
    }

    // ---- status mapping ------------------------------------------------------------------

    @Test
    fun `a 404 from GitHub names the real cause`() {
        val failure = runCatching {
            ReleaseResponseMapper.map(code = 404, payload = "", fromGitHub = true, rateLimited = false)
        }.exceptionOrNull()
        assertTrue(failure is UpdateSourceUnavailableException)
        val reason = (failure as UpdateSourceUnavailableException).reason
        assertTrue(reason, reason.contains("not reachable without signing in"))
        assertTrue(reason, reason.contains("private"))
    }

    @Test
    fun `a 404 from a configured feed is 'no published release'`() {
        val failure = runCatching {
            ReleaseResponseMapper.map(code = 404, payload = "", fromGitHub = false, rateLimited = false)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertEquals("No published release was found at this build's update feed.", failure.reason)
    }

    @Test
    fun `rate limiting and refusals are reported differently`() {
        val limited = runCatching {
            ReleaseResponseMapper.map(code = 403, payload = "", fromGitHub = true, rateLimited = true)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertTrue(limited.reason, limited.reason.contains("rate-limiting"))

        val refused = runCatching {
            ReleaseResponseMapper.map(code = 401, payload = "", fromGitHub = true, rateLimited = false)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertTrue(refused.reason, refused.reason.contains("refused"))
    }

    @Test
    fun `other server errors name the status`() {
        val failure = runCatching {
            ReleaseResponseMapper.map(code = 500, payload = "", fromGitHub = false, rateLimited = false)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertEquals("Couldn't check for updates (server said 500).", failure.reason)
    }

    @Test
    fun `a healthy feed parses through the mapper`() {
        val info = ReleaseResponseMapper.map(
            code = 200,
            payload = githubPayload(),
            fromGitHub = true,
            rateLimited = false
        )
        assertEquals("1.3.1", info!!.versionName)
    }

    @Test
    fun `a 2xx with garbage is reported as unreadable rather than as up to date`() {
        val failure = runCatching {
            ReleaseResponseMapper.map(code = 200, payload = "nope", fromGitHub = false, rateLimited = false)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertEquals("The update server sent an unreadable response.", failure.reason)
    }

    // ---- version comparison ---------------------------------------------------------------

    @Test
    fun `version comparison is numeric, not alphabetical`() {
        assertTrue(SemanticVersion.isNewer("1.0.10", "1.0.9"))
        assertFalse(SemanticVersion.isNewer("1.0.9", "1.0.10"))
        assertTrue(SemanticVersion.isNewer("1.1", "1.0.9"))
        assertFalse(SemanticVersion.isNewer("1.0.0", "1.0"))
        assertTrue(SemanticVersion.isNewer("1.3.1", "1.3.0"))
        // A pre-release never outranks the bare release it precedes.
        assertFalse(SemanticVersion.isNewer("2.0.0-rc1", "2.0.0"))
        assertTrue(SemanticVersion.isNewer("2.0.0", "2.0.0-rc1"))
        // Free-form tags that contain no number are not "newer".
        assertFalse(SemanticVersion.isNewer("release", "1.3.0"))
    }
}
