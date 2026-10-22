package com.example.data.repository

import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.TopicDao
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SubjectRepository(
    private val subjectDao: SubjectDao,
    private val topicDao: TopicDao
) {
    val activeSubjects: Flow<List<SubjectEntity>> = subjectDao.getAllActiveSubjectsFlow()

    fun getTopicsForSubject(subjectId: String): Flow<List<TopicEntity>> {
        return topicDao.getTopicsForSubjectFlow(subjectId)
    }

    suspend fun addSubject(name: String, colorHex: String = "#7C3AED"): SubjectEntity {
        val subject = SubjectEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            colorHex = colorHex
        )
        subjectDao.insertSubject(subject)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_SUBJECTS,
            subject.id,
            CloudJson.subjectToJson(subject).toString()
        )
        return subject
    }

    suspend fun addTopic(subjectId: String, name: String): TopicEntity {
        val topic = TopicEntity(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            name = name
        )
        topicDao.insertTopic(topic)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_TOPICS,
            topic.id,
            CloudJson.topicToJson(topic).toString()
        )
        return topic
    }

    suspend fun renameSubject(subject: SubjectEntity, newName: String): SubjectEntity {
        val trimmed = newName.trim()
        val updated = subject.copy(name = trimmed)
        subjectDao.updateSubject(updated)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_SUBJECTS,
            updated.id,
            CloudJson.subjectToJson(updated).toString()
        )
        return updated
    }

    suspend fun deleteSubject(subjectId: String) {
        subjectDao.deleteSubjectById(subjectId)
        topicDao.deleteTopicsForSubject(subjectId)
        SyncTracker.enqueueDelete(SyncTables.STUDY_SUBJECTS, subjectId)
    }

    suspend fun initializeDefaultSubjectsIfEmpty() {
        // Remove legacy preloaded subjects ("physics", "chemistry", "mathematics", "biology")
        // so that only user-created tags appear.
        val legacySeededIds = listOf("physics", "chemistry", "mathematics", "biology")
        legacySeededIds.forEach { legacyId ->
            val existing = subjectDao.getSubjectById(legacyId)
            if (existing != null) {
                subjectDao.deleteSubjectById(legacyId)
                topicDao.deleteTopicsForSubject(legacyId)
                SyncTracker.enqueueDelete(SyncTables.STUDY_SUBJECTS, legacyId)
            }
        }
    }
}
