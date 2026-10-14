package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlin.math.roundToInt

@Composable
fun DownloaderCard(
    state: DownloaderState,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val progress =
        if (state.status == DownloadStatus.SUCCESSFUL) 1f else state.progress.coerceIn(0f, 1f)
    val animatedProgress by
        animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )
    val progressColor =
        when (state.status) {
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            header?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadStatusBadge(state.status, modifier = Modifier.weight(1f, fill = false))
                if (state.totalBytes > 0 || state.status == DownloadStatus.SUCCESSFUL) {
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val indicatorModifier = Modifier.fillMaxWidth().height(6.dp)
            if (
                state.totalBytes <= 0 &&
                    state.status in setOf(DownloadStatus.PENDING, DownloadStatus.RUNNING)
            ) {
                LinearProgressIndicator(
                    modifier = indicatorModifier,
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = indicatorModifier,
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            DownloadTransferDetails(state)
            state.errorText?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = error.asString(),
                        modifier = Modifier.padding(MaterialTheme.spacings.small),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.status != DownloadStatus.SUCCESSFUL) {
                DownloadActions(
                    state.status,
                    onCancelClick,
                    onRetryClick,
                    onPauseClick,
                    onResumeClick,
                )
            }
        }
    }
}

@Composable
private fun DownloadActions(
    status: Int,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(MaterialTheme.spacings.small, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancelClick) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_x),
                    contentDescription = stringResource(CoreR.string.cancel_download),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val action =
                when (status) {
                    DownloadStatus.PENDING,
                    DownloadStatus.RUNNING ->
                        Triple(CoreR.string.download_pause, CoreR.drawable.ic_pause, onPauseClick)
                    DownloadStatus.PAUSED ->
                        Triple(CoreR.string.download_resume, CoreR.drawable.ic_play, onResumeClick)
                    DownloadStatus.FAILED ->
                        Triple(
                            CoreR.string.download_retry,
                            CoreR.drawable.ic_rotate_ccw,
                            onRetryClick,
                        )
                    else -> null
                }
            action?.let { (label, icon, onClick) ->
                FilledTonalButton(onClick = onClick, modifier = Modifier.weight(1f, fill = false)) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
@Preview(widthDp = 360)
private fun DownloaderCardPendingPreview() {
    FindroidTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadStatus.PENDING),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview(widthDp = 360)
private fun DownloaderCardDownloadingPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadStatus.RUNNING,
                    progress = 0.5f,
                    downloadedBytes = 536_870_912,
                    totalBytes = 1_073_741_824,
                    bytesPerSecond = 4_194_304,
                    remainingMillis = 128_000,
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview(widthDp = 360)
private fun DownloaderCardPausedPreview() {
    FindroidTheme(darkTheme = true) {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadStatus.PAUSED,
                    progress = 0.5f,
                    downloadedBytes = 536_870_912,
                    totalBytes = 1_073_741_824,
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview(widthDp = 320, fontScale = 1.5f)
private fun DownloaderCardFailedPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadStatus.FAILED,
                    progress = 0.5f,
                    downloadedBytes = 536_870_912,
                    totalBytes = 1_073_741_824,
                    errorText = UiText.StringResource(CoreR.string.download_error_storage),
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
