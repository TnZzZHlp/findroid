package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.FindroidTrickplayInfo
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.localSourceId
import dev.jdtech.jellyfin.models.toFindroidEpisodeDto
import dev.jdtech.jellyfin.models.toFindroidMediaStreamDto
import dev.jdtech.jellyfin.models.toFindroidMovieDto
import dev.jdtech.jellyfin.models.toFindroidSeasonDto
import dev.jdtech.jellyfin.models.toFindroidSegmentsDto
import dev.jdtech.jellyfin.models.toFindroidShowDto
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.models.toFindroidSourceDto
import dev.jdtech.jellyfin.models.toFindroidTrickplayInfoDto
import dev.jdtech.jellyfin.models.toFindroidUserDataDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import java.io.File
import java.util.UUID
import kotlin.Exception
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
) : Downloader {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val finalizeMutex = Mutex()

    // TODO: We should probably move most (if not all) code to a worker.
    //  At this moment it is possible that some things are not downloaded due to the user leaving
    //  the current screen
    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> = coroutineScope {
        var enqueuedDownloadId: Long? = null
        try {
            val mediaSources = jellyfinRepository.getMediaSources(item.id, true)
            val source =
                mediaSources.firstOrNull { it.id == sourceId } ?: mediaSources.firstOrNull()
                    ?: return@coroutineScope Pair(
                        -1,
                        UiText.StringResource(CoreR.string.downloading_error),
                    )
            val localSourceId = "${localSourceId(item.id, source.id)}-${UUID.randomUUID()}"
            val segments = jellyfinRepository.getSegments(item.id)
            val trickplayInfo =
                if (item is FindroidSources) {
                    item.trickplayInfo?.get(source.id)
                } else {
                    null
                }
            val storageLocation = context.getExternalFilesDirs(null)[storageIndex]
            if (
                storageLocation == null ||
                    Environment.getExternalStorageState(storageLocation) !=
                        Environment.MEDIA_MOUNTED
            ) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(CoreR.string.storage_unavailable),
                )
            }
            val path = Uri.fromFile(File(storageLocation, "downloads/$localSourceId.download"))
            val stats = StatFs(storageLocation.path)
            if (stats.availableBytes < source.size) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, source.size),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }
            when (item) {
                is FindroidMovie -> {
                    database.insertMovie(
                        item.toFindroidMovieDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }
                is FindroidEpisode -> {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(
                        show.toFindroidShowDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toFindroidSeasonDto())
                    database.insertEpisode(
                        item.toFindroidEpisodeDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )

                    startImagesDownloader(show)
                    startImagesDownloader(season)
                }
            }

            database.getSources(item.id).map { it.toFindroidSource(database) }.forEach {
                deleteSource(item.id, it)
            }
            val sourceDto =
                source.toFindroidSourceDto(
                    itemId = item.id,
                    path = path.path.orEmpty(),
                    localSourceId = localSourceId,
                )

            database.insertSource(sourceDto)

            val request =
                DownloadManager.Request(source.path.toUri())
                    .setTitle(item.name)
                    .setAllowedOverMetered(
                        appPreferences.getValue(appPreferences.downloadOverMobileData)
                    )
                    .setAllowedOverRoaming(
                        appPreferences.getValue(appPreferences.downloadWhenRoaming)
                    )
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationUri(path)
            val downloadId = downloadManager.enqueue(request)
            enqueuedDownloadId = downloadId
            database.setSourceDownloadId(localSourceId, downloadId)
            database.insertUserData(item.toFindroidUserDataDto(jellyfinRepository.getUserId()))

            downloadExternalMediaStreams(item, source, localSourceId, storageIndex)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, localSourceId, trickplayInfo)
            }

            startImagesDownloader(item)
            return@coroutineScope Pair(downloadId, null)
        } catch (e: Exception) {
            enqueuedDownloadId?.let { downloadManager.remove(it) }
            try {
                database
                    .getSources(item.id)
                    .firstOrNull { it.path.endsWith(".download") }
                    ?.toFindroidSource(database)
                    ?.let { deleteItem(item, it) }
            } catch (_: Exception) {}
            Timber.e(e)
            return@coroutineScope Pair(
                -1,
                if (e.message != null) UiText.DynamicString(e.message!!)
                else UiText.StringResource(CoreR.string.unknown_error),
            )
        }
    }

    override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) {
        val source =
            database.getSourceByDownloadId(downloadId)?.toFindroidSource(database) ?: return
        deleteItem(item, source)
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) {
        when (item) {
            is FindroidMovie -> {
                database.deleteMovie(item.id)
            }
            is FindroidEpisode -> {
                database.deleteEpisode(item.id)
                val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
                if (remainingEpisodes.isEmpty()) {
                    database.deleteSeason(item.seasonId)
                    database.deleteUserData(item.seasonId)
                    File(context.filesDir, "trickplay/${item.seasonId}").deleteRecursively()
                    File(context.filesDir, "images/${item.seasonId}").deleteRecursively()
                    val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
                    if (remainingSeasons.isEmpty()) {
                        database.deleteShow(item.seriesId)
                        database.deleteUserData(item.seriesId)
                        File(context.filesDir, "trickplay/${item.seriesId}").deleteRecursively()
                        File(context.filesDir, "images/${item.seriesId}").deleteRecursively()
                    }
                }
            }
        }

        deleteSource(item.id, source)

        database.deleteUserData(item.id)

        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    private fun deleteSource(itemId: UUID, source: FindroidSource) {
        source.downloadId?.let { downloadManager.remove(it) }
        File(source.path).delete()
        database.getMediaStreamsBySourceId(source.id).forEach { mediaStream ->
            mediaStream.downloadId?.let { downloadManager.remove(it) }
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(source.id)
        database.deleteSource(source.id)
        File(context.filesDir, "trickplay/$itemId/${source.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> {
        var downloadStatus = -1
        var progress = -1
        if (downloadId == null) {
            return Pair(downloadStatus, progress)
        }
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            downloadStatus =
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (downloadStatus) {
                DownloadManager.STATUS_RUNNING -> {
                    val totalBytes =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                    if (totalBytes > 0) {
                        val downloadedBytes =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            )
                        progress = downloadedBytes.times(100).div(totalBytes).toInt()
                    }
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    progress = 100
                }
            }
        } else {
            downloadStatus = DownloadManager.STATUS_FAILED
        }
        return Pair(downloadStatus, progress)
    }

    override suspend fun finalizeDownload(downloadId: Long): Boolean =
        finalizeMutex.withLock {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val download =
                downloadManager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return@withLock false
                    DownloadResult(
                        status =
                            cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                            ),
                        downloadedBytes =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            ),
                        totalBytes =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                )
                            ),
                    )
                }

            if (download.status != DownloadManager.STATUS_SUCCESSFUL) return@withLock false

            database.getSourceByDownloadId(downloadId)?.let { source ->
                return@withLock finalizeFile(
                    temporaryPath = source.path,
                    download = download,
                    updatePath = { database.setSourcePath(source.id, it) },
                )
            }

            database.getMediaStreamByDownloadId(downloadId)?.let { mediaStream ->
                return@withLock finalizeFile(
                    temporaryPath = mediaStream.path,
                    download = download,
                    updatePath = { database.setMediaStreamPath(mediaStream.id, it) },
                )
            }

            false
        }

    private fun finalizeFile(
        temporaryPath: String,
        download: DownloadResult,
        updatePath: (String) -> Unit,
    ): Boolean {
        val temporaryFile = File(temporaryPath)
        if (!temporaryFile.isFile) return false
        if (temporaryFile.length() <= 0L) return false
        if (
            download.totalBytes > 0L &&
                ((download.downloadedBytes >= 0L &&
                    download.downloadedBytes != download.totalBytes) ||
                    temporaryFile.length() != download.totalBytes)
        ) {
            return false
        }

        if (!temporaryPath.endsWith(".download")) return true

        val finalPath = temporaryPath.removeSuffix(".download")
        if (!temporaryFile.renameTo(File(finalPath))) return false
        updatePath(finalPath)
        return true
    }

    private data class DownloadResult(
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    private fun downloadExternalMediaStreams(
        item: FindroidItem,
        source: FindroidSource,
        localSourceId: String,
        storageIndex: Int = 0,
    ) {
        val storageLocation = context.getExternalFilesDirs(null)[storageIndex]
        for (mediaStream in source.mediaStreams.filter { it.isExternal }) {
            val id = UUID.randomUUID()
            val streamPath =
                Uri.fromFile(
                    File(storageLocation, "downloads/${item.id}.${source.id}.$id.download")
                )
            database.insertMediaStream(
                mediaStream.toFindroidMediaStreamDto(
                    id,
                    localSourceId,
                    streamPath.path.orEmpty(),
                )
            )
            val request =
                DownloadManager.Request(mediaStream.path!!.toUri())
                    .setTitle(mediaStream.title)
                    .setAllowedOverMetered(
                        appPreferences.getValue(appPreferences.downloadOverMobileData)
                    )
                    .setAllowedOverRoaming(
                        appPreferences.getValue(appPreferences.downloadWhenRoaming)
                    )
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    .setDestinationUri(streamPath)
            val downloadId = downloadManager.enqueue(request)
            database.setMediaStreamDownloadId(id, downloadId)
        }
    }

    private suspend fun downloadTrickplayData(
        itemId: UUID,
        localSourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
    ) {
        val maxIndex =
            ceil(
                    trickplayInfo.thumbnailCount
                        .toDouble()
                        .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                )
                .toInt()
        val byteArrays = mutableListOf<ByteArray>()
        for (i in 0..maxIndex) {
            jellyfinRepository.getTrickplayData(itemId, trickplayInfo.width, i)?.let { byteArray ->
                byteArrays.add(byteArray)
            }
        }
        saveTrickplayData(itemId, localSourceId, trickplayInfo, byteArrays)
    }

    private fun saveTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
        byteArrays: List<ByteArray>,
    ) {
        val basePath = "trickplay/$itemId/$sourceId"
        database.insertTrickplayInfo(trickplayInfo.toFindroidTrickplayInfoDto(sourceId))
        File(context.filesDir, basePath).mkdirs()
        for ((i, byteArray) in byteArrays.withIndex()) {
            val file = File(context.filesDir, "$basePath/$i")
            file.writeBytes(byteArray)
        }
    }

    private fun startImagesDownloader(item: FindroidItem) {
        val downloadImagesRequest =
            OneTimeWorkRequestBuilder<ImagesDownloaderWorker>()
                .setInputData(workDataOf(ImagesDownloaderWorker.KEY_ITEM_ID to item.id.toString()))
                .build()

        workManager.enqueue(downloadImagesRequest)
    }
}
