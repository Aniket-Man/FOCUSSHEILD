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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue 2 regression suite: the update feed.
 *
 * The updater used to have exactly one source (the unauthenticated GitHub API) and no unit tests, so
 * "the repository is private" was indistinguishable from "no release is published", and the asset
 * selection rules were only exercised by installing a release. These tests pin the two supported
 * payload shapes, the `.apk`-only asset rule, the status → message mapping, and the URL resolution.
 *
 * Runs under Robolectric: plain JUnit would hit the mockable android.jar's stubbed
 * `org.json.JSONObject` ("Method optString not mocked"). Robolectric supplies the real
 * framework classes while the parser itself stays pure JVM code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun `the default repository is the public releases feed`() {
        assertEquals(
            "https://api.github.com/repos/Aniket-Man/FOCUSSHEILD-releases/releases/latest",
            GitHubReleaseRepository().buildUrl()
        )
    }

    @Test
    fun `a configured feed url replaces the GitHub call`() {
        val repo = GitHubReleaseRepository(feedUrl = "https://feed.example.com/focusshield/release.json")
        assertEquals("https://feed.example.com/focusshield/release.json", repo.buildUrl())
    }

    @Test
    fun `a malformed feed url fails loudly instead of silently falling back to GitHub`() {
        // http:// is rejected too: on minSdk 24–27 cleartext is still permitted by the platform
        // default, and a feed read over http could point the updater at anything.
        for (bad in listOf(
            "not a url",
            "ftp://example.com/release.json",
            "example.com/release.json",
            "http://feed.example.com/release.json"
        )) {
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
            """.trimIndent(),
            // The feed's own host is what makes its APK links trusted (UpdateUrlPolicy).
            feedHost = "dl.example.com"
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
    fun `apk links that are cleartext or off-host are not offered`() {
        // https is required even on a known host: cleartext on minSdk 24–27 is a MITM opening.
        val cleartext = ReleaseJsonParser.parse(
            githubPayload(
                assets = listOf(
                    "FocusShield-v1.3.1.apk" to
                        "http://github.com/Aniket-Man/FOCUSSHEILD/releases/download/v1.3.1/FocusShield-v1.3.1.apk"
                )
            )
        )
        assertNotNull(cleartext)
        assertNull(cleartext!!.apkUrl)
        assertFalse(cleartext.isInstallable)

        // Exact hosts only: "evilgithub.com" must not pass a suffix check (it never did here —
        // but the download path used `endsWith("github.com")`, so pin it in the parser too).
        val spoofed = ReleaseJsonParser.parse(
            githubPayload(
                assets = listOf(
                    "FocusShield-v1.3.1.apk" to "https://evilgithub.com/FocusShield-v1.3.1.apk"
                )
            )
        )
        assertNotNull(spoofed)
        assertNull(spoofed!!.apkUrl)
        assertFalse(spoofed.isInstallable)
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
    fun `a 404 from GitHub is 'nothing published', not a failure`() {
        // GitHub answers 404 for an empty release list, and also for a private repository this
        // credential-less build cannot read. Either way there is nothing to install: mapping it to
        // a failure marked every check failed and drove a 15-minute retry loop whose only visible
        // symptom was a failed manual check.
        assertNull(
            ReleaseResponseMapper.map(code = 404, payload = "", rateLimited = false)
        )
    }

    @Test
    fun `a 404 from a configured feed is 'nothing published'`() {
        assertNull(
            ReleaseResponseMapper.map(
                code = 404,
                payload = "",
                rateLimited = false,
                feedHost = "feed.example.com"
            )
        )
    }

    @Test
    fun `rate limiting and refusals are reported differently`() {
        val limited = runCatching {
            ReleaseResponseMapper.map(code = 403, payload = "", rateLimited = true)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertTrue(limited.reason, limited.reason.contains("rate-limiting"))

        val refused = runCatching {
            ReleaseResponseMapper.map(code = 401, payload = "", rateLimited = false)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertTrue(refused.reason, refused.reason.contains("refused"))
    }

    @Test
    fun `other server errors name the status`() {
        val failure = runCatching {
            ReleaseResponseMapper.map(code = 500, payload = "", rateLimited = false)
        }.exceptionOrNull() as UpdateSourceUnavailableException
        assertEquals("Couldn't check for updates (server said 500).", failure.reason)
    }

    @Test
    fun `a healthy feed parses through the mapper`() {
        val info = ReleaseResponseMapper.map(
            code = 200,
            payload = githubPayload(),
            rateLimited = false
        )
        assertEquals("1.3.1", info!!.versionName)
    }

    @Test
    fun `a 2xx with garbage is reported as unreadable rather than as up to date`() {
        val failure = runCatching {
            ReleaseResponseMapper.map(code = 200, payload = "nope", rateLimited = false)
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

    @Test
    fun `tags with non-numeric segments are never newer than an installed release`() {
        // The parser used to *drop* the offending segment: "1.rc.5" became [1, 5] (beating an
        // installed 1.0.9) and "release/1.4.0" became [4, 0] (beating an installed 1.9.0) — i.e.
        // an older APK could be offered as an update. Unparseable now means "not newer", always.
        assertFalse(SemanticVersion.isNewer("1.rc.5", "1.0.9"))
        assertFalse(SemanticVersion.isNewer("release/1.4.0", "1.9.0"))
        assertFalse(SemanticVersion.isNewer("release-2024", "0.1.0"))
        assertFalse(SemanticVersion.isNewer("v", "1.0.0"))
    }

    @Test
    fun `pre-releases compare identifier by identifier with numeric segments numeric`() {
        // Whole-string compareTo ranked "beta.10" *below* "beta.2".
        assertTrue(SemanticVersion.isNewer("2.0.0-beta.10", "2.0.0-beta.2"))
        assertFalse(SemanticVersion.isNewer("2.0.0-beta.2", "2.0.0-beta.10"))
        assertTrue(SemanticVersion.isNewer("2.0.0-beta.11", "2.0.0-beta.10"))
        // Numeric identifiers rank below alphanumeric ones (semver §11.4).
        assertTrue(SemanticVersion.isNewer("2.0.0-alpha", "2.0.0-2"))
        assertFalse(SemanticVersion.isNewer("2.0.0-2", "2.0.0-alpha"))
        // Build metadata never participates in precedence.
        assertFalse(SemanticVersion.isNewer("1.0.0+build.5", "1.0.0"))
        assertTrue(SemanticVersion.isNewer("1.0.1+build.1", "1.0.0+build.9"))
    }

    @Test
    fun `versionCode decides when both sides state one, names otherwise`() {
        // Both codes present: the code is what Android enforces on install.
        assertTrue(SemanticVersion.isNewerRelease("1.0.0", 8, "2.0.0", 7))
        assertFalse(SemanticVersion.isNewerRelease("2.0.0", 7, "1.0.0", 8))
        assertFalse(SemanticVersion.isNewerRelease("2.0.0", 7, "1.0.0", 7))
        // Either side missing a code (GitHub tags, dev builds): fall back to the name.
        assertTrue(SemanticVersion.isNewerRelease("1.0.1", null, "1.0.0", 9))
        assertTrue(SemanticVersion.isNewerRelease("1.0.1", 9, "1.0.0", null))
        assertFalse(SemanticVersion.isNewerRelease("1.0.0", null, "1.0.1", null))
    }
}
