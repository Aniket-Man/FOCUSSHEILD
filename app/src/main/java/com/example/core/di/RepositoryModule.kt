package com.example.core.di

import com.example.FocusShieldApp
import com.example.cloud.auth.AuthRepository
import com.example.cloud.net.ConnectivityMonitor
import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.SyncEngine
import com.example.cloud.sync.SyncGateway
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

    // Every binding below delegates to the FocusShieldApp singleton instead of constructing a
    // fresh copy. That application object is what calls start()/restoreOnStart() on the sync stack
    // and owns the repository instances the rest of the app (accessibility service, receivers)
    // already uses — a fresh instance here would be a never-started object with divergent
    // in-memory caches (channel/keyword lookups are served from @Volatile fields), the exact bug
    // that killed cloud saving.

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
    fun provideSessionRepository(): SessionRepository =
        FocusShieldApp.instance.sessionRepository

    @Provides
    @Singleton
    fun provideSubjectRepository(): SubjectRepository =
        FocusShieldApp.instance.subjectRepository

    @Provides
    @Singleton
    fun provideStudyPlanRepository(): StudyPlanRepository =
        FocusShieldApp.instance.studyPlanRepository

    @Provides
    @Singleton
    fun provideBreakRepository(): BreakRepository =
        FocusShieldApp.instance.breakRepository

    @Provides
    @Singleton
    fun provideBlockedAppRepository(): BlockedAppRepository =
        FocusShieldApp.instance.blockedAppRepository

    @Provides
    @Singleton
    fun provideBlockedAttemptRepository(): BlockedAttemptRepository =
        FocusShieldApp.instance.blockedAttemptRepository

    @Provides
    @Singleton
    fun provideStudyChannelRepository(): StudyChannelRepository =
        FocusShieldApp.instance.studyChannelRepository

    @Provides
    @Singleton
    fun provideAppLimitRepository(): AppLimitRepository =
        FocusShieldApp.instance.appLimitRepository

    @Provides
    @Singleton
    fun provideAnalyticsRepository(): AnalyticsRepository =
        FocusShieldApp.instance.analyticsRepository

    @Provides
    @Singleton
    fun provideBlockedWebsiteRepository(): BlockedWebsiteRepository =
        FocusShieldApp.instance.blockedWebsiteRepository

    @Provides
    @Singleton
    fun provideFocusScheduleRepository(): FocusScheduleRepository =
        FocusShieldApp.instance.focusScheduleRepository

    @Provides
    @Singleton
    fun provideKeywordRepository(): KeywordRepository =
        FocusShieldApp.instance.keywordRepository

    @Provides
    @Singleton
    fun provideDailyUnlockRepository(): DailyUnlockRepository =
        FocusShieldApp.instance.dailyUnlockRepository

    @Provides
    @Singleton
    fun provideScratchCardRepository(): ScratchCardRepository =
        FocusShieldApp.instance.scratchCardRepository

    @Provides
    @Singleton
    fun provideSyncEngine(): SyncEngine = FocusShieldApp.instance.syncEngine

    @Provides
    @Singleton
    fun provideSyncGateway(): SyncGateway = FocusShieldApp.instance.syncGateway
}
