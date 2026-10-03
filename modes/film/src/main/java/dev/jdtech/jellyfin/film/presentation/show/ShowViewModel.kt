package dev.jdtech.jellyfin.film.presentation.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.presentation.loadCachedDetail
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class ShowViewModel @Inject constructor(private val repository: JellyfinRepository) : ViewModel() {
    private val _state = MutableStateFlow(ShowState())
    val state = _state.asStateFlow()

    lateinit var showId: UUID
    private var loadJob: Job? = null

    fun loadShow(showId: UUID, forceRefresh: Boolean = false) {
        this.showId = showId
        if (!forceRefresh && _state.value.show?.id == showId) return

        loadJob?.cancel()
        if (_state.value.show?.id != showId) _state.value = ShowState()
        else _state.value = _state.value.copy(error = null)
        loadJob = viewModelScope.launch {
            loadCachedDetail(
                readCache = { loadShowState(showId, localOnly = true) },
                fetchRemote = { hasCachedData ->
                    loadShowState(showId, forceRefresh = forceRefresh || hasCachedData)!!
                },
                onLoaded = { _state.emit(it) },
                onError = { _state.emit(_state.value.copy(error = it)) },
            )
        }
    }

    private suspend fun loadShowState(
        showId: UUID,
        localOnly: Boolean = false,
        forceRefresh: Boolean = false,
    ): ShowState? {
        val show =
            if (localOnly) repository.getLocalShow(showId) ?: return null
            else repository.getShow(showId, forceRefresh = forceRefresh)
        val nextUp =
            if (localOnly) repository.getLocalNextUp(showId)
            else repository.getNextUp(showId, forceRefresh = forceRefresh)
        val seasons =
            repository.getSeasons(showId, localOnly = localOnly, forceRefresh = forceRefresh)
        return ShowState(
            show = show,
            nextUp = nextUp.firstOrNull(),
            seasons = seasons,
            actors = getActors(show),
            director = getDirector(show),
            writers = getWriters(show),
        )
    }

    private suspend fun getActors(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidShow): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: ShowAction) {
        when (action) {
            is ShowAction.Retry -> loadShow(showId, forceRefresh = true)
            is ShowAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(showId)
                    loadShow(showId, forceRefresh = true)
                }
            }
            is ShowAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(showId)
                    loadShow(showId, forceRefresh = true)
                }
            }
            is ShowAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(showId)
                    loadShow(showId, forceRefresh = true)
                }
            }
            is ShowAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(showId)
                    loadShow(showId, forceRefresh = true)
                }
            }
            else -> Unit
        }
    }
}
