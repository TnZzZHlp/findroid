package dev.jdtech.jellyfin.downloads

import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler
@Inject
constructor(
    private val workManager: WorkManager,
    private val preferences: AppPreferences,
    private val engine: FetchDownloadEngine,
) {
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (
            key in
                setOf(
                    preferences.downloadOverMobileData.backendName,
                    preferences.downloadWhenRoaming.backendName,
                    preferences.downloadConcurrentLimit.backendName,
                ) &&
                engine.tasks.value.any {
                    it.progress.status in setOf(DownloadStatus.PENDING, DownloadStatus.RUNNING)
                }
        ) {
            schedule(ExistingWorkPolicy.REPLACE)
        }
    }

    init {
        preferences.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun schedule(immediate: Boolean = false) =
        schedule(
            if (immediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        )

    private fun schedule(policy: ExistingWorkPolicy) {
        val network =
            when {
                !preferences.getValue(preferences.downloadOverMobileData) -> NetworkType.UNMETERED
                !preferences.getValue(preferences.downloadWhenRoaming) -> NetworkType.NOT_ROAMING
                else -> NetworkType.CONNECTED
            }
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "media-downloads"
    }
}
