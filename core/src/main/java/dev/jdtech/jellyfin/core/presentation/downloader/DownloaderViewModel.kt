package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isPlayableLocalFile
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
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
        viewModelScope.launch {
            if (item.isDownloading()) {
                val source =
                    item.sources.firstOrNull {
                        it.type == FindroidSourceType.LOCAL && it.path.endsWith(".download")
                    }
                        ?: return@launch
                this@DownloaderViewModel.downloadId = source.downloadId
                pollDownloadProgress(source.downloadId)
            }
        }
    }

    private fun download(item: FindroidItem, storageIndex: Int = 0) {
        viewModelScope.launch {
            _state.emit(DownloaderState(status = DownloadManager.STATUS_PENDING))
            val sourceId =
                item.sources.firstOrNull { it.type == FindroidSourceType.REMOTE }?.id
            if (sourceId == null) {
                _state.emit(DownloaderState(status = DownloadManager.STATUS_FAILED))
                return@launch
            }
            val (downloadId, uiText) =
                downloader.downloadItem(
                    item = item,
                    sourceId = sourceId,
                    storageIndex = storageIndex,
                )
            if (downloadId != -1L) {
                this@DownloaderViewModel.downloadId = downloadId
                pollDownloadProgress(downloadId)
            } else {
                _state.emit(
                    DownloaderState(status = DownloadManager.STATUS_FAILED, errorText = uiText)
                )
            }
        }
    }

    private fun cancelDownload(item: FindroidItem) {
        viewModelScope.launch {
            // Stop progress polling
            progressJob?.cancel()

            // Cancel the download
            downloadId?.let { downloader.cancelDownload(item = item, downloadId = it) }

            // Emit empty DownloadState
            _state.emit(DownloaderState())
        }
    }

    private fun deleteDownload(item: FindroidItem) {
        viewModelScope.launch {
            downloader.deleteItem(
                item = item,
                source = item.sources.first { it.isPlayableLocalFile() },
            )
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun pollDownloadProgress(downloadId: Long?) {
        progressJob?.cancel()
        progressJob =
            viewModelScope.launch {
                while (true) {
                    val (status, progress) = downloader.getProgress(downloadId)
                    _state.emit(
                        DownloaderState(
                            status = status,
                            progress = progress.coerceAtLeast(0) / 100f,
                        )
                    )

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (downloadId != null && downloader.finalizeDownload(downloadId)) {
                                eventsChannel.send(DownloaderEvent.Successful)
                            } else {
                                _state.emit(
                                    DownloaderState(status = DownloadManager.STATUS_FAILED)
                                )
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> return@launch
                        else -> delay(1000L)
                    }
                }
            }
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.item, action.storageIndex)
            is DownloaderAction.DeleteDownload -> deleteDownload(action.item)
            is DownloaderAction.CancelDownload -> cancelDownload(action.item)
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
    }
}
