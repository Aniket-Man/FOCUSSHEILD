package com.example.feature.update.data

import com.example.feature.update.domain.DownloadProgress
import com.example.feature.update.domain.UpdateInfo
import java.io.File

/**
 * Primary [UpdateRepository] for FocusShield.
 *
 * Routes update checks and release history queries to the configured external Firebase project
 * ([FirebaseReleaseRepository]) whenever an [ExternalFirebaseUpdateConfig] is present (from `.env`
 * Secrets or an in-app pasted Connection Token), and falls back cleanly to
 * [GitHubReleaseRepository] when no external Firebase update server is configured.
 */
class CompositeUpdateRepository(
    val gitHubRepository: GitHubReleaseRepository,
    private val runtimeConfigProvider: () -> ExternalFirebaseUpdateConfig?
) : UpdateRepository {

    private val buildConfigFirebase: ExternalFirebaseUpdateConfig? =
        ExternalFirebaseUpdateConfig.fromBuildConfig()

    val firebaseRepository: FirebaseReleaseRepository = FirebaseReleaseRepository(
        configProvider = ::activeFirebaseConfig
    )

    /**
     * Returns the currently active external Firebase configuration, giving precedence to a runtime
     * token pasted inside the app, followed by compile-time `.env` / `BuildConfig` secrets.
     */
    fun activeFirebaseConfig(): ExternalFirebaseUpdateConfig? =
        runtimeConfigProvider()?.takeIf { it.isValid } ?: buildConfigFirebase?.takeIf { it.isValid }

    val isUsingExternalFirebase: Boolean
        get() = activeFirebaseConfig() != null

    override suspend fun fetchLatestRelease(): UpdateInfo? {
        return if (isUsingExternalFirebase) {
            firebaseRepository.fetchLatestRelease()
        } else {
            gitHubRepository.fetchLatestRelease()
        }
    }

    override suspend fun fetchLatestReleaseConditional(etag: String?): ReleaseFetchResponse {
        return if (isUsingExternalFirebase) {
            firebaseRepository.fetchLatestReleaseConditional(etag)
        } else {
            gitHubRepository.fetchLatestReleaseConditional(etag)
        }
    }

    /**
     * Fetches the full list of published releases (newest first).
     * When connected to an external Firebase project, returns all releases stored in Firestore.
     * When using the fallback repository, returns a single-element list if a latest release exists.
     */
    override suspend fun fetchReleaseHistory(): List<UpdateInfo> {
        return if (isUsingExternalFirebase) {
            firebaseRepository.fetchReleaseHistory()
        } else {
            listOfNotNull(gitHubRepository.fetchLatestRelease())
        }
    }

    suspend fun downloadChunkedApk(
        chunkedUri: String,
        targetPartialFile: File,
        onProgress: (DownloadProgress) -> Unit
    ) {
        firebaseRepository.downloadChunkedApk(chunkedUri, targetPartialFile, onProgress)
    }

    /** Dynamic set of allowed APK download hosts combining GitHub/feed hosts and Firebase Storage hosts. */
    fun apkHostAllowList(): Set<String> = buildSet {
        addAll(gitHubRepository.apkHostAllowList())
        activeFirebaseConfig()?.apkHostAllowList()?.let(::addAll)
    }
}
