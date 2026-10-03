package dev.jdtech.jellyfin.film.presentation.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.film.presentation.loadCachedDetail
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class EpisodeViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val videoMetadataParser: VideoMetadataParser,
) : ViewModel() {
    private val _state = MutableStateFlow(EpisodeState())
    val state = _state.asStateFlow()

    lateinit var episodeId: UUID
    private var loadJob: Job? = null

    fun loadEpisode(episodeId: UUID, forceRefresh: Boolean = false) {
        this.episodeId = episodeId
        if (!forceRefresh && _state.value.episode?.id == episodeId) return

        loadJob?.cancel()
        if (_state.value.episode?.id != episodeId) _state.value = EpisodeState()
        else _state.value = _state.value.copy(error = null)
        loadJob = viewModelScope.launch {
            loadCachedDetail(
                readCache = { loadEpisodeState(episodeId, localOnly = true) },
                fetchRemote = { loadEpisodeState(episodeId)!! },
                onLoaded = { _state.emit(it) },
                onError = { _state.emit(_state.value.copy(error = it)) },
            )
        }
    }

    private suspend fun loadEpisodeState(
        episodeId: UUID,
        localOnly: Boolean = false,
    ): EpisodeState? {
        val episode =
            if (localOnly) repository.getLocalEpisode(episodeId) ?: return null
            else repository.getEpisode(episodeId)
        val videoMetadata =
            try {
                episode.sources.firstOrNull()?.let { videoMetadataParser.parse(it) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
        return EpisodeState(
            episode = episode,
            videoMetadata = videoMetadata,
            actors = getActors(episode),
            displayExtraInfo = appPreferences.getValue(appPreferences.displayExtraInfo),
        )
    }

    private suspend fun getActors(item: FindroidEpisode): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    fun onAction(action: EpisodeAction) {
        when (action) {
            is EpisodeAction.Retry -> loadEpisode(episodeId, forceRefresh = true)
            is EpisodeAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(episodeId)
                    loadEpisode(episodeId, forceRefresh = true)
                }
            }
            is EpisodeAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(episodeId)
                    loadEpisode(episodeId, forceRefresh = true)
                }
            }
            is EpisodeAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(episodeId)
                    loadEpisode(episodeId, forceRefresh = true)
                }
            }
            is EpisodeAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(episodeId)
                    loadEpisode(episodeId, forceRefresh = true)
                }
            }
            else -> Unit
        }
    }
}
