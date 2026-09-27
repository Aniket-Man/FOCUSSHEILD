package com.example.core.di

import android.content.Context
import com.example.FocusShieldApp
import com.example.cloud.auth.AuthRepository
import com.example.cloud.net.ConnectivityMonitor
import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.SyncEngine
import com.example.cloud.sync.SyncGateway
import com.example.data.local.dao.AppLimitDao
import com.example.data.local.dao.AppLimitSessionDao
import com.example.data.local.dao.BlockedAppDao
import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.dao.BlockedWebsiteDao
import com.example.data.local.dao.BreakRecordDao
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
import com.example.data.local.dao.TopicDao
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.AnalyticsRepository
import com.example.data.repository.AppLimitRepository
import com.example.data.repository.BlockedAppRepository
import com.example.data.repository.BlockedAttemptRepository
import com.example.data.repository.BlockedWebsiteRepository
import com.example.data.repository.BreakRepository
import com.example.data.repository.DailyUnlockRepository
import com.example.data.repository.FocusScheduleRepository
import com.example.data.repository.KeywordRepository
import com.example.data.repository.ScratchCardRepository
import com.example.data.repository.SessionRepository
import com.example.data.repository.StudyChannelRepository
import com.example.data.repository.StudyPlanRepository
import com.example.data.repository.SubjectRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // NOTE: Everything below that also exists on FocusShieldApp delegates to that started singleton.
    // FocusShieldApp.onCreate() is what actually calls start()/restoreOnStart() on the sync stack, so
    // returning a fresh instance here would give Hilt consumers a never-started copy (frozen
    // connectivity, LocalOnly auth, inert SyncGateway) — the exact bug that killed cloud saving.

    @Provides
    @Singleton
    fun providePreferencesRepository(): FocusPreferencesRepository =
        FocusShieldApp.instance.preferencesRepository

    @Provides
    @Singleton
    fun provideCloudInstallState(): CloudInstallState =
        FocusShieldApp.instance.cloudInstallState

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository =
        FocusShieldApp.instance.authRepository

    @Provides
    @Singleton
    fun provideConnectivityMonitor(): ConnectivityMonitor =
        FocusShieldApp.instance.connectivityMonitor

    @Provides
    @Singleton
    fun provideSessionRepository(sessionDao: SessionDao, studyActivityDao: StudyActivityDao): SessionRepository =
        SessionRepository(sessionDao, studyActivityDao)

    @Provides
    @Singleton
    fun provideSubjectRepository(subjectDao: SubjectDao, topicDao: TopicDao): SubjectRepository =
        SubjectRepository(subjectDao, topicDao)

    @Provides
    @Singleton
    fun provideStudyPlanRepository(studyPlanDao: StudyPlanDao): StudyPlanRepository =
        StudyPlanRepository(studyPlanDao)

    @Provides
    @Singleton
    fun provideBreakRepository(breakRecordDao: BreakRecordDao): BreakRepository =
        BreakRepository(breakRecordDao)

    @Provides
    @Singleton
    fun provideBlockedAppRepository(blockedAppDao: BlockedAppDao): BlockedAppRepository =
        BlockedAppRepository(blockedAppDao)

    @Provides
    @Singleton
    fun provideBlockedAttemptRepository(
        blockedAttemptDao: BlockedAttemptDao,
        cloudInstallState: CloudInstallState
    ): BlockedAttemptRepository = BlockedAttemptRepository(blockedAttemptDao, cloudInstallState)

    @Provides
    @Singleton
    fun provideStudyChannelRepository(
        studyChannelDao: StudyChannelDao,
        applicationScope: CoroutineScope,
        @ApplicationContext context: Context
    ): StudyChannelRepository = StudyChannelRepository(studyChannelDao, applicationScope, context)

    @Provides
    @Singleton
    fun provideAppLimitRepository(
        appLimitDao: AppLimitDao,
        dailyAppUsageDao: DailyAppUsageDao,
        appLimitSessionDao: AppLimitSessionDao
    ): AppLimitRepository = AppLimitRepository(appLimitDao, dailyAppUsageDao, appLimitSessionDao)

    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        sessionDao: SessionDao,
        breakRecordDao: BreakRecordDao,
        blockedAttemptDao: BlockedAttemptDao,
        studyActivityDao: StudyActivityDao,
        preferencesRepository: FocusPreferencesRepository,
        @ApplicationContext context: Context
    ): AnalyticsRepository = AnalyticsRepository(sessionDao, breakRecordDao, blockedAttemptDao, studyActivityDao, preferencesRepository, context)

    @Provides
    @Singleton
    fun provideBlockedWebsiteRepository(blockedWebsiteDao: BlockedWebsiteDao): BlockedWebsiteRepository =
        BlockedWebsiteRepository(blockedWebsiteDao)

    @Provides
    @Singleton
    fun provideFocusScheduleRepository(
        focusScheduleDao: FocusScheduleDao,
        @ApplicationContext context: Context,
        applicationScope: CoroutineScope
    ): FocusScheduleRepository = FocusScheduleRepository(focusScheduleDao, context, applicationScope)

    @Provides
    @Singleton
    fun provideKeywordRepository(keywordDao: KeywordDao, applicationScope: CoroutineScope): KeywordRepository =
        KeywordRepository(keywordDao, applicationScope)

    @Provides
    @Singleton
    fun provideDailyUnlockRepository(dailyUnlockDao: DailyUnlockDao, applicationScope: CoroutineScope): DailyUnlockRepository =
        DailyUnlockRepository(dailyUnlockDao, applicationScope)

    @Provides
    @Singleton
    fun provideScratchCardRepository(scratchCardDao: ScratchCardDao, applicationScope: CoroutineScope): ScratchCardRepository =
        ScratchCardRepository(scratchCardDao, applicationScope)

    @Provides
    @Singleton
    fun provideSyncEngine(): SyncEngine = FocusShieldApp.instance.syncEngine

    @Provides
    @Singleton
    fun provideSyncGateway(): SyncGateway = FocusShieldApp.instance.syncGateway
}
