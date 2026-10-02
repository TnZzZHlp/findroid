package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.Downloader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadsViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `confirmed deletion cleans missing local source only for selected episode`() = runBlocking {
        val disappearedFile = temporaryFolder.newFile("episode.mkv")
        assertTrue(disappearedFile.delete())
        assertFalse(disappearedFile.exists())

        val localSource = source("local-selected", disappearedFile, FindroidSourceType.LOCAL)
        val remoteFile = temporaryFolder.newFile("remote.mkv")
        val remoteSource = source("remote-selected", remoteFile, FindroidSourceType.REMOTE)
        val selectedEpisode = episode(1, listOf(localSource, remoteSource))
        val unrelatedEpisode =
            episode(
                2,
                listOf(
                    source(
                        "local-unrelated",
                        File(temporaryFolder.root, "unrelated.mkv"),
                        FindroidSourceType.LOCAL,
                    )
                ),
            )
        val downloader = RecordingDownloader()

        deleteDownloadedEpisode(selectedEpisode, downloader)

        assertEquals(
            listOf(selectedEpisode.id to localSource.id),
            downloader.deleted.map { (item, source) -> item.id to source.id },
        )
        assertFalse(downloader.deleted.any { (item, _) -> item.id == unrelatedEpisode.id })
    }

    private fun source(id: String, file: File, type: FindroidSourceType) =
        FindroidSource(
            id = id,
            name = id,
            type = type,
            path = file.path,
            size = file.length(),
            mediaStreams = emptyList(),
        )

    private fun episode(id: Long, sources: List<FindroidSource>) =
        FindroidEpisode(
            id = UUID(0, id),
            name = "Episode $id",
            originalTitle = null,
            overview = "",
            indexNumber = id.toInt(),
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = sources,
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0,
            playbackPositionTicks = 0,
            premiereDate = null,
            seriesId = UUID(0, 100),
            seriesName = "Show",
            seasonId = UUID(0, 101),
            seasonName = "Season 1",
            communityRating = null,
            people = emptyList(),
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private class RecordingDownloader : Downloader {
        val deleted = mutableListOf<Pair<FindroidItem, FindroidSource>>()

        override suspend fun downloadItem(
            item: FindroidItem,
            sourceId: String,
            storageIndex: Int,
        ): Pair<Long, UiText?> = error("Unexpected download request")

        override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) =
            error("Unexpected cancel request")

        override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) {
            deleted += item to source
        }

        override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> =
            error("Unexpected progress request")

        override suspend fun finalizeDownload(downloadId: Long): Boolean =
            error("Unexpected finalization request")
    }
}
