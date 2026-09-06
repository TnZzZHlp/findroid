package dev.jdtech.jellyfin.film.presentation.home

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFilterTest {
    private val library = UUID(0, 1)
    private val episode = UUID(0, 2)
    private val movie = UUID(0, 3)

    @Test
    fun `hidden ancestors remove episodes and reuse membership across sections`() = runBlocking {
        var requests = 0
        val filter =
            HomeFilter(setOf(library)) { item ->
                requests++
                if (item == episode) setOf(UUID(0, 4), UUID(0, 5), library) else emptySet()
            }
        assertEquals(listOf(movie), filter.filter(listOf(episode, movie)) { it })
        assertEquals(listOf(movie), filter.filter(listOf(movie, episode)) { it })
        assertEquals(2, requests)
        assertEquals(emptyList<UUID>(), filter.filter(listOf(library, episode)) { it })
        assertEquals(2, requests)
    }

    @Test
    fun `empty selection and tv bypass membership queries`() = runBlocking {
        val filter = HomeFilter(emptySet()) { error("Must not query ancestors") }
        val items = listOf(episode, movie)
        assertEquals(items, filter.filter(items) { it })
    }

    @Test
    fun `new refresh does not retain old server membership`() = runBlocking {
        val first = HomeFilter(setOf(library)) { setOf(library) }
        val second = HomeFilter(setOf(library)) { emptySet() }
        assertEquals(emptyList<UUID>(), first.filter(listOf(episode)) { it })
        assertEquals(listOf(episode), second.filter(listOf(episode)) { it })
    }

    @Test(expected = IllegalStateException::class)
    fun `membership errors do not expose unfiltered content`() = runBlocking {
        HomeFilter(setOf(library)) { error("Network failure") }.filter(listOf(episode)) { it }
        Unit
    }
}
