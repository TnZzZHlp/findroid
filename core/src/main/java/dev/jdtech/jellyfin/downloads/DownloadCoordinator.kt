package dev.jdtech.jellyfin.downloads

import android.content.Context
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error as FetchError
import com.tonyodev.fetch2.Status
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class DownloadCoordinator
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val engine: FetchDownloadEngine,
    private val downloader: Downloader,
    private val scheduler: DownloadScheduler,
    private val cleanup: LegacyDownloadCleanup,
    private val database: ServerDatabaseDao,
    private val preferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val execution = Mutex()

    fun initialize() {
        scope.launch {
            try {
                prepare()
                if (engine.refresh().any { engine.needsWork(it) }) scheduler.schedule()
            } catch (e: Exception) {
                Timber.e("Unable to restore the download queue")
            }
        }
    }

    private suspend fun prepare() {
        cleanup.run()
        val serverId = preferences.getValue(preferences.currentServer)
        val userId = serverId?.let { database.getServerCurrentUser(it)?.id?.toString() }
        downloader.discardOtherIdentities(serverId, userId)
    }

    suspend fun run(updateNotification: suspend (List<DownloadTask>) -> Unit): Boolean =
        execution.withLock {
            execute(updateNotification)
        }

    private suspend fun execute(updateNotification: suspend (List<DownloadTask>) -> Unit): Boolean {
        prepare()
        try {
            engine.start()
            while (true) {
                engine.activate()
                val downloads = engine.refresh()
                downloads.forEach { download ->
                    when (download.status) {
                        Status.COMPLETED -> {
                            if (
                                !download.extras.getBoolean("finalized", false) &&
                                    download.extras.getString("failure", "").isEmpty()
                            ) {
                                val success = downloader.finalizeDownload(download.id.toLong())
                                engine.extras(
                                    download,
                                    if (success) mapOf("finalized" to "true")
                                    else
                                        mapOf(
                                            "failure" to
                                                context.getString(R.string.download_error_finalize)
                                        ),
                                )
                            }
                        }
                        Status.FAILED -> recover(download)
                        else -> Unit
                    }
                }
                val updated = engine.refresh()
                updateNotification(engine.tasks.value)
                val active = updated.any {
                    !it.extras.getBoolean("paused", false) &&
                        (it.status in
                            listOf(
                                Status.ADDED,
                                Status.PAUSED,
                                Status.QUEUED,
                                Status.DOWNLOADING,
                            ) || it.extras.getBoolean("retryRequested", false))
                }
                val retry = updated.any {
                    !it.extras.getBoolean("paused", false) &&
                        it.status == Status.FAILED &&
                        it.extras.getLong("retryAt", 0) > 0
                }
                if (!active) return retry
                delay(1000)
            }
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                engine.stop()
                engine.refresh()
            }
        }
    }

    private suspend fun recover(download: Download) {
        if (download.extras.getBoolean("paused", false)) return
        val problem = engine.problem(download)
        if (
            problem == "unvalidated" ||
                (problem in setOf("reset", "entity") && download.extras.getInt("resets", 0) < 2)
        ) {
            engine.reset(download)
            return
        }
        val code = problem.substringAfter("http:", "").toIntOrNull()
        val transient =
            (code != null && (code >= 500 || code == 408 || code == 429)) ||
                (problem !in setOf("reset", "entity", "unvalidated", "content") &&
                    code == null &&
                    download.error in
                        listOf(
                            FetchError.CONNECTION_TIMED_OUT,
                            FetchError.UNKNOWN_HOST,
                            FetchError.NO_NETWORK_CONNECTION,
                            FetchError.UNKNOWN_IO_ERROR,
                            FetchError.UNKNOWN,
                            FetchError.EMPTY_RESPONSE_FROM_SERVER,
                        ))
        val attempts = download.extras.getInt("attempts", 0)
        val now = System.currentTimeMillis()
        val retryAt = download.extras.getLong("retryAt", 0)
        if (transient && (attempts < 5 || retryAt > 0)) {
            if (retryAt == 0L) {
                engine.extras(
                    download,
                    mapOf(
                        "attempts" to (attempts + 1).toString(),
                        "retryAt" to (now + (30_000L shl attempts)).toString(),
                        "failure" to errorMessage(download, code),
                    ),
                )
            } else if (now >= retryAt) {
                engine.extras(download, mapOf("retryAt" to "0", "failure" to ""))
                engine.retryAutomatically(download)
            }
        } else if (retryAt != 0L || download.extras.getString("failure", "").isEmpty()) {
            engine.extras(
                download,
                mapOf("retryAt" to "0", "failure" to errorMessage(download, code)),
            )
        }
    }

    private fun errorMessage(download: Download, code: Int?): String =
        when {
            code != null -> context.getString(R.string.download_error_http, code)
            engine.problem(download) == "content" ->
                context.getString(R.string.download_error_content)
            engine.problem(download) in setOf("reset", "entity", "unvalidated") ->
                context.getString(R.string.download_error_range)
            download.error == FetchError.NO_STORAGE_SPACE ->
                context.getString(R.string.download_error_storage)
            download.error == FetchError.WRITE_PERMISSION_DENIED ->
                context.getString(R.string.download_error_permission)
            else -> context.getString(R.string.downloading_error)
        }
}
