package dev.jdtech.jellyfin.presentation.film.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.downloads.DownloadProgress
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.downloads.DownloadTask
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(MaterialTheme.spacings.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(CoreR.drawable.ic_alert_circle), contentDescription = null)
                    Text(error.asString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (state.tasks.isEmpty()) return
    item(key = "queue-title") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CoreR.string.download_queue),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = state.tasks.size.toString(),
                    modifier =
                        Modifier.padding(
                            horizontal = MaterialTheme.spacings.small,
                            vertical = MaterialTheme.spacings.extraSmall,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    items(state.tasks, key = { "task-${it.id}" }) { task ->
        val progress = task.progress
        DownloaderCard(
            state =
                DownloaderState(
                    status = progress.status,
                    progress = progress.progress,
                    errorText = progress.errorText,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    bytesPerSecond = progress.bytesPerSecond,
                    remainingMillis = progress.remainingMillis,
                ),
            onCancelClick = { onCancel(task.id) },
            onRetryClick = { onRetry(task.id) },
            onPauseClick = { onPause(task.id) },
            onResumeClick = { onResume(task.id) },
            header = { DownloadTaskHeader(task, onDetails = { onDetails(task) }) },
        )
    }
}

@Composable
private fun DownloadTaskHeader(task: DownloadTask, onDetails: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(
                    enabled = task.primary,
                    onClickLabel = stringResource(CoreR.string.view_details),
                    onClick = onDetails,
                )
                .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                painter =
                    painterResource(
                        when {
                            !task.primary -> CoreR.drawable.ic_closed_caption
                            task.movie -> CoreR.drawable.ic_film
                            else -> CoreR.drawable.ic_tv
                        }
                    ),
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!task.primary) {
                Text(
                    text = stringResource(CoreR.string.download_external_stream),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (task.primary) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
@Preview(name = "Downloads", widthDp = 360, heightDp = 920)
@Preview(
    name = "Downloads dark",
    widthDp = 360,
    heightDp = 920,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private fun DownloadQueuePreview() {
    val itemId = UUID(0, 1)
    val state =
        DownloadQueueState(
            tasks =
                listOf(
                    DownloadTask(
                        id = 1,
                        itemId = itemId,
                        title = "A very long episode title - Season 1, Episode 2",
                        primary = true,
                        created = 1,
                        progress =
                            DownloadProgress(
                                status = DownloadStatus.RUNNING,
                                downloadedBytes = 536_870_912,
                                totalBytes = 1_073_741_824,
                                bytesPerSecond = 4_194_304,
                                remainingMillis = 128_000,
                            ),
                    ),
                    DownloadTask(
                        id = 2,
                        itemId = itemId,
                        title = "Episode 2 - English subtitles",
                        primary = false,
                        created = 2,
                        progress = DownloadProgress(status = DownloadStatus.PAUSED),
                    ),
                    DownloadTask(
                        id = 3,
                        itemId = itemId,
                        title = "Movie",
                        primary = true,
                        created = 3,
                        progress =
                            DownloadProgress(
                                status = DownloadStatus.FAILED,
                                errorText =
                                    UiText.StringResource(CoreR.string.download_error_storage),
                            ),
                        movie = true,
                    ),
                )
        )
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(MaterialTheme.spacings.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            ) {
                downloadQueueItems(state, {}, {}, {}, {}, {})
            }
        }
    }
}
