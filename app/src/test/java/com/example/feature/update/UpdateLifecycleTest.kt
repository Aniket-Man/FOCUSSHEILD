package com.example.feature.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateLifecycleTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `installed cached offer is completely cleared during restore`() = runTest {
        val preferences = newPreferences()
        val release = release("1.3.1")
        val repository = MutableRepository(release)

        val oldManager = newManager(preferences, repository, installedVersion = "1.3.0")
        oldManager.checkManually().join()
        assertTrue(oldManager.showDot.value)

        preferences.markDismissed(release.versionName)
        preferences.markNotified(release.versionName)
        preferences.markRead(release.versionName)
        val apk = fakeDownloadedApk("installed-restore.apk")
        preferences.recordDownload(release.versionName, apk.absolutePath)

        val restartedManager = newManager(preferences, repository, installedVersion = "1.3.1")
        restartedManager.restore().join()

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
        val preferences = newPreferences()
        val release = release("1.3.1")
        val repository = MutableRepository(release)

        newManager(preferences, repository, installedVersion = "1.3.0").also {
            it.checkManually().join()
        }

        val currentBuildManager = newManager(preferences, repository, installedVersion = "1.3.1")
        currentBuildManager.refreshFromCache().join()

        assertEquals(UpdateState.UpToDate, currentBuildManager.state.value)
        assertFalse(currentBuildManager.showDot.value)
        assertNull(preferences.current().latestInfoJson)
    }

    @Test
    fun `checker clears against installed version when local build is ahead`() = runTest {
        val preferences = newPreferences()
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
        val preferences = newPreferences()
        val release = release("1.3.1")
        val repository = MutableRepository(release)
        val seedingManager = newManager(preferences, repository, installedVersion = "1.3.0")
        seedingManager.checkManually().join()

        val apk = fakeDownloadedApk("install-state.apk")
        preferences.recordDownload(release.versionName, apk.absolutePath)

        val manager = newManager(preferences, repository, installedVersion = "1.3.0")
        manager.restore().join()
        assertTrue(manager.state.value is UpdateState.Downloaded)

        manager.beginInstall()
        assertEquals(UpdateState.Installing, manager.state.value)

        manager.cancelInstall()
        assertTrue(manager.state.value is UpdateState.Downloaded)
    }

    @Test
    fun `newer known release discards old APK and forces a fresh check`() = runTest {
        val preferences = newPreferences()
        val repository = MutableRepository(release("1.3.1"))
        val seedingManager = newManager(preferences, repository, installedVersion = "1.3.0")
        seedingManager.checkManually().join()

        val apk = fakeDownloadedApk("obsolete-download.apk")
        preferences.recordDownload("1.3.1", apk.absolutePath)
        // Simulate process death between UpdateChecker.recordCheck() and UpdateManager.persistInfo().
        preferences.recordCheck("1.3.2", failed = false)
        repository.latest = release("1.3.2")

        val manager = newManager(preferences, repository, installedVersion = "1.3.0")
        manager.restore().join()

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

    private fun TestScope.newPreferences(): UpdatePreferences =
        UpdatePreferences(dataStore = FakeDataStore(), now = { 1_000_000L })

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

/**
 * In-memory [DataStore] for these tests.
 *
 * The production store runs every read and write through an actor coroutine on its own scope. A store
 * created with `PreferenceDataStoreFactory.create(scope = backgroundScope)` is not driven to
 * completion by `advanceUntilIdle()`, so a manager coroutine awaiting a preference read never resumes
 * and each assertion observes the initial state instead of the result under test — the assertions,
 * not the production code, were what failed. Backing the tests with a synchronous flow removes that
 * class of flakiness while still exercising the real [UpdatePreferences] key handling.
 */
private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = transform(state.value).also { state.value = it }
}
