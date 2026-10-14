package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.downloads.DownloadTask
import dev.jdtech.jellyfin.downloads.FetchDownloadEngine
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadQueueState(
    val tasks: List<DownloadTask> = emptyList(),
    val error: UiText? = null,
)

@HiltViewModel
class DownloadQueueViewModel
@Inject
constructor(
    private val engine: FetchDownloadEngine,
    private val downloader: Downloader,
    private val preferences: AppPreferences,
    private val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadQueueState())
    val state = _state.asStateFlow()
    private val stateLock = Any()
    private var observationError: UiText? = null
    private var operationError: UiText? = null
    private var observation: Job? = null

    fun observe() {
        observation?.cancel()
        observation =
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    try {
                        val server = preferences.getValue(preferences.currentServer)
                        val user = server?.let { database.getServerCurrentUser(it)?.id?.toString() }
                        val ids =
                            engine
                                .refresh()
                                .filter {
                                    it.extras.getString("serverId", "") == server &&
                                        it.extras.getString("userId", "") == user
                                }
                                .map { it.id.toLong() }
                                .toSet()
                        synchronized(stateLock) {
                            observationError = null
                            _state.value =
                                _state.value.copy(
                                    tasks =
                                        engine.tasks.value.filter {
                                            it.id in ids &&
                                                it.progress.status != DownloadStatus.SUCCESSFUL
                                        },
                                    error = operationError,
                                )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        synchronized(stateLock) {
                            observationError = UiText.StringResource(R.string.downloading_error)
                            _state.value =
                                _state.value.copy(error = operationError ?: observationError)
                        }
                    }
                    delay(1000)
                }
            }
    }

    fun stopObserving() {
        observation?.cancel()
    }

    fun pause(id: Long) = operate { downloader.pauseDownload(id) }

    fun resume(id: Long) = operate { downloader.resumeDownload(id) }

    fun retry(id: Long) = operate { downloader.retryDownload(id) }

    fun cancel(id: Long) = operate { downloader.cancelTask(id) }

    private fun operate(operation: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                operation()
                synchronized(stateLock) {
                    operationError = null
                    _state.value = _state.value.copy(error = observationError)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(stateLock) {
                    operationError = UiText.StringResource(R.string.downloading_error)
                    _state.value = _state.value.copy(error = operationError ?: observationError)
                }
            }
        }
    }
}
