package dev.jdtech.jellyfin.presentation.film.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun ItemButtonsBar(
    item: FindroidItem,
    onPlayClick: (startFromBeginning: Boolean) -> Unit,
    onMarkAsPlayedClick: () -> Unit,
    onMarkAsFavoriteClick: () -> Unit,
    onDownloadClick: (storageIndex: Int) -> Unit,
    onDownloadCancelClick: () -> Unit,
    onDownloadDeleteClick: () -> Unit,
    onTrailerClick: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    downloaderState: DownloaderState? = null,
    canPlay: Boolean = true,
    onDownloadPauseClick: () -> Unit = {},
    onDownloadResumeClick: () -> Unit = {},
    onDownloadRetryClick: () -> Unit = { onDownloadClick(0) },
) {
    val context = LocalContext.current
    val startDownload = rememberDownloadStart(onDownloadClick)

    val trailerUri =
        when (item) {
            is FindroidMovie -> {
                item.trailer
            }
            is FindroidShow -> {
                item.trailer
            }
            else -> null
        }

    var storageSelectionDialogOpen by remember { mutableStateOf(false) }
    var cancelDownloadDialogOpen by remember { mutableStateOf(false) }
    var deleteDownloadDialogOpen by remember { mutableStateOf(false) }

    var selectedStorageIndex by remember { mutableIntStateOf(0) }
    var storageLocations = remember { context.getExternalFilesDirs(null) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayButton(
                    item = item,
                    onClick = { onPlayClick(false) },
                    modifier = Modifier.weight(1f),
                    enabled = item.canPlay && canPlay,
                )
                if (item.playbackPositionTicks.div(600000000) > 0) {
                    FilledTonalIconButton(
                        onClick = { onPlayClick(true) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                            contentDescription = stringResource(CoreR.string.play_from_beginning),
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
            ) {
                FilterChip(
                    selected = item.played,
                    onClick = onMarkAsPlayedClick,
                    label = {
                        Text(
                            stringResource(
                                if (item.played) CoreR.string.unmark_as_played
                                else CoreR.string.mark_as_played
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(painterResource(CoreR.drawable.ic_check), contentDescription = null)
                    },
                )
                FilterChip(
                    selected = item.favorite,
                    onClick = onMarkAsFavoriteClick,
                    label = {
                        Text(
                            stringResource(
                                if (item.favorite) CoreR.string.remove_from_favorites
                                else CoreR.string.add_to_favorites
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(
                                if (item.favorite) CoreR.drawable.ic_heart_filled
                                else CoreR.drawable.ic_heart
                            ),
                            contentDescription = null,
                        )
                    },
                )
                trailerUri?.let { uri ->
                    FilledTonalButton(onClick = { onTrailerClick(uri) }) {
                        Icon(painterResource(CoreR.drawable.ic_film), contentDescription = null)
                        Text(
                            stringResource(CoreR.string.watch_trailer),
                            modifier = Modifier.padding(start = MaterialTheme.spacings.small),
                        )
                    }
                }
                if (downloaderState != null && !downloaderState.isDownloading) {
                    if (item.isDownloaded()) {
                        FilledTonalButton(onClick = { deleteDownloadDialogOpen = true }) {
                            Icon(
                                painterResource(CoreR.drawable.ic_trash),
                                contentDescription = null,
                            )
                            Text(
                                stringResource(CoreR.string.delete_download),
                                modifier = Modifier.padding(start = MaterialTheme.spacings.small),
                            )
                        }
                    } else if (item.canDownload) {
                        FilledTonalButton(
                            onClick = {
                                storageLocations = context.getExternalFilesDirs(null)
                                if (storageLocations.size > 1) {
                                    storageSelectionDialogOpen = true
                                } else {
                                    selectedStorageIndex = 0
                                    startDownload(selectedStorageIndex)
                                }
                            }
                        ) {
                            Icon(
                                painterResource(CoreR.drawable.ic_download),
                                contentDescription = null,
                            )
                            Text(
                                stringResource(CoreR.string.download_button_description),
                                modifier = Modifier.padding(start = MaterialTheme.spacings.small),
                            )
                        }
                    }
                }
            }
            if (downloaderState != null) {
                AnimatedVisibility(downloaderState.isDownloading) {
                    DownloaderCard(
                        state = downloaderState,
                        onCancelClick = { cancelDownloadDialogOpen = true },
                        onRetryClick = onDownloadRetryClick,
                        onPauseClick = onDownloadPauseClick,
                        onResumeClick = onDownloadResumeClick,
                    )
                }
            }
        }
    }
    if (storageSelectionDialogOpen) {
        val locations = remember {
            storageLocations.map { dir ->
                val locationStringRes =
                    if (Environment.isExternalStorageRemovable(dir)) CoreR.string.external
                    else CoreR.string.internal
                val locationString = context.getString(locationStringRes)

                val stat = StatFs(dir.path)
                val availableMegaBytes = stat.availableBytes.div(1000000)
                context.getString(CoreR.string.storage_name, locationString, availableMegaBytes)
            }
        }
        StorageSelectionDialog(
            storageLocations = locations,
            onSelect = { storageIndex ->
                selectedStorageIndex = storageIndex
                startDownload(selectedStorageIndex)
                storageSelectionDialogOpen = false
            },
            onDismiss = { storageSelectionDialogOpen = false },
        )
    }
    if (cancelDownloadDialogOpen) {
        CancelDownloadDialog(
            onCancel = {
                onDownloadCancelClick()
                cancelDownloadDialogOpen = false
            },
            onDismiss = { cancelDownloadDialogOpen = false },
        )
    }
    if (deleteDownloadDialogOpen) {
        DeleteDownloadDialog(
            onDelete = {
                onDownloadDeleteClick()
                deleteDownloadDialogOpen = false
            },
            onDismiss = { deleteDownloadDialogOpen = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarPreview() {
    FindroidTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            onPlayClick = {},
            onMarkAsPlayedClick = {},
            onMarkAsFavoriteClick = {},
            onDownloadClick = {},
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarDownloadingPreview() {
    FindroidTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            downloaderState = DownloaderState(status = DownloadStatus.RUNNING, progress = 0.3f),
            onPlayClick = {},
            onMarkAsPlayedClick = {},
            onMarkAsFavoriteClick = {},
            onDownloadClick = {},
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}
