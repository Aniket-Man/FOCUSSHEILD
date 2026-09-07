package com.example.data.repository

import com.example.data.local.dao.KeywordDao
import com.example.data.local.entity.KeywordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Repository for YouTube content-filter keyword rules.
 *
 * Maintains in-memory allow/block keyword caches refreshed reactively from
 * Room so the accessibility event path can read them synchronously without
 * any disk access.
 */
class KeywordRepository(
    private val dao: KeywordDao,
    private val scope: CoroutineScope
) {

    @Volatile
    private var cachedAllowKeywords: List<String> = emptyList()

    @Volatile
    private var cachedBlockKeywords: List<String> = emptyList()

    init {
        scope.launch {
            try {
                dao.getAllKeywordsFlow().collect { keywords ->
                    cachedAllowKeywords = keywords
                        .filter { it.isActive && it.type == TYPE_ALLOW }
                        .map { it.keyword.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                    cachedBlockKeywords = keywords
                        .filter { it.isActive && it.type == TYPE_BLOCK }
                        .map { it.keyword.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getAllowKeywordsSync(): List<String> = cachedAllowKeywords

    fun getBlockKeywordsSync(): List<String> = cachedBlockKeywords

    suspend fun addAllowKeyword(keyword: String) {
        val cleaned = keyword.trim().lowercase()
        if (cleaned.isEmpty()) return
        dao.insertKeyword(KeywordEntity(keyword = cleaned, type = TYPE_ALLOW))
    }

    suspend fun addBlockKeyword(keyword: String) {
        val cleaned = keyword.trim().lowercase()
        if (cleaned.isEmpty()) return
        dao.insertKeyword(KeywordEntity(keyword = cleaned, type = TYPE_BLOCK))
    }

    suspend fun deleteKeywordById(id: Long) {
        dao.deleteKeywordById(id)
    }

    suspend fun initializeDefaultKeywordsIfEmpty() {
        try {
            if (dao.getKeywordCount() > 0) return
            dao.insertKeywords(
                listOf(
                    KeywordEntity(keyword = "lecture", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "tutorial", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "physics", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "chemistry", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "mathematics", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "biology", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "jee", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "neet", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "education", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "explained", type = TYPE_ALLOW),
                    KeywordEntity(keyword = "funny", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "prank", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "comedy", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "roast", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "vlog", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "gossip", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "entertainment", type = TYPE_BLOCK),
                    KeywordEntity(keyword = "challenge", type = TYPE_BLOCK)
                )
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TYPE_ALLOW = "allow"
        const val TYPE_BLOCK = "block"
    }
}
