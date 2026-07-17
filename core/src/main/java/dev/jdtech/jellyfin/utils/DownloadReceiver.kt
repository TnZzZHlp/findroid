package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class DownloadReceiver : BroadcastReceiver() {

    @Inject lateinit var database: ServerDatabaseDao

    @Inject lateinit var downloader: Downloader

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!downloader.finalizeDownload(id)) cleanupFailedDownload(id)
            } catch (error: Exception) {
                Timber.e(error, "Failed to finalize download %d", id)
                cleanupFailedDownload(id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cleanupFailedDownload(downloadId: Long) {
        val source = database.getSourceByDownloadId(downloadId)
        if (source != null) {
            File(source.path).delete()
            database.getMediaStreamsBySourceId(source.id).forEach { mediaStream ->
                File(mediaStream.path).delete()
            }
            database.deleteMediaStreamsBySourceId(source.id)
            database.deleteSource(source.id)
            return
        }

        database.getMediaStreamByDownloadId(downloadId)?.let { mediaStream ->
            File(mediaStream.path).delete()
            database.deleteMediaStream(mediaStream.id)
        }
    }
}
