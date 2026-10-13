package dev.jdtech.jellyfin.downloads

import dev.jdtech.jellyfin.models.UiText
import java.util.UUID

object DownloadStatus {
    const val NONE = 0
    const val PENDING = 1
    const val RUNNING = 2
    const val PAUSED = 4
    const val SUCCESSFUL = 8
    const val FAILED = 16
}

data class DownloadProgress(
    val status: Int = DownloadStatus.NONE,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val bytesPerSecond: Long = -1,
    val remainingMillis: Long = -1,
    val errorText: UiText? = null,
) {
    val progress: Float
        get() =
            if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            else 0f
}

data class DownloadTask(
    val id: Long,
    val itemId: UUID,
    val title: String,
    val primary: Boolean,
    val created: Long,
    val progress: DownloadProgress,
    val movie: Boolean = false,
)
