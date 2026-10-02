package dev.jdtech.jellyfin.player.local.presentation

import android.content.Context
import dev.jdtech.jellyfin.player.local.R
import java.util.Locale

/**
 * A selectable video quality.
 *
 * [maxStreamingBitrate] is the bitrate cap requested from the server, `null` means the original
 * quality (direct play). For capped options, [height] is only used to filter presets when source
 * bitrate is unknown; it does not promise an output resolution. The original option's [height] and
 * [bitrate] describe the current media source.
 */
data class VideoQuality(
    val maxStreamingBitrate: Int?,
    val height: Int? = null,
    val bitrate: Int? = null,
) {
    val isOriginal: Boolean
        get() = maxStreamingBitrate == null

    companion object {
        private data class LadderStep(val height: Int, val bitrate: Int)

        private val ladder =
            listOf(
                LadderStep(height = 360, bitrate = 720_000),
                LadderStep(height = 480, bitrate = 1_500_000),
                LadderStep(height = 720, bitrate = 4_000_000),
                LadderStep(height = 1080, bitrate = 8_000_000),
                LadderStep(height = 1440, bitrate = 16_000_000),
                LadderStep(height = 2160, bitrate = 40_000_000),
            )

        fun original(height: Int? = null, bitrate: Int? = null): VideoQuality =
            VideoQuality(maxStreamingBitrate = null, height = height, bitrate = bitrate)

        fun fromStoredBitrate(bitrate: Int): VideoQuality {
            if (bitrate <= 0) return original()
            return ladder
                .firstOrNull { it.bitrate == bitrate }
                ?.let { VideoQuality(it.bitrate, height = it.height, bitrate = it.bitrate) }
                ?: VideoQuality(maxStreamingBitrate = bitrate, bitrate = bitrate)
        }

        /**
         * Quality options for a media source: the original quality plus every ladder step that does
         * not exceed the source bitrate (or resolution when the bitrate is unknown).
         */
        fun optionsFor(sourceHeight: Int?, sourceBitrate: Int?): List<VideoQuality> {
            val presets =
                ladder
                    .filter { step ->
                        when {
                            sourceBitrate != null && sourceBitrate > 0 ->
                                step.bitrate <= sourceBitrate
                            sourceHeight != null && sourceHeight > 0 -> step.height <= sourceHeight
                            else -> true
                        }
                    }
                    .map { VideoQuality(it.bitrate, height = it.height, bitrate = it.bitrate) }
            return buildList {
                add(original(height = sourceHeight, bitrate = sourceBitrate))
                addAll(presets)
            }
        }
    }
}

fun VideoQuality.formatLabel(context: Context): String =
    formatLabel(
        originalLabel = context.getString(R.string.video_quality_original),
        formatBitrateCap = { context.getString(R.string.video_quality_up_to, it) },
    )

internal fun VideoQuality.formatLabel(
    originalLabel: String,
    formatBitrateCap: (String) -> String,
): String {
    val parts = mutableListOf<String>()
    if (isOriginal) {
        parts.add(originalLabel)
        height?.let { parts.add("${it}p") }
        bitrate?.let { parts.add(formatBitrate(it)) }
    } else {
        bitrate?.let { parts.add(formatBitrateCap(formatBitrate(it))) }
            ?: maxStreamingBitrate?.let { parts.add(formatBitrateCap(formatBitrate(it))) }
    }
    return parts.joinToString(" · ")
}

private fun formatBitrate(bitrate: Int): String {
    return if (bitrate >= 1_000_000) {
        val mbps = bitrate / 1_000_000.0
        if (mbps == mbps.toInt().toDouble()) {
            "${mbps.toInt()} Mbps"
        } else {
            String.format(Locale.US, "%.1f Mbps", mbps)
        }
    } else {
        "${bitrate / 1_000} Kbps"
    }
}
