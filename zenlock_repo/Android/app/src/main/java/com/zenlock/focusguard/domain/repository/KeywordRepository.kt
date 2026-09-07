package com.zenlock.focusguard.domain.repository

import com.zenlock.focusguard.data.db.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

interface KeywordRepository {
    fun getAllKeywords(): Flow<List<KeywordEntity>>
    suspend fun getAllowKeywords(): List<KeywordEntity>
    suspend fun getBlockKeywords(): List<KeywordEntity>
    suspend fun getChannelKeywords(): List<KeywordEntity>
    suspend fun addKeyword(keyword: KeywordEntity)
    suspend fun addKeywords(keywords: List<KeywordEntity>)
    suspend fun updateKeyword(keyword: KeywordEntity)
    suspend fun deleteKeyword(keyword: KeywordEntity)
    suspend fun getKeywordCount(): Int
    suspend fun seedDefaultKeywords()
}
