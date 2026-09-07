package com.zenlock.focusguard.data.repository

import com.zenlock.focusguard.data.db.dao.KeywordDao
import com.zenlock.focusguard.data.db.entity.KeywordEntity
import com.zenlock.focusguard.domain.repository.KeywordRepository
import kotlinx.coroutines.flow.Flow

class KeywordRepositoryImpl(
    private val dao: KeywordDao
) : KeywordRepository {

    override fun getAllKeywords(): Flow<List<KeywordEntity>> =
        dao.getAllKeywords()

    override suspend fun getAllowKeywords(): List<KeywordEntity> =
        dao.getAllowKeywords()

    override suspend fun getBlockKeywords(): List<KeywordEntity> =
        dao.getBlockKeywords()

    override suspend fun getChannelKeywords(): List<KeywordEntity> =
        dao.getChannelKeywords()

    override suspend fun addKeyword(keyword: KeywordEntity) =
        dao.insertKeyword(keyword)

    override suspend fun addKeywords(keywords: List<KeywordEntity>) =
        dao.insertKeywords(keywords)

    override suspend fun updateKeyword(keyword: KeywordEntity) =
        dao.updateKeyword(keyword)

    override suspend fun deleteKeyword(keyword: KeywordEntity) =
        dao.deleteKeyword(keyword)

    override suspend fun getKeywordCount(): Int =
        dao.getKeywordCount()

    override suspend fun seedDefaultKeywords() {
        if (dao.getKeywordCount() > 0) return

        val defaults = listOf(
            // Allow keywords - educational content
            KeywordEntity(keyword = "tutorial", type = "allow"),
            KeywordEntity(keyword = "lecture", type = "allow"),
            KeywordEntity(keyword = "course", type = "allow"),
            KeywordEntity(keyword = "programming", type = "allow"),
            KeywordEntity(keyword = "class", type = "allow"),
            KeywordEntity(keyword = "education", type = "allow"),
            KeywordEntity(keyword = "lesson", type = "allow"),
            KeywordEntity(keyword = "learn", type = "allow"),
            KeywordEntity(keyword = "study", type = "allow"),
            KeywordEntity(keyword = "explained", type = "allow"),
            KeywordEntity(keyword = "how to", type = "allow"),
            KeywordEntity(keyword = "guide", type = "allow"),
            // Block keywords - distracting content
            KeywordEntity(keyword = "prank", type = "block"),
            KeywordEntity(keyword = "vlog", type = "block"),
            KeywordEntity(keyword = "edit", type = "block"),
            KeywordEntity(keyword = "status", type = "block"),
            KeywordEntity(keyword = "shorts", type = "block"),
            KeywordEntity(keyword = "funny", type = "block"),
            KeywordEntity(keyword = "meme", type = "block"),
            KeywordEntity(keyword = "tiktok", type = "block"),
            KeywordEntity(keyword = "challenge", type = "block"),
            KeywordEntity(keyword = "reaction", type = "block"),
            KeywordEntity(keyword = "drama", type = "block"),
            KeywordEntity(keyword = "gossip", type = "block"),
        )
        dao.insertKeywords(defaults)
    }
}
