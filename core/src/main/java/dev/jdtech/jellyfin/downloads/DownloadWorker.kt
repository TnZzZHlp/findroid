package dev.jdtech.jellyfin.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R
import kotlinx.coroutines.CancellationException

@HiltWorker
class DownloadWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val coordinator: DownloadCoordinator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        try {
            setForeground(notification(emptyList()))
            val retry = coordinator.run { tasks -> setForeground(notification(tasks)) }
            return if (retry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun notification(tasks: List<DownloadTask>): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.title_downloads),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val current = tasks.firstOrNull { it.progress.status == DownloadStatus.RUNNING }
        val notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(current?.title ?: context.getString(R.string.title_downloads))
                .setContentText(context.getString(R.string.download_downloading))
                .setProgress(
                    100,
                    ((current?.progress?.progress ?: 0f) * 100).toInt(),
                    current?.progress?.totalBytes?.let { it <= 0 } ?: true,
                )
                .setContentIntent(
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                        PendingIntent.getActivity(
                            context,
                            0,
                            it,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL = "media-downloads"
        private const val NOTIFICATION_ID = 4301
    }
}
