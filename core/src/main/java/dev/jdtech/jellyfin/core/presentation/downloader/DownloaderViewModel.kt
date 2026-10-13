package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.downloads.DownloadStatus
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isPlayableLocalFile
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel @Inject constructor(private val downloader: Downloader) : ViewModel() {
    private val _state = MutableStateFlow(DownloaderState())
    val state = _state.asStateFlow()
    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()
    var downloadId: Long? = null
    private var progressJob: Job? = null

    fun update(item: FindroidItem) {
        val source =
            item.sources.firstOrNull {
                it.type == FindroidSourceType.LOCAL && it.path.endsWith(".download")
            }
        if (source != null && source.downloadId != downloadId) {
            downloadId = source.downloadId
            observeProgress()
        }
    }

    private fun download(item: FindroidItem, storageIndex: Int) {
        operate {
            if (downloadId != null && _state.value.isDownloading) {
                downloader.retryDownload(downloadId!!)
                observeProgress()
                return@operate
            }
            _state.value = DownloaderState(status = DownloadStatus.PENDING)
            val sourceId = item.sources.firstOrNull { it.type == FindroidSourceType.REMOTE }?.id
            if (sourceId == null) {
                _state.value = DownloaderState(status = DownloadStatus.FAILED)
                return@operate
            }
            val (id, error) = downloader.downloadItem(item, sourceId, storageIndex)
            if (id != -1L) {
                downloadId = id
                observeProgress()
            } else _state.value = DownloaderState(status = DownloadStatus.FAILED, errorText = error)
        }
    }

    private fun observeProgress() {
        progressJob?.cancel()
        progressJob =
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    try {
                        val snapshot = downloader.getSnapshot(downloadId)
                        _state.value =
                            DownloaderState(
                                snapshot.status,
                                snapshot.progress,
                                snapshot.errorText,
                                snapshot.downloadedBytes,
                                snapshot.totalBytes,
                                snapshot.bytesPerSecond,
                                snapshot.remainingMillis,
                            )
                        if (snapshot.status == DownloadStatus.NONE) {
                            downloadId = null
                            return@launch
                        }
                        if (snapshot.status == DownloadStatus.SUCCESSFUL) {
                            if (downloadId != null && downloader.finalizeDownload(downloadId!!)) {
                                eventsChannel.send(DownloaderEvent.Successful)
                            } else
                                _state.value =
                                    _state.value.copy(
                                        status = DownloadStatus.FAILED,
                                        errorText =
                                            UiText.StringResource(R.string.download_error_finalize),
                                    )
                            return@launch
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.value =
                            _state.value.copy(
                                errorText = UiText.StringResource(R.string.downloading_error)
                            )
                    }
                    delay(1000)
                }
            }
    }

    private fun operate(operation: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(errorText = UiText.StringResource(R.string.downloading_error))
            }
        }
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.item, action.storageIndex)
            is DownloaderAction.DeleteDownload ->
                operate {
                    action.item.sources
                        .firstOrNull { it.isPlayableLocalFile() }
                        ?.let {
                            downloader.deleteItem(action.item, it)
                            eventsChannel.send(DownloaderEvent.Deleted)
                        }
                }
            is DownloaderAction.CancelDownload ->
                operate {
                    progressJob?.cancel()
                    downloader.cancelDownload(action.item, downloadId ?: -1L)
                    downloadId = null
                    _state.value = DownloaderState()
                }
            DownloaderAction.PauseDownload ->
                operate { downloadId?.let { downloader.pauseDownload(it) } }
            DownloaderAction.ResumeDownload ->
                operate {
                    downloadId?.let { downloader.resumeDownload(it) }
                    observeProgress()
                }
            DownloaderAction.RetryDownload ->
                operate {
                    downloadId?.let { downloader.retryDownload(it) }
                    observeProgress()
                }
        }
    }
}
