package dev.jdtech.jellyfin.film.presentation

import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeViewModel
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.film.presentation.show.ShowViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.model.api.ItemFields
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDetailViewModelsTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `show renders downloaded seasons and next up before server responds`() = runBlocking {
        val show = dummyShow
        val season = dummySeason.copy(seriesId = show.id)
        val episode = dummyEpisode.copy(seriesId = show.id, seasonId = season.id)
        val refreshStarted = CompletableDeferred<Unit>()
        val repository =
            object : JellyfinRepository by unexpectedRepository() {
                override suspend fun getLocalShow(itemId: UUID): FindroidShow {
                    assertEquals(show.id, itemId)
                    return show
                }

                override suspend fun getLocalNextUp(seriesId: UUID): List<FindroidEpisode> {
                    assertEquals(show.id, seriesId)
                    return listOf(episode)
                }

                override suspend fun getSeasons(
                    seriesId: UUID,
                    localOnly: Boolean,
                    forceRefresh: Boolean,
                ): List<FindroidSeason> {
                    assertEquals(show.id, seriesId)
                    check(localOnly)
                    return listOf(season)
                }

                override suspend fun getShow(itemId: UUID, forceRefresh: Boolean): FindroidShow {
                    check(forceRefresh)
                    refreshStarted.complete(Unit)
                    awaitCancellation()
                }
            }
        val viewModel = ShowViewModel(repository)
        try {
            viewModel.loadShow(show.id)
            val state = withTimeout(5_000) { viewModel.state.first { it.show != null } }
            withTimeout(5_000) { refreshStarted.await() }
            assertEquals(show, state.show)
            assertEquals(listOf(season), state.seasons)
            assertEquals(episode, state.nextUp)
            assertNull(state.error)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `season retains local episodes when server is unreachable`() = runBlocking {
        val season = dummySeason
        val episode = dummyEpisode.copy(seriesId = season.seriesId, seasonId = season.id)
        val repository =
            object : JellyfinRepository by unexpectedRepository() {
                override suspend fun getLocalSeason(itemId: UUID): FindroidSeason = season

                override suspend fun getEpisodes(
                    seriesId: UUID,
                    seasonId: UUID,
                    fields: List<ItemFields>?,
                    startItemId: UUID?,
                    limit: Int?,
                    localOnly: Boolean,
                ): List<FindroidEpisode> {
                    check(localOnly)
                    assertEquals(season.id, seasonId)
                    return listOf(episode)
                }

                override suspend fun getSeason(itemId: UUID): FindroidSeason {
                    throw IOException("Server unavailable")
                }
            }
        val viewModel = SeasonViewModel(repository)
        try {
            viewModel.loadSeason(season.id)
            val state = withTimeout(5_000) { viewModel.state.first { it.season != null } }
            assertEquals(season, state.season)
            assertEquals(listOf(episode), state.episodes)
            assertNull(state.error)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `episode metadata parsing failure cannot block downloaded details`() = runBlocking {
        val source = dummyEpisode.sources.single()
        val episode =
            dummyEpisode.copy(
                sources =
                    listOf(
                        source.copy(
                            mediaStreams = source.mediaStreams.map { it.copy(height = null) }
                        )
                    )
            )
        val refreshStarted = CompletableDeferred<Unit>()
        val repository =
            object : JellyfinRepository by unexpectedRepository() {
                override suspend fun getLocalEpisode(itemId: UUID): FindroidEpisode = episode

                override suspend fun getEpisode(itemId: UUID): FindroidEpisode {
                    refreshStarted.complete(Unit)
                    awaitCancellation()
                }
            }
        val preferences =
            AppPreferences(
                Proxy.newProxyInstance(
                    SharedPreferences::class.java.classLoader,
                    arrayOf(SharedPreferences::class.java),
                ) { _, method, _ ->
                    check(method.name == "getBoolean")
                    false
                } as SharedPreferences
            )
        val viewModel = EpisodeViewModel(repository, preferences, VideoMetadataParser)
        try {
            viewModel.loadEpisode(episode.id)
            val state = withTimeout(5_000) { viewModel.state.first { it.episode != null } }
            withTimeout(5_000) { refreshStarted.await() }
            assertEquals(episode, state.episode)
            assertNull(state.videoMetadata)
            assertNull(state.error)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun unexpectedRepository(): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            throw AssertionError("Unexpected repository call: ${method.name}")
        } as JellyfinRepository
}
