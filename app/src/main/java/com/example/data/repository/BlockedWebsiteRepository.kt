package com.example.data.repository

import com.example.data.local.dao.BlockedWebsiteDao
import com.example.data.local.entity.BlockedWebsiteEntity
import kotlinx.coroutines.flow.Flow

class BlockedWebsiteRepository(private val dao: BlockedWebsiteDao) {

    val allBlockedWebsites: Flow<List<BlockedWebsiteEntity>> = dao.getAllBlockedWebsites()
    val activeBlockedWebsites: Flow<List<BlockedWebsiteEntity>> = dao.getActiveBlockedWebsites()

    suspend fun getActiveBlockedWebsitesSync(): List<BlockedWebsiteEntity> {
        return dao.getActiveBlockedWebsitesSync()
    }

    suspend fun addWebsite(rawUrlOrDomain: String, category: String = "MANUAL"): Boolean {
        val cleanDomain = normalizeDomain(rawUrlOrDomain)
        if (cleanDomain.isBlank()) return false
        val entity = BlockedWebsiteEntity(
            domain = cleanDomain,
            isEnabled = true,
            category = category,
            createdAt = System.currentTimeMillis()
        )
        dao.insertWebsite(entity)
        return true
    }

    suspend fun removeWebsite(domain: String) {
        dao.deleteWebsite(domain)
    }

    suspend fun toggleWebsite(domain: String, isEnabled: Boolean) {
        dao.updateEnabled(domain, isEnabled)
    }

    companion object {
        fun normalizeDomain(input: String): String {
            var domain = input.trim().lowercase()
            if (domain.isBlank()) return ""
            // Strip protocol
            if (domain.startsWith("https://")) {
                domain = domain.substring(8)
            } else if (domain.startsWith("http://")) {
                domain = domain.substring(7)
            }
            // Strip www.
            if (domain.startsWith("www.")) {
                domain = domain.substring(4)
            }
            // Strip path, query, hash
            val slashIdx = domain.indexOf('/')
            if (slashIdx != -1) {
                domain = domain.substring(0, slashIdx)
            }
            val queryIdx = domain.indexOf('?')
            if (queryIdx != -1) {
                domain = domain.substring(0, queryIdx)
            }
            val portIdx = domain.indexOf(':')
            if (portIdx != -1) {
                domain = domain.substring(0, portIdx)
            }
            return domain.trim()
        }
    }
}
