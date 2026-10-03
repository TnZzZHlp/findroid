package dev.jdtech.jellyfin.film.presentation.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.presentation.loadCachedDetail
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFields

@HiltViewModel
class SeasonViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SeasonState())
    val state = _state.asStateFlow()

    lateinit var seasonId: UUID
    private var loadJob: Job? = null

    fun loadSeason(seasonId: UUID, forceRefresh: Boolean = false) {
        this.seasonId = seasonId
        if (!forceRefresh && _state.value.season?.id == seasonId) return

        loadJob?.cancel()
        if (_state.value.season?.id != seasonId) _state.value = SeasonState()
        else _state.value = _state.value.copy(error = null)
        loadJob = viewModelScope.launch {
            loadCachedDetail(
                readCache = { loadSeasonState(seasonId, localOnly = true) },
                fetchRemote = { loadSeasonState(seasonId)!! },
                onLoaded = { _state.emit(it) },
                onError = { _state.emit(_state.value.copy(error = it)) },
            )
        }
    }

    private suspend fun loadSeasonState(seasonId: UUID, localOnly: Boolean = false): SeasonState? {
        val season =
            if (localOnly) repository.getLocalSeason(seasonId) ?: return null
            else repository.getSeason(seasonId)
        val episodes =
            repository.getEpisodes(
                seriesId = season.seriesId,
                seasonId = seasonId,
                fields = listOf(ItemFields.OVERVIEW),
                localOnly = localOnly,
            )
        return SeasonState(season = season, episodes = episodes)
    }

    fun onAction(action: SeasonAction) {
        when (action) {
            is SeasonAction.Retry -> loadSeason(seasonId, forceRefresh = true)
            is SeasonAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(seasonId)
                    loadSeason(seasonId, forceRefresh = true)
                }
            }
            is SeasonAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(seasonId)
                    loadSeason(seasonId, forceRefresh = true)
                }
            }
            is SeasonAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(seasonId)
                    loadSeason(seasonId, forceRefresh = true)
                }
            }
            is SeasonAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(seasonId)
                    loadSeason(seasonId, forceRefresh = true)
                }
            }
            else -> Unit
        }
    }
}
