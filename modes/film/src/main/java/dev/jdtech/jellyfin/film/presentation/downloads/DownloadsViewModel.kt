package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun deleteDownloadedEpisode(episode: FindroidEpisode, downloader: Downloader) {
    episode.sources
        .filter { it.type == FindroidSourceType.LOCAL }
        .forEach { source -> downloader.deleteItem(episode, source) }
}

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(private val repository: JellyfinRepository, private val downloader: Downloader) :
    ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    private var observationJob: Job? = null

    fun loadDownloads() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                repository.observeDownloadedEpisodes().collect { episodes ->
                    val groups =
                        withContext(Dispatchers.Default) { groupDownloadedEpisodes(episodes) }
                    _state.update { it.copy(groups = groups, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    fun stopObserving() {
        observationJob?.cancel()
    }

    fun deleteDownload(episode: FindroidEpisode) {
        if (episode.id in _state.value.deletingIds) return
        _state.update { it.copy(deletingIds = it.deletingIds + episode.id, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { deleteDownloadedEpisode(episode, downloader) }
                loadDownloads()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e) }
            } finally {
                _state.update { it.copy(deletingIds = it.deletingIds - episode.id) }
            }
        }
    }
}
