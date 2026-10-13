package dev.jdtech.jellyfin.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2core.Extras
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.downloads.DownloadFileFinalizer
import dev.jdtech.jellyfin.downloads.DownloadProgress
import dev.jdtech.jellyfin.downloads.DownloadScheduler
import dev.jdtech.jellyfin.downloads.FetchDownloadEngine
import dev.jdtech.jellyfin.downloads.LegacyDownloadCleanup
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.FindroidTrickplayInfo
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isPlayableLocalFile
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
    private val engine: FetchDownloadEngine,
    private val scheduler: DownloadScheduler,
    private val finalizer: DownloadFileFinalizer,
    private val legacyCleanup: LegacyDownloadCleanup,
) : Downloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> =
        scope
            .async { operationMutex.withLock { prepareDownload(item, sourceId, storageIndex) } }
            .await()

    private suspend fun prepareDownload(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> {
        legacyCleanup.run()
        var enqueuedDownloadId: Long? = null
        try {
            if (
                database.getSources(item.id).any {
                    it.toFindroidSource(database).isPlayableLocalFile()
                }
            ) {
                return Pair(-1, UiText.StringResource(CoreR.string.download_completed))
            }
            val mediaSources = jellyfinRepository.getMediaSources(item.id, true)
            val source =
                mediaSources.firstOrNull { it.id == sourceId }
                    ?: mediaSources.firstOrNull()
                    ?: return Pair(
                        -1,
                        UiText.StringResource(CoreR.string.downloading_error),
                    )
            val localSourceId = localSourceId(item.id, UUID.randomUUID().toString())
            val segments = jellyfinRepository.getSegments(item.id)
            val trickplayInfo =
                if (item is FindroidSources) {
                    item.trickplayInfo?.get(source.id)
                } else {
                    null
                }
            val storageLocation = context.getExternalFilesDirs(null).getOrNull(storageIndex)
            if (
                storageLocation == null ||
                    Environment.getExternalStorageState(storageLocation) !=
                        Environment.MEDIA_MOUNTED
            ) {
                return Pair(
                    -1,
                    UiText.StringResource(CoreR.string.storage_unavailable),
                )
            }
            val path = Uri.fromFile(File(storageLocation, "downloads/$localSourceId.download"))
            val stats = StatFs(storageLocation.path)
            if (stats.availableBytes < source.size) {
                return Pair(
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

            database
                .getSources(item.id)
                .map { it.toFindroidSource(database) }
                .forEach { deleteSource(item.id, it) }
            val sourceDto =
                source.toFindroidSourceDto(
                    itemId = item.id,
                    path = path.path.orEmpty(),
                    localSourceId = localSourceId,
                )

            database.insertSource(sourceDto)

            val request =
                createRequest(
                    source.path,
                    path.path.orEmpty(),
                    item,
                    localSourceId,
                    true,
                    item.name,
                )
            database.setSourceDownloadId(localSourceId, request.id.toLong())
            val downloadId = engine.enqueue(request)
            enqueuedDownloadId = downloadId
            scheduler.schedule(immediate = true)
            database.insertUserData(item.toFindroidUserDataDto(jellyfinRepository.getUserId()))

            saveVideoStreamMetadata(source, localSourceId)
            downloadExternalMediaStreams(item, source, localSourceId, storageIndex)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, localSourceId, trickplayInfo)
            }

            startImagesDownloader(item)
            scheduler.schedule()
            return Pair(downloadId, null)
        } catch (e: Exception) {
            enqueuedDownloadId?.let { engine.delete(it) }
            try {
                database
                    .getSources(item.id)
                    .firstOrNull { it.path.endsWith(".download") }
                    ?.toFindroidSource(database)
                    ?.let { deleteItem(item, it) }
            } catch (_: Exception) {}
            Timber.e("Unable to prepare a media download")
            return Pair(-1, UiText.StringResource(CoreR.string.downloading_error))
        }
    }

    override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) =
        operationMutex.withLock {
            val source =
                (database.getSourceByDownloadId(downloadId)
                        ?: database.getSources(item.id).firstOrNull {
                            it.type == dev.jdtech.jellyfin.models.FindroidSourceType.LOCAL
                        })
                    ?.toFindroidSource(database) ?: return@withLock
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

    private suspend fun deleteSource(itemId: UUID, source: FindroidSource) {
        engine
            .downloads()
            .filter { it.extras.getString("sourceId", "") == source.id }
            .forEach { engine.delete(it.id.toLong()) }
        File(source.path).delete()
        File(source.path.removeSuffix(".download")).delete()
        database.getMediaStreamsBySourceId(source.id).forEach { mediaStream ->
            if (mediaStream.isExternal) {
                File(mediaStream.path).delete()
                File(mediaStream.path.removeSuffix(".download")).delete()
            }
        }
        database.deleteMediaStreamsBySourceId(source.id)
        database.deleteSource(source.id)
        File(context.filesDir, "trickplay/$itemId/${source.id}").deleteRecursively()
    }

    override suspend fun cancelTask(downloadId: Long): Unit = operationMutex.withLock {
        val source = database.getSourceByDownloadId(downloadId)
        if (source != null) {
            deleteSource(source.itemId, source.toFindroidSource(database))
        } else {
            engine.delete(downloadId)
            database.getMediaStreamByDownloadId(downloadId)?.let { stream ->
                File(stream.path).delete()
                File(stream.path.removeSuffix(".download")).delete()
                database.deleteMediaStream(stream.id)
            }
        }
    }

    override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> {
        val snapshot = getSnapshot(downloadId)
        return snapshot.status to (snapshot.progress * 100).toInt()
    }

    override suspend fun getSnapshot(downloadId: Long?): DownloadProgress {
        val download =
            engine.refresh().firstOrNull { it.id.toLong() == downloadId }
                ?: return DownloadProgress()
        return engine.progress(download)
    }

    override suspend fun finalizeDownload(downloadId: Long): Boolean {
        val download =
            engine.downloads().firstOrNull { it.id.toLong() == downloadId } ?: return false
        return finalizer.finalize(download)
    }

    override suspend fun pauseDownload(downloadId: Long) {
        group(downloadId)
            .filter { it.status != Status.COMPLETED }
            .forEach { engine.pause(it.id.toLong()) }
    }

    override suspend fun resumeDownload(downloadId: Long) {
        group(downloadId)
            .filter { it.status != Status.COMPLETED }
            .forEach { engine.resume(it.id.toLong()) }
        scheduler.schedule(immediate = true)
    }

    override suspend fun retryDownload(downloadId: Long) {
        group(downloadId)
            .filter {
                it.status == Status.FAILED ||
                    (it.status == Status.COMPLETED &&
                        it.extras.getString("failure", "").isNotEmpty())
            }
            .forEach { engine.retry(it.id.toLong()) }
        scheduler.schedule(immediate = true)
    }

    private suspend fun group(downloadId: Long): List<com.tonyodev.fetch2.Download> {
        val downloads = engine.downloads()
        val task = downloads.firstOrNull { it.id.toLong() == downloadId } ?: return emptyList()
        if (!task.extras.getBoolean("primary", false)) return listOf(task)
        val sourceId = task.extras.getString("sourceId", "")
        return downloads.filter { it.extras.getString("sourceId", "") == sourceId }
    }

    override suspend fun discardOtherIdentities(serverId: String?, userId: String?) =
        operationMutex.withLock {
            legacyCleanup.run()
            engine
                .downloads()
                .filter {
                    (it.extras.getString("serverId", "") != serverId ||
                        it.extras.getString("userId", "") != userId)
                }
                .forEach { task ->
                    if (
                        task.status == Status.COMPLETED &&
                            (task.extras.getBoolean("finalized", false) || finalizer.finalize(task))
                    )
                        return@forEach
                    val source = database.getSourceByDownloadId(task.id.toLong())
                    if (source != null) {
                        deleteSource(source.itemId, source.toFindroidSource(database))
                    } else {
                        engine
                            .downloads()
                            .firstOrNull { it.id == task.id }
                            ?.let { engine.delete(task.id.toLong()) }
                        database.getMediaStreamByDownloadId(task.id.toLong())?.let { stream ->
                            File(stream.path).delete()
                            File(stream.path.removeSuffix(".download")).delete()
                            database.deleteMediaStream(stream.id)
                        }
                    }
                }
        }

    private suspend fun createRequest(
        url: String,
        path: String,
        item: FindroidItem,
        localSourceId: String,
        primary: Boolean,
        title: String,
    ): Request {
        val serverId = appPreferences.getValue(appPreferences.currentServer) ?: error("No server")
        val userId = jellyfinRepository.getUserId()
        check(database.getServerCurrentUser(serverId)?.id == userId)
        File(path).parentFile?.mkdirs()
        val uri = Uri.parse(url)
        val origin = Uri.parse(jellyfinRepository.getBaseUrl())
        check(
            uri.scheme in setOf("http", "https") &&
                uri.scheme == origin.scheme &&
                uri.host.equals(origin.host, ignoreCase = true) &&
                (uri.port.takeIf { it >= 0 } ?: if (uri.scheme == "https") 443 else 80) ==
                    (origin.port.takeIf { it >= 0 } ?: if (origin.scheme == "https") 443 else 80)
        )
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames
            .filterNot { it.equals("api_key", true) || it.equals("access_token", true) }
            .forEach { name ->
                uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
            }
        return Request(builder.build().toString(), path).apply {
            tag = title
            extras =
                Extras(
                    mapOf(
                        "itemId" to item.id.toString(),
                        "sourceId" to localSourceId,
                        "serverId" to serverId,
                        "userId" to userId.toString(),
                        "primary" to primary.toString(),
                        "movie" to (item is FindroidMovie).toString(),
                    )
                )
        }
    }

    private suspend fun saveVideoStreamMetadata(source: FindroidSource, localSourceId: String) {
        val videoStream =
            source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO } ?: return
        // This metadata-only row has no separately downloaded stream file.
        database.insertMediaStream(
            videoStream
                .copy(isExternal = false, path = null)
                .toFindroidMediaStreamDto(
                    id = UUID.randomUUID(),
                    sourceId = localSourceId,
                    path = "",
                )
        )
    }

    private suspend fun downloadExternalMediaStreams(
        item: FindroidItem,
        source: FindroidSource,
        localSourceId: String,
        storageIndex: Int = 0,
    ) {
        val storageLocation = context.getExternalFilesDirs(null).getOrNull(storageIndex)
        for (mediaStream in source.mediaStreams.filter { it.isExternal }) {
            val url = mediaStream.path ?: continue
            val id = UUID.randomUUID()
            val streamPath =
                Uri.fromFile(File(storageLocation, "downloads/$localSourceId.$id.download"))
            database.insertMediaStream(
                mediaStream.toFindroidMediaStreamDto(
                    id,
                    localSourceId,
                    streamPath.path.orEmpty(),
                )
            )
            val request =
                createRequest(
                    url,
                    streamPath.path.orEmpty(),
                    item,
                    localSourceId,
                    false,
                    "${item.name} - ${mediaStream.title}",
                )
            database.setMediaStreamDownloadId(id, request.id.toLong())
            engine.enqueue(request)
            scheduler.schedule()
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

    private suspend fun saveTrickplayData(
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
