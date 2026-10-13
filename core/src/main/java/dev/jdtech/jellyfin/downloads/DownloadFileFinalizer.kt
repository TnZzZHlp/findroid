package dev.jdtech.jellyfin.downloads

import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Status
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DownloadFileFinalizer @Inject constructor(private val database: ServerDatabaseDao) {
    private val mutex = Mutex()

    suspend fun finalize(download: Download): Boolean = mutex.withLock {
        if (download.status != Status.COMPLETED) return@withLock false
        database.getSourceByDownloadId(download.id.toLong())?.let { source ->
            return@withLock finalizeFile(source.path, download) {
                database.setSourcePath(source.id, it)
            }
        }
        database.getMediaStreamByDownloadId(download.id.toLong())?.let { stream ->
            return@withLock finalizeFile(stream.path, download) {
                database.setMediaStreamPath(stream.id, it)
            }
        }
        false
    }

    private suspend fun finalizeFile(
        path: String,
        download: Download,
        update: suspend (String) -> Unit,
    ): Boolean {
        val finalPath = path.removeSuffix(".download")
        if (finalPath != download.file.removeSuffix(".download")) return false
        val temporary = File(download.file)
        val file = if (temporary.isFile) temporary else File(finalPath)
        if (
            !file.isFile ||
                file.length() <= 0 ||
                (download.total > 0 &&
                    (file.length() != download.total || download.downloaded != download.total))
        )
            return false
        if (file == temporary && temporary.path != finalPath && !file.renameTo(File(finalPath)))
            return false
        if (path != finalPath) update(finalPath)
        return true
    }
}
