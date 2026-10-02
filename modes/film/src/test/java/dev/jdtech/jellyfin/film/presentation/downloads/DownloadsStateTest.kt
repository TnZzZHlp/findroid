package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsStateTest {
    @Test
    fun `empty downloads produce no groups`() {
        assertEquals(emptyList<DownloadedShowGroup>(), groupDownloadedEpisodes(emptyList()))
    }

    @Test
    fun `shows are sorted by name and episodes by season then episode`() {
        val laterShow = episode(1, 1, "Zulu", 1, 1)
        val secondSeason = episode(2, 2, "alpha", 2, 1)
        val secondEpisode = episode(3, 2, "alpha", 1, 2)
        val firstEpisode = episode(4, 2, "alpha", 1, 1)
        val groups =
            groupDownloadedEpisodes(listOf(laterShow, secondSeason, secondEpisode, firstEpisode))

        assertEquals(listOf("alpha", "Zulu"), groups.map { it.name })
        assertEquals(listOf(firstEpisode, secondEpisode, secondSeason), groups.first().episodes)
        assertEquals(listOf(laterShow), groups.last().episodes)
    }

    @Test
    fun `different shows with identical names remain separate`() {
        val first = episode(1, 1, "Same title", 1, 1)
        val second = episode(2, 2, "Same title", 1, 1)
        val groups = groupDownloadedEpisodes(listOf(second, first))

        assertEquals(listOf(first.seriesId, second.seriesId), groups.map { it.seriesId })
        assertEquals(listOf(listOf(first), listOf(second)), groups.map { it.episodes })
    }

    @Test
    fun `specials precede regular seasons and multi episode entries remain intact`() {
        val regular = episode(1, 1, "Show", 1, 1).copy(indexNumberEnd = 2)
        val special = episode(2, 1, "Show", 0, 1)
        val groups = groupDownloadedEpisodes(listOf(regular, special))

        assertEquals(listOf(special, regular), groups.single().episodes)
        assertEquals(2, groups.single().episodes.last().indexNumberEnd)
    }

    private fun episode(id: Long, series: Long, seriesName: String, season: Int, number: Int) =
        FindroidEpisode(
            id = UUID(0, id),
            name = "Episode $number",
            originalTitle = null,
            overview = "",
            indexNumber = number,
            indexNumberEnd = null,
            parentIndexNumber = season,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0,
            playbackPositionTicks = 0,
            premiereDate = null,
            seriesId = UUID(0, series),
            seriesName = seriesName,
            seasonId = UUID(series, season.toLong()),
            seasonName = null,
            communityRating = null,
            people = emptyList(),
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )
}
