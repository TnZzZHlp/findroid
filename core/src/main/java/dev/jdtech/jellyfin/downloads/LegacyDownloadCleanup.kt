package dev.jdtech.jellyfin.downloads

import android.app.DownloadManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LegacyDownloadCleanup
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: ServerDatabaseDao,
    private val engine: FetchDownloadEngine,
) {
    private val mutex = Mutex()
    private val preferences =
        context.getSharedPreferences("download-migrations", Context.MODE_PRIVATE)
    private var deferred = false

    suspend fun run() = mutex.withLock {
        if (preferences.getBoolean("fetch-migrated", false)) return@withLock
        deferred = false
        val managed = engine.downloads().associateBy { it.id.toLong() }
        val manager = context.getSystemService(DownloadManager::class.java)
        database.getIncompleteSources().forEach { source ->
            if (managed[source.downloadId]?.file == source.path) return@forEach
            if (
                finalizeLegacy(manager, source.downloadId, source.path) {
                    database.setSourcePath(source.id, it)
                }
            )
                return@forEach
            source.downloadId?.let { runCatching { manager.remove(it) } }
            database.getMediaStreamsBySourceId(source.id).forEach { stream ->
                stream.downloadId?.let { runCatching { manager.remove(it) } }
                if (stream.isExternal) File(stream.path).delete()
            }
            File(source.path).delete()
            database.deleteMediaStreamsBySourceId(source.id)
            database.deleteSource(source.id)
        }
        database.getIncompleteMediaStreams().forEach { stream ->
            if (managed[stream.downloadId]?.file == stream.path) return@forEach
            if (
                finalizeLegacy(manager, stream.downloadId, stream.path) {
                    database.setMediaStreamPath(stream.id, it)
                }
            )
                return@forEach
            stream.downloadId?.let { runCatching { manager.remove(it) } }
            File(stream.path).delete()
            database.deleteMediaStream(stream.id)
        }
        if (!deferred) preferences.edit().putBoolean("fetch-migrated", true).commit()
    }

    private suspend fun finalizeLegacy(
        manager: DownloadManager,
        id: Long?,
        path: String,
        update: suspend (String) -> Unit,
    ): Boolean {
        if (id == null) return false
        val completed =
            runCatching {
                manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    Triple(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                            )
                        ),
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        ),
                    )
                }
            }
                .getOrNull() ?: return false
        if (completed.first != DownloadManager.STATUS_SUCCESSFUL) return false
        val temporary = File(path)
        val final = File(path.removeSuffix(".download"))
        val file = if (temporary.isFile) temporary else final
        if (
            !file.isFile ||
                file.length() <= 0 ||
                (completed.second > 0 && file.length() != completed.second) ||
                (completed.third > 0 && file.length() != completed.third) ||
                (file == temporary && temporary != final && !temporary.renameTo(final))
        ) {
            deferred = true
            return true
        }
        update(final.path)
        return true
    }
}
