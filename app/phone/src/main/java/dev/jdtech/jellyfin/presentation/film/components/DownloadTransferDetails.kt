package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun DownloadTransferDetails(state: DownloaderState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
    ) {
        Text(
            text =
                stringResource(
                    CoreR.string.download_size,
                    Formatter.formatShortFileSize(context, state.downloadedBytes),
                    if (state.totalBytes > 0)
                        Formatter.formatShortFileSize(context, state.totalBytes)
                    else stringResource(CoreR.string.download_unknown_size),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (
            state.status == DownloadStatus.RUNNING &&
                (state.bytesPerSecond > 0 || state.remainingMillis >= 0)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
            ) {
                if (state.bytesPerSecond > 0) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.download_speed,
                                Formatter.formatShortFileSize(context, state.bytesPerSecond),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.remainingMillis >= 0) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.download_eta,
                                (state.remainingMillis + 59_999) / 60_000,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
