package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import java.util.UUID

data class DownloadsState(
    val groups: List<DownloadedShowGroup> = emptyList(),
    val isLoading: Boolean = true,
    val deletingIds: Set<UUID> = emptySet(),
    val error: Exception? = null,
)

data class DownloadedShowGroup(
    val seriesId: UUID,
    val name: String,
    val episodes: List<FindroidEpisode>,
)

internal fun groupDownloadedEpisodes(episodes: List<FindroidEpisode>): List<DownloadedShowGroup> =
    episodes
        .groupBy { it.seriesId }
        .map { (seriesId, items) ->
            DownloadedShowGroup(
                seriesId = seriesId,
                name = items.first().seriesName,
                episodes =
                    items.sortedWith(
                        compareBy<FindroidEpisode> { it.parentIndexNumber }
                            .thenBy { it.indexNumber }
                            .thenBy { it.id }
                    ),
            )
        }
        .sortedWith(
            compareBy<DownloadedShowGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.seriesId }
        )
