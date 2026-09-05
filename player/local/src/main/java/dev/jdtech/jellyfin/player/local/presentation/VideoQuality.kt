package dev.jdtech.jellyfin.player.local.presentation

enum class VideoQuality(val maxStreamingBitrate: Int?, val label: String) {
    AUTO(null, "Auto"),
    MBPS_1(1_000_000, "1 Mbps"),
    MBPS_2(2_000_000, "2 Mbps"),
    MBPS_4(4_000_000, "4 Mbps"),
    MBPS_8(8_000_000, "8 Mbps"),
    MBPS_12(12_000_000, "12 Mbps"),
}
