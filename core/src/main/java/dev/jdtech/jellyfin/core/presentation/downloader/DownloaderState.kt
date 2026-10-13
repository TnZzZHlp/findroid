package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.models.UiText

data class DownloaderState(
    val status: Int = DownloadStatus.NONE,
    val progress: Float = 0f,
    val errorText: UiText? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val bytesPerSecond: Long = -1,
    val remainingMillis: Long = -1,
) {
    val isDownloading: Boolean
        get() =
            status in
                arrayOf(
                    DownloadStatus.PENDING,
                    DownloadStatus.RUNNING,
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED,
                )
}
