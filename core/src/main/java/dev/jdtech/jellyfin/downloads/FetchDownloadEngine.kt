package dev.jdtech.jellyfin.downloads

import android.content.Context
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error as FetchError
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2core.Extras
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class FetchDownloadEngine
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val authentication: DownloadAuthentication? = null,
) {
    private val http =
        ResumableHttpDownloader(context, preferences) { request -> authentication?.token(request) }
    private val metadata = FetchMetadataDatabase(context, "findroid-media")
    private val apiMutex = Mutex()
    private val controlMutex = Mutex()
    private var limit = configuredLimit()
    private val live = ConcurrentHashMap<Int, DownloadProgress>()
    private var listener: AbstractFetchListener? = null

    private fun configuredLimit() =
        preferences.getValue(preferences.downloadConcurrentLimit)?.toIntOrNull()?.coerceIn(1, 6)
            ?: 2

    private fun newFetch(): Fetch =
        Fetch.getInstance(
                FetchConfiguration.Builder(context)
                    .setNamespace("findroid-media")
                    .setDatabaseManager(metadata)
                    .setHttpDownloader(http)
                    .setDownloadConcurrentLimit(limit)
                    .enableAutoStart(false)
                    .setProgressReportingInterval(1000)
                    .setAutoRetryMaxAttempts(0)
                    .enableRetryOnNetworkGain(false)
                    .enableFileExistChecks(false)
                    .preAllocateFileOnCreation(false)
                    .build()
            )
            .apply {
                pauseAll()
                val observer =
                    object : AbstractFetchListener() {
                        override fun onProgress(
                            download: Download,
                            etaInMilliSeconds: Long,
                            downloadedBytesPerSecond: Long,
                        ) {
                            live[download.id] =
                                DownloadProgress(
                                    DownloadStatus.RUNNING,
                                    download.downloaded,
                                    download.total,
                                    downloadedBytesPerSecond,
                                    etaInMilliSeconds,
                                )
                        }
                    }
                listener = observer
                addListener(observer, false, false)
            }

    private var fetch = newFetch()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    suspend fun downloads(): List<Download> = apiMutex.withLock {
        suspendCancellableCoroutine { continuation ->
            fetch.getDownloads { if (continuation.isActive) continuation.resume(it) }
        }
    }

    suspend fun refresh(): List<Download> {
        val downloads = downloads()
        _tasks.value =
            downloads
                .mapNotNull { download ->
                    val itemId =
                        runCatching { UUID.fromString(download.extras.getString("itemId", "")) }
                            .getOrNull() ?: return@mapNotNull null
                    DownloadTask(
                        download.id.toLong(),
                        itemId,
                        download.tag.orEmpty(),
                        download.extras.getBoolean("primary", false),
                        download.created,
                        progress(download),
                        download.extras.getBoolean("movie", false),
                    )
                }
                .sortedByDescending { it.created }
        return downloads
    }

    fun progress(download: Download): DownloadProgress {
        if (download.status == Status.DOWNLOADING && !download.extras.getBoolean("paused", false))
            live[download.id]?.let {
                return it
            }
        val error = download.extras.getString("failure", "")
        val status =
            when {
                error.isNotEmpty() && download.status == Status.COMPLETED -> DownloadStatus.FAILED
                download.status != Status.COMPLETED &&
                    download.extras.getBoolean("paused", false) -> DownloadStatus.PAUSED
                download.status in listOf(Status.ADDED, Status.PAUSED) -> DownloadStatus.PENDING
                download.extras.getBoolean("retryRequested", false) ||
                    download.extras.getLong("retryAt", 0) > 0 -> DownloadStatus.PENDING
                else ->
                    when (download.status) {
                        Status.QUEUED -> DownloadStatus.PENDING
                        Status.DOWNLOADING -> DownloadStatus.RUNNING
                        Status.COMPLETED -> DownloadStatus.SUCCESSFUL
                        else -> DownloadStatus.FAILED
                    }
            }
        return DownloadProgress(
            status,
            download.downloaded,
            download.total,
            download.downloadedBytesPerSecond,
            download.etaInMilliSeconds,
            when {
                error.isNotEmpty() -> UiText.DynamicString(error)
                download.status != Status.FAILED -> null
                download.error == FetchError.NO_STORAGE_SPACE ->
                    UiText.StringResource(R.string.download_error_storage)
                download.error == FetchError.WRITE_PERMISSION_DENIED ->
                    UiText.StringResource(R.string.download_error_permission)
                else -> UiText.StringResource(R.string.downloading_error)
            },
        )
    }

    suspend fun enqueue(request: Request): Long {
        request.downloadOnEnqueue = false
        await<Unit> { success, failure ->
            fetch.enqueue(request, { success(Unit) }, { failure(it) })
        }
        refresh()
        Timber.i("Download enqueued: id=%d", request.id)
        return request.id.toLong()
    }

    suspend fun pause(id: Long) = controlMutex.withLock {
        val download = downloads().firstOrNull { it.id.toLong() == id } ?: return@withLock
        val updated = extras(download, mapOf("paused" to "true")) ?: return@withLock
        Timber.i("Download paused: id=%d", id)
        if (updated.status in listOf(Status.QUEUED, Status.DOWNLOADING)) {
            await<Download> { success, failure -> fetch.pause(id.toInt(), success, failure) }
        }
        refresh()
        Unit
    }

    suspend fun resume(id: Long) = controlMutex.withLock {
        val download = downloads().firstOrNull { it.id.toLong() == id } ?: return@withLock
        extras(download, mapOf("paused" to "false"))
        Timber.i("Download resumed: id=%d", id)
        refresh()
        Unit
    }

    suspend fun retry(id: Long) = controlMutex.withLock {
        val download = downloads().firstOrNull { it.id.toLong() == id } ?: return@withLock
        http.clearProblem(download.id)
        Timber.i("Download retry requested: id=%d", id)
        if (download.status == Status.COMPLETED) {
            val temporary = java.io.File(download.file)
            val final = java.io.File(download.file.removeSuffix(".download"))
            val file = if (temporary.isFile) temporary else final
            if (
                !file.isFile ||
                    file.length() <= 0 ||
                    (download.total > 0 &&
                        (file.length() != download.total || download.downloaded != download.total))
            ) {
                if (file == final) file.delete()
                resetInternal(download)
                return@withLock
            }
        }
        extras(
            download,
            mapOf(
                "attempts" to "0",
                "retryAt" to "0",
                "failure" to "",
                "resets" to "0",
                "paused" to "false",
                "retryRequested" to (download.status == Status.FAILED).toString(),
            ),
        )
        refresh()
        Unit
    }

    suspend fun delete(id: Long) = controlMutex.withLock {
        deleteInternal(id)
        Timber.i("Download removed: id=%d", id)
    }

    private suspend fun deleteInternal(id: Long) {
        apiMutex.withLock {
            val current =
                awaitUnlocked<List<Download>> { success, _ -> fetch.getDownloads { success(it) } }
                    .firstOrNull { it.id.toLong() == id }
            if (current != null)
                awaitUnlocked<Download> { success, failure ->
                    fetch.delete(id.toInt(), success, failure)
                }
        }
        http.clear(id.toInt())
        live.remove(id.toInt())
        refresh()
    }

    suspend fun extras(download: Download, values: Map<String, String>): Download? =
        apiMutex.withLock {
            val current =
                awaitUnlocked<List<Download>> { success, _ -> fetch.getDownloads { success(it) } }
                    .firstOrNull { it.id == download.id } ?: return@withLock null
            awaitUnlocked<Download> { success, failure ->
                fetch.replaceExtras(
                    current.id,
                    Extras(current.extras.map + values),
                    success,
                    failure,
                )
            }
        }

    suspend fun reset(download: Download) = controlMutex.withLock { resetInternal(download) }

    private suspend fun resetInternal(download: Download) {
        val current = downloads().firstOrNull { it.id == download.id } ?: return
        if (current.extras.getBoolean("paused", false)) return
        val request =
            current.request.apply {
                extras =
                    Extras(
                        current.extras.map +
                            mapOf(
                                "sequential" to
                                    (http.problem(current.id) in setOf("reset", "unvalidated") ||
                                            current.extras.getBoolean("sequential", false))
                                        .toString(),
                                "failure" to "",
                                "retryAt" to "0",
                                "resets" to (current.extras.getInt("resets", 0) + 1).toString(),
                            )
                    )
            }
        deleteInternal(current.id.toLong())
        enqueue(request)
    }

    suspend fun start() = controlMutex.withLock {
        apiMutex.withLock {
            val desired = configuredLimit()
            if (desired != limit) {
                val previous = fetch
                listener?.let { previous.removeListener(it) }
                limit = desired
                live.clear()
                fetch = newFetch()
                previous.close()
            }
        }
    }

    suspend fun activate() = controlMutex.withLock {
        downloads().forEach { download ->
            if (!download.extras.getBoolean("paused", false)) {
                if (download.status in listOf(Status.ADDED, Status.PAUSED)) {
                    if (download.downloaded > 0 && !java.io.File(download.file).isFile)
                        resetInternal(download)
                    else
                        await<Download> { success, failure ->
                            fetch.resume(download.id, success, failure)
                        }
                } else if (
                    download.status == Status.FAILED &&
                        download.extras.getBoolean("retryRequested", false)
                ) {
                    extras(download, mapOf("retryRequested" to "false"))
                    retryAutomatically(download)
                }
            }
        }
    }

    suspend fun stop() = controlMutex.withLock {
        await<List<Download>> { success, failure ->
            fetch.pauseAll()
            fetch.getDownloads { success(it) }
        }
        live.clear()
    }

    fun needsWork(download: Download): Boolean =
        !download.extras.getBoolean("paused", false) &&
            (download.status in
                listOf(Status.ADDED, Status.PAUSED, Status.QUEUED, Status.DOWNLOADING) ||
                download.extras.getBoolean("retryRequested", false) ||
                download.extras.getLong("retryAt", 0) > 0 ||
                (download.status == Status.COMPLETED &&
                    !download.extras.getBoolean("finalized", false) &&
                    download.extras.getString("failure", "").isEmpty()))

    fun problem(download: Download): String = http.problem(download.id)

    suspend fun retryAutomatically(download: Download) = apiMutex.withLock {
        val current =
            awaitUnlocked<List<Download>> { success, _ -> fetch.getDownloads { success(it) } }
                .firstOrNull { it.id == download.id } ?: return@withLock
        if (current.extras.getBoolean("paused", false)) return@withLock
        http.clearProblem(current.id)
        awaitUnlocked<Download> { success, failure -> fetch.retry(current.id, success, failure) }
        Unit
    }

    private suspend fun <T> await(operation: ((T) -> Unit, (FetchError) -> Unit) -> Unit): T =
        apiMutex.withLock {
            awaitUnlocked(operation)
        }

    private suspend fun <T> awaitUnlocked(
        operation: ((T) -> Unit, (FetchError) -> Unit) -> Unit
    ): T = suspendCancellableCoroutine { continuation ->
        operation(
            { if (continuation.isActive) continuation.resume(it) },
            {
                if (continuation.isActive)
                    continuation.resumeWithException(IllegalStateException(it.name, it.throwable))
            },
        )
    }
}
