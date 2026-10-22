package com.example.feature.update.data

import android.util.Base64
import com.example.feature.update.domain.DownloadProgress
import com.example.feature.update.domain.SemanticVersion
import com.example.feature.update.domain.UpdateChannel
import com.example.feature.update.domain.UpdateInfo
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches FocusShield releases and past release history from the dedicated release server
 * (`releases` collection) and supports both HTTPS APK links and chunked binary packages
 * (`release_binaries/{releaseId}/chunks`).
 */
class FirebaseReleaseRepository(
    private val configProvider: () -> ExternalFirebaseUpdateConfig?,
    private val client: OkHttpClient = defaultClient()
) : UpdateRepository {

    override suspend fun fetchLatestRelease(): UpdateInfo? =
        fetchReleaseHistory().firstOrNull()

    override suspend fun fetchLatestReleaseConditional(etag: String?): ReleaseFetchResponse {
        val activeChannel = UpdateChannel.normalizeKey(configProvider()?.normalizedChannel)
        val latest = fetchLatestRelease()
        val syntheticEtag = latest?.let {
            "$activeChannel:${it.versionName}-${it.versionCode ?: 0}-${it.publishedAt.orEmpty()}"
        }
        return if (etag != null && syntheticEtag != null && etag == syntheticEtag) {
            ReleaseFetchResponse(info = latest, etag = syntheticEtag, notModified = true)
        } else {
            ReleaseFetchResponse(info = latest, etag = syntheticEtag, notModified = false)
        }
    }

    /**
     * Fetches all published releases for the currently selected update channel (`production`,
     * `beta`, `prerelease`, `alpha`, or `internal`), sorted newest-first (`versionCode` and
     * semantic `versionName` descending).
     */
    override suspend fun fetchReleaseHistory(): List<UpdateInfo> = withContext(Dispatchers.IO) {
        val config = configProvider()
            ?: throw UpdateSourceUnavailableException(
                "Update server is not configured yet."
            )
        if (!config.isValid) {
            throw UpdateSourceUnavailableException(
                "Update server configuration is invalid."
            )
        }

        val url = buildFirestoreCollectionUrl(config, "releases", pageSize = 50)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "FocusShield-Android")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateSourceUnavailableException(
                "Couldn't reach the update server. Check your internet connection."
            )
        }

        response.use { resp ->
            val bodyString = resp.body?.string().orEmpty()
            if (resp.code == 404) {
                return@withContext emptyList()
            }
            if (resp.code == 403 || resp.code == 401) {
                throw UpdateSourceUnavailableException(
                    "Update server denied read access (HTTP ${resp.code})."
                )
            }
            if (!resp.isSuccessful) {
                throw UpdateSourceUnavailableException(
                    "Update server returned HTTP ${resp.code}."
                )
            }

            parseFirestoreReleasesList(bodyString, config)
        }
    }

    /**
     * Reassembles a chunked APK stored on the release server under
     * `release_binaries/{releaseId}/chunks`.
     */
    suspend fun downloadChunkedApk(
        chunkedUri: String,
        targetPartialFile: File,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val config = configProvider()
            ?: throw UpdateDownloadException("Update server is not configured.")
        val stripped = chunkedUri.removePrefix(UpdateUrlPolicy.FIREBASE_CHUNKED_SCHEME).trim('/')
        val parts = stripped.split('/')
        if (parts.size < 2) {
            throw UpdateDownloadException("Malformed update package descriptor.")
        }
        val releaseId = parts.drop(1).joinToString("/")

        data class ApkChunk(val index: Int, val base64Data: String, val totalSizeBytes: Long)

        val chunks = mutableListOf<ApkChunk>()
        var pageToken: String? = null

        do {
            currentCoroutineContext().ensureActive()
            val baseUrl = buildFirestoreCollectionUrl(
                config = config,
                collectionPath = "release_binaries/$releaseId/chunks",
                pageSize = 200,
                pageToken = pageToken
            )
            val req = Request.Builder()
                .url(baseUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "FocusShield-Android")
                .get()
                .build()

            val resp = try {
                client.newCall(req).execute()
            } catch (e: IOException) {
                throw UpdateDownloadException("Failed while downloading update package from server.")
            }

            resp.use { r ->
                if (!r.isSuccessful) {
                    throw UpdateDownloadException("Update package download failed (HTTP ${r.code}).")
                }
                val json = try {
                    JSONObject(r.body?.string().orEmpty())
                } catch (_: Exception) {
                    throw UpdateDownloadException("Unreadable update package payload from server.")
                }
                val docs = json.optJSONArray("documents") ?: JSONArray()
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    val fields = doc.optJSONObject("fields") ?: continue
                    val docName = doc.optString("name")
                    val fallbackIndex = docName.substringAfterLast('/').removePrefix("chunk_").toIntOrNull() ?: i
                    val idx = readIntField(fields, "index")
                        ?: readIntField(fields, "chunkIndex")
                        ?: fallbackIndex
                    val b64 = readStringField(fields, "data")
                        .ifBlank { readStringField(fields, "base64") }
                        .ifBlank { readStringField(fields, "bytes") }
                    val totalSize = readLongField(fields, "totalSizeBytes")
                        ?: readLongField(fields, "apkSizeBytes")
                        ?: -1L
                    if (b64.isNotBlank()) {
                        chunks += ApkChunk(index = idx, base64Data = b64, totalSizeBytes = totalSize)
                    }
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            }
        } while (!pageToken.isNullOrBlank())

        if (chunks.isEmpty()) {
            throw UpdateDownloadException("No APK binary chunks were found on the update server for this release.")
        }

        val ordered = chunks.sortedBy { it.index }
        val declaredTotalBytes = ordered.firstOrNull { it.totalSizeBytes > 0 }?.totalSizeBytes ?: -1L
        var writtenBytes = 0L

        BufferedOutputStream(targetPartialFile.outputStream()).use { output ->
            ordered.forEachIndexed { idx, chunk ->
                currentCoroutineContext().ensureActive()
                val decoded = decodeBase64Chunk(chunk.base64Data)
                    ?: throw UpdateDownloadException("Corrupted APK chunk #${chunk.index} from update server.")
                output.write(decoded)
                writtenBytes += decoded.size
                val percent = if (declaredTotalBytes > 0) {
                    ((writtenBytes * 100L) / declaredTotalBytes).toInt().coerceIn(0, 99)
                } else {
                    (((idx + 1) * 99) / ordered.size).coerceIn(1, 99)
                }
                onProgress(
                    DownloadProgress(
                        percent = percent,
                        bytesReceived = writtenBytes,
                        totalBytes = if (declaredTotalBytes > 0) declaredTotalBytes else writtenBytes
                    )
                )
            }
            output.flush()
        }
    }

    internal fun parseFirestoreReleasesList(
        rawBody: String,
        config: ExternalFirebaseUpdateConfig
    ): List<UpdateInfo> {
        val root = try {
            JSONObject(rawBody)
        } catch (_: Exception) {
            throw UpdateSourceUnavailableException("Update server returned invalid JSON.")
        }

        val docs = root.optJSONArray("documents") ?: return emptyList()
        val targetChannel = UpdateChannel.normalizeKey(config.normalizedChannel)
        val parsed = mutableListOf<UpdateInfo>()

        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val info = parseFirestoreReleaseDoc(doc, config, targetChannel) ?: continue
            parsed += info
        }

        return parsed.sortedWith { a, b ->
            val codeA = a.versionCode
            val codeB = b.versionCode
            if (codeA != null && codeB != null && codeA != codeB) {
                codeB.compareTo(codeA)
            } else if (SemanticVersion.isNewer(a.versionName, b.versionName)) {
                -1
            } else if (SemanticVersion.isNewer(b.versionName, a.versionName)) {
                1
            } else {
                (b.publishedAt ?: "").compareTo(a.publishedAt ?: "")
            }
        }
    }

    private fun parseFirestoreReleaseDoc(
        doc: JSONObject,
        config: ExternalFirebaseUpdateConfig,
        targetChannel: String
    ): UpdateInfo? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docPath = doc.optString("name")
        val releaseId = docPath.substringAfterLast('/').ifBlank { "latest" }

        val rawDocChannel = readStringField(fields, "channel")
            .ifBlank { readStringField(fields, "track") }
            .ifBlank { readStringField(fields, "releaseChannel") }
        val normalizedDocChannel = UpdateChannel.normalizeKey(rawDocChannel)
        val normalizedTargetChannel = UpdateChannel.normalizeKey(targetChannel)
        if (normalizedTargetChannel != "all" && normalizedDocChannel != normalizedTargetChannel) {
            return null
        }

        val rawVersion = readStringField(fields, "versionName")
            .ifBlank { readStringField(fields, "version") }
            .ifBlank { readStringField(fields, "tagName") }
        val versionName = SemanticVersion.normalize(rawVersion)
        if (versionName.isBlank()) return null

        val versionCode = readIntField(fields, "versionCode")?.takeIf { it > 0 }
        val tagName = readStringField(fields, "tagName").ifBlank { "v$versionName" }
        val title = readStringField(fields, "title")
            .ifBlank { "FocusShield v$versionName" }

        val whatChanged = readStringField(fields, "whatChanged")
        val whatImproved = readStringField(fields, "whatImproved")
        val bugFixes = readStringField(fields, "bugFixes")
        val explicitNotes = readStringField(fields, "releaseNotes")
            .ifBlank { readStringField(fields, "notes") }
            .ifBlank { readStringField(fields, "body") }

        val combinedReleaseNotes = buildStructuredReleaseNotes(
            explicitNotes = explicitNotes,
            whatChanged = whatChanged,
            whatImproved = whatImproved,
            bugFixes = bugFixes
        )

        val storageMode = readStringField(fields, "storageMode")
        val chunkCount = readIntField(fields, "chunkCount") ?: 0
        val rawApkUrl = readStringField(fields, "apkUrl")
            .ifBlank { readStringField(fields, "downloadUrl") }
        val apkAssetName = readStringField(fields, "apkAssetName")
            .ifBlank { "FocusShield-v$versionName.apk" }

        val resolvedApkUrl = when {
            rawApkUrl.startsWith(UpdateUrlPolicy.FIREBASE_CHUNKED_SCHEME, ignoreCase = true) -> rawApkUrl
            storageMode.equals("firestore_chunks", ignoreCase = true) ||
                (rawApkUrl.isBlank() && chunkCount > 0) -> {
                "${UpdateUrlPolicy.FIREBASE_CHUNKED_SCHEME}${config.projectId}/$releaseId"
            }
            rawApkUrl.isNotBlank() &&
                UpdateUrlPolicy.isAllowedApkUrl(rawApkUrl, config.apkHostAllowList()) &&
                UpdateUrlPolicy.isApkResource(rawApkUrl, apkAssetName) -> rawApkUrl
            else -> null
        }

        val publishedAt = readStringField(fields, "publishedAt")
            .ifBlank { doc.optString("createTime") }
            .takeIf { it.isNotBlank() }

        return UpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            tagName = tagName,
            title = title,
            releaseNotes = combinedReleaseNotes,
            apkUrl = resolvedApkUrl,
            apkAssetName = apkAssetName,
            releasePageUrl = readStringField(fields, "releasePageUrl"),
            publishedAt = publishedAt
        )
    }

    private fun buildStructuredReleaseNotes(
        explicitNotes: String,
        whatChanged: String,
        whatImproved: String,
        bugFixes: String
    ): String {
        if (explicitNotes.isNotBlank() && whatChanged.isBlank() && whatImproved.isBlank() && bugFixes.isBlank()) {
            return explicitNotes.trim()
        }
        if (explicitNotes.contains("### ") && (explicitNotes.contains(whatChanged.take(15).ifBlank { "\u0000" }))) {
            return explicitNotes.trim()
        }
        return buildString {
            if (whatChanged.isNotBlank()) {
                appendLine("### What Changed")
                whatChanged.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    appendLine(if (line.startsWith("-") || line.startsWith("*")) line else "- $line")
                }
                appendLine()
            }
            if (whatImproved.isNotBlank()) {
                appendLine("### What Improved")
                whatImproved.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    appendLine(if (line.startsWith("-") || line.startsWith("*")) line else "- $line")
                }
                appendLine()
            }
            if (bugFixes.isNotBlank()) {
                appendLine("### Fixes & Update Notes")
                bugFixes.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    appendLine(if (line.startsWith("-") || line.startsWith("*")) line else "- $line")
                }
                appendLine()
            }
            if (isEmpty() && explicitNotes.isNotBlank()) {
                append(explicitNotes.trim())
            }
        }.trim()
    }

    private fun buildFirestoreCollectionUrl(
        config: ExternalFirebaseUpdateConfig,
        collectionPath: String,
        pageSize: Int = 50,
        pageToken: String? = null
    ): String {
        val encodedProject = URLEncoder.encode(config.projectId.trim(), "UTF-8")
        val encodedDb = URLEncoder.encode(config.normalizedDatabaseId, "UTF-8")
        val cleanPath = collectionPath.trim('/')
        return buildString {
            append("https://firestore.googleapis.com/v1/projects/")
            append(encodedProject)
            append("/databases/")
            append(encodedDb)
            append("/documents/")
            append(cleanPath)
            append("?pageSize=")
            append(pageSize)
            if (config.apiKey.isNotBlank()) {
                append("&key=")
                append(URLEncoder.encode(config.apiKey.trim(), "UTF-8"))
            }
            if (!pageToken.isNullOrBlank()) {
                append("&pageToken=")
                append(URLEncoder.encode(pageToken, "UTF-8"))
            }
        }
    }

    private fun readStringField(fields: JSONObject, key: String): String {
        val fieldObj = fields.optJSONObject(key) ?: return ""
        return fieldObj.optString("stringValue")
            .ifBlank { fieldObj.optString("timestampValue") }
            .trim()
    }

    private fun readIntField(fields: JSONObject, key: String): Int? {
        val fieldObj = fields.optJSONObject(key) ?: return null
        if (fieldObj.has("integerValue")) {
            return fieldObj.optString("integerValue").toIntOrNull()
                ?: fieldObj.optInt("integerValue", 0)
        }
        if (fieldObj.has("doubleValue")) {
            return fieldObj.optDouble("doubleValue").toInt()
        }
        if (fieldObj.has("stringValue")) {
            return fieldObj.optString("stringValue").trim().toIntOrNull()
        }
        return null
    }

    private fun readLongField(fields: JSONObject, key: String): Long? {
        val fieldObj = fields.optJSONObject(key) ?: return null
        if (fieldObj.has("integerValue")) {
            return fieldObj.optString("integerValue").toLongOrNull()
        }
        if (fieldObj.has("doubleValue")) {
            return fieldObj.optDouble("doubleValue").toLong()
        }
        if (fieldObj.has("stringValue")) {
            return fieldObj.optString("stringValue").trim().toLongOrNull()
        }
        return null
    }

    private fun decodeBase64Chunk(rawBase64: String): ByteArray? {
        val cleaned = rawBase64.substringAfter("base64,").trim()
        return try {
            try {
                Base64.decode(cleaned, Base64.DEFAULT)
            } catch (_: Throwable) {
                java.util.Base64.getMimeDecoder().decode(cleaned)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
