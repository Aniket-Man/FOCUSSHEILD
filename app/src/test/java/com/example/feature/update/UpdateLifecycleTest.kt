package com.example.feature.update

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.feature.update.data.UpdateCheckResult
import com.example.feature.update.data.UpdateChecker
import com.example.feature.update.data.UpdateDownloadManager
import com.example.feature.update.data.UpdatePreferences
import com.example.feature.update.data.UpdateRepository
import com.example.feature.update.domain.UpdateInfo
import com.example.feature.update.domain.UpdateState
import com.example.feature.update.engine.UpdateManager
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.backgroundScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateLifecycleTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `installed cached offer is completely cleared during restore`() = runTest {
        val preferences = newPreferences("installed-restore")
        val release = release("1.3.1")
        val repository = MutableRepository(release)

        val oldManager = newManager(preferences, repository, installedVersion = "1.3.0")
        oldManager.checkManually()
        advanceUntilIdle()
        assertTrue(oldManager.showDot.value)

        preferences.markDismissed(release.versionName)
        preferences.markNotified(release.versionName)
        preferences.markRead(release.versionName)
        val apk = fakeDownloadedApk("installed-restore.apk")
        preferences.recordDownload(release.versionName, apk.absolutePath)

        val restartedManager = newManager(preferences, repository, installedVersion = "1.3.1")
        restartedManager.restore()
        advanceUntilIdle()

        assertEquals(UpdateState.UpToDate, restartedManager.state.value)
        assertFalse(restartedManager.showDot.value)
        assertFalse(apk.exists())
        with(preferences.current()) {
            assertNull(latestInfoJson)
            assertNull(downloadedVersion)
            assertNull(downloadedPath)
            assertNull(dismissedVersion)
            assertNull(notifiedVersion)
            assertNull(readVersion)
            assertEquals("1.3.1", latestKnownVersion)
        }
    }

    @Test
    fun `opening update screen cannot resurrect an installed cached offer`() = runTest {
        val preferences = newPreferences("installed-cache")
        val release = release("1.3.1")
        val repository = MutableRepository(release)

        newManager(preferences, repository, installedVersion = "1.3.0").also {
            it.checkManually()
            advanceUntilIdle()
        }

        val currentBuildManager = newManager(preferences, repository, installedVersion = "1.3.1")
        currentBuildManager.refreshFromCache()
        advanceUntilIdle()

        assertEquals(UpdateState.UpToDate, currentBuildManager.state.value)
        assertFalse(currentBuildManager.showDot.value)
        assertNull(preferences.current().latestInfoJson)
    }

    @Test
    fun `checker clears against installed version when local build is ahead`() = runTest {
        val preferences = newPreferences("ahead-build")
        preferences.markDismissed("1.3.1")
        preferences.markNotified("1.3.1")
        preferences.markRead("1.3.1")
        preferences.recordDownload("1.3.1", "/tmp/old.apk")
        preferences.setLatestInfoJson("{\"versionName\":\"1.3.1\"}")

        val checker = UpdateChecker(
            repository = MutableRepository(release("1.3.1")),
            preferences = preferences,
            installedVersionName = "1.4.0"
        )

        assertEquals(UpdateCheckResult.UpToDate, checker.check(manual = true))
        with(preferences.current()) {
            assertEquals("1.4.0", latestKnownVersion)
            assertNull(latestInfoJson)
            assertNull(downloadedVersion)
            assertNull(downloadedPath)
            assertNull(dismissedVersion)
            assertNull(notifiedVersion)
            assertNull(readVersion)
        }
    }

    @Test
    fun `install handoff produces Installing and permission cancellation restores download`() = runTest {
        val preferences = newPreferences("install-state")
        val release = release("1.3.1")
        val repository = MutableRepository(release)
        val seedingManager = newManager(preferences, repository, installedVersion = "1.3.0")
        seedingManager.checkManually()
        advanceUntilIdle()

        val apk = fakeDownloadedApk("install-state.apk")
        preferences.recordDownload(release.versionName, apk.absolutePath)

        val manager = newManager(preferences, repository, installedVersion = "1.3.0")
        manager.restore()
        advanceUntilIdle()
        assertTrue(manager.state.value is UpdateState.Downloaded)

        manager.beginInstall()
        assertEquals(UpdateState.Installing, manager.state.value)

        manager.cancelInstall()
        assertTrue(manager.state.value is UpdateState.Downloaded)
    }

    @Test
    fun `newer known release discards old APK and forces a fresh check`() = runTest {
        val preferences = newPreferences("obsolete-download")
        val repository = MutableRepository(release("1.3.1"))
        val seedingManager = newManager(preferences, repository, installedVersion = "1.3.0")
        seedingManager.checkManually()
        advanceUntilIdle()

        val apk = fakeDownloadedApk("obsolete-download.apk")
        preferences.recordDownload("1.3.1", apk.absolutePath)
        // Simulate process death between UpdateChecker.recordCheck() and UpdateManager.persistInfo().
        preferences.recordCheck("1.3.2", failed = false)
        repository.latest = release("1.3.2")

        val manager = newManager(preferences, repository, installedVersion = "1.3.0")
        manager.restore()
        advanceUntilIdle()

        val state = manager.state.value as UpdateState.UpdateAvailable
        assertEquals("1.3.2", state.info.versionName)
        assertFalse(apk.exists())
        assertTrue(repository.fetchCount > 1)
        with(preferences.current()) {
            assertEquals("1.3.2", latestKnownVersion)
            assertNull(downloadedVersion)
            assertNull(downloadedPath)
            assertTrue(latestInfoJson.orEmpty().contains("1.3.2"))
        }
    }

    private fun TestScope.newPreferences(name: String): UpdatePreferences {
        val file = File(temporaryFolder.root, "$name.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        return UpdatePreferences(dataStore = dataStore, now = { 1_000_000L })
    }

    private fun TestScope.newManager(
        preferences: UpdatePreferences,
        repository: UpdateRepository,
        installedVersion: String
    ): UpdateManager {
        val checker = UpdateChecker(
            repository = repository,
            preferences = preferences,
            installedVersionName = installedVersion,
            isOnline = { true },
            now = { 1_000_000L }
        )
        return UpdateManager(
            context = context,
            checker = checker,
            preferences = preferences,
            downloadManager = UpdateDownloadManager(context),
            scope = this,
            installedVersionName = installedVersion
        )
    }

    private fun fakeDownloadedApk(name: String): File =
        File(UpdateDownloadManager(context).updatesDir(), name).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }

    private fun release(version: String) = UpdateInfo(
        versionName = version,
        tagName = "v$version",
        title = "FocusShield $version",
        apkUrl = "https://example.com/FocusShield-$version.apk",
        apkAssetName = "FocusShield-$version.apk"
    )

    private class MutableRepository(var latest: UpdateInfo?) : UpdateRepository {
        var fetchCount: Int = 0

        override suspend fun fetchLatestRelease(): UpdateInfo? {
            fetchCount += 1
            return latest
        }
    }
}
