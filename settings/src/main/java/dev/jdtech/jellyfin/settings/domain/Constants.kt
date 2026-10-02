package dev.jdtech.jellyfin.settings.domain

object Constants {
    // Player - video quality
    const val VIDEO_QUALITY_ORIGINAL = -1

    // Player - Media Segments
    object PlayerMediaSegmentsAutoSkip {
        const val ALWAYS = "always"
        const val PIP = "pip"
    }

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L
}
