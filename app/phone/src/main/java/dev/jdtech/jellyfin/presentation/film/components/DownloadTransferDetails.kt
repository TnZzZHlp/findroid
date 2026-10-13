package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState

@Composable
fun DownloadTransferDetails(state: DownloaderState) {
    val context = LocalContext.current
    Column {
        Text(
            stringResource(
                CoreR.string.download_size,
                Formatter.formatShortFileSize(context, state.downloadedBytes),
                if (state.totalBytes > 0) Formatter.formatShortFileSize(context, state.totalBytes)
                else stringResource(CoreR.string.download_unknown_size),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.bytesPerSecond > 0)
            Text(
                stringResource(
                    CoreR.string.download_speed,
                    Formatter.formatShortFileSize(context, state.bytesPerSecond),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        if (state.remainingMillis >= 0)
            Text(
                stringResource(
                    CoreR.string.download_eta,
                    (state.remainingMillis + 59_999) / 60_000,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
    }
}
