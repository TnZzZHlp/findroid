package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.downloads.DownloadProgress
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText

interface Downloader {
    suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<Long, UiText?>

    suspend fun cancelDownload(item: FindroidItem, downloadId: Long)

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    suspend fun getProgress(downloadId: Long?): Pair<Int, Int>

    suspend fun finalizeDownload(downloadId: Long): Boolean

    suspend fun getSnapshot(downloadId: Long?): DownloadProgress = DownloadProgress()

    suspend fun cancelTask(downloadId: Long) {}

    suspend fun pauseDownload(downloadId: Long) {}

    suspend fun resumeDownload(downloadId: Long) {}

    suspend fun retryDownload(downloadId: Long) {}

    suspend fun discardOtherIdentities(serverId: String?, userId: String?) {}
}
