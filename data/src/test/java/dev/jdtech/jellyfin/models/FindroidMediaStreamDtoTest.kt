package dev.jdtech.jellyfin.models

import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindroidMediaStreamDtoTest {
    @Test
    fun `video metadata survives persisted DTO conversion`() {
        val stream = videoStream(height = 1080, bitrate = 12_000_000)
        val dto =
            stream.toFindroidMediaStreamDto(
                id = UUID.randomUUID(),
                sourceId = "local-source-id",
                path = "",
            )

        assertEquals(1080, dto.height)
        assertEquals(12_000_000, dto.bitrate)
        assertEquals("", dto.path)
        assertNull(dto.downloadId)

        val restored = dto.toFindroidMediaStream()
        assertEquals(1080, restored.height)
        assertEquals(12_000_000, restored.bitrate)
        assertEquals("", restored.path)
    }

    @Test
    fun `legacy video metadata without bitrate remains unknown`() {
        val legacyDto =
            FindroidMediaStreamDto(
                id = UUID.randomUUID(),
                sourceId = "local-source-id",
                title = "",
                displayTitle = null,
                language = "",
                type = MediaStreamType.VIDEO,
                codec = "h264",
                isExternal = false,
                path = "",
                channelLayout = null,
                videoRangeType = null,
                height = 720,
                width = 1280,
                videoDoViTitle = null,
            )

        val restored = legacyDto.toFindroidMediaStream()

        assertEquals(720, restored.height)
        assertNull(restored.bitrate)
    }

    private fun videoStream(height: Int?, bitrate: Int?) =
        FindroidMediaStream(
            title = "",
            displayTitle = null,
            language = "",
            type = MediaStreamType.VIDEO,
            codec = "h264",
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = height,
            width = height?.times(16)?.div(9),
            videoDoViTitle = null,
            bitrate = bitrate,
        )
}
