package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.downloads.DownloadTask
import dev.jdtech.jellyfin.presentation.theme.spacings

fun LazyListScope.downloadQueueItems(
    state: DownloadQueueState,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onDetails: (DownloadTask) -> Unit,
) {
    state.error?.let { error ->
        item(key = "queue-error") {
            Text(error.asString(), color = MaterialTheme.colorScheme.error)
        }
    }
    if (state.tasks.isEmpty()) return
    item(key = "queue-title") {
        Text(
            stringResource(CoreR.string.download_queue),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    items(state.tasks, key = { "task-${it.id}" }) { task ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.clickable(enabled = task.primary) { onDetails(task) }
                        .padding(vertical = MaterialTheme.spacings.small),
            )
            if (!task.primary)
                Text(
                    stringResource(CoreR.string.download_external_stream),
                    style = MaterialTheme.typography.bodySmall,
                )
            val progress = task.progress
            DownloaderCard(
                state =
                    DownloaderState(
                        progress.status,
                        progress.progress,
                        progress.errorText,
                        progress.downloadedBytes,
                        progress.totalBytes,
                        progress.bytesPerSecond,
                        progress.remainingMillis,
                    ),
                onCancelClick = { onCancel(task.id) },
                onRetryClick = { onRetry(task.id) },
                onPauseClick = { onPause(task.id) },
                onResumeClick = { onResume(task.id) },
            )
        }
    }
}
