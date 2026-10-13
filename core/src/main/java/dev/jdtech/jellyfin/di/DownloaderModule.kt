package dev.jdtech.jellyfin.di

import android.app.Application
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.downloads.DownloadFileFinalizer
import dev.jdtech.jellyfin.downloads.DownloadScheduler
import dev.jdtech.jellyfin.downloads.FetchDownloadEngine
import dev.jdtech.jellyfin.downloads.LegacyDownloadCleanup
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.DownloaderImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloaderModule {
    @Singleton
    @Provides
    fun provideDownloader(
        application: Application,
        serverDatabase: ServerDatabaseDao,
        jellyfinRepository: JellyfinRepository,
        appPreferences: AppPreferences,
        workManager: WorkManager,
        engine: FetchDownloadEngine,
        scheduler: DownloadScheduler,
        finalizer: DownloadFileFinalizer,
        legacyCleanup: LegacyDownloadCleanup,
    ): Downloader {
        return DownloaderImpl(
            application,
            serverDatabase,
            jellyfinRepository,
            appPreferences,
            workManager,
            engine,
            scheduler,
            finalizer,
            legacyCleanup,
        )
    }
}
