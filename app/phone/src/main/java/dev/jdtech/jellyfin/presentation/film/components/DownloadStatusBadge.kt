package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun DownloadStatusBadge(status: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (containerColor, contentColor) =
        when (status) {
            DownloadStatus.PENDING -> colors.surfaceContainerHigh to colors.onSurfaceVariant
            DownloadStatus.PAUSED -> colors.tertiaryContainer to colors.onTertiaryContainer
            DownloadStatus.FAILED -> colors.errorContainer to colors.onErrorContainer
            DownloadStatus.SUCCESSFUL -> colors.secondaryContainer to colors.onSecondaryContainer
            else -> colors.primaryContainer to colors.onPrimaryContainer
        }
    val (text, icon) =
        when (status) {
            DownloadStatus.PENDING -> CoreR.string.download_pending to CoreR.drawable.ic_download
            DownloadStatus.PAUSED -> CoreR.string.download_paused to CoreR.drawable.ic_pause
            DownloadStatus.FAILED -> CoreR.string.download_failed to CoreR.drawable.ic_alert_circle
            DownloadStatus.SUCCESSFUL -> CoreR.string.download_completed to CoreR.drawable.ic_check
            else -> CoreR.string.download_downloading to CoreR.drawable.ic_download
        }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacings.small,
                    vertical = MaterialTheme.spacings.extraSmall,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text = stringResource(text), style = MaterialTheme.typography.labelMedium)
        }
    }
}
