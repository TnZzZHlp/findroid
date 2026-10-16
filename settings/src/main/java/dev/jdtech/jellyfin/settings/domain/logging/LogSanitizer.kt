package dev.jdtech.jellyfin.settings.domain.logging

import java.net.URI

object LogSanitizer {
    private val authorization =
        Regex(
            "(?im)\\b(?:authorization|proxy-authorization|x-emby-authorization|cookie|set-cookie)\\s*[:=][^\\r\\n]*"
        )
    private val credentials =
        Regex(
            """(?i)(["']?\b(?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|passwd|pw|secret|authorization|cookie)["']?[ \t]*[:=][ \t]*)(?:"(?:\\.|[^"\\])*+"|'(?:\\.|[^'\\])*+'|[^,\r\n;&)\]}]*)"""
        )
    private val bearer = Regex("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9+/=_~.%-]+")
    private val url = Regex("(?i)\\b(?:https?|wss?|file|content)://[^\\s<>\\\"']+")
    private val windowsPath = Regex("(?i)\\b[A-Z]:[\\\\/][^\\r\\n\\\"']+")
    private val unixPath = Regex("""(?<![A-Za-z0-9])["']?/[^\r\n]*""")

    fun sanitize(value: String): String {
        var result =
            authorization.replace(value) {
                "${it.value.substringBefore(':').substringBefore('=')}=[redacted]"
            }
        result = credentials.replace(result) { "${it.groupValues[1]}[redacted]" }
        result = bearer.replace(result) { "${it.groupValues[1]} [redacted]" }
        result =
            url.replace(result) { match ->
                val uri = runCatching { URI(match.value) }.getOrNull()
                if (uri?.scheme.equals("file", true) || uri?.scheme.equals("content", true)) {
                    "[path]"
                } else {
                    uri?.host?.let { "[host=$it]" } ?: "[url]"
                }
            }
        result = windowsPath.replace(result, "[path]")
        return unixPath.replace(result, "[path]").replace('\u0000', ' ')
    }
}
