package dev.jdtech.jellyfin.settings.domain.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun `long credential values cannot crash log sanitization`() {
        assertEquals("token=[redacted]", LogSanitizer.sanitize("token=\"${"s".repeat(20_000)}\""))
    }

    @Test
    fun `diagnostics remove the rest of the current line after unix paths with spaces`() {
        val examples =
            listOf(
                "error=/storage/emulated/0/Private Folder/secret final.nfo",
                "error=/storage/emulated/0/Private Folder/Secret File",
                "error=\"/storage/emulated/0/Private Folder/Secret Final.nfo\"",
            )
        val followingLines = "server=media.example title=Example Movie\nNext diagnostic"

        examples.forEach { example ->
            val input =
                "host=before.example title=Before $example; server=after.example\n$followingLines"
            assertEquals(
                "host=before.example title=Before error=[path]\n$followingLines",
                LogSanitizer.sanitize(input),
            )
        }
    }

    @Test
    fun `diagnostics remove credentials and locations while keeping useful metadata`() {
        val message =
            """
            Authorization: MediaBrowser Token="header-secret", UserId="user"
            {"AccessToken":"json-secret","Password":"escaped\"secret"}
            access_token=bare secret with spaces, item=movie-42
            request=https://user:uri-secret@media.example:8096/Videos/42?api_key=query-secret
            subtitle=content://provider/private/subtitle.srt
            local=/storage/emulated/0/Movies/private.mkv
            windows=C:\Users\Private\movie.mkv
            java.lang.Exception: failed
                at example.Player.load(Player.kt:42)
            title=Example Movie
            """
                .trimIndent()

        val result = LogSanitizer.sanitize(message)

        listOf(
                "header-secret",
                "json-secret",
                "escaped",
                "secret",
                "bare secret with spaces",
                "uri-secret",
                "query-secret",
                "/Videos/42",
                "/storage/",
                "private.mkv",
                "subtitle.srt",
                "C:\\Users",
                "movie.mkv",
            )
            .forEach { assertFalse("Unexpected private value: $it", result.contains(it)) }
        assertTrue(result.contains("media.example"))
        assertTrue(result.contains("movie-42"))
        assertTrue(result.contains("Example Movie"))
        assertTrue(result.contains("Player.kt:42"))
    }
}
