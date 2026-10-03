package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsState
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.DeleteDownloadDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun DownloadsScreen(
    navigateBack: () -> Unit,
    navigateToShow: (UUID) -> Unit,
    navigateToSeason: (UUID) -> Unit,
    navigateToEpisode: (UUID) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.loadDownloads()
        onPauseOrDispose { viewModel.stopObserving() }
    }

    DownloadsScreenLayout(
        state = state,
        navigateBack = navigateBack,
        navigateToShow = navigateToShow,
        navigateToSeason = navigateToSeason,
        navigateToEpisode = navigateToEpisode,
        onPlay = { episode ->
            context.startActivity(
                Intent(context, PlayerActivity::class.java).apply {
                    putExtra("itemId", episode.id.toString())
                    putExtra("itemKind", BaseItemKind.EPISODE.serialName)
                    putExtra("startFromBeginning", false)
                }
            )
        },
        onDelete = viewModel::deleteDownload,
        onRetry = viewModel::loadDownloads,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: DownloadsState,
    navigateBack: () -> Unit,
    navigateToShow: (UUID) -> Unit,
    navigateToSeason: (UUID) -> Unit,
    navigateToEpisode: (UUID) -> Unit,
    onPlay: (FindroidEpisode) -> Unit,
    onDelete: (FindroidEpisode) -> Unit,
    onRetry: () -> Unit,
) {
    var pendingDeletion by remember { mutableStateOf<FindroidEpisode?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.title_downloads)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = stringResource(CoreR.string.close),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(MaterialTheme.spacings.default),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    if (state.error != null) {
                        item(key = "error") {
                            ErrorCard(
                                onShowStacktrace = { showErrorDialog = true },
                                onRetryClick = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    state.groups.forEach { group ->
                        item(key = "series-${group.seriesId}") {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleLarge,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable { navigateToShow(group.seriesId) }
                                        .padding(vertical = MaterialTheme.spacings.small),
                            )
                        }
                        group.episodes
                            .groupBy { it.parentIndexNumber }
                            .forEach { (season, episodes) ->
                                item(key = "season-${group.seriesId}-$season") {
                                    Text(
                                        text = stringResource(CoreR.string.season_number, season),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .clickable {
                                                    navigateToSeason(episodes.first().seasonId)
                                                }
                                                .padding(vertical = MaterialTheme.spacings.small),
                                    )
                                }
                                items(episodes, key = { it.id }) { episode ->
                                    DownloadedEpisodeRow(
                                        episode = episode,
                                        enabled = episode.id !in state.deletingIds,
                                        onPlay = { onPlay(episode) },
                                        onDetails = { navigateToEpisode(episode.id) },
                                        onDelete = { pendingDeletion = episode },
                                    )
                                }
                            }
                    }
                }
                if (state.groups.isEmpty() && state.error == null) {
                    Text(
                        text = stringResource(CoreR.string.no_downloaded_episodes),
                        modifier =
                            Modifier.align(Alignment.Center)
                                .padding(MaterialTheme.spacings.default),
                    )
                }
            }
        }
    }

    pendingDeletion?.let { episode ->
        DeleteDownloadDialog(
            onDelete = {
                onDelete(episode)
                pendingDeletion = null
            },
            onDismiss = { pendingDeletion = null },
        )
    }
    if (showErrorDialog) {
        state.error?.let { error ->
            ErrorDialog(exception = error, onDismissRequest = { showErrorDialog = false })
        }
    }
}

@Composable
private fun DownloadedEpisodeRow(
    episode: FindroidEpisode,
    enabled: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        OutlinedCard(onClick = onPlay, enabled = enabled, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemPoster(
                    item = episode,
                    direction = Direction.HORIZONTAL,
                    modifier = Modifier.width(96.dp),
                )
                Column(modifier = Modifier.padding(MaterialTheme.spacings.small)) {
                    val indexNumberEnd = episode.indexNumberEnd
                    Text(
                        text =
                            if (indexNumberEnd != null) {
                                stringResource(
                                    CoreR.string.episode_name_extended_with_end,
                                    episode.parentIndexNumber,
                                    episode.indexNumber,
                                    indexNumberEnd,
                                    episode.name,
                                )
                            } else {
                                stringResource(
                                    CoreR.string.episode_name_extended,
                                    episode.parentIndexNumber,
                                    episode.indexNumber,
                                    episode.name,
                                )
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onDetails, enabled = enabled) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_info),
                contentDescription = stringResource(CoreR.string.view_details),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_trash),
                contentDescription = stringResource(CoreR.string.delete_download),
            )
        }
    }
}
