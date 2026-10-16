package dev.jdtech.jellyfin.logging

import dev.jdtech.jellyfin.settings.domain.logging.LogSanitizer
import timber.log.Timber

class SanitizedDebugTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, LogSanitizer.sanitize(message.take(32_000)), null)
    }
}
