package com.example.core.di

import com.example.BuildConfig
import com.example.feature.update.data.GitHubReleaseRepository
import com.example.feature.update.data.UpdateDownloadManager
import com.example.feature.update.data.UpdatePreferences
import com.example.feature.update.engine.UpdateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Named("installedVersionName")
    fun provideInstalledVersionName(): String = BuildConfig.VERSION_NAME

    // Delegates to the FocusShieldApp singletons: the application owns restore()/lifecycle for
    // these, so a fresh instance here would be a never-restored copy.
    @Provides
    @Singleton
    fun provideUpdatePreferences(): UpdatePreferences =
        com.example.FocusShieldApp.instance.updatePreferences

    @Provides
    @Singleton
    fun provideUpdateRepository(): GitHubReleaseRepository =
        com.example.FocusShieldApp.instance.updateRepository

    @Provides
    @Singleton
    fun provideUpdateDownloadManager(): UpdateDownloadManager =
        com.example.FocusShieldApp.instance.updateDownloadManager

    @Provides
    @Singleton
    fun provideUpdateManager(): UpdateManager =
        com.example.FocusShieldApp.instance.updateManager
}
