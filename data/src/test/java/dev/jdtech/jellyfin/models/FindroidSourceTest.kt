package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindroidSourceTest {
    private val itemId = UUID.randomUUID()

    @Test
    fun `capped playback does not fall back when source bitrate exceeds cap`() = runBlocking {
        val source = mediaSource(protocol = MediaProtocol.FILE, bitrate = 8_000_000)

        val result =
            source.toFindroidSource(
                repository(),
                itemId,
                forceTranscoding = true,
                maxStreamingBitrate = 5_000_000,
            )

        assertEquals("", result.path)

        val httpSource =
            mediaSource(
                protocol = MediaProtocol.HTTP,
                bitrate = 8_000_000,
                path = "https://media/source.mkv",
            )
        val httpResult =
            httpSource.toFindroidSource(
                repository(),
                itemId,
                forceTranscoding = true,
                maxStreamingBitrate = 5_000_000,
            )

        assertEquals("", httpResult.path)
    }

    @Test
    fun `capped playback does not fall back when source bitrate is unknown or invalid`() =
        runBlocking {
            for (bitrate in listOf(null, 0, -1)) {
                val source = mediaSource(protocol = MediaProtocol.FILE, bitrate = bitrate)

                val result =
                    source.toFindroidSource(
                        repository(),
                        itemId,
                        forceTranscoding = true,
                        maxStreamingBitrate = 5_000_000,
                    )

                assertEquals("", result.path)
            }
        }

    @Test
    fun `capped playback may fall back when total source bitrate fits cap`() = runBlocking {
        val source = mediaSource(protocol = MediaProtocol.FILE, bitrate = 5_000_000)

        val result =
            source.toFindroidSource(
                repository(),
                itemId,
                forceTranscoding = true,
                maxStreamingBitrate = 5_000_000,
            )

        assertEquals("https://server/static-stream", result.path)
    }

    @Test
    fun `available transcoding url is preferred regardless of source bitrate`() = runBlocking {
        val source =
            mediaSource(
                protocol = MediaProtocol.FILE,
                bitrate = 8_000_000,
                transcodingUrl = "/Videos/item/master.m3u8",
            )

        val result =
            source.toFindroidSource(
                repository(),
                itemId,
                forceTranscoding = true,
                maxStreamingBitrate = 5_000_000,
            )

        assertEquals("https://server/Videos/item/master.m3u8", result.path)
    }

    @Test
    fun `uncapped source conversion keeps its existing direct path behavior`() = runBlocking {
        val source = mediaSource(protocol = MediaProtocol.HTTP, path = "https://media/source.mkv")

        val result = source.toFindroidSource(repository(), itemId)

        assertEquals("https://media/source.mkv", result.path)
        assertTrue(source.canFallBackToDirectSource(maxStreamingBitrate = null))
        assertFalse(source.canFallBackToDirectSource(maxStreamingBitrate = 0))
    }

    private fun mediaSource(
        protocol: MediaProtocol,
        bitrate: Int? = null,
        transcodingUrl: String? = null,
        path: String? = null,
    ) =
        MediaSourceInfo(
            protocol = protocol,
            type = MediaSourceType.DEFAULT,
            isRemote = false,
            readAtNativeFramerate = false,
            ignoreDts = false,
            ignoreIndex = false,
            genPtsInput = false,
            supportsTranscoding = false,
            supportsDirectStream = false,
            supportsDirectPlay = false,
            isInfiniteStream = false,
            requiresOpening = false,
            requiresClosing = false,
            requiresLooping = false,
            supportsProbing = false,
            bitrate = bitrate,
            transcodingSubProtocol = MediaStreamProtocol.HLS,
            transcodingUrl = transcodingUrl,
            hasSegments = false,
            path = path,
        )

    private fun repository(): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getStreamUrl" -> "https://server/static-stream"
                "getBaseUrl" -> "https://server/"
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as JellyfinRepository
}
