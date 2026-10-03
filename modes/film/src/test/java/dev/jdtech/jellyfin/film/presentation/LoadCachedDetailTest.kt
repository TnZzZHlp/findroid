package dev.jdtech.jellyfin.film.presentation

import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadCachedDetailTest {
    @Test
    fun `persistent cache is displayed before network refresh starts`() = runBlocking {
        val displayed = mutableListOf<String>()
        loadCachedDetail(
            readCache = { "downloaded page" },
            fetchRemote = { hasCachedData ->
                assertTrue(hasCachedData)
                assertEquals(listOf("downloaded page"), displayed)
                "refreshed page"
            },
            onLoaded = { displayed += it },
            onError = { throw AssertionError(it) },
        )
        assertEquals(listOf("downloaded page", "refreshed page"), displayed)
    }

    @Test
    fun `unreachable server retains downloaded page`() = runBlocking {
        val displayed = mutableListOf<String>()
        loadCachedDetail(
            readCache = { "downloaded page" },
            fetchRemote = { throw IOException("Server unavailable") },
            onLoaded = { displayed += it },
            onError = { throw AssertionError(it) },
        )
        assertEquals(listOf("downloaded page"), displayed)
    }

    @Test
    fun `refresh timeout retains downloaded page`() = runBlocking {
        val displayed = mutableListOf<String>()
        loadCachedDetail(
            readCache = { "downloaded page" },
            fetchRemote = { awaitCancellation() },
            onLoaded = { displayed += it },
            onError = { throw AssertionError(it) },
            refreshTimeoutMillis = 30,
        )
        assertEquals(listOf("downloaded page"), displayed)
    }

    @Test
    fun `missing cache and failed request produce error instead of endless loading`() =
        runBlocking {
            val failure = IOException("No connection")
            val errors = mutableListOf<Exception>()
            loadCachedDetail<String>(
                readCache = { null },
                fetchRemote = { hasCachedData ->
                    assertTrue(!hasCachedData)
                    throw failure
                },
                onLoaded = { throw AssertionError("No page should be loaded") },
                onError = { errors += it },
            )
            assertEquals(1, errors.size)
            assertEquals(failure.javaClass, errors.single().javaClass)
            assertEquals(failure.message, errors.single().message)
        }

    @Test
    fun `missing cache and hanging request end with a timeout error`() = runBlocking {
        val errors = mutableListOf<Exception>()
        loadCachedDetail<String>(
            readCache = { null },
            fetchRemote = { awaitCancellation() },
            onLoaded = { throw AssertionError("No page should be loaded") },
            onError = { errors += it },
            refreshTimeoutMillis = 30,
        )
        assertEquals(1, errors.size)
        assertTrue(errors.single() is TimeoutCancellationException)
    }

    @Test
    fun `cache read failure still allows online loading`() = runBlocking {
        val displayed = mutableListOf<String>()
        loadCachedDetail(
            readCache = { throw IOException("Missing metadata") },
            fetchRemote = { "online page" },
            onLoaded = { displayed += it },
            onError = { throw AssertionError(it) },
        )
        assertEquals(listOf("online page"), displayed)
    }

    @Test
    fun `cancelling refresh does not emit an error or stale remote state`() = runBlocking {
        val displayed = mutableListOf<String>()
        val job = launch {
            loadCachedDetail(
                readCache = { "downloaded page" },
                fetchRemote = { awaitCancellation() },
                onLoaded = { displayed += it },
                onError = { throw AssertionError(it) },
            )
        }
        // Let the child publish the cache and suspend inside refresh.
        kotlinx.coroutines.yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf("downloaded page"), displayed)
    }

    @Test
    fun `cancelling cache read does not start network refresh`() = runBlocking {
        val job = launch {
            loadCachedDetail<String>(
                readCache = { awaitCancellation() },
                fetchRemote = { throw AssertionError("Cancelled read must not start refresh") },
                onLoaded = { throw AssertionError("Cancelled read must not publish data") },
                onError = { throw AssertionError(it) },
            )
        }
        kotlinx.coroutines.yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
