package dev.jdtech.jellyfin.settings.domain.logging

import java.time.Instant

enum class LogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7),
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    fun formatted(): String = "${Instant.ofEpochMilli(timestamp)} ${level.name} [$tag] $message"
}
