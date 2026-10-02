package dev.jdtech.jellyfin.player.local.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityTest {
    @Test
    fun `options always start with the original quality`() {
        val options = VideoQuality.optionsFor(sourceHeight = 1080, sourceBitrate = 8_000_000)

        val original = options.first()
        assertTrue(original.isOriginal)
        assertEquals(1080, original.height)
        assertEquals(8_000_000, original.bitrate)
    }

    @Test
    fun `options do not exceed the source bitrate`() {
        val options = VideoQuality.optionsFor(sourceHeight = 1080, sourceBitrate = 5_000_000)

        val bitrates = options.filterNot { it.isOriginal }.map { it.maxStreamingBitrate }
        assertTrue(bitrates.isNotEmpty())
        assertTrue(bitrates.all { it != null && it <= 5_000_000 })
    }

    @Test
    fun `options fall back to the source resolution when the bitrate is unknown`() {
        val options = VideoQuality.optionsFor(sourceHeight = 720, sourceBitrate = null)

        val heights = options.filterNot { it.isOriginal }.map { it.height }
        assertTrue(heights.isNotEmpty())
        assertTrue(heights.all { it != null && it <= 720 })
    }

    @Test
    fun `unknown source info keeps the full ladder`() {
        val options = VideoQuality.optionsFor(sourceHeight = null, sourceBitrate = null)

        assertEquals(7, options.size)
    }

    @Test
    fun `capped quality label shows only its bitrate limit`() {
        val label =
            VideoQuality(maxStreamingBitrate = 4_000_000, height = 720, bitrate = 4_000_000)
                .formatLabel(originalLabel = "Original", formatBitrateCap = { "Up to $it" })

        assertEquals("Up to 4 Mbps", label)

        val fractionalBitrateLabel =
            VideoQuality(maxStreamingBitrate = 1_500_000, bitrate = 1_500_000)
                .formatLabel(originalLabel = "Original", formatBitrateCap = { "Up to $it" })
        assertEquals("Up to 1.5 Mbps", fractionalBitrateLabel)
    }

    @Test
    fun `original quality label keeps source resolution and bitrate`() {
        val label =
            VideoQuality.original(height = 1080, bitrate = 8_000_000)
                .formatLabel(originalLabel = "Original", formatBitrateCap = { "Up to $it" })

        assertEquals("Original · 1080p · 8 Mbps", label)
    }

    @Test
    fun `stored bitrate is restored and resolves to the original quality`() {
        assertNull(VideoQuality.fromStoredBitrate(-1).maxStreamingBitrate)
        assertEquals(4_000_000, VideoQuality.fromStoredBitrate(4_000_000).maxStreamingBitrate)
        assertEquals(720, VideoQuality.fromStoredBitrate(4_000_000).height)
    }
}
