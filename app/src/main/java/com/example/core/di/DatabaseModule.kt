package com.example.core.di

import android.content.Context
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.dao.AppLimitDao
import com.example.data.local.dao.AppLimitSessionDao
import com.example.data.local.dao.BlockedAppDao
import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.dao.BlockedWebsiteDao
import com.example.data.local.dao.BreakRecordDao
import com.example.data.local.dao.CloudBulkDao
import com.example.data.local.dao.DailyAppUsageDao
import com.example.data.local.dao.DailyUnlockDao
import com.example.data.local.dao.FocusScheduleDao
import com.example.data.local.dao.KeywordDao
import com.example.data.local.dao.ScratchCardDao
import com.example.data.local.dao.SessionDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.dao.StudyChannelDao
import com.example.data.local.dao.StudyPlanDao
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.dao.TopicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusShieldDatabase =
        FocusShieldDatabase.getInstance(context)

    @Provides fun provideSessionDao(db: FocusShieldDatabase): SessionDao = db.sessionDao()
    @Provides fun provideSubjectDao(db: FocusShieldDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideTopicDao(db: FocusShieldDatabase): TopicDao = db.topicDao()
    @Provides fun provideStudyPlanDao(db: FocusShieldDatabase): StudyPlanDao = db.studyPlanDao()
    @Provides fun provideBlockedAppDao(db: FocusShieldDatabase): BlockedAppDao = db.blockedAppDao()
    @Provides fun provideBlockedAttemptDao(db: FocusShieldDatabase): BlockedAttemptDao = db.blockedAttemptDao()
    @Provides fun provideBreakRecordDao(db: FocusShieldDatabase): BreakRecordDao = db.breakRecordDao()
    @Provides fun provideStudyChannelDao(db: FocusShieldDatabase): StudyChannelDao = db.studyChannelDao()
    @Provides fun provideStudyActivityDao(db: FocusShieldDatabase): StudyActivityDao = db.studyActivityDao()
    @Provides fun provideAppLimitDao(db: FocusShieldDatabase): AppLimitDao = db.appLimitDao()
    @Provides fun provideDailyAppUsageDao(db: FocusShieldDatabase): DailyAppUsageDao = db.dailyAppUsageDao()
    @Provides fun provideAppLimitSessionDao(db: FocusShieldDatabase): AppLimitSessionDao = db.appLimitSessionDao()
    @Provides fun provideBlockedWebsiteDao(db: FocusShieldDatabase): BlockedWebsiteDao = db.blockedWebsiteDao()
    @Provides fun provideFocusScheduleDao(db: FocusShieldDatabase): FocusScheduleDao = db.focusScheduleDao()
    @Provides fun provideKeywordDao(db: FocusShieldDatabase): KeywordDao = db.keywordDao()
    @Provides fun provideDailyUnlockDao(db: FocusShieldDatabase): DailyUnlockDao = db.dailyUnlockDao()
    @Provides fun provideScratchCardDao(db: FocusShieldDatabase): ScratchCardDao = db.scratchCardDao()
    @Provides fun provideSyncOutboxDao(db: FocusShieldDatabase): SyncOutboxDao = db.syncOutboxDao()
    @Provides fun provideCloudBulkDao(db: FocusShieldDatabase): CloudBulkDao = db.cloudBulkDao()
}
