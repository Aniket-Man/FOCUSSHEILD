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

    suspend fun initializeDefaultSubjectsIfEmpty() {
        if (subjectDao.getSubjectCount() == 0) {
            val subjects = listOf(
                SubjectEntity("physics", "Physics", "#7C3AED", "physics"),
                SubjectEntity("chemistry", "Chemistry", "#F59E0B", "chemistry"),
                SubjectEntity("mathematics", "Mathematics", "#22C55E", "mathematics"),
                SubjectEntity("biology", "Biology", "#38BDF8", "biology")
            )
            subjectDao.insertAll(subjects)
            subjects.forEach { subject ->
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_SUBJECTS,
                    subject.id,
                    CloudJson.subjectToJson(subject).toString()
                )
            }

            val topics = listOf(
                TopicEntity(UUID.randomUUID().toString(), "physics", "Electrostatics"),
                TopicEntity(UUID.randomUUID().toString(), "physics", "Current Electricity"),
                TopicEntity(UUID.randomUUID().toString(), "physics", "Magnetism"),
                TopicEntity(UUID.randomUUID().toString(), "chemistry", "Chemical Bonding"),
                TopicEntity(UUID.randomUUID().toString(), "chemistry", "Thermodynamics"),
                TopicEntity(UUID.randomUUID().toString(), "mathematics", "Calculus"),
                TopicEntity(UUID.randomUUID().toString(), "mathematics", "Algebra")
            )
            topicDao.insertAll(topics)
            topics.forEach { topic ->
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_TOPICS,
                    topic.id,
                    CloudJson.topicToJson(topic).toString()
                )
            }
        }
    }
}
