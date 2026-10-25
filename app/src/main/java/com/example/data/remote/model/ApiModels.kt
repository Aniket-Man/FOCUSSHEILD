package com.example.data.remote.model

import com.squareup.moshi.Json

/**
 * DTO representing a release response from GitHub or a custom release feed.
 */
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "releaseNotes") val releaseNotes: String? = null,
    @Json(name = "versionName") val versionName: String? = null,
    @Json(name = "versionCode") val versionCode: Int? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "releasePageUrl") val releasePageUrl: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "apkUrl") val apkUrl: String? = null,
    @Json(name = "assets") val assets: List<GitHubAssetDto>? = null
)

/**
 * DTO representing an asset attached to a release.
 */
data class GitHubAssetDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "content_type") val contentType: String? = null
)

/**
 * DTO representing a motivational quote from a remote endpoint.
 */
data class QuoteResponseDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "category") val category: String? = null
)
